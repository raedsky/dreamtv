package com.mytv.player

/**
 * ربط التطبيق بلوحة التحكم.
 * انسخ القيمتين من صفحة "الإعدادات" في اللوحة، ثم أعد بناء التطبيق.
 * إذا تركتهما فارغتين يعمل التطبيق بدون لوحة.
 */
object PanelConfig {
    /** مثال: https://your-domain.com/panel/api.php */
    const val API_URL = ""

    /** مفتاح الـ API من صفحة الإعدادات في اللوحة */
    const val API_KEY = ""

    val enabled: Boolean get() = API_URL.isNotBlank() && API_KEY.isNotBlank()
}
