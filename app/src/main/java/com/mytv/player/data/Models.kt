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
    val url: String?
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
