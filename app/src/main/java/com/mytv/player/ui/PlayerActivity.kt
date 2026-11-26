package com.mytv.player.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.mytv.player.App
import com.mytv.player.R

/** قائمة التشغيل الحالية (تُستخدم لتقليب القنوات بالأسهم في المباشر). */
object PlayQueue {
    data class Entry(val title: String, val url: String)

    var entries: List<Entry> = emptyList()
        private set
    var index = 0
    var live = false
        private set

    fun set(list: List<Entry>, start: Int, live: Boolean) {
        entries = list
        index = start.coerceIn(0, (list.size - 1).coerceAtLeast(0))
        this.live = live
    }

    val current: Entry? get() = entries.getOrNull(index)
}

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    companion object {
        fun start(ctx: Context) = ctx.startActivity(Intent(ctx, PlayerActivity::class.java))
    }

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var tvTitle: TextView
    private val hideTitle = Runnable { tvTitle.isVisible = false }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (PlayQueue.current == null) { finish(); return }
        setContentView(R.layout.activity_player)
        playerView = findViewById(R.id.playerView)
        tvTitle = findViewById(R.id.tvTitle)
        playerView.keepScreenOn = true
        if (PlayQueue.live) playerView.controllerAutoShow = false

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, playerView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onStart() {
        super.onStart()
        if (::playerView.isInitialized) initPlayer()
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
    }

    private fun initPlayer() {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(App.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val dataSource = DefaultDataSource.Factory(this, http)
        val p = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(dataSource))
            .build()
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(
                    this@PlayerActivity,
                    getString(R.string.playback_error, error.errorCodeName),
                    Toast.LENGTH_LONG
                ).show()
            }
        })
        playerView.player = p
        player = p
        playCurrent()
    }

    private fun playCurrent() {
        val e = PlayQueue.current ?: return
        val p = player ?: return
        val builder = MediaItem.Builder().setUri(e.url)
        if (e.url.contains(".m3u8", ignoreCase = true)) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        p.setMediaItem(builder.build())
        p.prepare()
        p.playWhenReady = true
        showTitle(e.title)
    }

    private fun showTitle(title: String) {
        val pos = if (PlayQueue.live) "${PlayQueue.index + 1}/${PlayQueue.entries.size}  " else ""
        tvTitle.text = pos + title
        tvTitle.isVisible = true
        tvTitle.removeCallbacks(hideTitle)
        tvTitle.postDelayed(hideTitle, 4000)
    }

    private fun zap(delta: Int) {
        val size = PlayQueue.entries.size
        if (size <= 1) return
        PlayQueue.index = (PlayQueue.index + delta + size) % size
        playCurrent()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (PlayQueue.live && event.action == KeyEvent.ACTION_DOWN && !playerView.isControllerFullyVisible) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { zap(-1); return true }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { zap(1); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
