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
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
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
        // مخزن أكبر = تقطيع أقل في البث المباشر
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(30_000, 60_000, 2_500, 5_000)
            .build()
        val player = ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(ctx).setDataSourceFactory(dataSource))
            .setLoadControl(loadControl)
            .build()
        if (App.instance.prefs.sdOnly) {
            // إذا كان السيرفر يوفر أكثر من جودة نختار SD
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setMaxVideoSizeSd()
                .build()
        }
        return player
    }

    fun mediaItem(url: String): MediaItem {
        val b = MediaItem.Builder().setUri(url)
        if (url.contains(".m3u8", ignoreCase = true)) b.setMimeType(MimeTypes.APPLICATION_M3U8)
        return b.build()
    }
}

/** يشغّل في المشغل المدمج أو في مشغل خارجي حسب الإعدادات. */
object Playback {
    fun play(act: android.app.Activity, entries: List<PlayQueue.Entry>, index: Int, live: Boolean) {
        PlayQueue.set(entries, index, live)
        val e = PlayQueue.current ?: return
        if (App.instance.prefs.playerMode == com.mytv.player.data.Prefs.PLAYER_EXTERNAL) {
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(android.net.Uri.parse(e.url), "video/*")
                .putExtra("title", e.title)
            if (intent.resolveActivity(act.packageManager) != null) {
                // نسجّل المشاهدة في اللوحة قبل الانتقال للمشغّل الخارجي
                com.mytv.player.data.Panel.NowPlaying.set(e.title,
                    if (live) "live" else if (e.seriesKey != null) "series" else "movie")
                com.mytv.player.data.Panel.ping(act)
                act.startActivity(Intent.createChooser(intent, e.title))
                return
            }
            Toast.makeText(act, R.string.no_external_player, Toast.LENGTH_LONG).show()
        }
        PlayerActivity.start(act)
    }
}

/** قائمة التشغيل الحالية (تُستخدم لتقليب القنوات بالأسهم في المباشر). */
object PlayQueue {
    /** [key] لحفظ مكان المشاهدة (أفلام/حلقات)، [seriesKey] لتذكّر آخر حلقة في المسلسل */
    data class Entry(val title: String, val url: String, val key: String? = null, val seriesKey: String? = null)

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
    private var controllerVisible = false
    private val hideOverlay = Runnable { if (!controllerVisible) setOverlayVisible(false) }

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
            controllerVisible = visibility == View.VISIBLE
            setOverlayVisible(controllerVisible)
        })
        if (PlayQueue.live) {
            playerView.setShowFastForwardButton(false)
            playerView.setShowRewindButton(false)
        }
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
        com.mytv.player.data.Panel.NowPlaying.clear(this)
        saveProgress()
        if (::tvTitle.isInitialized) tvTitle.removeCallbacks(saveTick)
        player?.release()
        player = null
    }

    // ---------------- حفظ مكان المشاهدة ----------------

    private val saveTick = object : Runnable {
        override fun run() {
            saveProgress()
            tvTitle.postDelayed(this, 10_000)
        }
    }

    private fun saveProgress() {
        val e = PlayQueue.current ?: return
        val key = e.key ?: return
        if (PlayQueue.live) return
        val p = player ?: return
        val dur = p.duration.takeIf { it > 0 } ?: 0L
        App.instance.watch.save(key, p.currentPosition, dur, e.seriesKey)
    }

    private var resumeDialog: androidx.appcompat.app.AlertDialog? = null

    /** إذا فيه مكان محفوظ: نسأل "متابعة أو من البداية" */
    private fun askResume(e: PlayQueue.Entry, p: ExoPlayer) {
        val key = e.key ?: return
        if (PlayQueue.live) return
        val pos = App.instance.watch.resumePosition(key) ?: return
        p.playWhenReady = false
        resumeDialog?.dismiss()
        resumeDialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(e.title)
            .setItems(arrayOf(
                getString(R.string.resume_from, com.mytv.player.data.WatchStore.format(pos)),
                getString(R.string.start_over)
            )) { _, which ->
                if (which == 0) p.seekTo(pos)
                p.playWhenReady = true
            }
            .setOnCancelListener { p.seekTo(pos); p.playWhenReady = true }
            .show()
    }

    private fun initPlayer() {
        val p = Players.create(this)
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                // نهاية الحلقة: نحفظها كمشاهدة وننتقل للحلقة التالية تلقائياً
                if (state == Player.STATE_ENDED && !PlayQueue.live) {
                    saveProgress()
                    if (PlayQueue.index < PlayQueue.entries.size - 1) zap(1)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(
                    this@PlayerActivity,
                    getString(R.string.playback_error, error.errorCodeName),
                    Toast.LENGTH_LONG
                ).show()
            }
        })
        // أزرار ⏮ ⏭ في شريط التحكم تغيّر القناة / الحلقة
        playerView.player = wrapForZapping(p)
        player = p
        playCurrent()
        // على التابلت والجوال: نُظهر الأزرار في البداية حتى يعرف المستخدم مكانها
        if (hasTouch) playerView.showController()
    }

    private fun wrapForZapping(p: ExoPlayer): Player = object : ForwardingPlayer(p) {
        private fun multi() = PlayQueue.entries.size > 1
        private val zapCommands = intArrayOf(
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
        )
        override fun getAvailableCommands(): Player.Commands {
            val b = super.getAvailableCommands().buildUpon()
            if (multi()) b.addAll(*zapCommands)
            return b.build()
        }
        override fun isCommandAvailable(command: Int): Boolean =
            (multi() && command in zapCommands) || super.isCommandAvailable(command)
        override fun hasNextMediaItem() = multi() || super.hasNextMediaItem()
        override fun hasPreviousMediaItem() = multi() || super.hasPreviousMediaItem()
        override fun seekToNext() { if (multi()) zap(1) else super.seekToNext() }
        override fun seekToNextMediaItem() { if (multi()) zap(1) else super.seekToNextMediaItem() }
        override fun seekToPrevious() { if (multi()) zap(-1) else super.seekToPrevious() }
        override fun seekToPreviousMediaItem() { if (multi()) zap(-1) else super.seekToPreviousMediaItem() }
    }

    private fun setOverlayVisible(visible: Boolean) {
        btnBack.isVisible = visible
        zapButtons.isVisible = visible && PlayQueue.live && PlayQueue.entries.size > 1
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN && ::btnBack.isInitialized) {
            // أي لمسة تُظهر زر الرجوع وأزرار القناة لمدة 4 ثوانٍ
            setOverlayVisible(true)
            btnBack.removeCallbacks(hideOverlay)
            btnBack.postDelayed(hideOverlay, 4000)
        }
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
        com.mytv.player.data.Panel.NowPlaying.set(e.title,
            if (PlayQueue.live) "live" else if (e.seriesKey != null) "series" else "movie", this)
        com.mytv.player.data.Panel.ping(this)
        askResume(e, p)
        tvTitle.removeCallbacks(saveTick)
        if (!PlayQueue.live && e.key != null) tvTitle.postDelayed(saveTick, 10_000)
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
        saveProgress()
        if (PlayQueue.live) {
            PlayQueue.index = (PlayQueue.index + delta + size) % size   // القنوات تدور
        } else {
            val next = PlayQueue.index + delta                         // الحلقات لا تدور
            if (next !in 0 until size) return
            PlayQueue.index = next
        }
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
