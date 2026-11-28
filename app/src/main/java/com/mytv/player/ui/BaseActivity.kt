package com.mytv.player.ui

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity

/**
 * نوع الجهاز من الإعدادات: يغيّر "حجم الشاشة الافتراضي" حتى تتناسب الواجهة مع الجهاز.
 *  auto   = حسب النظام
 *  tv     = شاشة / بوكس (عناصر كبيرة واضحة من بعيد)
 *  tablet = تابلت (عناصر متوسطة ومساحة أكبر)
 *  phone  = جوال (واجهة مضغوطة للشاشات الصغيرة)
 */
object DeviceMode {
    const val AUTO = "auto"
    const val TV = "tv"
    const val TABLET = "tablet"
    const val PHONE = "phone"
    val all = listOf(AUTO, TV, TABLET, PHONE)

    fun get(ctx: Context): String =
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("device_mode", AUTO) ?: AUTO

    fun set(ctx: Context, mode: String) =
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("device_mode", mode).commit()

    /** ارتفاع الشاشة (بالعرض) الذي نصمم عليه لكل نوع، بوحدة dp */
    private fun targetShortDp(mode: String) = when (mode) {
        TV -> 540
        TABLET -> 640
        PHONE -> 380
        else -> 0
    }

    fun wrap(base: Context): Context {
        val target = targetShortDp(get(base))
        if (target == 0) return base
        val dm = base.resources.displayMetrics
        val shortPx = minOf(dm.widthPixels, dm.heightPixels)
        val longPx = maxOf(dm.widthPixels, dm.heightPixels)
        if (shortPx <= 0) return base
        val dpi = (shortPx * 160 / target).coerceIn(80, 640)
        val cfg = Configuration(base.resources.configuration)
        cfg.densityDpi = dpi
        val landscape = cfg.orientation != Configuration.ORIENTATION_PORTRAIT
        val shortDp = shortPx * 160 / dpi
        val longDp = longPx * 160 / dpi
        cfg.smallestScreenWidthDp = shortDp
        cfg.screenWidthDp = if (landscape) longDp else shortDp
        cfg.screenHeightDp = if (landscape) shortDp else longDp
        return base.createConfigurationContext(cfg)
    }
}

/** كل شاشات التطبيق ترث منها حتى يطبَّق نوع الجهاز */
open class BaseActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(DeviceMode.wrap(newBase))
    }
}
