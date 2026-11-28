package com.mytv.player

import android.app.Application
import com.mytv.player.data.GroupStore
import com.mytv.player.data.Prefs
import com.mytv.player.data.Repository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class App : Application() {

    lateinit var prefs: Prefs
        private set
    lateinit var groups: GroupStore
        private set
    lateinit var repo: Repository
        private set
    lateinit var watch: com.mytv.player.data.WatchStore
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        groups = GroupStore(this)
        repo = Repository(prefs)
        watch = com.mytv.player.data.WatchStore(this)
        com.mytv.player.data.Themes.load(this)
        startHeartbeat()
    }

    // ---------- نبضة "متصل" كل دقيقة ما دام التطبيق ظاهر ----------
    private var started = 0
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val beat = object : Runnable {
        override fun run() {
            if (started > 0) com.mytv.player.data.Panel.ping(this@App)
            handler.postDelayed(this, 60_000)
        }
    }

    private fun startHeartbeat() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(a: android.app.Activity) {
                if (started++ == 0) { handler.removeCallbacks(beat); handler.post(beat) }
            }
            override fun onActivityStopped(a: android.app.Activity) {
                started = (started - 1).coerceAtLeast(0)
                if (started == 0) {
                    // التطبيق راح للخلفية: نبلغ اللوحة إنه توقف عن المشاهدة
                    com.mytv.player.data.Panel.NowPlaying.clear()
                    handler.removeCallbacks(beat)
                }
            }
            override fun onActivityCreated(a: android.app.Activity, b: android.os.Bundle?) {}
            override fun onActivityResumed(a: android.app.Activity) {}
            override fun onActivityPaused(a: android.app.Activity) {}
            override fun onActivitySaveInstanceState(a: android.app.Activity, b: android.os.Bundle) {}
            override fun onActivityDestroyed(a: android.app.Activity) {}
        })
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android) MyTV/1.0"

        lateinit var instance: App
            private set

        val http: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder().header("User-Agent", USER_AGENT).build()
                    )
                }
                .build()
        }
    }
}
