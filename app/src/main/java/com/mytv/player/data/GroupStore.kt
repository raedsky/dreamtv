package com.mytv.player.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/**
 * يحفظ القروبات الخاصة والمفضلة لكل قسم (مباشر / أفلام / مسلسلات) بشكل منفصل.
 */
class GroupStore(context: Context) {
    private val sp = context.getSharedPreferences("groups", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val groupListType = object : TypeToken<MutableList<UserGroup>>() {}.type
    private val stringListType = object : TypeToken<MutableList<String>>() {}.type

    // ---------- القروبات ----------

    fun groups(type: ContentType): MutableList<UserGroup> {
        val json = sp.getString("g_${type.name}", null) ?: return mutableListOf()
        return runCatching { gson.fromJson<MutableList<UserGroup>>(json, groupListType) }
            .getOrNull() ?: mutableListOf()
    }

    private fun saveGroups(type: ContentType, list: List<UserGroup>) {
        sp.edit().putString("g_${type.name}", gson.toJson(list)).apply()
    }

    fun add(type: ContentType, name: String): UserGroup {
        val list = groups(type)
        val g = UserGroup(UUID.randomUUID().toString(), name.trim(), mutableListOf())
        list += g
        saveGroups(type, list)
        return g
    }

    fun rename(type: ContentType, id: String, name: String) {
        val list = groups(type)
        list.find { it.id == id }?.name = name.trim()
        saveGroups(type, list)
    }

    fun delete(type: ContentType, id: String) {
        val list = groups(type)
        list.removeAll { it.id == id }
        saveGroups(type, list)
    }

    fun setMember(type: ContentType, groupId: String, key: String, member: Boolean) {
        val list = groups(type)
        val g = list.find { it.id == groupId } ?: return
        if (member) {
            if (key !in g.items) g.items += key
        } else {
            g.items.remove(key)
        }
        saveGroups(type, list)
    }

    /** يضيف مجموعة عناصر دفعة واحدة (مثلاً كل قنوات تصنيف معيّن). */
    fun addAll(type: ContentType, groupId: String, keys: List<String>): Int {
        val list = groups(type)
        val g = list.find { it.id == groupId } ?: return 0
        val existing = g.items.toHashSet()
        var added = 0
        for (k in keys) if (existing.add(k)) { g.items += k; added++ }
        saveGroups(type, list)
        return added
    }

    // ---------- المفضلة ----------

    fun favorites(type: ContentType): MutableList<String> {
        val json = sp.getString("f_${type.name}", null) ?: return mutableListOf()
        return runCatching { gson.fromJson<MutableList<String>>(json, stringListType) }
            .getOrNull() ?: mutableListOf()
    }

    /** يرجع true إذا أصبح العنصر في المفضلة. */
    fun toggleFavorite(type: ContentType, key: String): Boolean {
        val list = favorites(type)
        val nowFav = if (key in list) { list.remove(key); false } else { list += key; true }
        sp.edit().putString("f_${type.name}", gson.toJson(list)).apply()
        return nowFav
    }
}
