package com.nvanloo.retroglass.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

/**
 * Palette and primitives for the in-game menu, taken from the "In-Game Menu" design
 * (claude.ai/design project "Retroglass Emulator Menu Design").
 *
 * The design's own note: *"CompanionView palette; PlayStation blue as the per-console tint
 * (identity dot, active value, left-rail selection); green is the system focus ring + live
 * values everywhere."* That split is the rule to hold on to when adding screens — [ACCENT] is
 * never decoration, it means "focused" or "this is a live value"; the console tint is identity.
 *
 * The tint is not hard-coded: the design's #2D8CFF is exactly `Console.PSX.accentColor`, so
 * every console brings its own and the menu re-tints per game.
 */
object MenuTheme {

    // --- surfaces (the single source; CompanionView reads these too) ---
    //
    // Near-black with a faint green cast rather than the old blue-grey. The console artwork is
    // lime line work on #010100, and a blue-tinted chrome around it read as two designs sharing
    // a screen. These are warm-neutral enough to disappear behind the art instead.
    const val BG = 0xFF060806.toInt()
    const val TILE = 0xFF10140E.toInt()
    const val STROKE = 0xFF2B3323.toInt()
    const val TRACK = 0xFF1D2318.toInt()
    const val HAIRLINE = 0xFF161B13.toInt()

    // --- text ---
    const val FG = 0xFFECF1E4.toInt()
    const val DIM = 0xFF8B9A7C.toInt()
    const val GROUP = 0xFF6C7A5E.toInt()
    const val CHEVRON = 0xFF5B6850.toInt()

    // --- semantic ---
    /**
     * Focus ring and live values. Never used as plain decoration.
     *
     * Sampled from the console artwork's own stroke core, so the accent and the drawings are
     * literally the same green. The previous #9BE870 was a softer mint that sat next to the
     * art's lime without matching it, which is worse than an obvious contrast.
     */
    const val ACCENT = 0xFF9AC40C.toInt()
    /** A dimmer pass of [ACCENT], for secondary live readouts that must not outshout focus. */
    const val ACCENT_DIM = 0xFF6E8C22.toInt()
    /**
     * Identity tint for the library, which has no console of its own to borrow one from.
     * Amber rather than another green: the design rule is that the tint means identity and
     * [ACCENT] means focus, so the two must stay tellable apart. Green-and-amber is also what
     * the phosphor monitors this artwork is drawn like actually came in.
     */
    const val LIBRARY_TINT = 0xFFD8A32B.toInt()
    /** Destructive actions (Save & exit, Reset to defaults). Kept warm — it has to read as
     *  "stop" against a field of green, which a second green never would. */
    const val DANGER = 0xFFFF6B6B.toInt()

    // --- metrics (dp), read straight off the design ---
    const val RADIUS = 14f
    const val ROW_H = 52f
    const val TILE_H = 54f
    const val TOGGLE_W = 44f
    const val TOGGLE_H = 26f
    const val KNOB = 26f
    const val TRACK_H = 10f
    /** Focus ring thickness. The design uses a 2.5px outline plus an outer glow; Android views
     *  have no outline-offset, so the ring is drawn as the tile's own stroke instead. */
    const val FOCUS_STROKE = 2.5f

    fun Context.dp(v: Float): Int = (v * resources.displayMetrics.density).toInt()

    /** A resting row/card: filled tile with a hairline border. */
    fun Context.tile(
        fill: Int = TILE,
        stroke: Int = STROKE,
        radius: Float = RADIUS,
        strokeWidth: Float = 1f,
    ) = GradientDrawable().apply {
        cornerRadius = dp(radius).toFloat()
        setColor(fill)
        setStroke(dp(strokeWidth).coerceAtLeast(1), stroke)
    }

    /** The same row wearing the focus ring. */
    fun Context.focusedTile(fill: Int = TILE, radius: Float = RADIUS) =
        tile(fill = fill, stroke = ACCENT, radius = radius, strokeWidth = FOCUS_STROKE)

    /** A tinted variant — used for the console-tinted "active" row and the danger buttons. */
    fun Context.tintedTile(tint: Int, fillAlpha: Int = 0x22, strokeAlpha: Int = 0x80) = tile(
        fill = Color.argb(fillAlpha, Color.red(tint), Color.green(tint), Color.blue(tint)),
        stroke = Color.argb(strokeAlpha, Color.red(tint), Color.green(tint), Color.blue(tint)),
    )

    fun alpha(color: Int, a: Int): Int =
        Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))

    /** Nudge a fill toward light, for the pressed state. */
    fun lighten(color: Int, amount: Int = 22): Int = Color.argb(
        Color.alpha(color),
        (Color.red(color) + amount).coerceAtMost(255),
        (Color.green(color) + amount).coerceAtMost(255),
        (Color.blue(color) + amount).coerceAtMost(255),
    )

    /**
     * Background for any interactive row, as a state list rather than a focus listener:
     * pressed → lighter fill, focused → accent ring, otherwise the resting tile.
     *
     * Doing it declaratively matters. Swapping the drawable in `onFocusChange` covered focus
     * but left touch with no feedback at all — you tapped a row and nothing acknowledged the
     * contact until the screen changed.
     */
    fun Context.rowBackground(
        fill: Int = TILE,
        stroke: Int = STROKE,
        radius: Float = RADIUS,
    ) = android.graphics.drawable.StateListDrawable().apply {
        addState(
            intArrayOf(android.R.attr.state_pressed),
            tile(fill = lighten(fill), stroke = stroke, radius = radius),
        )
        addState(
            intArrayOf(android.R.attr.state_focused),
            focusedTile(fill = fill, radius = radius),
        )
        addState(intArrayOf(), tile(fill = fill, stroke = stroke, radius = radius))
    }
}
