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
