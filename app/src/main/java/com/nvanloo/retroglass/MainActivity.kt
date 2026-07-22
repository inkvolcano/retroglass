package com.nvanloo.retroglass

import com.nvanloo.retroglass.ui.MenuTheme
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.input.InputManager
import android.os.Bundle
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.nvanloo.retroglass.controller.InputConfig
import com.nvanloo.retroglass.controller.LayoutStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nvanloo.retroglass.model.BiosCatalog
import com.nvanloo.retroglass.model.Console
import com.nvanloo.retroglass.model.RomEntry
import com.nvanloo.retroglass.model.RomLibrary

class MainActivity : AppCompatActivity() {

    private lateinit var gamesAdapter: GamesAdapter
    private lateinit var carouselAdapter: ConsoleCarouselAdapter
    private lateinit var carousel: RecyclerView
    private lateinit var emptyView: LinearLayout
    private lateinit var sortToggle: TextView
    private val libraryMenu by lazy { com.nvanloo.retroglass.ui.GameMenuView(this).apply { visibility = View.GONE } }
    private lateinit var searchBox: android.widget.EditText
    private lateinit var consoleTitle: TextView
    private lateinit var consoleMeta: TextView
    private lateinit var dotsRow: LinearLayout
    private lateinit var controllerBar: LinearLayout
    private val inputConfig by lazy { InputConfig(this) }
    private val layoutStore by lazy { com.nvanloo.retroglass.controller.LayoutStore(this) }
    private var bindingCapture: ((Int) -> Unit)? = null
    private var bindingCaptureDevice: String? = null
    private val snapHelper = androidx.recyclerview.widget.PagerSnapHelper()
    private var listConsoles: List<com.nvanloo.retroglass.model.Console> = emptyList()
    private var allGames: List<RomEntry> = emptyList()
    // Adapter position of the centred tile (large when looping — real console = pos mod size).
    private var selectedIndex = 0
    private var itemW = 0
    private fun realIndex() = if (listConsoles.isEmpty()) 0 else selectedIndex.mod(listConsoles.size)
    private var searchQuery = ""
    private val history by lazy { com.nvanloo.retroglass.model.GameHistory(this) }
    private val uiPrefs by lazy { getSharedPreferences("ui", MODE_PRIVATE) }
    // Sort games within the selected console: 0 = A–Z, 1 = recently played.
    private var gamesSort: Int
        get() = uiPrefs.getInt("games_sort", 0)
        set(v) = uiPrefs.edit().putInt("games_sort", v).apply()

    private val pickRoms = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        val result = RomLibrary.importAll(this, uris)
        val message = buildString {
            if (result.imported.isNotEmpty()) {
                append(getString(R.string.imported_count, result.imported.size))
            }
            if (result.skipped.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append(getString(R.string.skipped_files, result.skipped.joinToString(", ")))
            }
        }
        if (message.isNotEmpty()) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        refresh()
    }

    private val pickBios = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val name = RomLibrary.importBios(this, uri)
        Toast.makeText(
            this,
            if (name != null) getString(R.string.bios_imported, name)
            else getString(R.string.bios_import_failed),
            Toast.LENGTH_LONG,
        ).show()
    }

    private val pickFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        importFolder(uri)
    }

    private var pendingCoverGame: RomEntry? = null

    private val pickCover = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val entry = pendingCoverGame ?: return@registerForActivityResult
        pendingCoverGame = null
        if (uri == null) return@registerForActivityResult
        val ok = com.nvanloo.retroglass.model.GameCovers.setFromUri(this, entry.file.absolutePath, uri)
        if (ok) refresh() else Toast.makeText(this, R.string.cover_failed, Toast.LENGTH_SHORT).show()
    }

    private val createBackup = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        Thread {
            val n = runCatching { com.nvanloo.retroglass.model.SaveBackup.export(this, uri) }.getOrDefault(-1)
            runOnUiThread {
                Toast.makeText(
                    this,
                    if (n >= 0) getString(R.string.backup_done, n) else getString(R.string.backup_failed),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }.start()
    }

    private val restoreBackup = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        Thread {
            val n = runCatching { com.nvanloo.retroglass.model.SaveBackup.import(this, uri) }.getOrDefault(-1)
            runOnUiThread {
                Toast.makeText(
                    this,
                    if (n >= 0) getString(R.string.restore_done, n) else getString(R.string.backup_failed),
                    Toast.LENGTH_LONG,
                ).show()
                refresh()
            }
        }.start()
    }

    /** On launch, if a crash log was written last session, offer to view/share it. */
    private fun checkForCrashReport() {
        val dir = RetroGlassApp.crashDir(application)
        val logs = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: return
        if (logs.isEmpty()) return
        val latest = logs.last()
        val text = runCatching { latest.readText() }.getOrNull() ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.crash_title)
            .setMessage(getString(R.string.crash_message))
            .setPositiveButton(R.string.crash_share) { _, _ ->
                startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "RetroGlass crash report")
                            putExtra(Intent.EXTRA_TEXT, text)
                        },
                        getString(R.string.crash_share),
                    ),
                )
                logs.forEach { it.delete() }
            }
            .setNegativeButton(R.string.crash_dismiss) { _, _ -> logs.forEach { it.delete() } }
            .show().gamepadNavigable()
    }

    /** Recursively scans a folder into the unified library on a background thread. */
    private fun importFolder(uri: android.net.Uri) {
        Toast.makeText(this, R.string.scanning_folder, Toast.LENGTH_SHORT).show()
        Thread {
            val result = RomLibrary.importTree(this, uri)
            runOnUiThread {
                val message = buildString {
                    append(getString(R.string.imported_count, result.imported.size))
                    if (result.skipped.isNotEmpty()) {
                        append("\n")
                        append(getString(R.string.skipped_files, result.skipped.take(8).joinToString(", ")))
                    }
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                refresh()
            }
        }.start()
    }

    // ---- All-storage auto-scan (internal + SD + USB) --------------------------------

    /**
     * "Scan storage" is the SAF folder picker: point it at the ROMs folder once and the
     * recursive import does the rest. There is no whole-filesystem walk any more - it needed
     * MANAGE_EXTERNAL_STORAGE, which Play does not grant to emulators, and the picker covers
     * the same job with one extra tap.
     */
    private fun startStorageScan() {
        pickFolder.launch(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Debug-only: dump the real layout coordinates so scripts/gen_layout_previews.py can
        // render the doc's controller-layout previews straight from ground truth. No-op in release.
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            try {
                java.io.File(filesDir, "layout_dump.json")
                    .writeText(com.nvanloo.retroglass.model.ControllerDefs.dumpLayoutsJson())
            } catch (_: Throwable) {}
        }

        val d = resources.displayMetrics.density
        fun dp(v: Float) = (v * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // Transparent: the base colour + the black hero band come from the frame behind it.
            fitsSystemWindows = true
        }

        // ---- top bar: centred RETRO / GLASS lockup + settings + overflow
        fun iconButton(glyph: String, size: Float, onTap: () -> Unit) = TextView(this).apply {
            text = glyph
            setTextColor(MenuTheme.FG)
            textSize = size
            gravity = Gravity.CENTER
            setPadding(dp(10f), dp(4f), dp(10f), dp(4f))
            isClickable = true
            isFocusable = true
            setOnClickListener { onTap() }
        }
        val logoBox = android.widget.ImageView(this).apply {
            setImageResource(R.drawable.retroglass_logo)
            adjustViewBounds = true
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        }
        // ---- console carousel: looping snapping pager; neighbours scaled + dimmed.
        // Landscape puts the carousel in a ~42%-wide left pane, so size the tile to that pane.
        val landscape = resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val heroW = if (landscape) (resources.displayMetrics.widthPixels * 0.42f).toInt()
                    else resources.displayMetrics.widthPixels
        itemW = (heroW * 0.56f).toInt()
        carouselAdapter = ConsoleCarouselAdapter()
        carousel = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity, RecyclerView.HORIZONTAL, false)
            adapter = carouselAdapter
            clipChildren = false
            setPadding(0, dp(6f), 0, dp(4f))
        }
        snapHelper.attachToRecyclerView(carousel)
        carousel.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) { transformCarousel() }
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                transformCarousel()
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return
                val lm = rv.layoutManager as LinearLayoutManager
                val v = snapHelper.findSnapView(lm) ?: return
                val pos = lm.getPosition(v)
                if (pos != selectedIndex) selectConsole(pos)
            }
        })
        // Tapping anywhere on a side (arrow → screen edge) steps; centre tap plays. Swipe still scrolls.
        val carouselTaps = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: android.view.MotionEvent): Boolean {
                if (listConsoles.isEmpty()) return false
                val cx = carousel.width / 2f
                when {
                    e.x < cx - itemW / 2f -> moveCarousel(-1)
                    e.x > cx + itemW / 2f -> moveCarousel(1)
                    else -> launchFirstGame(listConsoles[realIndex()])
                }
                return true
            }
        })
        carousel.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: android.view.MotionEvent): Boolean {
                carouselTaps.onTouchEvent(e)
                return false
            }
        })

        consoleTitle = TextView(this).apply {
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textSize = 25f
            gravity = Gravity.CENTER
        }
        consoleMeta = TextView(this).apply {
            setTextColor(MenuTheme.DIM)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(2f), 0, dp(6f))
        }
        dotsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(2f), 0, dp(10f))
        }

        // ---- search (scoped to the selected console) + sort pill
        searchBox = android.widget.EditText(this).apply {
            hint = getString(R.string.search_games)
            setHintTextColor(Color.parseColor("#66FFFFFF"))
            setTextColor(Color.WHITE)
            textSize = 15f
            maxLines = 1
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            background = GradientDrawable().apply { cornerRadius = dp(14f).toFloat(); setColor(MenuTheme.TILE) }
            setPadding(dp(16f), dp(11f), dp(14f), dp(11f))
            addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) { searchQuery = s?.toString()?.trim().orEmpty(); refreshGames() }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        sortToggle = TextView(this).apply {
            setTextColor(MenuTheme.FG)
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = dp(14f).toFloat(); setColor(MenuTheme.TILE) }
            setPadding(dp(14f), dp(11f), dp(14f), dp(11f))
            isClickable = true
            isFocusable = true
            setOnClickListener { gamesSort = (gamesSort + 1) % 3; updateSortLabel(); refreshGames() }
        }
        updateSortLabel()

        // ---- games list for the selected console
        gamesAdapter = GamesAdapter(
            onClick = { entry ->
                history.recordPlayed(entry.file.absolutePath)
                EmulationActivity.launch(this, entry.file.absolutePath, entry.console)
            },
            onLongClick = { entry -> showGameOptions(entry) },
        )
        val recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = gamesAdapter
            isFocusable = true
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }

        emptyView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24f), dp(36f), dp(24f), dp(24f))
            addView(TextView(this@MainActivity).apply {
                text = getString(R.string.empty_library)
                setTextColor(Color.parseColor("#88FFFFFF"))
                textSize = 14f
                gravity = Gravity.CENTER
            })
            // Prominent call-to-action when the library is empty.
            addView(TextView(this@MainActivity).apply {
                text = getString(R.string.scan_cta)
                setTextColor(Color.WHITE)
                textSize = 16f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = GradientDrawable().apply { cornerRadius = dp(14f).toFloat(); setColor(MenuTheme.ACCENT) }
                setPadding(dp(28f), dp(14f), dp(28f), dp(14f))
                isClickable = true
                isFocusable = true
                setOnClickListener { startStorageScan() }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(22f) })
        }

        // ---- controller bar: a centred row of player slots, sits between the carousel and the title
        controllerBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8f), dp(6f), dp(8f), dp(8f))
        }

        // hero = carousel with ‹ › arrows sitting just outside the centre tile (visual only —
        // the whole side is tappable via the carousel's gesture handler above).
        val peek = (heroW - itemW) / 2
        fun arrow(glyph: String, gravity: Int) = TextView(this).apply {
            text = glyph
            setTextColor(Color.parseColor("#8CFFFFFF"))
            textSize = 30f
            layoutParams = android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                gravity or Gravity.CENTER_VERTICAL,
            ).apply {
                val inset = (peek - dp(34f)).coerceAtLeast(dp(2f))
                if (gravity == Gravity.START) marginStart = inset else marginEnd = inset
            }
        }
        val heroBox = android.widget.FrameLayout(this).apply {
            clipChildren = false
            addView(carousel, android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(arrow("‹", Gravity.START))
            addView(arrow("›", Gravity.END))
        }

        fun iconsRow(topPad: Float) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(dp(6f), dp(topPad), dp(6f), 0)
            addView(iconButton("⚙", 20f) { showTopMenu() })
        }

        if (landscape) {
            // ---- LANDSCAPE: black hero pane on the left; search + list on the right.
            val leftPane = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setBackgroundColor(Color.BLACK)
                setPadding(0, dp(12f), 0, dp(10f))
            }
            leftPane.addView(logoBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40f))
                .apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(4f) })
            leftPane.addView(heroBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            leftPane.addView(controllerBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))  // player slots between the carousel and the title
            leftPane.addView(consoleTitle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            leftPane.addView(consoleMeta, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            leftPane.addView(dotsRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            // One top row: (shortened) search + filter, then the gear + overflow — frees vertical
            // space for the games list.
            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14f), dp(8f), dp(6f), dp(8f))
            }
            topRow.addView(searchBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            topRow.addView(View(this), LinearLayout.LayoutParams(dp(8f), 1))
            topRow.addView(sortToggle)
            topRow.addView(View(this), LinearLayout.LayoutParams(dp(4f), 1))
            topRow.addView(iconButton("⚙", 20f) { showTopMenu() })

            val rightPane = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            rightPane.addView(topRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            rightPane.addView(emptyView)
            rightPane.addView(recycler, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(MenuTheme.BG)
                fitsSystemWindows = true
            }
            row.addView(leftPane, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.42f))
            row.addView(rightPane, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.58f))
            setContentWithMenu(row)
        } else {
            // ---- PORTRAIT: vertical stack with the black hero band behind the transparent content.
            val searchRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14f), dp(2f), dp(14f), dp(8f))
            }
            searchRow.addView(searchBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            searchRow.addView(View(this), LinearLayout.LayoutParams(dp(10f), 1))
            searchRow.addView(sortToggle)
            val topBar = android.widget.FrameLayout(this).apply {
                setPadding(dp(14f), dp(10f), dp(6f), dp(6f))
                addView(logoBox, android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(44f), Gravity.CENTER))
                addView(iconsRow(4f), android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.END or Gravity.CENTER_VERTICAL))
            }
            root.addView(topBar)
            root.addView(heroBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(172f)))
            root.addView(controllerBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))  // player slots between the carousel and the title
            root.addView(consoleTitle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            root.addView(consoleMeta, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            root.addView(dotsRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            root.addView(searchRow)
            root.addView(emptyView)
            root.addView(recycler, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

            val blackBand = View(this).apply { setBackgroundColor(Color.BLACK) }
            val frame = android.widget.FrameLayout(this).apply {
                setBackgroundColor(MenuTheme.BG)
                addView(blackBand, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0))
                addView(root, android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
            root.viewTreeObserver.addOnGlobalLayoutListener {
                fun yInFrame(v: View): Float {
                    var y = 0f
                    var cur: View? = v
                    while (cur != null && cur !== frame) { y += cur.y; cur = cur.parent as? View }
                    return y
                }
                val top = (yInFrame(logoBox) + logoBox.height * 0.677f).toInt() // 0.677 = pill centre in the logo
                val bottom = yInFrame(searchRow).toInt()
                val lp = blackBand.layoutParams as android.widget.FrameLayout.LayoutParams
                if (bottom > top && (lp.topMargin != top || lp.height != bottom - top)) {
                    lp.topMargin = top
                    lp.height = bottom - top
                    blackBand.layoutParams = lp
                }
            }
            setContentWithMenu(frame)
        }
        checkForCrashReport()
    }

    // Translate a gamepad's face button into "confirm" so a Bluetooth pad can launch the
    // focused game (and operate the long-press options dialog) without touching the screen.
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        // Capturing a button for controller remap: bind the first gamepad key pressed.
        if (bindingCapture != null && event.action == KeyEvent.ACTION_DOWN) {
            val dev = event.device
            if (dev != null && isGamepad(dev.sources) &&
                (bindingCaptureDevice == null || surfaceKey(dev) == bindingCaptureDevice)
            ) {
                bindingCapture?.invoke(event.keyCode)
                bindingCapture = null; bindingCaptureDevice = null
                return true
            }
        }
        if (event.keyCode == android.view.KeyEvent.KEYCODE_BUTTON_A) {
            return super.dispatchKeyEvent(
                android.view.KeyEvent(event.action, android.view.KeyEvent.KEYCODE_DPAD_CENTER),
            )
        }
        return super.dispatchKeyEvent(event)
    }

    /** A → confirm, B → back, so the long-press / BIOS dialogs work from a gamepad. */
    private fun AlertDialog.gamepadNavigable(): AlertDialog {
        setOnKeyListener { d, keyCode, ev ->
            val mapped = when (keyCode) {
                android.view.KeyEvent.KEYCODE_BUTTON_A -> android.view.KeyEvent.KEYCODE_DPAD_CENTER
                android.view.KeyEvent.KEYCODE_BUTTON_B -> android.view.KeyEvent.KEYCODE_BACK
                else -> return@setOnKeyListener false
            }
            (d as? android.app.Dialog)?.window?.decorView
                ?.dispatchKeyEvent(android.view.KeyEvent(ev.action, mapped))
            true
        }
        return this
    }

    override fun onResume() {
        super.onResume()
        refresh()
        buildControllerBar()
        (getSystemService(INPUT_SERVICE) as? InputManager)?.registerInputDeviceListener(deviceListener, null)
    }

    override fun onPause() {
        super.onPause()
        (getSystemService(INPUT_SERVICE) as? InputManager)?.unregisterInputDeviceListener(deviceListener)
    }

    private fun refresh() {
        allGames = RomLibrary.scan(this)
        // Preserve the selected console across refreshes and across rotation (activity recreation).
        val prevReal = if (selectedIndex != 0) realIndex() else uiPrefs.getInt("sel_console", 0)
        // Consoles that actually have games, grouped by maker then newest-first (stable order).
        listConsoles = allGames.map { it.console }.distinct()
            .sortedWith(compareBy({ it.maker }, { -it.year }, { it.displayName }))
        carouselAdapter.submit(listConsoles)

        val hasGames = listConsoles.isNotEmpty()
        emptyView.visibility = if (hasGames) View.GONE else View.VISIBLE
        for (v in listOf(carousel, consoleTitle, consoleMeta, dotsRow)) {
            v.visibility = if (hasGames) View.VISIBLE else View.GONE
        }
        if (!hasGames) { gamesAdapter.submit(emptyList()); return }

        val size = listConsoles.size
        val startReal = prevReal.coerceIn(0, size - 1)
        // Start near the middle of the looped range so the user can swipe far both ways.
        selectedIndex = if (size > 1) (carouselAdapter.itemCount / 2 / size) * size + startReal else startReal
        carousel.post { centerCarousel(selectedIndex); transformCarousel() }
        selectConsole(selectedIndex)
    }

    /** Scrolls so the tile at [pos] is horizontally centred in the viewport. */
    private fun centerCarousel(pos: Int) {
        val lm = carousel.layoutManager as? LinearLayoutManager ?: return
        lm.scrollToPositionWithOffset(pos, (carousel.width - itemW) / 2)
    }

    /** Switches the hero + games list to the console at [index] in the carousel. */
    private fun selectConsole(index: Int) {
        if (listConsoles.isEmpty()) return
        selectedIndex = index
        val console = listConsoles[realIndex()]
        val count = allGames.count { it.console == console }
        consoleTitle.text = console.displayName
        val gameWord = if (count == 1) getString(R.string.game_one) else getString(R.string.game_many)
        val bios = when (biosStatusFor(console)) {
            true -> "  ·  BIOS ✓"
            false -> "  ·  BIOS needed"
            null -> ""
        }
        consoleMeta.text = "${console.maker}  ·  ${console.year}  ·  $count $gameWord$bios"
        searchBox.setText("")
        searchQuery = ""
        searchBox.hint = getString(R.string.search_in, console.displayName)
        uiPrefs.edit().putInt("sel_console", realIndex()).apply()
        buildDots(realIndex())
        refreshGames()
        buildControllerBar() // player count + layout depend on the console
    }

    /** Refreshes only the games list for the current console (search / sort). */
    private fun refreshGames() {
        if (listConsoles.isEmpty()) { gamesAdapter.submit(emptyList()); return }
        val console = listConsoles[realIndex()]
        val q = searchQuery.lowercase()
        var games = allGames.filter { it.console == console }
        if (q.isNotEmpty()) games = games.filter { it.displayName.lowercase().contains(q) }
        games = when (gamesSort) {
            1 -> games.sortedByDescending { it.displayName.lowercase() }
            2 -> {
                val recents = history.recents()
                games.sortedBy { val i = recents.indexOf(it.file.absolutePath); if (i < 0) Int.MAX_VALUE else i }
            }
            else -> games.sortedBy { it.displayName.lowercase() }
        }
        gamesAdapter.accent = console.accentColor
        gamesAdapter.submit(games)
    }

    /** Pagination dots under the hero; collapses to "n / total" past a dozen consoles. */
    private fun buildDots(active: Int) {
        dotsRow.removeAllViews()
        val d = resources.displayMetrics.density
        val n = listConsoles.size
        if (n > 12) {
            dotsRow.addView(TextView(this).apply {
                text = "${active + 1} / $n"
                setTextColor(MenuTheme.GROUP)
                textSize = 11f
            })
            return
        }
        for (i in 0 until n) {
            val on = i == active
            dotsRow.addView(View(this).apply {
                background = GradientDrawable().apply {
                    cornerRadius = 100f
                    setColor(if (on) MenuTheme.ACCENT else Color.parseColor("#3A3A44"))
                }
                layoutParams = LinearLayout.LayoutParams(((if (on) 16 else 6) * d).toInt(), (6 * d).toInt())
                    .apply { marginEnd = (5 * d).toInt() }
            })
        }
    }

    /** null = this console needs no BIOS; true = BIOS present; false = required BIOS missing. */
    private fun biosStatusFor(console: Console): Boolean? {
        val sys = when (console) {
            Console.PSX -> "PlayStation"
            Console.PS2 -> "PlayStation 2"
            Console.DREAMCAST -> "Dreamcast"
            Console.SATURN -> "Saturn"
            Console.THREEDO -> "3DO"
            Console.COLECO -> "ColecoVision"
            Console.INTELLIVISION -> "Intellivision"
            Console.SEGACD -> "Sega CD / Mega-CD"
            Console.PCECD -> "PC Engine CD"
            Console.NEOGEOCD -> "Neo Geo CD"
            Console.NAOMI -> "Sega NAOMI"
            Console.ATOMISWAVE -> "Atomiswave"
            else -> return null
        }
        return BiosCatalog.status(this).firstOrNull { it.system == sys }?.present
    }

    private fun launchFirstGame(console: com.nvanloo.retroglass.model.Console) {
        val g = allGames.firstOrNull { it.console == console } ?: return
        history.recordPlayed(g.file.absolutePath)
        EmulationActivity.launch(this, g.file.absolutePath, console)
    }

    /** Coverflow emphasis: scale + fade each carousel tile by its distance from centre. */
    private fun transformCarousel() {
        val center = carousel.width / 2f
        if (center <= 0f) return
        for (i in 0 until carousel.childCount) {
            val child = carousel.getChildAt(i)
            val childCenter = (child.left + child.right) / 2f
            val frac = (kotlin.math.abs(center - childCenter) / center).coerceIn(0f, 1f)
            val scale = 1f - 0.34f * frac
            child.scaleX = scale
            child.scaleY = scale
            child.alpha = 1f - 0.55f * frac
        }
    }

    private fun moveCarousel(delta: Int) {
        if (listConsoles.size <= 1) return
        // Tiles are itemW wide with no gaps, so one item-width scroll advances exactly one console;
        // the snap-on-idle finalises centring and the idle listener updates the selection.
        carousel.smoothScrollBy(delta * itemW, 0)
    }

    /**
     * The library's single settings surface. Previously split between a gear (backup/restore)
     * and a "⋯" (add ROMs / BIOS / backup) that overlapped — the gear's two items were both
     * reachable from the dots. Merged into one gear using the same overlay as the in-game menu.
     */
    /** Content plus the settings overlay on top, so the gear can cover the library. */
    private fun setContentWithMenu(content: View) {
        setContentView(android.widget.FrameLayout(this).apply {
            addView(content, android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(libraryMenu, android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        })
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (libraryMenu.onBack()) return
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })
    }

    /**
     * A screen's heading, from the label of the row that opens it — same rule as the in-game
     * menu: rows announce what they do, headings are already the answer.
     */
    private fun menuTitle(res: Int): String =
        getString(res).substringBefore(" (").trimEnd('…', '.', ' ')

    /** Opens the library overlay at its root when a screen is reached from outside the gear. */
    private fun openLibraryMenuIfNeeded() {
        if (libraryMenu.isOpen) return
        libraryMenu.consoleTint = MenuTheme.LIBRARY_TINT
        libraryMenu.rootStatus = null
        libraryMenu.open { libraryMenuScreen() }
    }

    private fun showTopMenu() {
        // No console here, and green means "focused / live" - so the identity rule falls back
        // to the app's own colorPrimary rather than borrowing the focus colour.
        libraryMenu.consoleTint = MenuTheme.LIBRARY_TINT
        libraryMenu.rootStatus = null
        libraryMenu.open { libraryMenuScreen() }
    }

    private fun libraryMenuScreen(): View = with(libraryMenu) {
        body {
            addView(group(getString(R.string.menu_library_title)))
            addView(navRow("＋", getString(R.string.add_roms)) {
                push(getString(R.string.add_roms)) { addSourceScreen() }
            })
            addView(navRow("▤", getString(R.string.bios_status_title)) {
                libraryMenu.close(); showBiosStatus()
            })
            addView(group(getString(R.string.menu_group_data)))
            addView(navRow("↑", getString(R.string.backup_save)) {
                libraryMenu.close(); createBackup.launch("retroglass-saves.zip")
            })
            addView(navRow("↓", getString(R.string.restore_save)) {
                libraryMenu.close()
                restoreBackup.launch(arrayOf("application/zip", "application/octet-stream"))
            })
            addView(group(getString(R.string.menu_group_setup)))
            addView(navRow("▣", getString(R.string.screen_mode_title), screenModeShort(layoutStore.screenMode())) {
                libraryMenu.close(); showScreenModePicker()
            })
        }
    }

    private fun addSourceScreen(): View = with(libraryMenu) {
        body {
            addView(navRow(null, getString(R.string.add_files)) {
                libraryMenu.close(); pickRoms.launch(arrayOf("*/*"))
            })
            addView(navRow(null, getString(R.string.add_folder)) {
                libraryMenu.close(); pickFolder.launch(null)
            })
            // Android refuses to grant the root of internal storage ("choose a different
            // folder to protect your privacy"), so say so before the picker does.
            addView(note(getString(R.string.add_folder_hint)))
        }
    }

    /** 1–2 letter monogram from a game's name, skipping filler words. */
    private fun initials(name: String): String {
        val stop = setOf("the", "of", "and", "a", "an", "de", "la", "el")
        val words = name.split(Regex("[\\s:_\\-]+")).filter { it.isNotBlank() && it.lowercase() !in stop }
        return when {
            words.isEmpty() -> name.take(2).uppercase()
            words.size == 1 -> words[0].take(2).uppercase()
            else -> (words[0].take(1) + words[1].take(1)).uppercase()
        }
    }

    private fun lighten(c: Int): Int = Color.rgb(
        (Color.red(c) + 45).coerceAtMost(255),
        (Color.green(c) + 45).coerceAtMost(255),
        (Color.blue(c) + 45).coerceAtMost(255),
    )

    private fun updateSortLabel() {
        sortToggle.text = when (gamesSort) {
            1 -> "↕  Z–A"
            2 -> "↕  Recent"
            else -> "↕  A–Z"
        }
    }

    // ---------------------------------------- controller surfaces (bottom bar) ----------------

    private data class Surface(val key: String, val name: String, val device: InputDevice?, val isGamepad: Boolean)

    private fun isGamepad(sources: Int): Boolean =
        sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK

    private fun connectedGamepads(): List<InputDevice> {
        val result = mutableListOf<InputDevice>()
        for (id in InputDevice.getDeviceIds()) {
            val dev = InputDevice.getDevice(id) ?: continue
            if (!dev.isVirtual && isGamepad(dev.sources)) result.add(dev)
        }
        return result.sortedBy { if (it.controllerNumber <= 0) 99 else it.controllerNumber }
    }

    private fun hasGamepad(): Boolean = connectedGamepads().isNotEmpty()

    private fun surfaceKey(d: InputDevice?): String = d?.descriptor ?: "gamepad"

    private fun portFor(key: String, device: InputDevice?): Int =
        inputConfig.storedPort(key) ?: if (key == InputConfig.PHONE) {
            if (hasGamepad()) InputConfig.PORT_OFF else 0
        } else {
            ((device?.controllerNumber ?: 1) - 1).coerceAtLeast(0)
        }

    private fun portLabel(port: Int): String =
        if (port < 0) getString(R.string.player_off) else getString(R.string.player_n, port + 1)

    private fun availableSurfaces(): List<Surface> {
        val list = mutableListOf(Surface(InputConfig.PHONE, getString(R.string.surface_touch), null, false))
        connectedGamepads().forEachIndexed { i, dev ->
            val nm = dev.name?.takeIf { it.isNotBlank() } ?: getString(R.string.surface_pad, i + 1)
            list.add(Surface(surfaceKey(dev), nm, dev, true))
        }
        return list
    }

    /** Rebuilds the bottom bar: a slot per player port the current console supports, each showing
     *  the control layout + the controller assigned to it (empty slots greyed out). */
    private fun buildControllerBar() {
        if (!::controllerBar.isInitialized) return
        controllerBar.removeAllViews()
        val d = resources.displayMetrics.density
        fun dp(v: Float) = (v * d).toInt()
        val console = listConsoles.getOrNull(realIndex()) ?: return
        val surfaces = availableSurfaces()

        // Screen-mode selector, sitting to the left of the player slots.
        controllerBar.addView(buildScreenModeCard())
        controllerBar.addView(View(this).apply {
            setBackgroundColor(Color.parseColor("#26262E"))
        }, LinearLayout.LayoutParams(dp(1f), dp(58f)).apply { marginStart = dp(3f); marginEnd = dp(5f) })

        val thumb = runCatching {
            com.nvanloo.retroglass.controller.LayoutPreview.render(
                console, com.nvanloo.retroglass.model.ControllerDefs.controlsFor(console), dp(42f), dp(60f),
            )
        }.getOrNull()
        for (port in 0 until console.maxPlayers) {
            val onPort = surfaces.filter { portFor(it.key, it.device) == port }
            val occupied = onPort.isNotEmpty()
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                background = GradientDrawable().apply { cornerRadius = dp(12f).toFloat(); setColor(MenuTheme.TILE) }
                setPadding(dp(9f), dp(6f), dp(9f), dp(6f))
                alpha = if (occupied) 1f else 0.42f
                isClickable = true; isFocusable = true
                setOnClickListener { onPlayerSlotTap(port, onPort) }
            }
            card.addView(android.widget.ImageView(this).apply {
                if (thumb != null) setImageBitmap(thumb)
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(dp(42f), dp(60f))
            })
            card.addView(TextView(this).apply {
                text = "P${port + 1}"
                setTextColor(Color.WHITE); textSize = 11f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(0, dp(2f), 0, 0)
            })
            card.addView(TextView(this).apply {
                text = if (occupied) onPort.joinToString(" + ") { it.name } else getString(R.string.player_empty)
                setTextColor(Color.parseColor(if (occupied) "#9AA0B0" else "#6A6A76"))
                textSize = 9f; gravity = Gravity.CENTER; maxLines = 1
                maxWidth = dp(62f); ellipsize = android.text.TextUtils.TruncateAt.END
            })
            // Sized so the screen-mode card + a divider + up to four slots all fit (and centre)
            // in portrait and the narrow landscape left pane without clipping.
            controllerBar.addView(card, LinearLayout.LayoutParams(dp(66f), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(2f); marginEnd = dp(2f)
            })
        }
    }

    // ---- screen-mode selector (left of the player slots): where/how the game is shown

    /** Modes offered right now — Fullscreen only appears when a physical gamepad is connected. */
    private fun availableScreenModes(): List<Int> {
        val modes = mutableListOf(
            LayoutStore.SCREEN_AUTO, LayoutStore.SCREEN_INT_PORTRAIT,
            LayoutStore.SCREEN_INT_LANDSCAPE, LayoutStore.SCREEN_EXTERNAL,
        )
        if (hasGamepad()) modes.add(LayoutStore.SCREEN_FULLSCREEN)
        return modes
    }

    private fun screenModeLabel(m: Int) = when (m) {
        LayoutStore.SCREEN_INT_PORTRAIT -> getString(R.string.screen_int_portrait)
        LayoutStore.SCREEN_INT_LANDSCAPE -> getString(R.string.screen_int_landscape)
        LayoutStore.SCREEN_EXTERNAL -> getString(R.string.screen_external)
        LayoutStore.SCREEN_FULLSCREEN -> getString(R.string.screen_fullscreen)
        else -> getString(R.string.screen_auto)
    }

    private fun screenModeShort(m: Int) = when (m) {
        LayoutStore.SCREEN_INT_PORTRAIT -> getString(R.string.screen_short_portrait)
        LayoutStore.SCREEN_INT_LANDSCAPE -> getString(R.string.screen_short_landscape)
        LayoutStore.SCREEN_EXTERNAL -> getString(R.string.screen_short_external)
        LayoutStore.SCREEN_FULLSCREEN -> getString(R.string.screen_short_fullscreen)
        else -> getString(R.string.screen_short_auto)
    }

    private fun buildScreenModeCard(): View {
        val d = resources.displayMetrics.density
        fun dp(v: Float) = (v * d).toInt()
        val mode = layoutStore.screenMode()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = dp(12f).toFloat(); setColor(MenuTheme.TILE) }
            setPadding(dp(8f), dp(6f), dp(8f), dp(6f))
            isClickable = true; isFocusable = true
            layoutParams = LinearLayout.LayoutParams(dp(56f), ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(android.widget.ImageView(this@MainActivity).apply {
                setImageBitmap(screenModeIcon(mode, dp(34f)))
                layoutParams = LinearLayout.LayoutParams(dp(34f), dp(34f))
            })
            addView(TextView(this@MainActivity).apply {
                text = getString(R.string.screen_label)
                setTextColor(MenuTheme.GROUP); textSize = 8.5f
                gravity = Gravity.CENTER; setPadding(0, dp(3f), 0, 0)
                letterSpacing = 0.04f
            })
            addView(TextView(this@MainActivity).apply {
                text = screenModeShort(mode)
                setTextColor(Color.WHITE); textSize = 9.5f; gravity = Gravity.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                maxLines = 1; maxWidth = dp(52f); ellipsize = android.text.TextUtils.TruncateAt.END
            })
            setOnClickListener { showScreenModePicker() }
        }
    }

    /** Where to play: Auto / phone portrait / phone landscape / external / fullscreen. */
    private fun showScreenModePicker() {
        val modes = availableScreenModes()
        openLibraryMenuIfNeeded()
        libraryMenu.pushSelect(
            menuTitle(R.string.screen_mode_title),
            modes.map { screenModeLabel(it) },
            modes.indexOf(layoutStore.screenMode()).coerceAtLeast(0),
        ) { which ->
            layoutStore.setScreenMode(modes[which])
            buildControllerBar()
        }
    }

    /** Draws a small device glyph for a screen mode (phone portrait/landscape, monitor, fullscreen). */
    private fun screenModeIcon(mode: Int, px: Int): android.graphics.Bitmap {
        val b = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        val col = MenuTheme.FG
        val stroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = col; style = android.graphics.Paint.Style.STROKE; strokeWidth = px * 0.055f
            strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
        }
        val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = col }
        val cx = px / 2f; val cy = px / 2f
        fun rr(w: Float, h: Float, filled: Boolean = false, oy: Float = 0f) {
            val r = android.graphics.RectF(cx - w / 2, cy - h / 2 + oy, cx + w / 2, cy + h / 2 + oy)
            c.drawRoundRect(r, px * 0.08f, px * 0.08f, if (filled) fill else stroke)
        }
        when (mode) {
            LayoutStore.SCREEN_INT_PORTRAIT -> {
                rr(px * 0.46f, px * 0.70f)
                c.drawLine(cx - px * 0.15f, cy - px * 0.13f, cx + px * 0.15f, cy - px * 0.13f, stroke)
            }
            LayoutStore.SCREEN_INT_LANDSCAPE -> {
                rr(px * 0.72f, px * 0.46f)
                c.drawLine(cx - px * 0.11f, cy - px * 0.15f, cx - px * 0.11f, cy + px * 0.15f, stroke)
            }
            LayoutStore.SCREEN_EXTERNAL -> {
                val w = px * 0.72f; val h = px * 0.44f; val t = cy - h / 2 - px * 0.06f
                c.drawRoundRect(android.graphics.RectF(cx - w / 2, t, cx + w / 2, t + h), px * 0.06f, px * 0.06f, stroke)
                c.drawRect(android.graphics.RectF(cx - px * 0.045f, t + h, cx + px * 0.045f, t + h + px * 0.10f), fill)
                c.drawRoundRect(android.graphics.RectF(cx - px * 0.15f, t + h + px * 0.10f, cx + px * 0.15f, t + h + px * 0.15f), px * 0.02f, px * 0.02f, fill)
            }
            LayoutStore.SCREEN_FULLSCREEN -> rr(px * 0.74f, px * 0.48f, filled = true)
            else -> { // AUTO: a portrait and a landscape frame overlaid
                rr(px * 0.64f, px * 0.42f)
                rr(px * 0.40f, px * 0.60f)
            }
        }
        return b
    }

    private fun onPlayerSlotTap(port: Int, onPort: List<Surface>) {
        // Straight to the surface when there is only one on this port - an intermediate list
        // of one item is a tap that asks a question it already knows the answer to.
        if (onPort.size == 1) {
            showSurfaceConfig(onPort.first())
            return
        }
        openLibraryMenuIfNeeded()
        libraryMenu.push(getString(R.string.player_n, port + 1)) {
            with(libraryMenu) {
                body {
                    for (surface in onPort) {
                        addView(navRow(null, surface.name, portLabel(port), valueIsLive = false) {
                            showSurfaceConfig(surface)
                        })
                    }
                    addView(navRow(null, getString(R.string.assign_to_player, port + 1)) {
                        assignToPort(port)
                    })
                }
            }
        }
    }

    private fun assignToPort(port: Int) {
        val surfaces = availableSurfaces()
        openLibraryMenuIfNeeded()
        libraryMenu.pushActions(
            getString(R.string.assign_to_player, port + 1),
            surfaces.map { surface ->
                surface.name to {
                    inputConfig.setPort(surface.key, port)
                    buildControllerBar()
                    libraryMenu.close()
                }
            },
        )
    }

    private fun showSurfaceConfig(s: Surface) {
        // The touchscreen only has a player assignment — go straight to it.
        if (!s.isGamepad) { showPlayerPicker(s); return }
        openLibraryMenuIfNeeded()
        libraryMenu.push(s.name) {
            with(libraryMenu) {
                body {
                    addView(navRow(null, getString(R.string.ctrl_set_player),
                        portLabel(portFor(s.key, s.device)), valueIsLive = false) {
                        showPlayerPicker(s)
                    })
                    // A switch, not a list item whose label rewrites itself.
                    addView(toggleRow(
                        getString(R.string.ctrl_stick_dpad), inputConfig.leftStickAsDpad(s.key),
                    ) { on ->
                        inputConfig.setLeftStickAsDpad(s.key, on)
                        buildControllerBar()
                    })
                    addView(navRow(null, getString(R.string.ctrl_remap)) {
                        libraryMenu.close(); showRemap(s.key, s.name)
                    })
                    addView(navRow(null, getString(R.string.ctrl_stick_tuning)) {
                        showStickTuning(s.key, s.name)
                    })
                    addView(spacer())
                    addView(bigButton(getString(R.string.ctrl_reset_map), danger = true) {
                        inputConfig.clearBindings(s.key)
                        Toast.makeText(this@MainActivity, R.string.ctrl_map_reset, Toast.LENGTH_SHORT).show()
                    })
                }
            }
        }
    }

    private fun showPlayerPicker(s: Surface) {
        val ports = listOf(0, 1, 2, 3, InputConfig.PORT_OFF)
        openLibraryMenuIfNeeded()
        libraryMenu.pushSelect(
            menuTitle(R.string.ctrl_set_player),
            ports.map { portLabel(it) },
            ports.indexOf(portFor(s.key, s.device)).coerceAtLeast(0),
        ) { which ->
            inputConfig.setPort(s.key, ports[which])
            buildControllerBar()
        }
    }

    private fun showStickTuning(key: String, name: String) {
        openLibraryMenuIfNeeded()
        libraryMenu.push(name) {
            with(libraryMenu) {
                body(padSides = 18f) {
                    // Dead zone 0..0.5, sensitivity 0.5..2.0, each printing its own unit.
                    addView(slider(
                        getString(R.string.ctrl_deadzone),
                        (inputConfig.deadzone(key) / 0.5f).coerceIn(0f, 1f),
                        format = { "${(it * 50).toInt()}%" },
                    ) { inputConfig.setDeadzone(key, it * 0.5f) })
                    addView(slider(
                        getString(R.string.ctrl_sensitivity),
                        ((inputConfig.sensitivity(key) - 0.5f) / 1.5f).coerceIn(0f, 1f),
                        format = { "%.2f×".format(0.5f + it * 1.5f) },
                    ) { inputConfig.setSensitivity(key, 0.5f + it * 1.5f) })
                }
            }
        }
    }

    private fun showRemap(key: String, name: String) {
        val buttons = InputConfig.RETRO_BUTTONS
        fun labelFor(i: Int): String {
            val (bName, retroKey) = buttons[i]
            val phys = inputConfig.physicalFor(key, retroKey)
            val physName = phys?.let { KeyEvent.keyCodeToString(it).removePrefix("KEYCODE_") } ?: getString(R.string.remap_default)
            return "$bName  →  $physName"
        }
        val labels = buttons.indices.map { labelFor(it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.remap_title, name))
            .setItems(labels) { _, which ->
                val (bName, retroKey) = buttons[which]
                val prompt = Toast.makeText(this, getString(R.string.remap_press, bName), Toast.LENGTH_LONG); prompt.show()
                bindingCaptureDevice = key
                bindingCapture = { physicalKey ->
                    inputConfig.bind(key, physicalKey, retroKey)
                    prompt.cancel()
                    Toast.makeText(this, getString(R.string.remap_done, bName), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> bindingCapture = null; bindingCaptureDevice = null }
            .show().gamepadNavigable()
    }

    private val deviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = buildControllerBar()
        override fun onInputDeviceRemoved(deviceId: Int) = buildControllerBar()
        override fun onInputDeviceChanged(deviceId: Int) = buildControllerBar()
    }

    private fun showGameOptions(entry: RomEntry) {
        val path = entry.file.absolutePath
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        actions += getString(
            if (history.isFavorite(path)) R.string.unfavorite else R.string.favorite,
        ) to { history.toggleFavorite(path); refresh() }
        actions += getString(R.string.set_cover) to {
            pendingCoverGame = entry
            pickCover.launch(arrayOf("image/*"))
        }
        if (com.nvanloo.retroglass.model.GameCovers.has(this, path)) {
            actions += getString(R.string.remove_cover) to {
                com.nvanloo.retroglass.model.GameCovers.remove(this, path); refresh()
            }
        }
        actions += getString(R.string.change_system) to { chooseSystem(entry) }
        actions += getString(R.string.delete) to { confirmDelete(entry) }
        libraryMenu.consoleTint = MenuTheme.LIBRARY_TINT
        libraryMenu.rootStatus = null
        libraryMenu.open {
            with(libraryMenu) {
                body {
                    addView(group(entry.displayName))
                    for ((text, action) in actions.dropLast(1)) {
                        addView(navRow(null, text) { libraryMenu.close(); action() })
                    }
                    addView(spacer())
                    addView(bigButton(getString(R.string.delete), danger = true) {
                        libraryMenu.close(); confirmDelete(entry)
                    })
                }
            }
        }
    }

    private fun chooseSystem(entry: RomEntry) {
        val consoles = RomLibrary.candidateConsoles(entry)
        val names = consoles.map { it.displayName }.toTypedArray()
        val current = consoles.indexOf(entry.console).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.change_system)
            .setSingleChoiceItems(names, current) { dialog, which ->
                RomLibrary.moveToConsole(this, entry, consoles[which])
                dialog.dismiss()
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show().gamepadNavigable()
    }

    private fun showBiosStatus() {
        val statuses = BiosCatalog.status(this)
        openLibraryMenuIfNeeded()
        libraryMenu.push(menuTitle(R.string.bios_status_title)) {
            with(libraryMenu) {
                body {
                    val missing = statuses.count { !it.present }
                    addView(group(
                        getString(R.string.bios_status_title),
                        if (missing == 0) getString(R.string.bios_all_present)
                        else getString(R.string.bios_missing_count, missing),
                        trailingLive = missing == 0,
                    ))
                    // One row per system, so a missing BIOS is scannable instead of buried in
                    // a wall of text inside a message box.
                    for (st in statuses) {
                        addView(infoRow(
                            st.system,
                            // A column of the word "present" says nothing; the tick carries the
                            // state and the words are kept for what is actually missing.
                            if (st.present) "✓" else getString(R.string.bios_needs, st.filenames),
                            ok = st.present,
                        ))
                    }
                    addView(bigButton(getString(R.string.import_bios_file), tint = true) {
                        libraryMenu.close(); pickBios.launch(arrayOf("image/*", "*/*"))
                    })
                }
            }
        }
    }

    private fun confirmDelete(entry: RomEntry) {
        AlertDialog.Builder(this)
            .setTitle(entry.displayName)
            .setMessage(R.string.delete_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                RomLibrary.delete(this, entry)
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show().gamepadNavigable()
    }

    // ------------------------------------------------------------ adapter

    /** Horizontal console coverflow — each page is the console's photo (or a placeholder).
     *  Reports a large virtual count so the pager loops endlessly (real console = pos mod size). */
    private inner class ConsoleCarouselAdapter : RecyclerView.Adapter<ConsoleCarouselAdapter.VH>() {
        private var items: List<com.nvanloo.retroglass.model.Console> = emptyList()
        @Suppress("NotifyDataSetChanged")
        fun submit(list: List<com.nvanloo.retroglass.model.Console>) { items = list; notifyDataSetChanged() }
        override fun getItemCount() = if (items.size <= 1) items.size else items.size * 1000
        inner class VH(val img: android.widget.ImageView) : RecyclerView.ViewHolder(img)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val d = parent.resources.displayMetrics.density
            val img = android.widget.ImageView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(itemW, ViewGroup.LayoutParams.MATCH_PARENT)
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                val p = (10 * d).toInt(); setPadding(p, p, p, p)
            }
            return VH(img)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val console = items[position % items.size]
            val bmp = com.nvanloo.retroglass.model.ConsoleImages.photo(holder.img.context, console)
            if (bmp != null) {
                holder.img.setImageBitmap(bmp); holder.img.background = null
            } else {
                holder.img.setImageDrawable(null)
                holder.img.background = GradientDrawable().apply {
                    cornerRadius = 20f; setColor(Color.parseColor("#15151C"))
                    setStroke(2, Color.parseColor("#2A2A34"))
                }
            }
        }
    }

    /** Games for the selected console: monogram chip (console accent) + name + play. */
    private inner class GamesAdapter(
        val onClick: (RomEntry) -> Unit,
        val onLongClick: (RomEntry) -> Unit,
    ) : RecyclerView.Adapter<GamesAdapter.VH>() {
        private var items: List<RomEntry> = emptyList()
        var accent: Int = MenuTheme.ACCENT
        @Suppress("NotifyDataSetChanged")
        fun submit(list: List<RomEntry>) { items = list; notifyDataSetChanged() }
        override fun getItemCount() = items.size
        inner class VH(
            val row: LinearLayout, val chip: TextView,
            val name: TextView, val sub: TextView,
        ) : RecyclerView.ViewHolder(row)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context
            val d = ctx.resources.displayMetrics.density
            fun dp(v: Float) = (v * d).toInt()
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16f), dp(9f), dp(12f), dp(9f))
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                isFocusable = true
                setOnFocusChangeListener { v, f -> v.setBackgroundColor(if (f) Color.parseColor("#22FFFFFF") else Color.TRANSPARENT) }
            }
            val chip = TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dp(46f), dp(46f))
                gravity = Gravity.CENTER; setTextColor(Color.WHITE); textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            val texts = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(dp(14f), 0, dp(8f), 0)
            }
            val name = TextView(ctx).apply { setTextColor(Color.WHITE); textSize = 16f; maxLines = 1 }
            val sub = TextView(ctx).apply { setTextColor(Color.parseColor("#8A8A96")); textSize = 12f }
            texts.addView(name); texts.addView(sub)
            val play = TextView(ctx).apply {
                text = "▶"; setTextColor(MenuTheme.GROUP); textSize = 14f
                setPadding(dp(10f), dp(8f), dp(8f), dp(8f))
            }
            row.addView(chip); row.addView(texts); row.addView(play)
            return VH(row, chip, name, sub)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val e = items[position]
            val d = holder.row.resources.displayMetrics.density
            holder.name.text = e.displayName
            holder.chip.text = initials(e.displayName)
            holder.chip.background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR, intArrayOf(lighten(accent), accent),
            ).apply { cornerRadius = 12f * d }
            if (history.isFavorite(e.file.absolutePath)) {
                holder.sub.visibility = View.VISIBLE
                holder.sub.text = getString(R.string.fav_tag)
            } else {
                holder.sub.visibility = View.GONE
            }
            holder.row.setOnClickListener { onClick(e) }
            holder.row.setOnLongClickListener { onLongClick(e); true }
        }
    }
}
