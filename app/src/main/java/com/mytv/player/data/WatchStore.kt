package com.mytv.player.data

import android.content.Context

/**
 * يحفظ آخر مكان وصلت له في كل فيلم / حلقة، وآخر حلقة شاهدتها في كل مسلسل.
 * المفتاح: StreamItem.key للأفلام، و "E:<id>" للحلقات.
 */
class WatchStore(context: Context) {
    private val sp = context.getSharedPreferences("watch", Context.MODE_PRIVATE)

    data class Progress(val position: Long, val duration: Long) {
        val percent: Int get() = if (duration > 0) ((position * 100) / duration).toInt().coerceIn(0, 100) else 0
    }

    fun get(key: String): Progress? {
        val v = sp.getString("p_$key", null) ?: return null
        val parts = v.split(',')
        val pos = parts.getOrNull(0)?.toLongOrNull() ?: return null
        val dur = parts.getOrNull(1)?.toLongOrNull() ?: 0
        return Progress(pos, dur)
    }

    /** مكان الاستئناف (null إذا لم يبدأ أو انتهى تقريباً) */
    fun resumePosition(key: String): Long? {
        val p = get(key) ?: return null
        if (p.position < 15_000) return null
        if (p.duration > 0 && p.position > p.duration - 60_000) return null
        return p.position
    }

    fun isWatched(key: String): Boolean = sp.getBoolean("w_$key", false)

    fun save(key: String, position: Long, duration: Long, seriesKey: String?) {
        val e = sp.edit()
        val finished = duration > 0 && position >= duration * 95 / 100
        if (finished) {
            e.remove("p_$key").putBoolean("w_$key", true)
        } else if (position > 5_000) {
            e.putString("p_$key", "$position,$duration")
        }
        if (seriesKey != null) e.putString("last_$seriesKey", key)
        e.apply()
    }

    /** مفتاح آخر حلقة شوهدت في المسلسل */
    fun lastEpisode(seriesKey: String): String? = sp.getString("last_$seriesKey", null)

    companion object {
        fun episodeKey(e: Episode) = "E:${e.id}"

        fun format(ms: Long): String {
            val s = ms / 1000
            val h = s / 3600
            val m = (s % 3600) / 60
            val sec = s % 60
            return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, sec)
                   else String.format(java.util.Locale.US, "%02d:%02d", m, sec)
        }
    }
}
