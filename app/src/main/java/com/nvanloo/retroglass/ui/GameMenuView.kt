package com.nvanloo.retroglass.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.nvanloo.retroglass.R
import com.nvanloo.retroglass.ui.MenuTheme.focusedTile
import com.nvanloo.retroglass.ui.MenuTheme.rowBackground
import com.nvanloo.retroglass.ui.MenuTheme.tile
import com.nvanloo.retroglass.ui.MenuTheme.tintedTile

/**
 * The in-game menu — a full-screen, gamepad-first overlay that replaces the stack of
 * `AlertDialog`s. Implements the "In-Game Menu" design (claude.ai/design project
 * "Retroglass Emulator Menu Design").
 *
 * Three things about the design drive the structure here:
 *
 *  * **Gamepad focus is a functional state, not polish.** In glasses mode there is no
 *    touchscreen in front of the user's eyes, so every interactive row is a focusable View
 *    with an explicit accent ring, and D-pad traversal is the primary input.
 *  * **Screens are a stack, not a pile of dialogs.** [push]/[pop] keep one overlay alive, so
 *    B/back walks back up instead of dismissing to the game.
 *  * **The live preview is a hole, not a screenshot.** [previewWindow] paints nothing; this
 *    view's own background is transparent and each section paints its own, so the running game
 *    shows through the gap at 1:1. That makes it genuinely live — you are looking at the real
 *    filter output while dragging its slider.
 */
@SuppressLint("ViewConstructor")
class GameMenuView(context: Context) : FrameLayout(context) {

    fun interface ScreenBuilder { fun build(): View }

    private class Entry(val title: String?, val builder: ScreenBuilder)

    /** Per-console identity colour (`Console.accentColor`); tints the top bar and status dot. */
    var consoleTint: Int = MenuTheme.ACCENT
    var consoleName: String = ""

    /** Identity line under the logo ("PlayStation · running"). Null hides the row — the
     *  library has no console to identify, so it just gets the logo. */
    var rootStatus: String? = null

    /** Called when the user closes the whole menu (✕, B at the root, or back). */
    var onClosed: (() -> Unit)? = null

    private val stack = ArrayList<Entry>()
    private val host = FrameLayout(context)

    /** Local dp: MenuTheme.dp is a Context extension and the receiver here is the View. */
    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).toInt()

    init {
        // Transparent: every screen paints its own background so previewWindow() can be a hole.
        addView(host, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        isClickable = true
        isFocusable = true
        descendantFocusability = FOCUS_AFTER_DESCENDANTS
        // The library draws edge-to-edge, so without this the ✕ / logo / B row sits under the
        // clock and battery. In-game the bars are hidden and these insets are simply 0.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val bars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                    androidx.core.view.WindowInsetsCompat.Type.displayCutout(),
            )
            host.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
    }

    // ------------------------------------------------------------------ stack

    fun open(builder: ScreenBuilder) {
        stack.clear()
        stack.add(Entry(null, builder))
        visibility = View.VISIBLE
        render()
    }

    fun push(title: String, builder: ScreenBuilder) {
        stack.add(Entry(title, builder))
        render()
    }

    /** @return true if a screen was popped; false when already at the root. */
    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.size - 1)
        render()
        return true
    }

    fun close() {
        visibility = View.GONE
        stack.clear()
        host.removeAllViews()
        onClosed?.invoke()
    }

    /** Rebuilds the current screen in place — call after changing a value the screen displays. */
    fun refresh() {
        if (stack.isNotEmpty()) render()
    }

    val isOpen: Boolean get() = visibility == View.VISIBLE && stack.isNotEmpty()

    /** Routes gamepad B / back. @return true when handled. */
    fun onBack(): Boolean {
        if (!isOpen) return false
        if (!pop()) close()
        return true
    }

    private fun render() {
        val entry = stack.last()
        host.removeAllViews()
        val screen = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        if (entry.title == null) screen.addView(rootHeader()) else screen.addView(subHeader(entry.title))
        val body = entry.builder.build()
        screen.addView(body, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f,
        ))
        host.addView(screen, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        post { screen.findFocusableChild()?.requestFocus() }
    }

    private fun View.findFocusableChild(): View? {
        if (isFocusable && visibility == View.VISIBLE && this !is ViewGroup) return this
        if (this is ViewGroup) {
            if (isFocusable && this.tag == TAG_FOCUSABLE) return this
            for (i in 0 until childCount) getChildAt(i).findFocusableChild()?.let { return it }
        }
        return null
    }

    // ----------------------------------------------------------------- header

    /** Root: ✕ · logo · "B" hint, then the console identity line, under a tinted rule. */
    private fun rootHeader(): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(MenuTheme.BG)
        // 3dp rule that fades in from the console's colour at both ends.
        addView(View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.TRANSPARENT, MenuTheme.alpha(consoleTint, 0xB3), Color.TRANSPARENT),
            )
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3f)))

        addView(FrameLayout(context).apply {
            setPadding(dp(16f), dp(14f), dp(16f), dp(8f))
            addView(glyphButton("✕") { close() }, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START or Gravity.CENTER_VERTICAL,
            ))
            addView(ImageView(context).apply {
                setImageResource(R.drawable.retroglass_logo)
                adjustViewBounds = true
            }, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(22f), Gravity.CENTER,
            ))
            addView(keyBadge("B"), FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END or Gravity.CENTER_VERTICAL,
            ))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        rootStatus?.let { status ->
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(12f))
                addView(View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(consoleTint)
                    }
                }, LinearLayout.LayoutParams(dp(7f), dp(7f)).apply { marginEnd = dp(7f) })
                addView(TextView(context).apply {
                    text = status
                    setTextColor(MenuTheme.DIM)
                    textSize = 12f
                })
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        if (rootStatus == null) addView(View(context), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(6f),
        ))
    }

    /** Sub-screen: ‹ back · title · "B" hint. */
    private fun subHeader(title: String): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(MenuTheme.BG)
        setPadding(dp(16f), dp(14f), dp(16f), dp(10f))
        addView(glyphButton("‹") { onBack() })
        addView(TextView(context).apply {
            text = title
            setTextColor(MenuTheme.FG)
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(12f)
        })
        addView(keyBadge("B"))
    }

    private fun glyphButton(glyph: String, onClick: () -> Unit) = TextView(context).apply {
        text = glyph
        setTextColor(MenuTheme.DIM)
        textSize = 18f
        gravity = Gravity.CENTER
        // These two are the most-used controls in the menu and were under the 48dp floor.
        minWidth = dp(48f)
        minHeight = dp(48f)
        isClickable = true
        isFocusable = true
        background = context.rowBackground(
            fill = Color.TRANSPARENT, stroke = Color.TRANSPARENT, radius = 24f,
        )
        setOnClickListener { onClick() }
    }

    /** The little bordered "B" chip that tells a pad user which button goes back. */
    private fun keyBadge(label: String) = TextView(context).apply {
        text = label
        setTextColor(MenuTheme.DIM)
        textSize = 11f
        setPadding(dp(7f), dp(2f), dp(7f), dp(2f))
        background = context.tile(fill = Color.TRANSPARENT, radius = 7f)
    }

    // ------------------------------------------------------------ row factory

    /** Uppercase monospace section label ("PLAY", "SETTINGS"). */
    fun group(text: String, trailing: String? = null, trailingLive: Boolean = false): View =
        LinearLayout(context).apply {
            tag = TAG_GROUP
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2f), dp(6f), dp(2f), dp(2f))
            addView(TextView(context).apply {
                this.text = text.uppercase()
                setTextColor(MenuTheme.GROUP)
                textSize = 11f
                typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.BOLD)
                letterSpacing = 0.14f
            })
            if (trailing != null) addView(TextView(context).apply {
                this.text = trailing
                setTextColor(if (trailingLive) MenuTheme.ACCENT else MenuTheme.DIM)
                textSize = 11f
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = dp(8f) })
        }

    /**
     * Base row: tile background, focus ring, click handling. Everything else composes on top.
     */
    private fun rowShell(
        heightDp: Float,
        fill: Int = MenuTheme.TILE,
        strokeTint: Int? = null,
        onClick: (() -> Unit)? = null,
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(heightDp)
        val strokeColor =
            if (strokeTint != null) MenuTheme.alpha(strokeTint, 0x66) else MenuTheme.STROKE
        background = if (onClick != null) {
            context.rowBackground(fill = fill, stroke = strokeColor)
        } else {
            context.tile(fill = fill, stroke = strokeColor)
        }
        if (onClick != null) {
            isFocusable = true
            tag = TAG_FOCUSABLE
            setOnClickListener { onClick() }
        }
    }

    private fun label(text: String, size: Float = 15f, color: Int = MenuTheme.FG, bold: Boolean = false) =
        TextView(context).apply {
            this.text = text
            setTextColor(color)
            textSize = size
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

    /** A row that drills down: optional icon, label, live value, chevron. */
    fun navRow(
        icon: String?,
        text: String,
        value: String? = null,
        valueIsLive: Boolean = true,
        chevron: Boolean = true,
        onClick: () -> Unit,
    ): View = rowShell(MenuTheme.ROW_H, onClick = onClick).apply {
        setPadding(dp(14f), 0, dp(14f), 0)
        if (icon != null) addView(label(icon, color = MenuTheme.DIM), LinearLayout.LayoutParams(
            dp(20f), ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        addView(label(text), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f,
        ).apply { marginStart = if (icon != null) dp(6f) else 0 })
        if (value != null) addView(
            label(
                value, size = 13f,
                color = if (valueIsLive) MenuTheme.ACCENT else MenuTheme.DIM,
                bold = valueIsLive,
            ).apply { maxWidth = dp(170f) },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = dp(8f) },
        )
        if (chevron) addView(label("›", color = MenuTheme.CHEVRON))
    }

    /** A row carrying a pill switch. */
    fun toggleRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val sw = ToggleView(context).apply { isOn = checked }
        return rowShell(50f, onClick = {
            sw.isOn = !sw.isOn
            onChange(sw.isOn)
        }).apply {
            setPadding(dp(15f), 0, dp(15f), 0)
            addView(label(text), LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f,
            ))
            addView(sw, LinearLayout.LayoutParams(dp(MenuTheme.TOGGLE_W), dp(MenuTheme.TOGGLE_H)))
        }
    }

    /** Square-ish two-line tile, used for the Save/Load pair. */
    fun actionTile(title: String, sub: String?, onClick: () -> Unit): View =
        rowShell(MenuTheme.TILE_H, onClick = onClick).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            // A vertical LinearLayout hands children MATCH_PARENT width, so the TextView would
            // fill the tile and left-align its own text regardless of this gravity.
            val wrap = { -> LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) }
            addView(label(title, bold = true), wrap())
            if (sub != null) addView(label(sub, size = 10f, color = MenuTheme.DIM), wrap())
        }

    /** Full-width button. [danger] paints the destructive variant, [tint] the console one. */
    fun bigButton(
        text: String,
        danger: Boolean = false,
        tint: Boolean = false,
        onClick: () -> Unit,
    ): View {
        val accentColor = when {
            danger -> MenuTheme.DANGER
            tint -> consoleTint
            else -> null
        }
        val fill = if (accentColor != null) MenuTheme.alpha(accentColor, 0x22) else MenuTheme.TILE
        return rowShell(MenuTheme.ROW_H, fill = fill, strokeTint = accentColor, onClick = onClick).apply {
            gravity = Gravity.CENTER
            addView(label(
                text,
                color = accentColor ?: MenuTheme.FG,
                bold = accentColor != null,
            ))
        }
    }

    /**
     * Label + live value + track. The whole block takes focus and D-pad left/right nudges the
     * value, matching the design's ring-around-the-block focus state.
     */
    fun slider(
        text: String,
        value: Float,
        format: (Float) -> String = { "%.2f".format(it) },
        onChange: (Float) -> Unit,
    ): View {
        val readout = label(format(value), size = 14f, color = MenuTheme.ACCENT, bold = true)
        val track = SliderView(context).apply {
            this.value = value
            onValueChanged = { v ->
                readout.text = format(v)
                onChange(v)
            }
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8f), dp(10f), dp(8f), dp(10f))
            isFocusable = true
            tag = TAG_FOCUSABLE
            val focusBg = context.focusedTile(fill = Color.TRANSPARENT, radius = 12f)
            setOnFocusChangeListener { v, has -> v.background = if (has) focusBg else null }
            setOnKeyListener { _, keyCode, ev ->
                if (ev.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { track.nudge(-STEP); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { track.nudge(STEP); true }
                    else -> false
                }
            }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(label(text, size = 14f), LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f,
                ))
                addView(readout)
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(12f) })
            addView(track, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(MenuTheme.KNOB),
            ))
        }
    }

    /**
     * One stage of the filter chain: grip, ordinal badge, name, on/off. The ordinal is the
     * point of the screen — it says the chain is ordered, which a checklist cannot.
     */
    fun pipelineRow(
        ordinal: Int,
        text: String,
        on: Boolean,
        onToggle: () -> Unit,
    ): View {
        val sw = ToggleView(context).apply { isOn = on }
        return rowShell(56f, strokeTint = MenuTheme.ACCENT, onClick = {
            sw.isOn = !sw.isOn
            onToggle()
        }).apply {
            setPadding(dp(12f), 0, dp(12f), 0)
            addView(label("≡", size = 19f, color = MenuTheme.ACCENT))
            addView(TextView(context).apply {
                this.text = ordinal.toString()
                setTextColor(MenuTheme.ACCENT)
                textSize = 12f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    cornerRadius = dp(7f).toFloat()
                    setColor(MenuTheme.alpha(MenuTheme.ACCENT, 0x2E))
                }
            }, LinearLayout.LayoutParams(dp(22f), dp(22f)).apply {
                marginStart = dp(10f); marginEnd = dp(10f)
            })
            addView(label(text), LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f,
            ))
            addView(sw, LinearLayout.LayoutParams(dp(MenuTheme.TOGGLE_W), dp(MenuTheme.TOGGLE_H)))
        }
    }

    /** A dashed "off" stage waiting to be added to the chain. */
    fun addRow(text: String, onAdd: () -> Unit): View =
        rowShell(48f, onClick = onAdd).apply {
            setPadding(dp(14f), 0, dp(14f), 0)
            background = GradientDrawable().apply {
                cornerRadius = dp(MenuTheme.RADIUS).toFloat()
                setColor(Color.TRANSPARENT)
                setStroke(dp(1f), MenuTheme.STROKE, dp(4f).toFloat(), dp(3f).toFloat())
            }
            val resting = background
            setOnFocusChangeListener { v, has ->
                v.background = if (has) context.focusedTile(Color.TRANSPARENT) else resting
            }
            addView(TextView(context).apply {
                this.text = "＋"
                setTextColor(MenuTheme.DIM)
                textSize = 13f
                gravity = Gravity.CENTER
                background = context.tile(fill = Color.TRANSPARENT, radius = 7f)
            }, LinearLayout.LayoutParams(dp(22f), dp(22f)).apply { marginEnd = dp(10f) })
            addView(label(text, color = MenuTheme.DIM), LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f,
            ))
        }

    /**
     * A transparent gap the running game shows through — the design's "◉ LIVE" tile. Nothing is
     * painted, so what you see is the real, currently-filtered frame rather than a snapshot.
     */
    fun previewWindow(heightDp: Float, caption: String): View = FrameLayout(context).apply {
        addView(TextView(context).apply {
            text = caption
            setTextColor(MenuTheme.DIM)
            textSize = 11f
            typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.BOLD)
            setBackgroundColor(MenuTheme.alpha(Color.BLACK, 0xAA))
            setPadding(dp(6f), dp(2f), dp(6f), dp(2f))
        }, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.START,
        ).apply { leftMargin = dp(11f); bottomMargin = dp(9f) })
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp),
        )
    }

    /** Vertical body with the design's 9dp rhythm, scrolling when it overflows. */
    fun body(padSides: Float = 14f, build: LinearLayout.() -> Unit): View {
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(MenuTheme.BG)
            setPadding(dp(padSides), dp(2f), dp(padSides), dp(14f))
            build()
            // Start at 1: margining the first child left a stray gap under the header.
            // Group labels take more air above so sections actually read as sections.
            for (i in 1 until childCount) {
                val child = getChildAt(i)
                val lp = child.layoutParams as? LinearLayout.LayoutParams ?: continue
                lp.topMargin = if (child.tag == TAG_GROUP) dp(18f) else dp(9f)
            }
        }
        return ScrollView(context).apply {
            isFillViewport = true
            setBackgroundColor(MenuTheme.BG)
            addView(col, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }
    }

    /**
     * Two-line row: label above, current value below in monospace. The value is [MenuTheme.ACCENT]
     * when the user has overridden it and [MenuTheme.DIM] when it is still the core's default —
     * so "what have I actually changed" is readable by colour alone.
     */
    fun valueRow(title: String, value: String, changed: Boolean, onClick: () -> Unit): View =
        rowShell(58f, onClick = onClick).apply {
            setPadding(dp(14f), dp(9f), dp(14f), dp(9f))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(title, size = 14f))
                addView(TextView(context).apply {
                    text = value
                    setTextColor(if (changed) MenuTheme.ACCENT else MenuTheme.DIM)
                    textSize = 12f
                    typeface = android.graphics.Typeface.MONOSPACE
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(2f) })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(label("›", color = MenuTheme.CHEVRON))
        }

    /** Single-select row: the chosen one is ticked and tinted. */
    fun selectRow(text: String, selected: Boolean, onClick: () -> Unit): View =
        rowShell(MenuTheme.ROW_H, onClick = onClick).apply {
            setPadding(dp(14f), 0, dp(14f), 0)
            addView(label(text, color = if (selected) MenuTheme.ACCENT else MenuTheme.FG),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (selected) addView(label("✓", color = MenuTheme.ACCENT, bold = true))
        }

    /** Non-interactive status line (BIOS present/missing, disc info). */
    fun infoRow(text: String, value: String, ok: Boolean? = null): View =
        rowShell(MenuTheme.ROW_H).apply {
            setPadding(dp(14f), 0, dp(14f), 0)
            addView(label(text), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(label(
                value, size = 13f,
                color = when (ok) { true -> MenuTheme.ACCENT; false -> MenuTheme.DANGER; else -> MenuTheme.DIM },
                bold = ok != null,
            ))
        }

    /** Rounded search/filter field. [onChange] fires per keystroke. */
    fun searchField(hint: String, onChange: (String) -> Unit): View =
        rowShell(46f).apply {
            background = context.tile(radius = 23f)
            setPadding(dp(16f), 0, dp(16f), 0)
            addView(label("⌕", size = 14f, color = MenuTheme.DIM))
            addView(android.widget.EditText(context).apply {
                this.hint = hint
                setHintTextColor(MenuTheme.DIM)
                setTextColor(MenuTheme.FG)
                textSize = 14f
                background = null
                isSingleLine = true
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun afterTextChanged(s: android.text.Editable?) { onChange(s?.toString() ?: "") }
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(8f)
            })
        }

    /**
     * Push a single-select list — the shape most of the old `setSingleChoiceItems` /
     * `setItems` dialogs actually were. Picking pops back to the caller, which rebuilds and so
     * re-reads the value it displays.
     */
    fun pushSelect(title: String, labels: List<String>, checked: Int, onPick: (Int) -> Unit) {
        push(title) {
            body {
                labels.forEachIndexed { i, text ->
                    addView(selectRow(text, i == checked) { onPick(i); pop() })
                }
            }
        }
    }

    /** Push a list of plain actions (no current-value semantics). */
    fun pushActions(title: String, items: List<Pair<String, () -> Unit>>) {
        push(title) {
            body {
                for ((text, action) in items) addView(navRow(null, text) { action() })
            }
        }
    }

    /** Eats the leftover height so a trailing row sits at the bottom (the design's margin-top:auto). */
    fun spacer(): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
    }

    /** Side-by-side pair (Save/Load, Screenshot/Save & exit). */
    fun pair(left: View, right: View): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(9f)
        })
    }

    /** A labelled column for the landscape root's four-category layout. */
    fun columnOf(title: String, vararg rows: View): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(group(title))
        for (r in rows) addView(r, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f,
        ).apply { topMargin = dp(8f) })
    }

    fun columns(vararg cols: View): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setBackgroundColor(MenuTheme.BG)
        setPadding(dp(14f), dp(6f), dp(14f), dp(14f))
        for ((i, c) in cols.withIndex()) addView(c, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.MATCH_PARENT, 1f,
        ).apply { if (i > 0) marginStart = dp(11f) })
    }

    // -------------------------------------------------------------- sub-views

    /** The 44×26 pill switch. */
    private class ToggleView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val d = context.resources.displayMetrics.density
        /** 0 = off, 1 = on. Animated, so flipping reads as movement rather than a redraw. */
        private var t = 0f
        var isOn: Boolean = false
            set(v) {
                if (field == v) return
                field = v
                android.animation.ValueAnimator.ofFloat(t, if (v) 1f else 0f).apply {
                    duration = 150L
                    addUpdateListener { a -> t = a.animatedValue as Float; invalidate() }
                }.start()
            }

        override fun onDraw(canvas: Canvas) {
            val h = height.toFloat()
            val r = h / 2f
            paint.color = blend(MenuTheme.STROKE, MenuTheme.alpha(MenuTheme.ACCENT, 0x47), t)
            canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), h), r, r, paint)
            val knob = 20f * d
            val inset = 3f * d
            val left = inset + knob / 2f
            val cx = left + (width - inset - knob / 2f - left) * t
            paint.color = blend(MenuTheme.DIM, MenuTheme.ACCENT, t)
            canvas.drawCircle(cx, h / 2f, knob / 2f, paint)
        }

        private fun blend(a: Int, b: Int, f: Float): Int = Color.argb(
            (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * f).toInt(),
            (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt(),
        )
    }

    /** Track + fill + ringed knob, matching the design's slider exactly. */
    private class SliderView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        init { setLayerType(LAYER_TYPE_SOFTWARE, null) }  // setShadowLayer needs software layers
        private val d = context.resources.displayMetrics.density
        var value: Float = 0f
            set(v) { field = v.coerceIn(0f, 1f); invalidate() }
        var onValueChanged: ((Float) -> Unit)? = null

        fun nudge(delta: Float) {
            value += delta
            onValueChanged?.invoke(value)
        }

        private fun usableWidth() = width - KNOB_D * d

        override fun onDraw(canvas: Canvas) {
            val cy = height / 2f
            val th = MenuTheme.TRACK_H * d
            val left = KNOB_D * d / 2f
            val right = width - left
            paint.color = MenuTheme.TRACK
            canvas.drawRoundRect(RectF(left, cy - th / 2, right, cy + th / 2), th / 2, th / 2, paint)
            val kx = left + usableWidth() * value
            paint.color = MenuTheme.ACCENT
            canvas.drawRoundRect(RectF(left, cy - th / 2, kx.coerceAtLeast(left), cy + th / 2), th / 2, th / 2, paint)
            // Knob: light disc with an accent ring, lifted off the track by a soft shadow.
            paint.setShadowLayer(4f * d, 0f, 2f * d, MenuTheme.alpha(Color.BLACK, 0x88))
            paint.color = MenuTheme.ACCENT
            canvas.drawCircle(kx, cy, KNOB_D * d / 2f, paint)
            paint.clearShadowLayer()
            paint.color = MenuTheme.FG
            canvas.drawCircle(kx, cy, (KNOB_D / 2f - 3f) * d, paint)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    val left = KNOB_D * d / 2f
                    value = ((event.x - left) / usableWidth().coerceAtLeast(1f))
                    onValueChanged?.invoke(value)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        private companion object { const val KNOB_D = MenuTheme.KNOB }
    }

    private companion object {
        const val TAG_FOCUSABLE = "menu-focusable"
        const val TAG_GROUP = "menu-group"
        const val STEP = 0.05f
    }
}
