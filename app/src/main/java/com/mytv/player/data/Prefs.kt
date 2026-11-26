package com.mytv.player.data

import android.content.Context
import com.google.gson.Gson

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val gson = Gson()

    var profile: Profile?
        get() = sp.getString("profile", null)?.let {
            runCatching { gson.fromJson(it, Profile::class.java) }.getOrNull()
        }
        set(value) {
            val editor = sp.edit()
            if (value == null) editor.remove("profile") else editor.putString("profile", gson.toJson(value))
            editor.apply()
        }
}
