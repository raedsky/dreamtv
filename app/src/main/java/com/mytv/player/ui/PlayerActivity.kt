package com.mytv.player.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.content.pm.PackageManager
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
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

/** إنشاء مشغّل موحّد للمعاينة وملء الشاشة. */
object Players {
    @OptIn(UnstableApi::class)
    fun create(ctx: Context): ExoPlayer {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(App.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val dataSource = DefaultDataSource.Factory(ctx, http)
        return ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(ctx).setDataSourceFactory(dataSource))
            .build()
    }

    fun mediaItem(url: String): MediaItem {
        val b = MediaItem.Builder().setUri(url)
        if (url.contains(".m3u8", ignoreCase = true)) b.setMimeType(MimeTypes.APPLICATION_M3U8)
        return b.build()
    }
}

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
    private lateinit var btnBack: View
    private lateinit var zapButtons: View
    private lateinit var gestures: GestureDetector
    private var hasTouch = false
    private val hideTitle = Runnable { tvTitle.isVisible = false }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (PlayQueue.current == null) { finish(); return }
        setContentView(R.layout.activity_player)
        playerView = findViewById(R.id.playerView)
        tvTitle = findViewById(R.id.tvTitle)
        playerView.keepScreenOn = true
        if (PlayQueue.live) playerView.controllerAutoShow = false
        hasTouch = packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)

        // أزرار اللمس: رجوع + تغيير القناة
        btnBack = findViewById(R.id.btnBack)
        zapButtons = findViewById(R.id.zapButtons)
        btnBack.setOnClickListener { finish() }
        findViewById<View>(R.id.btnChUp).setOnClickListener { zap(-1) }
        findViewById<View>(R.id.btnChDown).setOnClickListener { zap(1) }
        if (!hasTouch) {
            // في الشاشات والبوكسات لا نخلي الأزرار تسرق تركيز الريموت
            for (id in intArrayOf(R.id.btnBack, R.id.btnChUp, R.id.btnChDown)) findViewById<View>(id).isFocusable = false
        }
        playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            setOverlayVisible(visibility == View.VISIBLE)
        })
        setOverlayVisible(false)

        // السحب لأعلى/لأسفل على الشاشة يغيّر القناة
        gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (!PlayQueue.live || e1 == null) return false
                val dy = e2.y - e1.y
                val dx = e2.x - e1.x
                if (Math.abs(dy) > 120 && Math.abs(dy) > Math.abs(dx) * 1.5f) {
                    zap(if (dy < 0) 1 else -1)
                    return true
                }
                return false
            }
        })

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
        val p = Players.create(this)
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
        // على التابلت والجوال: نُظهر الأزرار في البداية حتى يعرف المستخدم مكانها
        if (hasTouch) playerView.showController()
    }

    private fun setOverlayVisible(visible: Boolean) {
        btnBack.isVisible = visible
        zapButtons.isVisible = visible && PlayQueue.live && PlayQueue.entries.size > 1
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (::gestures.isInitialized && gestures.onTouchEvent(ev)) return true
        return super.dispatchTouchEvent(ev)
    }

    private fun playCurrent() {
        val e = PlayQueue.current ?: return
        val p = player ?: return
        p.setMediaItem(Players.mediaItem(e.url))
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
