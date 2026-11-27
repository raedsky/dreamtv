package com.mytv.player

/**
 * ربط التطبيق بلوحة التحكم.
 * إذا تركت API_URL فارغاً يعمل التطبيق بدون لوحة.
 */
object PanelConfig {
    /** رابط api.php في لوحة التحكم */
    const val API_URL = "https://skytv.online/panel/api.php"

    /**
     * اختياري: يُستخدم فقط إذا فعّلت "طلب المفتاح" في اللوحة.
     * الحماية الأساسية أن كل جهاز مربوط بمفتاحه السري الخاص.
     */
    const val API_KEY = ""

    /** كود الموزّع (يُعبّأ تلقائياً من APK Builder لكل موزّع) — فارغ = التطبيق الرئيسي */
    const val RESELLER = ""

    /** السيرفر الافتراضي إذا لم تصل قائمة السيرفرات من اللوحة بعد */
    const val DEFAULT_SERVER = "http://dreammax.lol"
    const val DEFAULT_SERVER_NAME = "DreamMax"

    val enabled: Boolean get() = API_URL.isNotBlank()
}
