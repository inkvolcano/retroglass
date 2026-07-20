package com.nvanloo.retroglass.controller

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.nvanloo.retroglass.R
import com.nvanloo.retroglass.model.Console

/**
 * The phone-as-companion dashboard shown while a single player drives the game on an external
 * display with a physical controller. Instead of a touch gamepad, the phone shows:
 *
 *  - live performance stats (FPS, frame time, play-session time, emulation speed),
 *  - a live-input display: the console's controller diagram, lit up in real time from the pad
 *    (a [ControllerView] in [ControllerView.monitorMode]),
 *  - the current button mapping (retro button -> physical key), tappable to remap,
 *  - big quick-action buttons (save / load state, fast-forward, screenshot, menu).
 *
 * It is a passive view: [EmulationActivity] pushes stats and physical-input state in, and the
 * action buttons call back out. The game keeps running on the external display throughout.
 */
class CompanionView(context: Context) : android.widget.FrameLayout(context) {

    var onSaveState: () -> Unit = {}
    var onLoadState: () -> Unit = {}
    var onFastForward: () -> Unit = {}
    var onScreenshot: () -> Unit = {}
    var onOpenMenu: () -> Unit = {}
    var onRemap: () -> Unit = {}
    var onUseTouchPad: () -> Unit = {}

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = (v * density).toInt()

    private val monitor = ControllerView(context).apply {
        monitorMode = true
        overlayMode = false
        layoutMode = ControllerView.LAYOUT_FULLPAD
        isClickable = false
        isFocusable = false
    }

    private val titleView = text(19f, FG, bold = true)
    private val padView = text(13f, ACCENT2, bold = true).apply { gravity = Gravity.END }

    private val fpsValue = statValueView()
    private val frameValue = statValueView()
    private val timeValue = statValueView()
    private val speedValue = statValueView()

    private val mappingContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private lateinit var contentRow: LinearLayout

    // D-pad diagram is driven from two sources at once (some pads send the D-pad as button
    // key events, others as a hat/analog axis); track each and show their union.
    private var dpadKeyX = 0f
    private var dpadKeyY = 0f
    private var dpadHatX = 0f
    private var dpadHatY = 0f

    init {
        setBackgroundColor(BG)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(14f)
            setPadding(p, p, p, p)
        }
        root.addView(buildHeader())
        root.addView(buildStatsRow(), lp(matchW = true, marginTop = 12f))
        contentRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        contentRow.addView(buildDiagramPanel(), weightRowChild())
        contentRow.addView(buildMappingPanel(), weightRowChild())
        root.addView(contentRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f,
        ).apply { topMargin = dp(12f) })
        root.addView(buildActionRow(), lp(matchW = true, marginTop = 12f))
        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    // ----------------------------------------------------------------- build

    private fun buildHeader(): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(padView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { marginEnd = dp(10f) })
        row.addView(pillButton(R.string.companion_use_pad) { onUseTouchPad() })
        return row
    }

    private fun buildStatsRow(): View {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(statTile(R.string.companion_stat_fps, fpsValue), statCell())
        row.addView(statTile(R.string.companion_stat_frame, frameValue), statCell())
        row.addView(statTile(R.string.companion_stat_time, timeValue), statCell())
        row.addView(statTile(R.string.companion_stat_speed, speedValue), statCell())
        return row
    }

    private fun buildDiagramPanel(): View {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = card()
            val p = dp(6f)
            setPadding(p, p, p, p)
        }
        panel.addView(sectionLabel(R.string.companion_input_title))
        panel.addView(monitor, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f,
        ).apply { topMargin = dp(4f) })
        return panel
    }

    private fun buildMappingPanel(): View {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = card()
            val p = dp(10f)
            setPadding(p, p, p, p)
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(sectionLabel(R.string.companion_mapping_title),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(pillButton(R.string.companion_action_remap) { onRemap() })
        panel.addView(header)
        val scroll = ScrollView(context).apply { isFillViewport = true }
        scroll.addView(mappingContainer, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        panel.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f,
        ).apply { topMargin = dp(6f) })
        return panel
    }

    private fun buildActionRow(): View {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(actionButton(R.string.companion_action_save) { onSaveState() }, actionCell())
        row.addView(actionButton(R.string.companion_action_load) { onLoadState() }, actionCell())
        row.addView(actionButton(R.string.companion_action_ff) { onFastForward() }, actionCell())
        row.addView(actionButton(R.string.companion_action_shot) { onScreenshot() }, actionCell())
        row.addView(actionButton(R.string.companion_action_menu) { onOpenMenu() }, actionCell())
        return row
    }

    // ----------------------------------------------------------------- public API

    fun bindConsole(console: Console) = monitor.setConsole(console)

    fun setHeader(title: String, controllerName: String?) {
        titleView.text = title
        padView.text = controllerName ?: context.getString(R.string.companion_no_pad)
    }

    /** Rebuilds the mapping list: each row is a retro button and the physical key bound to it. */
    fun setMapping(rows: List<Pair<String, String>>) {
        mappingContainer.removeAllViews()
        for ((retro, physical) in rows) {
            val r = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val vp = dp(5f)
                setPadding(0, vp, 0, vp)
            }
            r.addView(text(14f, FG).apply { text = retro },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            r.addView(text(14f, ACCENT2, bold = true).apply {
                text = physical
                gravity = Gravity.END
            })
            mappingContainer.addView(r)
        }
        if (rows.isEmpty()) {
            mappingContainer.addView(text(13f, DIM).apply {
                setText(R.string.companion_no_pad)
            })
        }
    }

    fun updateStats(fps: Int, frameMs: Float, playSeconds: Long, fastForward: Boolean) {
        fpsValue.text = fps.toString()
        frameValue.text = if (fps > 0) String.format("%.1f", frameMs) else "—"
        timeValue.text = formatTime(playSeconds)
        speedValue.text = context.getString(
            if (fastForward) R.string.companion_speed_ff else R.string.companion_speed_normal,
        )
        speedValue.setTextColor(if (fastForward) ACCENT else FG)
    }

    // --- live physical input -> diagram

    /** A physical face/shoulder/system button (or D-pad key) went down/up. */
    fun inputButton(retroKeyCode: Int, pressed: Boolean) {
        when (retroKeyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> { dpadKeyY = if (pressed) -1f else 0f; refreshDpad() }
            KeyEvent.KEYCODE_DPAD_DOWN -> { dpadKeyY = if (pressed) 1f else 0f; refreshDpad() }
            KeyEvent.KEYCODE_DPAD_LEFT -> { dpadKeyX = if (pressed) -1f else 0f; refreshDpad() }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { dpadKeyX = if (pressed) 1f else 0f; refreshDpad() }
            else -> monitor.monitorButton(retroKeyCode, pressed)
        }
    }

    /** D-pad reported as a hat/analog axis (most Bluetooth pads). */
    fun inputHat(x: Float, y: Float) {
        dpadHatX = x; dpadHatY = y; refreshDpad()
    }

    fun inputStick(id: String, x: Float, y: Float) = monitor.monitorStick(id, x, y)

    fun clearInput() {
        dpadKeyX = 0f; dpadKeyY = 0f; dpadHatX = 0f; dpadHatY = 0f
        monitor.monitorClear()
    }

    private fun refreshDpad() = monitor.monitorDpad(
        (dpadKeyX + dpadHatX).coerceIn(-1f, 1f),
        (dpadKeyY + dpadHatY).coerceIn(-1f, 1f),
    )

    // ----------------------------------------------------------------- layout

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Side-by-side (diagram | mapping) when there's width to spare; stacked when tall.
        val horizontal = w >= h
        val orient = if (horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        if (contentRow.orientation != orient) {
            contentRow.orientation = orient
            for (i in 0 until contentRow.childCount) {
                contentRow.getChildAt(i).layoutParams = weightRowChild()
            }
            contentRow.requestLayout()
        }
    }

    /** Row child that splits space evenly along the row's current axis, with a gap between. */
    private fun weightRowChild(): LinearLayout.LayoutParams =
        if (contentRow.orientation == LinearLayout.HORIZONTAL) {
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                .apply { marginEnd = dp(6f) }
        } else {
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                .apply { bottomMargin = dp(6f) }
        }

    // ----------------------------------------------------------------- view helpers

    private fun text(size: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun statValueView() = TextView(context).apply {
        textSize = 22f
        setTextColor(FG)
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun statTile(labelRes: Int, value: TextView): View {
        val tile = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = card()
            val p = dp(8f)
            setPadding(p, p, p, p)
        }
        tile.addView(value)
        tile.addView(text(11f, DIM, bold = true).apply {
            setText(labelRes)
            letterSpacing = 0.08f
        })
        return tile
    }

    private fun sectionLabel(res: Int) = text(12f, DIM, bold = true).apply {
        setText(res)
        letterSpacing = 0.06f
    }

    private fun statCell() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        .apply { marginEnd = dp(6f) }

    private fun actionCell() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        .apply { marginEnd = dp(6f) }

    private fun lp(matchW: Boolean, marginTop: Float) = LinearLayout.LayoutParams(
        if (matchW) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(marginTop) }

    private fun actionButton(labelRes: Int, onClick: () -> Unit) =
        MaterialButton(context).apply {
            setText(labelRes)
            isAllCaps = false
            textSize = 13f
            insetTop = 0
            insetBottom = 0
            setPadding(dp(6f), dp(10f), dp(6f), dp(10f))
            setOnClickListener { onClick() }
        }

    private fun pillButton(labelRes: Int, onClick: () -> Unit) =
        MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            setText(labelRes)
            isAllCaps = false
            textSize = 12f
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(12f), dp(4f), dp(12f), dp(4f))
            setOnClickListener { onClick() }
        }

    private fun card() = GradientDrawable().apply {
        setColor(TILE)
        cornerRadius = dp(12f).toFloat()
        setStroke(dp(1f), STROKE)
    }

    companion object {
        private const val BG = 0xFF0B0B12.toInt()
        private const val TILE = 0xFF171722.toInt()
        private const val STROKE = 0xFF2A2A3A.toInt()
        private const val FG = 0xFFEDEDF5.toInt()
        private const val DIM = 0xFF8A8AA6.toInt()
        private const val ACCENT = 0xFF9BE870.toInt()
        private const val ACCENT2 = 0xFF7FB0FF.toInt()

        private fun formatTime(totalSeconds: Long): String {
            val s = totalSeconds % 60
            val m = (totalSeconds / 60) % 60
            val h = totalSeconds / 3600
            return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
            else String.format("%d:%02d", m, s)
        }
    }
}
