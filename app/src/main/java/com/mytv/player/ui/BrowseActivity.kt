package com.mytv.player.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mytv.player.App
import com.mytv.player.R
import com.mytv.player.data.ContentType
import com.mytv.player.data.Repository
import com.mytv.player.data.StreamItem
import com.mytv.player.data.UserGroup
import com.mytv.player.ui.CategoryRow.Kind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * شاشة القسم (مباشر / أفلام / مسلسلات): التصنيفات على الجانب والعناصر في الوسط.
 * نفس الشاشة تُستخدم للأقسام الثلاثة، لذلك ميزة "إضافة قروب" متوفرة في الجميع.
 */
class BrowseActivity : BaseActivity() {

    companion object {
        private const val EXTRA_TYPE = "type"
        private const val LATEST_COUNT = 100
        fun start(ctx: Context, type: ContentType) =
            ctx.startActivity(Intent(ctx, BrowseActivity::class.java).putExtra(EXTRA_TYPE, type.name))
    }

    private val app get() = App.instance
    private lateinit var type: ContentType

    private lateinit var catAdapter: CategoryAdapter
    private lateinit var itemAdapter: ItemAdapter
    private lateinit var rvCategories: RecyclerView
    private lateinit var rvItems: RecyclerView
    private lateinit var etSearch: EditText
    private lateinit var tvEmpty: TextView
    private lateinit var tvHeader: TextView
    private lateinit var btnSort: TextView
    private lateinit var progress: ProgressBar

    private var content: Repository.Content? = null
    private var index: Map<String, StreamItem> = emptyMap()
    private var rawContent: Repository.Content? = null

    /** المحتوى بعد حذف الباقات والعناصر المخفية من الإعدادات */
    private fun visibleContent(): Repository.Content {
        val raw = rawContent ?: return Repository.Content(emptyList(), emptyList())
        val hc = app.groups.hiddenCategories(type).keys
        val hi = app.groups.hiddenItems(type).keys
        if (hc.isEmpty() && hi.isEmpty()) return raw
        return Repository.Content(
            raw.categories.filter { it.id !in hc },
            raw.items.filter { it.key !in hi && it.categoryId !in hc }
        )
    }

    /** بعد إخفاء شيء: نعيد الفلترة ونحدّث القوائم */
    private fun reapplyHidden() {
        val c = visibleContent()
        content = c
        index = c.items.associateBy { it.key }
        refresh(reloadItems = true)
    }
    private var favSet: Set<String> = emptySet()
    private var current: CategoryRow? = null
    private var currentItems: List<StreamItem> = emptyList()

    // معاينة البث المباشر
    private var previewPlayer: ExoPlayer? = null
    private var previewItem: StreamItem? = null
    private lateinit var previewView: PlayerView
    private lateinit var previewHint: TextView
    private lateinit var previewTitle: TextView
    private lateinit var previewCategory: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        type = runCatching { ContentType.valueOf(intent.getStringExtra(EXTRA_TYPE) ?: "") }
            .getOrDefault(ContentType.LIVE)
        setContentView(R.layout.activity_browse)

        ThemeUi.apply(findViewById(android.R.id.content))
        setupTabs()
        rvCategories = findViewById(R.id.rvCategories)
        rvItems = findViewById(R.id.rvItems)
        etSearch = findViewById(R.id.etSearch)
        tvEmpty = findViewById(R.id.tvEmpty)
        tvHeader = findViewById(R.id.tvHeader)
        btnSort = findViewById(R.id.btnSort)
        btnSort.setOnClickListener { chooseSort() }
        findViewById<View>(R.id.btnSortIcon).setOnClickListener { chooseSort() }
        updateSortLabel()
        progress = findViewById(R.id.progress)

        catAdapter = CategoryAdapter(::onCategoryClick, ::onCategoryLongClick)
        rvCategories.layoutManager = LinearLayoutManager(this)
        rvCategories.adapter = catAdapter

        itemAdapter = ItemAdapter(type, { favSet }, ::openItem, ::showItemActions)
        rvItems.layoutManager = GridLayoutManager(this, spanCount())
        rvItems.adapter = itemAdapter

        etSearch.doAfterTextChanged { applyFilter() }

        if (type == ContentType.LIVE) setupPreview()
        setupTopBar()

        load(force = false)
    }

    private fun spanCount(): Int {
        val widthDp = resources.configuration.screenWidthDp
        val paneDp = widthDp * 0.72f
        if (type == ContentType.LIVE) return 1   // قائمة قنوات بجانب المعاينة
        val cellDp = 150f
        return (paneDp / cellDp).toInt().coerceAtLeast(1)
    }

    // ---------------- تحميل البيانات ----------------

    private fun load(force: Boolean) {
        progress.isVisible = true
        tvEmpty.isVisible = false
        lifecycleScope.launch {
            try {
                rawContent = app.repo.load(type, force)
                val c = visibleContent()
                content = c
                index = c.items.associateBy { it.key }
                refresh(reloadItems = true)
                rvCategories.post { rvCategories.getChildAt(0)?.requestFocus() }
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

    /**
     * يعيد بناء قائمة التصنيفات (الكل، المفضلة، القروبات الخاصة، إضافة قروب، تصنيفات السيرفر).
     * [reloadItems] = false يحدّث العدّاد والنجوم فقط بدون تحريك القائمة.
     */
    private fun refresh(reloadItems: Boolean) {
        val c = content ?: return
        val favs = app.groups.favorites(type)
        favSet = favs.toHashSet()
        val counts = c.items.groupingBy { it.categoryId }.eachCount()

        val rows = ArrayList<CategoryRow>()
        rows += CategoryRow(Kind.RECENT, "recent", getString(R.string.recent),
            app.groups.recents(type).count { it in index })
        if (type != ContentType.LIVE && c.items.any { it.added > 0 }) {
            rows += CategoryRow(Kind.LATEST, "latest", getString(R.string.recently_added), latest(c).size)
        }
        rows += CategoryRow(Kind.ALL, "all", getString(R.string.all), c.items.size)
        rows += CategoryRow(Kind.FAVORITES, "fav", getString(R.string.favorites), favs.count { it in index })
        for (g in app.groups.groups(type)) {
            rows += CategoryRow(Kind.GROUP, g.id, g.name, g.items.count { it in index })
        }
        rows += CategoryRow(Kind.ADD_GROUP, "add", getString(R.string.add_group), -1)
        for (cat in c.categories) {
            rows += CategoryRow(Kind.SERVER, cat.id, cat.name, counts[cat.id] ?: 0)
        }
        catAdapter.submit(rows)

        val sel = current?.let { cur -> rows.firstOrNull { it.key == cur.key } }
            ?: rows.first { it.kind == Kind.ALL }
        val changed = sel.key != current?.key
        current = sel
        catAdapter.setSelected(sel)
        tvHeader.text = "${sel.name} (${itemAdapter.itemCount})"
        if (reloadItems || changed) {
            currentItems = itemsFor(sel)
            applyFilter()
        } else {
            itemAdapter.refreshMarks()
        }
    }

    /** آخر ما أضيف للسيرفر (أحدث 100) */
    private fun latest(c: Repository.Content): List<StreamItem> =
        c.items.filter { it.added > 0 }.sortedByDescending { it.added }.take(LATEST_COUNT)

    private fun itemsFor(row: CategoryRow): List<StreamItem> {
        val c = content ?: return emptyList()
        return when (row.kind) {
            Kind.LATEST -> latest(c)
            Kind.RECENT -> app.groups.recents(type).mapNotNull { index[it] }
            Kind.ALL -> c.items
            Kind.FAVORITES -> app.groups.favorites(type).mapNotNull { index[it] }
            Kind.GROUP -> (app.groups.groups(type).find { it.id == row.id }?.items ?: emptyList())
                .mapNotNull { index[it] }
            Kind.SERVER -> c.items.filter { it.categoryId == row.id }
            Kind.ADD_GROUP -> emptyList()
        }
    }

    private fun applyFilter() {
        val q = etSearch.text.toString().trim()
        var list = if (q.isEmpty()) currentItems
                   else currentItems.filter { it.name.contains(q, ignoreCase = true) }
        // القروبات والمفضلة تبقى بترتيب المستخدم
        if (current?.kind != Kind.GROUP && current?.kind != Kind.FAVORITES && current?.kind != Kind.RECENT && current?.kind != Kind.LATEST) {
            list = when (app.prefs.sortMode(type)) {
                "added" -> list.sortedByDescending { it.added }
                "name" -> list.sortedBy { it.name.lowercase() }
                else -> list
            }
        }
        itemAdapter.submit(list)
        tvHeader.text = "${current?.name.orEmpty()} (${list.size})"
        rvItems.scrollToPosition(0)
        val emptyMsg = when {
            list.isNotEmpty() -> null
            current?.kind == Kind.RECENT -> getString(R.string.empty_recent)
            current?.kind == Kind.GROUP || current?.kind == Kind.FAVORITES -> getString(R.string.empty_group)
            else -> getString(R.string.empty_list)
        }
        tvEmpty.text = emptyMsg
        tvEmpty.isVisible = emptyMsg != null
    }

    // ---------------- الشريط العلوي ----------------

    private fun setupTopBar() {
        findViewById<View>(R.id.btnRefresh).setOnClickListener {
            Toast.makeText(this, R.string.refreshing, Toast.LENGTH_SHORT).show()
            load(force = true)
        }
    }

    // ---------------- الترتيب ----------------

    private fun sortLabel(mode: String) = getString(when (mode) {
        "added" -> R.string.sort_added
        "name" -> R.string.sort_name
        else -> R.string.sort_default
    })

    private fun updateSortLabel() {
        btnSort.text = getString(R.string.sort_by, sortLabel(app.prefs.sortMode(type)))
    }

    private fun chooseSort() {
        val modes = arrayOf("default", "added", "name")
        AlertDialog.Builder(this)
            .setTitle(R.string.sort)
            .setSingleChoiceItems(modes.map { sortLabel(it) }.toTypedArray(),
                modes.indexOf(app.prefs.sortMode(type)).coerceAtLeast(0)) { d, which ->
                app.prefs.setSortMode(type, modes[which])
                updateSortLabel()
                applyFilter()
                d.dismiss()
            }
            .show()
    }

    // ---------------- التصنيفات والقروبات ----------------

    private fun onCategoryClick(row: CategoryRow) {
        if (row.kind == Kind.ADD_GROUP) {
            promptNewGroup { g ->
                current = CategoryRow(Kind.GROUP, g.id, g.name, 0)
                refresh(reloadItems = true)
                Toast.makeText(this, R.string.group_created, Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (row.key == current?.key) {
            rvItems.post { rvItems.getChildAt(0)?.requestFocus() }
            return
        }
        current = row
        refresh(reloadItems = true)
    }

    private fun onCategoryLongClick(row: CategoryRow) {
        when (row.kind) {
            Kind.RECENT -> AlertDialog.Builder(this)
                .setItems(arrayOf(getString(R.string.clear_recent))) { _, _ ->
                    app.groups.clearRecents(type)
                    refresh(reloadItems = current?.kind == Kind.RECENT)
                }
                .show()
            Kind.GROUP -> groupOptions(row)
            Kind.SERVER -> AlertDialog.Builder(this)
                .setTitle(row.name)
                .setItems(arrayOf(getString(R.string.add_category_to_group, row.name), getString(R.string.hide_category))) { _, which ->
                    if (which == 0) {
                        // إضافة كل عناصر التصنيف إلى قروب خاص دفعة واحدة
                        val keys = content?.items?.filter { it.categoryId == row.id }?.map { it.key } ?: return@setItems
                        chooseGroupFor(getString(R.string.add_category_to_group, row.name)) { g ->
                            val n = app.groups.addAll(type, g.id, keys)
                            refresh(reloadItems = current?.key == "${Kind.GROUP.name}:${g.id}")
                            Toast.makeText(this, getString(R.string.added_n, n), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        app.groups.setCategoryHidden(type, row.id, row.name, true)
                        if (current?.key == row.key) current = null
                        reapplyHidden()
                        Toast.makeText(this, R.string.hidden_done, Toast.LENGTH_LONG).show()
                    }
                }
                .show()
            else -> Unit
        }
    }

    private fun groupOptions(row: CategoryRow) {
        AlertDialog.Builder(this)
            .setTitle(row.name)
            .setItems(arrayOf(getString(R.string.rename), getString(R.string.delete))) { _, which ->
                if (which == 0) {
                    inputDialog(R.string.rename, row.name) { name ->
                        app.groups.rename(type, row.id, name)
                        if (current?.key == row.key) current = row.copy(name = name)
                        refresh(reloadItems = false)
                        tvHeader.text = "${current?.name.orEmpty()} (${itemAdapter.itemCount})"
                    }
                } else {
                    AlertDialog.Builder(this)
                        .setMessage(getString(R.string.confirm_delete_group, row.name))
                        .setPositiveButton(R.string.delete) { _, _ ->
                            app.groups.delete(type, row.id)
                            if (current?.key == row.key) current = null
                            refresh(reloadItems = true)
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
            }
            .show()
    }

    // ---------------- خيارات العنصر ----------------

    private fun showItemActions(item: StreamItem) {
        val isFav = item.key in favSet
        val inGroup = current?.takeIf { it.kind == Kind.GROUP }
        val labels = mutableListOf(
            getString(if (isFav) R.string.remove_favorite else R.string.add_favorite),
            getString(R.string.add_to_group)
        )
        if (inGroup != null) labels += getString(R.string.remove_from_group, inGroup.name)
        val infoIndex = labels.size
        labels += getString(R.string.image_info)
        val hideIndex = labels.size
        labels += getString(R.string.hide_item)

        AlertDialog.Builder(this)
            .setTitle(item.name)
            .setItems(labels.toTypedArray()) { _, which ->
                when (which) {
                    0 -> {
                        app.groups.toggleFavorite(type, item.key)
                        refresh(reloadItems = current?.kind == Kind.FAVORITES)
                    }
                    1 -> chooseGroups(item)
                    infoIndex -> showImageInfo(item)
                    hideIndex -> {
                        app.groups.setItemHidden(type, item.key, item.name, true)
                        reapplyHidden()
                        Toast.makeText(this, R.string.hidden_done, Toast.LENGTH_LONG).show()
                    }
                    2 -> if (inGroup != null) {
                        app.groups.setMember(type, inGroup.id, item.key, false)
                        refresh(reloadItems = true)
                    }
                }
            }
            .show()
    }

    /** يعرض رابط صورة العنصر (لتشخيص مشاكل الصور) مع زر نسخ. */
    private fun showImageInfo(item: StreamItem) {
        val p = app.prefs.profile
        // نخفي اسم المستخدم وكلمة المرور إن ظهرت في الرابط
        var url = item.icon ?: "-"
        if (p != null && p.username.isNotEmpty()) url = url.replace(p.username, "***")
        if (p != null && p.password.isNotEmpty()) url = url.replace(p.password, "***")
        val sameCount = content?.items?.count { it.icon != null && it.icon == item.icon } ?: 0
        val text = "${item.name}\n\n$url\n\n" + getString(R.string.same_image_count, sameCount)
        AlertDialog.Builder(this)
            .setTitle(R.string.image_info)
            .setMessage(text)
            .setPositiveButton(R.string.copy) { _, _ ->
                val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("image", text))
                Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** اختيار قروب واحد أو أكثر للعنصر، مع زر لإنشاء قروب جديد. */
    private fun chooseGroups(item: StreamItem) {
        val groups = app.groups.groups(type)
        if (groups.isEmpty()) {
            promptNewGroup { g ->
                app.groups.setMember(type, g.id, item.key, true)
                afterMembershipChange()
            }
            return
        }
        val names = groups.map { it.name }.toTypedArray()
        val checked = BooleanArray(groups.size) { item.key in groups[it].items }
        AlertDialog.Builder(this)
            .setTitle(R.string.choose_groups)
            .setMultiChoiceItems(names, checked) { _, i, isChecked -> checked[i] = isChecked }
            .setPositiveButton(R.string.save) { _, _ ->
                groups.forEachIndexed { i, g -> app.groups.setMember(type, g.id, item.key, checked[i]) }
                afterMembershipChange()
            }
            .setNeutralButton(R.string.new_group) { _, _ ->
                promptNewGroup { g ->
                    app.groups.setMember(type, g.id, item.key, true)
                    afterMembershipChange()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** اختيار قروب واحد (أو إنشاء جديد) ثم تنفيذ [onPicked]. */
    private fun chooseGroupFor(title: String, onPicked: (UserGroup) -> Unit) {
        val groups = app.groups.groups(type)
        val names = (groups.map { it.name } + ("＋ " + getString(R.string.new_group))).toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(names) { _, which ->
                if (which < groups.size) onPicked(groups[which]) else promptNewGroup(onPicked)
            }
            .show()
    }

    private fun afterMembershipChange() {
        refresh(reloadItems = current?.kind == Kind.GROUP)
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
    }

    private fun promptNewGroup(onCreated: (UserGroup) -> Unit) =
        inputDialog(R.string.new_group, "") { name -> onCreated(app.groups.add(type, name)) }

    private fun inputDialog(@StringRes title: Int, initial: String, onOk: (String) -> Unit) {
        val et = EditText(this).apply {
            setSingleLine()
            hint = getString(R.string.group_name)
            setText(initial)
            setSelection(text.length)
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(et)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(box)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = et.text.toString().trim()
                if (name.isNotEmpty()) onOk(name)
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        et.requestFocus()
    }

    // ---------------- التشغيل ----------------

    private fun openItem(item: StreamItem, position: Int) {
        app.groups.addRecent(type, item.key)
        if (item.type == ContentType.SERIES && item.url == null) {
            SeriesDetailActivity.open(this, item)
            return
        }
        val list = itemAdapter.items
        val playable = list.filter { it.url != null }
        val start = playable.indexOf(item).coerceAtLeast(0)
        if (type == ContentType.LIVE) {
            // أول ضغطة: معاينة، الضغطة الثانية على نفس القناة: ملء الشاشة
            if (previewItem?.key != item.key) {
                startPreview(item)
                return
            }
            stopPreview()
            Playback.play(this, playable.map { PlayQueue.Entry(it.name, it.url!!) }, start, live = true)
        } else {
            MovieDetailActivity.open(this, item)
        }
    }

    // ---------------- المعاينة ----------------

    private fun setupPreview() {
        val pane = findViewById<View>(R.id.previewPane)
        val box = findViewById<View>(R.id.previewBox)
        previewView = findViewById(R.id.previewPlayer)
        previewHint = findViewById(R.id.previewHint)
        previewTitle = findViewById(R.id.previewTitle)
        previewCategory = findViewById(R.id.previewCategory)

        pane.isVisible = true
        findViewById<View>(R.id.previewSep).isVisible = true
        (findViewById<View>(R.id.itemsPane).layoutParams as LinearLayout.LayoutParams).weight = 3.0f
        (pane.layoutParams as LinearLayout.LayoutParams).weight = 4.7f
        pane.requestLayout()
        findViewById<View>(R.id.btnFullscreen).setOnClickListener { previewItem?.let { openItem(it, 0) } }
        findViewById<View>(R.id.btnSearch).setOnClickListener {
            etSearch.requestFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .showSoftInput(etSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        findViewById<TextView>(R.id.btnPreviewFav).setOnClickListener {
            val item = previewItem ?: return@setOnClickListener
            app.groups.toggleFavorite(type, item.key)
            refresh(reloadItems = current?.kind == Kind.FAVORITES)
            updatePreviewFav()
        }
        for (id in intArrayOf(R.id.btnFullscreen, R.id.btnPreviewFav, R.id.btnSearch)) {
            findViewById<View>(id).isFocusable = true
        }
        // مقاس 16:9
        box.post {
            box.layoutParams = box.layoutParams.apply { height = box.width * 9 / 16 }
        }
        box.setOnClickListener { previewItem?.let { openItem(it, 0) } }
    }

    private fun updatePreviewFav() {
        val item = previewItem ?: return
        val fav = item.key in app.groups.favorites(type)
        findViewById<TextView>(R.id.btnPreviewFav).setText(if (fav) R.string.remove_favorite else R.string.add_favorite)
    }

    /** تبويبات: الرئيسية | مباشر | أفلام | مسلسل */
    private fun setupTabs() {
        val tabs = mapOf(R.id.tabLive to ContentType.LIVE, R.id.tabMovies to ContentType.MOVIE, R.id.tabSeries to ContentType.SERIES)
        findViewById<View>(R.id.tabHome).setOnClickListener { finish() }
        for ((id, t) in tabs) {
            val v = findViewById<View>(id)
            v.isSelected = t == type
            v.setOnClickListener {
                if (t != type) {
                    start(this, t)
                    finish()
                    overridePendingTransition(0, 0)
                }
            }
        }
    }

    private fun startPreview(item: StreamItem) {
        com.mytv.player.data.Panel.NowPlaying.set(item.name, "live", this)
        com.mytv.player.data.Panel.ping(this)
        val url = item.url ?: return
        previewItem = item
        val p = previewPlayer ?: Players.create(this).also {
            previewPlayer = it
            previewView.player = it
        }
        p.setMediaItem(Players.mediaItem(url))
        p.prepare()
        p.playWhenReady = true
        previewHint.isVisible = false
        previewTitle.text = item.name
        itemAdapter.selectedKey = item.key
        updatePreviewFav()
        previewCategory.text = content?.categories?.firstOrNull { it.id == item.categoryId }?.name.orEmpty()
        loadEpg(item)
    }

    // ---------------- دليل البرامج ----------------

    private var epgJob: kotlinx.coroutines.Job? = null

    private fun loadEpg(item: StreamItem) {
        val box = findViewById<LinearLayout>(R.id.epgList)
        epgJob?.cancel()
        box.removeAllViews()
        epgJob = lifecycleScope.launch {
            val list = app.repo.epg(item)
            if (previewItem?.key != item.key) return@launch
            box.removeAllViews()
            if (list.isEmpty()) {
                box.addView(TextView(this@BrowseActivity).apply {
                    setText(R.string.no_epg)
                    setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_secondary))
                    textSize = 14f
                })
                return@launch
            }
            val tf = java.text.SimpleDateFormat(
                if (android.text.format.DateFormat.is24HourFormat(this@BrowseActivity)) "HH:mm" else "hh:mm a",
                java.util.Locale.getDefault())
            val now = System.currentTimeMillis() / 1000
            val pad = (8 * resources.displayMetrics.density).toInt()
            for (e in list) {
                val isNow = e.start <= now && now < e.end
                val row = LinearLayout(this@BrowseActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(pad, pad, pad, pad)
                    if (isNow) background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = 8 * resources.displayMetrics.density
                        setColor(0x26FFFFFF)
                        setStroke((resources.displayMetrics.density).toInt().coerceAtLeast(1), com.mytv.player.data.Themes.current.accent)
                    }
                }
                row.addView(TextView(this@BrowseActivity).apply {
                    text = tf.format(java.util.Date(e.start * 1000)) + " - " + tf.format(java.util.Date(e.end * 1000)) +
                        if (isNow) "   •  " + getString(R.string.now_playing) else ""
                    setTextColor(androidx.core.content.ContextCompat.getColor(context,
                        R.color.text_secondary))
                    if (isNow) setTextColor(com.mytv.player.data.Themes.current.accent)
                    textSize = 12f
                })
                row.addView(TextView(this@BrowseActivity).apply {
                    text = e.title
                    setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_primary))
                    textSize = 15f
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    if (isNow) setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                if (isNow) {
                    row.addView(ProgressBar(this@BrowseActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
                        max = 100
                        progress = (((now - e.start) * 100) / (e.end - e.start).coerceAtLeast(1)).toInt()
                        progressTintList = android.content.res.ColorStateList.valueOf(
                            com.mytv.player.data.Themes.current.accent)
                    }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, pad / 2).apply { topMargin = pad / 2 })
                    if (!e.desc.isNullOrBlank()) row.addView(TextView(this@BrowseActivity).apply {
                        text = e.desc
                        setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_secondary))
                        textSize = 12f
                        maxLines = 3
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    })
                }
                box.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = pad / 2 })
            }
        }
    }

    private fun stopPreview() {
        com.mytv.player.data.Panel.NowPlaying.clear(this)
        previewPlayer?.release()
        previewPlayer = null
        if (::previewView.isInitialized) previewView.player = null
    }

    override fun onStart() {
        super.onStart()
        // البيانات أقدم من 15 دقيقة؟ نجلب الجديد من السيرفر (أفلام/مسلسلات مضافة حديثاً)
        if (content != null && app.repo.isStale(type)) load(force = false)
        // تحديث عدّاد "المشاهدات الأخيرة" بعد الرجوع من التشغيل
        else if (content != null) refresh(reloadItems = current?.kind == Kind.RECENT)
        // الرجوع من ملء الشاشة: نكمل المعاينة على نفس القناة
        if (type == ContentType.LIVE) previewItem?.let { startPreview(it) }
    }

    override fun onStop() {
        super.onStop()
        stopPreview()
    }
}
