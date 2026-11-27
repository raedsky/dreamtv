package com.mytv.player.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/** الإعدادات + قوائم التشغيل المحفوظة (يمكن حفظ أكثر من قائمة والتبديل بينها). */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listType = object : TypeToken<MutableList<Profile>>() {}.type

    // ---------------- قوائم التشغيل ----------------

    fun profiles(): MutableList<Profile> {
        val json = sp.getString("profiles", null)
        if (json == null) {
            // ترحيل من النسخ القديمة (قائمة واحدة فقط)
            val legacy = sp.getString("profile", null)?.let {
                runCatching { gson.fromJson(it, Profile::class.java) }.getOrNull()
            } ?: return mutableListOf()
            val p = legacy.copy(id = UUID.randomUUID().toString(), name = defaultName(legacy))
            saveAll(listOf(p))
            sp.edit().putString("active", p.id).remove("profile").apply()
            return mutableListOf(p)
        }
        return runCatching { gson.fromJson<MutableList<Profile>>(json, listType) }.getOrNull()
            ?: mutableListOf()
    }

    private fun saveAll(list: List<Profile>) {
        sp.edit().putString("profiles", gson.toJson(list)).apply()
    }

    /** قائمة التشغيل النشطة. */
    val profile: Profile?
        get() {
            val list = profiles()
            val id = sp.getString("active", null)
            return list.firstOrNull { it.id == id } ?: list.firstOrNull()
        }

    fun saveProfile(p: Profile) {
        val list = profiles()
        val i = list.indexOfFirst { it.id == p.id }
        if (i >= 0) list[i] = p else list += p
        saveAll(list)
    }

    fun deleteProfile(id: String) {
        val list = profiles()
        list.removeAll { it.id == id }
        saveAll(list)
        if (sp.getString("active", null) == id) sp.edit().remove("active").apply()
    }

    fun setActive(id: String) {
        sp.edit().putString("active", id).apply()
    }

    // ---------------- إعدادات التشغيل ----------------

    /** "internal" = المشغل المدمج، "external" = VLC / MX Player / أي مشغل مثبت */
    var playerMode: String
        get() = sp.getString("player", PLAYER_INTERNAL) ?: PLAYER_INTERNAL
        set(v) { sp.edit().putString("player", v).apply() }

    /** صيغة البث المباشر في Xtream: "ts" أو "m3u8" */
    var liveFormat: String
        get() = sp.getString("live_format", "ts") ?: "ts"
        set(v) { sp.edit().putString("live_format", v).apply() }

    /** تقييد الجودة إلى SD عندما يوفر السيرفر أكثر من جودة (يقلل التقطيع) */
    var sdOnly: Boolean
        get() = sp.getBoolean("sd_only", false)
        set(v) { sp.edit().putBoolean("sd_only", v).apply() }

    // ---------------- لوحة التحكم ----------------

    /** آخر رد من اللوحة (JSON) للعمل بدون إنترنت */
    var panelCache: String?
        get() = sp.getString("panel_cache", null)
        set(v) { sp.edit().putString("panel_cache", v).apply() }

    /** آخر رسالة من اللوحة رآها المستخدم */
    var seenMessageId: Long
        get() = sp.getLong("seen_msg", 0L)
        set(v) { sp.edit().putLong("seen_msg", v).apply() }

    /**
     * يدمج قوائم اللوحة مع قوائم المستخدم: يضيف الجديدة، يحدّث الموجودة، ويحذف التي أزالها المدير.
     * يرجع true إذا تغيّرت القائمة النشطة.
     */
    fun syncPanelPlaylists(panel: List<Profile>): Boolean {
        val before = profile?.id
        val beforeActive = profile
        val list = profiles()
        val panelIds = panel.map { it.id }.toSet()
        list.removeAll { it.id.startsWith("panel:") && it.id !in panelIds }
        for (p in panel) {
            val i = list.indexOfFirst { it.id == p.id }
            if (i >= 0) list[i] = p.copy(expiry = list[i].expiry) else list += p
        }
        saveAll(list)
        val after = profile
        return before != after?.id || beforeActive != after
    }

    /**
     * ترتيب العناصر: "default" (ترتيب السيرفر) أو "added" (الأحدث) أو "name" (أبجدي).
     * الأفلام والمسلسلات افتراضياً "الأحدث" حتى يظهر الجديد أول الباقة وأول الكل.
     */
    fun sortMode(type: ContentType): String =
        sp.getString("sort_${type.name}", null) ?: if (type == ContentType.LIVE) "default" else "added"

    fun setSortMode(type: ContentType, v: String) { sp.edit().putString("sort_${type.name}", v).apply() }

    companion object {
        const val PLAYER_INTERNAL = "internal"
        const val PLAYER_EXTERNAL = "external"

        fun defaultName(p: Profile): String =
            if (p.kind == Profile.XTREAM) {
                p.server.substringAfter("://").substringBefore('/').substringBefore(':')
                    .ifBlank { "Xtream" }
            } else "M3U"
    }
}
