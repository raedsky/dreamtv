package com.mytv.player.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mytv.player.App
import com.mytv.player.R
import com.mytv.player.data.Episode
import com.mytv.player.data.StreamItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** صفحة المسلسل: أزرار المواسم + حلقات الموسم المختار. */
class SeriesDetailActivity : AppCompatActivity() {

    companion object {
        private var pending: StreamItem? = null
        fun open(ctx: Context, item: StreamItem) {
            pending = item
            ctx.startActivity(Intent(ctx, SeriesDetailActivity::class.java))
        }
    }

    private lateinit var item: StreamItem
    private lateinit var adapter: EpisodeAdapter
    private lateinit var seasonBar: LinearLayout
    private lateinit var rv: RecyclerView
    private var allEpisodes: List<Episode> = emptyList()
    private var currentSeason = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        item = pending ?: run { finish(); return }
        setContentView(R.layout.activity_series_detail)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvTitle).text = item.name
        Images.load(findViewById<ImageView>(R.id.ivCover), item.icon)
        Images.load(findViewById<ImageView>(R.id.ivBackdrop), item.icon)
        showRating(item.rating)
        btnFav = findViewById(R.id.btnFav)
        btnFav.setOnClickListener {
            App.instance.groups.toggleFavorite(com.mytv.player.data.ContentType.SERIES, item.key)
            updateFav()
        }
        updateFav()

        val progress = findViewById<ProgressBar>(R.id.progress)
        val tvEmpty = findViewById<TextView>(R.id.tvEmpty)
        seasonBar = findViewById(R.id.seasonBar)
        rv = findViewById(R.id.rvEpisodes)
        adapter = EpisodeAdapter({ item.icon }) { index ->
            // نشغّل ضمن حلقات الموسم الحالي (⏮ ⏭ تنقل بين الحلقات)
            Playback.play(
                this,
                adapter.episodes.map { PlayQueue.Entry("${item.name} - ${EpisodeAdapter.episodeLabel(it)}", it.url) },
                index, live = false
            )
        }
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        lifecycleScope.launch {
            try {
                val (info, eps) = App.instance.repo.seriesDetails(item)
                allEpisodes = eps
                info?.let { bindInfo(it) }
                tvEmpty.setText(R.string.no_episodes)
                tvEmpty.isVisible = allEpisodes.isEmpty()
                buildSeasons()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                tvEmpty.text = getString(R.string.load_error, e.message ?: e.javaClass.simpleName)
                tvEmpty.isVisible = true
            } finally {
                progress.isVisible = false
            }
        }
    }

    private lateinit var btnFav: TextView

    private fun updateFav() {
        val fav = item.key in App.instance.groups.favorites(com.mytv.player.data.ContentType.SERIES)
        btnFav.text = (if (fav) "♥  " else "♡  ") + getString(if (fav) R.string.remove_favorite else R.string.add_favorite)
    }

    private fun showRating(r: String?) {
        findViewById<TextView>(R.id.tvRating).apply {
            isVisible = !r.isNullOrBlank()
            text = "★ $r"
        }
    }

    private fun bindInfo(info: com.mytv.player.data.MediaInfo) {
        info.backdrop?.let { Images.load(findViewById<ImageView>(R.id.ivBackdrop), it) }
        showRating(info.rating ?: item.rating)
        val year = info.release?.take(4)?.takeIf { it.all(Char::isDigit) }
        findViewById<TextView>(R.id.tvMeta).text =
            listOfNotNull(year, info.genre).filter { it.isNotBlank() }.joinToString("  •  ")
        findViewById<TextView>(R.id.tvPlot).text = info.plot.orEmpty()
        findViewById<TextView>(R.id.tvCast).apply {
            isVisible = !info.cast.isNullOrBlank()
            text = getString(R.string.cast, info.cast)
        }
    }

    private fun buildSeasons() {
        val seasons = allEpisodes.map { it.season }.distinct().sorted()
        seasonBar.removeAllViews()
        findViewById<View>(R.id.seasonScroll).isVisible = seasons.size > 1
        val pad = (14 * resources.displayMetrics.density).toInt()
        for (s in seasons) {
            val b = TextView(this).apply {
                text = if (s > 0) getString(R.string.season, s) else getString(R.string.episodes)
                tag = s
                textSize = 16f
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setBackgroundResource(R.drawable.bg_tab)
                setPadding(pad * 2, pad * 2 / 3, pad * 2, pad * 2 / 3)
                isFocusable = true
                isClickable = true
                setOnClickListener { showSeason(s) }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = pad / 2 }
            seasonBar.addView(b, lp)
        }
        seasons.firstOrNull()?.let { showSeason(it) }
        (if (seasons.size > 1) seasonBar.getChildAt(0) else null)?.requestFocus()
            ?: rv.post { rv.getChildAt(0)?.requestFocus() }
    }

    private fun showSeason(season: Int) {
        currentSeason = season
        adapter.episodes = allEpisodes.filter { it.season == season }
        for (i in 0 until seasonBar.childCount) {
            val v = seasonBar.getChildAt(i)
            v.isSelected = v.tag == season
        }
        rv.scrollToPosition(0)
    }
}
