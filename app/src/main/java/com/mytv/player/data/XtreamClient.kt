package com.mytv.player.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder

/** عميل بسيط لواجهة Xtream Codes (player_api.php). */
class XtreamClient(
    private val http: OkHttpClient,
    server: String,
    private val user: String,
    private val pass: String,
    private val liveExt: String = "ts"
) {
    val base: String = normalizeServer(server)

    private fun request(action: String?, extra: String = ""): JsonElement {
        val url = buildString {
            append(base).append("/player_api.php?username=").append(enc(user))
            append("&password=").append(enc(pass))
            if (action != null) append("&action=").append(action)
            append(extra)
        }
        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body?.string() ?: throw IOException("Empty response")
            return try {
                JsonParser.parseString(body)
            } catch (e: Exception) {
                throw IOException("Invalid server response")
            }
        }
    }

    /** يتحقق من الحساب ويرجع تاريخ الانتهاء (ثواني Unix) إن وُجد. */
    fun authenticate(): Long? {
        val root = request(null)
        val info: JsonObject = root.takeIf { it.isJsonObject }?.asJsonObject
            ?.get("user_info")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw IOException("Invalid server response")
        if (info.str("auth") != "1") throw IOException("Wrong username or password")
        val status = info.str("status")
        if (status != null && !status.equals("Active", ignoreCase = true)) {
            throw IOException("Account status: $status")
        }
        return info.str("exp_date")?.toLongOrNull()?.takeIf { it > 0 }
    }

    fun categories(type: ContentType): List<Category> {
        val action = when (type) {
            ContentType.LIVE -> "get_live_categories"
            ContentType.MOVIE -> "get_vod_categories"
            ContentType.SERIES -> "get_series_categories"
        }
        val arr = request(action).takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return arr.mapNotNull { e ->
            val o = e.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val id = o.str("category_id") ?: return@mapNotNull null
            Category(id, o.str("category_name") ?: id)
        }
    }

    fun streams(type: ContentType): List<StreamItem> {
        val action = when (type) {
            ContentType.LIVE -> "get_live_streams"
            ContentType.MOVIE -> "get_vod_streams"
            ContentType.SERIES -> "get_series"
        }
        val arr = request(action).takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return arr.mapNotNull { e ->
            val o = e.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val name = o.str("name") ?: ""
            val cat = o.str("category_id") ?: ""
            when (type) {
                ContentType.LIVE -> {
                    val id = o.str("stream_id") ?: return@mapNotNull null
                    StreamItem("L:$id", id, name, o.str("stream_icon"), cat, type,
                        "$base/live/$user/$pass/$id.$liveExt", o.str("added")?.toLongOrNull() ?: 0,
                        num = o.str("num")?.toIntOrNull() ?: 0)
                }
                ContentType.MOVIE -> {
                    val id = o.str("stream_id") ?: return@mapNotNull null
                    val ext = o.str("container_extension")?.takeIf { it.isNotBlank() } ?: "mp4"
                    StreamItem("M:$id", id, name, o.str("stream_icon"), cat, type,
                        "$base/movie/$user/$pass/$id.$ext", o.str("added")?.toLongOrNull() ?: 0,
                        rating = rating(o))
                }
                ContentType.SERIES -> {
                    val id = o.str("series_id") ?: return@mapNotNull null
                    StreamItem("S:$id", id, name, o.str("cover"), cat, type, null,
                        o.str("last_modified")?.toLongOrNull() ?: 0, rating = rating(o))
                }
            }
        }
    }

    private fun rating(o: JsonObject): String? {
        val r = o.str("rating")?.toDoubleOrNull()?.takeIf { it > 0 }
            ?: o.str("rating_5based")?.toDoubleOrNull()?.takeIf { it > 0 }?.times(2)
            ?: return null
        return String.format(java.util.Locale.US, "%.1f", r.coerceAtMost(10.0))
    }

    private fun JsonObject.obj(name: String): JsonObject? = get(name)?.takeIf { it.isJsonObject }?.asJsonObject

    /** أول نص غير فارغ من حقل قد يكون نصاً أو مصفوفة */
    private fun JsonObject.first(name: String): String? {
        val e = get(name) ?: return null
        if (e.isJsonArray) return e.asJsonArray.firstOrNull { it.isJsonPrimitive && it.asString.isNotBlank() }?.asString
        return str(name)?.takeIf { it.isNotBlank() && it != "null" }
    }

    private fun mediaInfo(info: JsonObject?): MediaInfo? {
        info ?: return null
        fun t(vararg keys: String) = keys.firstNotNullOfOrNull { info.first(it) }?.trim()?.takeIf { it.isNotEmpty() }
        val r = t("rating")?.toDoubleOrNull()?.takeIf { it > 0 }
        return MediaInfo(
            plot = t("plot", "description"),
            genre = t("genre"),
            cast = t("cast", "actors"),
            director = t("director"),
            release = t("releasedate", "releaseDate", "release_date"),
            duration = t("duration"),
            rating = r?.let { String.format(java.util.Locale.US, "%.1f", it.coerceAtMost(10.0)) },
            backdrop = t("backdrop_path"),
            cover = t("movie_image", "cover", "cover_big"),
            trailer = t("youtube_trailer")
        )
    }

    fun vodInfo(vodId: String): MediaInfo? {
        val root = request("get_vod_info", "&vod_id=" + enc(vodId))
        return mediaInfo(root.takeIf { it.isJsonObject }?.asJsonObject?.obj("info"))
    }

    /** دليل البرامج القصير للقناة (الحالي والقادم). */
    fun shortEpg(streamId: String, limit: Int = 6): List<EpgEntry> {
        val root = request("get_short_epg", "&stream_id=" + enc(streamId) + "&limit=$limit")
        val arr = root.takeIf { it.isJsonObject }?.asJsonObject?.get("epg_listings")
            ?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        fun b64(s: String?): String? = s?.let {
            runCatching { String(android.util.Base64.decode(it, android.util.Base64.DEFAULT), Charsets.UTF_8) }.getOrDefault(it)
        }?.trim()
        return arr.mapNotNull { e ->
            val o = e.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val start = o.str("start_timestamp")?.toLongOrNull() ?: return@mapNotNull null
            val end = o.str("stop_timestamp")?.toLongOrNull() ?: return@mapNotNull null
            EpgEntry(b64(o.str("title")) ?: "", start, end, b64(o.str("description")))
        }.filter { it.end > System.currentTimeMillis() / 1000 }
    }

    fun seriesEpisodes(seriesId: String): List<Episode> = seriesDetails(seriesId).second

    fun seriesDetails(seriesId: String): Pair<MediaInfo?, List<Episode>> {
        val root = request("get_series_info", "&series_id=" + enc(seriesId))
        if (!root.isJsonObject) return null to emptyList()
        val info = mediaInfo(root.asJsonObject.obj("info"))
        val eps = root.asJsonObject.get("episodes") ?: return info to emptyList()
        // بعض السيرفرات ترجع المواسم ككائن {"1":[...]} وبعضها كمصفوفة [[...]]
        val seasons: List<JsonElement> = when {
            eps.isJsonObject -> eps.asJsonObject.entrySet().map { it.value }
            eps.isJsonArray -> eps.asJsonArray.toList()
            else -> emptyList()
        }
        val out = ArrayList<Episode>()
        for (seasonEl in seasons) {
            if (!seasonEl.isJsonArray) continue
            for (e in seasonEl.asJsonArray) {
                if (!e.isJsonObject) continue
                val o = e.asJsonObject
                val id = o.str("id") ?: continue
                val ext = o.str("container_extension")?.takeIf { it.isNotBlank() } ?: "mp4"
                val season = o.str("season")?.toIntOrNull() ?: 0
                val num = o.str("episode_num")?.toIntOrNull() ?: 0
                val ei = o.obj("info")
                out += Episode(
                    id = id,
                    title = o.str("title") ?: "Episode $num",
                    season = season,
                    number = num,
                    url = "$base/series/$user/$pass/$id.$ext",
                    icon = ei?.first("movie_image"),
                    duration = ei?.first("duration"),
                    plot = ei?.first("plot")
                )
            }
        }
        return info to out.sortedWith(compareBy({ it.season }, { it.number }))
    }

    companion object {
        fun normalizeServer(raw: String): String {
            var s = raw.trim().trimEnd('/')
            if (s.endsWith("/player_api.php")) s = s.removeSuffix("/player_api.php")
            if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "http://$s"
            return s
        }

        private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
    }
}
