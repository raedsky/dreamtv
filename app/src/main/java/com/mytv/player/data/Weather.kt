package com.mytv.player.data

import com.google.gson.JsonParser
import com.mytv.player.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import kotlin.math.roundToInt

/**
 * المنطقة ودرجة الحرارة للشاشة الرئيسية.
 * الموقع التقريبي من عنوان الإنترنت (ipwho.is) والطقس من open-meteo.com — كلاهما مجاني وبدون مفتاح.
 */
object Weather {
    data class Info(val place: String, val tempC: Int?)

    private var cached: Info? = null
    private var cachedAt = 0L
    private const val TTL_MS = 30 * 60 * 1000L

    suspend fun get(): Info? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        cached?.let { if (now - cachedAt < TTL_MS) return@withContext it }
        val info = runCatching {
            val geo = JsonParser.parseString(fetch("https://ipwho.is/")).asJsonObject
            if (geo.get("success")?.asBoolean == false) return@runCatching null
            val city = geo.str("city")
            val country = geo.str("country")
            val lat = geo.str("latitude")
            val lon = geo.str("longitude")
            val temp = if (lat != null && lon != null) runCatching {
                val w = JsonParser.parseString(
                    fetch("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m")
                ).asJsonObject
                w.getAsJsonObject("current").get("temperature_2m").asDouble.roundToInt()
            }.getOrNull() else null
            Info(listOfNotNull(city, country).joinToString("، "), temp)
        }.getOrNull()
        if (info != null) { cached = info; cachedAt = now }
        info
    }

    private fun fetch(url: String): String =
        App.http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            r.body?.string() ?: ""
        }
}
