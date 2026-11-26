package com.mytv.player.data

import com.mytv.player.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException

class Repository(private val prefs: Prefs) {

    data class Content(val categories: List<Category>, val items: List<StreamItem>)

    private val cache = HashMap<ContentType, Content>()
    private var m3u: List<M3uEntry>? = null
    private val mutex = Mutex()

    private fun xtream(p: Profile) = XtreamClient(App.http, p.server, p.username, p.password)

    /** يتحقق من الاشتراك قبل حفظه. */
    /** يرجع الملف الشخصي بعد التحقق (مع تاريخ الانتهاء لحسابات Xtream). */
    suspend fun login(p: Profile): Profile = withContext(Dispatchers.IO) {
        mutex.withLock {
            cache.clear()
            m3u = null
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
        val p = prefs.profile ?: throw IOException("Not logged in")
        xtream(p).seriesEpisodes(item.id)
    }

    fun clear() {
        cache.clear()
        m3u = null
    }

    private fun loadM3u(p: Profile, type: ContentType, force: Boolean): Content {
        val all = (if (force) null else m3u) ?: fetchM3u(p.m3uUrl).also { m3u = it }
        val cats = LinkedHashMap<String, Category>()
        val items = ArrayList<StreamItem>()
        for (e in all) {
            if (M3uParser.classify(e.url) != type) continue
            if (e.group !in cats) cats[e.group] = Category(e.group, e.group)
            items += StreamItem("U:${e.url}", e.url, e.name, e.logo, e.group, type, e.url)
        }
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
