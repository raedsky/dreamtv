package com.mytv.player.data

import android.content.Context

/** ثيم التطبيق — يُختار من لوحة التحكم (أو من الإعدادات). */
data class AppTheme(
    val id: String,
    val name: String,
    val bg: Int,          // لون الخلفية
    val bgEnd: Int,       // نهاية تدرج الخلفية
    val streaks: IntArray,// ألوان خطوط الضوء
    val iconStart: Int,   // تدرج الأيقونات
    val iconEnd: Int,
    val accent: Int       // لون التحديد
)

object Themes {
    private fun c(v: Long) = v.toInt()

    val all = listOf(
        AppTheme("dream", "Dream", c(0xFF000000), c(0xFF0B0614),
            intArrayOf(c(0x997B2FF7), c(0xAAC026D3), c(0x883B5BFF)), c(0xFF3B82F6), c(0xFFC026D3), c(0xFFA855F7)),
        AppTheme("ocean", "Ocean", c(0xFF00070F), c(0xFF001A2C),
            intArrayOf(c(0x990EA5E9), c(0xAA0369A1), c(0x8822D3EE)), c(0xFF22D3EE), c(0xFF3B82F6), c(0xFF06B6D4)),
        AppTheme("sunset", "Sunset", c(0xFF0A0300), c(0xFF1C0710),
            intArrayOf(c(0x99F97316), c(0xAADB2777), c(0x88F59E0B)), c(0xFFF59E0B), c(0xFFEF4444), c(0xFFF97316)),
        AppTheme("emerald", "Emerald", c(0xFF000A06), c(0xFF02180F),
            intArrayOf(c(0x9910B981), c(0xAA065F46), c(0x8834D399)), c(0xFF34D399), c(0xFF06B6D4), c(0xFF10B981)),
        AppTheme("gold", "Gold", c(0xFF080600), c(0xFF171103),
            intArrayOf(c(0x99EAB308), c(0xAAA16207), c(0x88FDE047)), c(0xFFFDE047), c(0xFFF59E0B), c(0xFFEAB308)),
        AppTheme("crimson", "Crimson", c(0xFF0A0003), c(0xFF1C030A),
            intArrayOf(c(0x99E11D48), c(0xAA7F1D1D), c(0x88FB7185)), c(0xFFFB7185), c(0xFFE11D48), c(0xFFE11D48)),
        AppTheme("ibo", "IBO", c(0xFF0E1525), c(0xFF2A1450),
            intArrayOf(c(0x553B5BFF), c(0x557B2FF7), c(0x4400C6FF)), c(0xFF00C6FF), c(0xFF8E2DE2), c(0xFFFFB300))
    )

    var current: AppTheme = all[0]
        private set

    fun load(ctx: Context) {
        val id = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("theme_id", null)
        current = all.firstOrNull { it.id == id } ?: all[0]
    }

    /** يرجع true إذا تغيّر الثيم (لإعادة رسم الشاشة) */
    fun set(ctx: Context, id: String?): Boolean {
        val t = all.firstOrNull { it.id == id } ?: return false
        if (t.id == current.id) return false
        current = t
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("theme_id", t.id).apply()
        return true
    }
}
