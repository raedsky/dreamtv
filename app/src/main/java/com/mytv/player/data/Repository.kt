package com.mytv.player.data

import com.mytv.player.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException

/** يستخرج (اسم المسلسل، الموسم، الحلقة) من أسماء مثل "Name S01 E05" أو "الاسم الموسم 1 الحلقة 5". */
internal fun parseEpisode(title: String): Triple<String, Int, Int>? {
    val patterns = listOf(
        Regex("""^(.*?)[\s._\-|:]*[Ss](\d{1,2})[\s._\-]*[Ee][Pp]?(\d{1,4})"""),
        Regex("""^(.*?)[\s._\-|:]*(\d{1,2})[xX](\d{1,3})\b"""),
        Regex("""^(.*?)\s*(?:الموسم|موسم)\s*(\d{1,2})\s*[-_|:]*\s*(?:الحلقة|حلقة)\s*(\d{1,4})""")
    )
    for (r in patterns) {
        val m = r.find(title) ?: continue
        val name = m.groupValues[1].trim().trimEnd('-', '_', '|', ':', '.').trim()
        if (name.isEmpty()) continue
        return Triple(name, m.groupValues[2].toInt(), m.groupValues[3].toInt())
    }
    return null
}

private const val M3U_SERIES = "MS:"

class Repository(private val prefs: Prefs) {

    data class Content(val categories: List<Category>, val items: List<StreamItem>)

    private val cache = HashMap<ContentType, Content>()
    private var m3u: List<M3uEntry>? = null
    /** حلقات مسلسلات M3U بعد تجميعها: مفتاح المسلسل ← الحلقات */
    private val m3uEpisodes = HashMap<String, List<Episode>>()
    private val mutex = Mutex()

    private fun asXtream(p: Profile): Profile? {
        val (server, user, pass) = xtreamFromM3u(p.m3uUrl) ?: return null
        return p.copy(kind = Profile.XTREAM, server = server, username = user, password = pass)
    }

    private fun xtream(p: Profile) = XtreamClient(App.http, p.server, p.username, p.password, prefs.liveFormat)

    /** يتحقق من الاشتراك قبل حفظه. */
    /** يرجع الملف الشخصي بعد التحقق (مع تاريخ الانتهاء لحسابات Xtream). */
    suspend fun login(p: Profile): Profile = withContext(Dispatchers.IO) {
        mutex.withLock {
            cache.clear()
            m3u = null
            // رابط M3U من سيرفر Xtream؟ نتحول لاتصال Xtream (مكتبة مرتبة)
            val converted = if (p.kind == Profile.M3U) asXtream(p) else null
            if (converted != null) {
                runCatching { converted.copy(expiry = xtream(converted).authenticate()) }.getOrNull()
                    ?.let { return@withLock it }
            }
            if (p.kind == Profile.XTREAM) {
                p.copy(expiry = xtream(p).authenticate())
            } else {
                val list = fetchM3u(p.m3uUrl)
                if (list.isEmpty()) throw IOException("Playlist is empty")
                m3u = list
                p
            }
        }
    }

    suspend fun load(type: ContentType, force: Boolean = false): Content = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!force) cache[type]?.let { return@withLock it }
            val p = prefs.profile ?: throw IOException("Not logged in")
            val content = if (p.kind == Profile.XTREAM) {
                val x = xtream(p)
                Content(x.categories(type), x.streams(type))
            } else {
                loadM3u(p, type, force)
            }
            cache[type] = content
            content
        }
    }

    suspend fun episodes(item: StreamItem): List<Episode> = withContext(Dispatchers.IO) {
        if (item.key.startsWith(M3U_SERIES)) return@withContext m3uEpisodes[item.key] ?: emptyList()
        val p = prefs.profile ?: throw IOException("Not logged in")
        xtream(p).seriesEpisodes(item.id)
    }

    fun clear() {
        cache.clear()
        m3u = null
        m3uEpisodes.clear()
    }

    private fun loadM3u(p: Profile, type: ContentType, force: Boolean): Content {
        val all = (if (force) null else m3u) ?: fetchM3u(p.m3uUrl).also { m3u = it }
        if (type == ContentType.SERIES) {
            return buildM3uSeries(all.filter { M3uParser.classify(it.url) == ContentType.SERIES })
        }
        val cats = LinkedHashMap<String, Category>()
        val items = ArrayList<StreamItem>()
        for (e in all) {
            if (M3uParser.classify(e.url) != type) continue
            if (e.group !in cats) cats[e.group] = Category(e.group, e.group)
            items += StreamItem("U:${e.url}", e.url, e.name, e.logo, e.group, type, e.url)
        }
        return Content(cats.values.toList(), items)
    }

    /**
     * في قوائم M3U كل حلقة تأتي كعنصر مستقل ("Name S01 E05").
     * هنا نجمعها: مسلسل واحد ← مواسم ← حلقات.
     */
    private fun buildM3uSeries(entries: List<M3uEntry>): Content {
        class Acc(val name: String, val group: String, var logo: String?) {
            val eps = ArrayList<Episode>()
        }
        val cats = LinkedHashMap<String, Category>()
        val series = LinkedHashMap<String, Acc>()
        val singles = ArrayList<StreamItem>()
        for (e in entries) {
            if (e.group !in cats) cats[e.group] = Category(e.group, e.group)
            val parsed = parseEpisode(e.name)
            if (parsed == null) {
                singles += StreamItem("U:${e.url}", e.url, e.name, e.logo, e.group, ContentType.SERIES, e.url)
                continue
            }
            val (name, season, number) = parsed
            val key = "$M3U_SERIES${e.group}|${name.lowercase()}"
            val acc = series.getOrPut(key) { Acc(name, e.group, e.logo) }
            if (acc.logo == null) acc.logo = e.logo
            acc.eps += Episode(e.url, e.name, season, number, e.url, e.logo)
        }
        m3uEpisodes.clear()
        val items = ArrayList<StreamItem>()
        for ((key, a) in series) {
            m3uEpisodes[key] = a.eps.sortedWith(compareBy({ it.season }, { it.number }))
            // صورة المسلسل = الصورة المتكررة أكثر بين حلقاته (غالباً البوستر الحقيقي)
            val poster = a.eps.mapNotNull { it.icon?.takeIf { u -> u.isNotBlank() } }
                .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: a.logo
            items += StreamItem(key, key, a.name, poster, a.group, ContentType.SERIES, null)
        }
        items += singles
        return Content(cats.values.toList(), items)
    }

    private fun fetchM3u(url: String): List<M3uEntry> {
        App.http.newCall(Request.Builder().url(url.trim()).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val body = r.body ?: throw IOException("Empty response")
            return M3uParser.parse(body.charStream().buffered())
        }
    }
}
