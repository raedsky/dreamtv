package com.mytv.player.data

import com.google.gson.JsonObject

enum class ContentType { LIVE, MOVIE, SERIES }

data class Category(val id: String, val name: String)

/**
 * عنصر واحد: قناة أو فيلم أو مسلسل.
 * [key] معرّف فريد يُحفظ داخل القروبات والمفضلة.
 * [url] يكون null لمسلسلات Xtream لأن الحلقات تُجلب من صفحة المسلسل.
 */
data class StreamItem(
    val key: String,
    val id: String,
    val name: String,
    val icon: String?,
    val categoryId: String,
    val type: ContentType,
    val url: String?,
    /** وقت الإضافة (ثواني Unix) للترتيب حسب الأحدث — 0 إذا غير معروف */
    val added: Long = 0
)

data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val number: Int,
    val url: String,
    val icon: String?
)

/** قروب خاص ينشئه المستخدم. [items] قائمة مفاتيح StreamItem.key بالترتيب. */
data class UserGroup(
    val id: String,
    var name: String,
    val items: MutableList<String> = mutableListOf()
)

data class Profile(
    val kind: String,
    val id: String = "",
    val name: String = "",
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val m3uUrl: String = "",
    /** تاريخ انتهاء الاشتراك (ثواني Unix) أو null = غير محدود / غير معروف */
    val expiry: Long? = null
) {
    companion object {
        const val XTREAM = "xtream"
        const val M3U = "m3u"
    }
}

/** يقرأ حقلاً كنص سواء كان رقماً أو نصاً في JSON. */
internal fun JsonObject.str(name: String): String? {
    val e = get(name) ?: return null
    if (e.isJsonNull) return null
    return if (e.isJsonPrimitive) e.asString else e.toString()
}

/**
 * أغلب روابط M3U تأتي من سيرفرات Xtream بهذا الشكل:
 *   http://host:port/get.php?username=USER&password=PASS&type=m3u_plus
 * نستخرج منها بيانات Xtream لنحصل على مكتبة مرتبة (صور المسلسلات الصحيحة، المواسم، تاريخ الإضافة).
 */
fun xtreamFromM3u(url: String): Triple<String, String, String>? {
    val u = runCatching { java.net.URI(url.trim()) }.getOrNull() ?: return null
    if (u.path?.endsWith("get.php", ignoreCase = true) != true) return null
    val host = u.host ?: return null
    val params = (u.rawQuery ?: return null).split('&').mapNotNull {
        val i = it.indexOf('=')
        if (i <= 0) null
        else it.substring(0, i).lowercase() to runCatching { java.net.URLDecoder.decode(it.substring(i + 1), "UTF-8") }.getOrDefault(it.substring(i + 1))
    }.toMap()
    val user = params["username"]?.takeIf { it.isNotEmpty() } ?: return null
    val pass = params["password"]?.takeIf { it.isNotEmpty() } ?: return null
    val port = if (u.port > 0) ":${u.port}" else ""
    return Triple("${u.scheme ?: "http"}://$host$port", user, pass)
}
