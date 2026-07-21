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

    // --- surfaces (shared with CompanionView) ---
    const val BG = 0xFF0B0B12.toInt()
    const val TILE = 0xFF171722.toInt()
    const val STROKE = 0xFF2A2A3A.toInt()
    const val TRACK = 0xFF23232F.toInt()
    const val HAIRLINE = 0xFF1C1C26.toInt()

    // --- text ---
    const val FG = 0xFFEDEDF5.toInt()
    const val DIM = 0xFF8A8AA6.toInt()
    const val GROUP = 0xFF6B6B82.toInt()
    const val CHEVRON = 0xFF5A5A72.toInt()

    // --- semantic ---
    /** Focus ring and live values. Never used as plain decoration. */
    const val ACCENT = 0xFF9BE870.toInt()
    /** Destructive actions (Save & exit, Reset to defaults). */
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
}
