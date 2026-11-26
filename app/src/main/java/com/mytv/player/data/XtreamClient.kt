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
                        "$base/live/$user/$pass/$id.$liveExt")
                }
                ContentType.MOVIE -> {
                    val id = o.str("stream_id") ?: return@mapNotNull null
                    val ext = o.str("container_extension")?.takeIf { it.isNotBlank() } ?: "mp4"
                    StreamItem("M:$id", id, name, o.str("stream_icon"), cat, type,
                        "$base/movie/$user/$pass/$id.$ext")
                }
                ContentType.SERIES -> {
                    val id = o.str("series_id") ?: return@mapNotNull null
                    StreamItem("S:$id", id, name, o.str("cover"), cat, type, null)
                }
            }
        }
    }

    fun seriesEpisodes(seriesId: String): List<Episode> {
        val root = request("get_series_info", "&series_id=" + enc(seriesId))
        if (!root.isJsonObject) return emptyList()
        val eps = root.asJsonObject.get("episodes") ?: return emptyList()
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
                val img = o.get("info")?.takeIf { it.isJsonObject }?.asJsonObject?.str("movie_image")
                out += Episode(
                    id = id,
                    title = o.str("title") ?: "Episode $num",
                    season = season,
                    number = num,
                    url = "$base/series/$user/$pass/$id.$ext",
                    icon = img
                )
            }
        }
        return out.sortedWith(compareBy({ it.season }, { it.number }))
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
