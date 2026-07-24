package com.nvanloo.retroglass.controller

/**
 * The zone grid from the controls-layout handoff (docs/controls-layout-handoff.md §1).
 *
 * This is the part [ZoneLayout] does not yet do: instead of a console naming absolute
 * coordinates, it names a **zone**, a **module**, and an **anchor**, and the grid solves the
 * position. That is what makes one authored layout re-flow between portrait and landscape —
 * the zone rects change, the assignments do not.
 *
 * ## Coordinate space
 *
 * All rects are in the **pad view's** 0..1 box, not the whole screen. In portrait the game is a
 * separate view above the pad, so `SCREEN` has no rect here; it is listed in the handoff because
 * that document describes the whole display. Landscape is the same story with the pad overlaid.
 *
 * ## The 40% figure (resolved)
 *
 * §1 reads as a contradiction — the centre boxes are "40% of screen width, centered" *and*
 * "overlap into LC and RC by 40%". Both hold once you notice the two percentages measure
 * different things: the box is 40% of the **pad**, and each block is 50% of the pad, so a
 * centred box reaches 0.2 into each — which is 40% of a **block**. Confirmed as the intent:
 * 40% overlap on each side.
 *
 * ## Falling back to combined buttons
 *
 * Because CT/CL sit on top of LC/RC, separate pills can collide with whatever the big blocks
 * hold. The rule is to degrade rather than overlap: when the separate arrangement does not fit,
 * the zone switches to its **combined** variant (one divided pill), which is narrower. The same
 * applies to LT/RT shoulders. [fitsSeparate] is that decision.
 */
object ZoneGrid {

    /** The named boxes. SCREEN is deliberately absent — see the class note. */
    enum class Zone { LT, RT, CT, CL, LC, RC }

    /** Where a module sits within its block's three inner divisions. */
    enum class VAnchor { TOP, MID, LOW }

    /**
     * Where a module sits across its block: 30 / 50 / 70%, **mirrored per side** so that on a
     * right-hand block OUTER means toward x = 1 and on a left-hand block toward x = 0.
     */
    enum class HAnchor { INNER, CENTER, OUTER }

    /** The 7 directional designs (handoff §2, 2e). */
    enum class DirectionalDesign { CROSS, CENTER_BUTTON, DISC, OCTAGON, SPLIT_ARROWS, PLATE, DISHED }

    /** The 6 analog-stick designs (2f). */
    enum class StickDesign { CONCENTRIC, DISHED_CAP, RING_NUB, SQUARE_GATE, DIMPLED_CAP, KNURLED_CAP }

    /** Shoulder module variants for LT/RT (2c). There is deliberately no combined L3/R3. */
    enum class ShoulderVariant { SINGLE, DOUBLE_STACKED, TRIPLE_STACKED, COMBINED_2, COMBINED_3, THUMB_CLICK }

    /** Start/Select variants for CT/CL (2d); each may additionally carry the settings gear. */
    enum class PillVariant { SINGLE, DUAL, COMBINED }

    data class Rect(val l: Float, val t: Float, val r: Float, val b: Float) {
        val cx get() = (l + r) / 2f
        val cy get() = (t + b) / 2f
        val w get() = r - l
        val h get() = b - t
    }

    // --- portrait geometry ----------------------------------------------------------------
    // The shoulder row sits directly under the screen; the two big blocks run to the bottom.
    private const val TOP_ROW_H = 0.18f
    // Centre boxes: 40% of the pad wide, centred on the LC/RC seam — which is the same thing as
    // overlapping 40% into each block, since each block is half the pad. See the class note.
    private const val CENTER_W = 0.40f

    /** Overlap into each big block, as a fraction of that block's width. */
    const val SEAM_OVERLAP = 0.40f

    private val PORTRAIT = mapOf(
        Zone.LT to Rect(0f, 0f, 0.5f, TOP_ROW_H),
        Zone.RT to Rect(0.5f, 0f, 1f, TOP_ROW_H),
        Zone.LC to Rect(0f, TOP_ROW_H, 0.5f, 1f),
        Zone.RC to Rect(0.5f, TOP_ROW_H, 1f, 1f),
        Zone.CT to Rect(0.5f - CENTER_W / 2f, 0.02f, 0.5f + CENTER_W / 2f, 0.20f),
        Zone.CL to Rect(0.5f - CENTER_W / 2f, 0.78f, 0.5f + CENTER_W / 2f, 0.96f),
    )

    // --- landscape geometry ---------------------------------------------------------------
    // LT / CT / RT across the top, LC · (screen) · RC across the middle, CL under the screen.
    // The middle band is where the game sits, so the side blocks hug the outer edges.
    private const val SIDE_W = 0.27f

    private val LANDSCAPE = mapOf(
        Zone.LT to Rect(0f, 0f, SIDE_W, 0.16f),
        Zone.CT to Rect(0.5f - CENTER_W / 2f, 0f, 0.5f + CENTER_W / 2f, 0.14f),
        Zone.RT to Rect(1f - SIDE_W, 0f, 1f, 0.16f),
        Zone.LC to Rect(0f, 0.16f, SIDE_W, 1f),
        Zone.RC to Rect(1f - SIDE_W, 0.16f, 1f, 1f),
        Zone.CL to Rect(0.5f - CENTER_W / 2f, 0.82f, 0.5f + CENTER_W / 2f, 0.98f),
    )

    fun rect(zone: Zone, landscape: Boolean): Rect =
        (if (landscape) LANDSCAPE else PORTRAIT).getValue(zone)

    /** True for the zones on the left, which is what mirrors [HAnchor]. */
    private fun isLeft(zone: Zone) = zone == Zone.LT || zone == Zone.LC

    /**
     * Whether [count] separate pills of width [pillW] fit inside [zone] without colliding, given
     * a gap of [gap] between them. When this is false the caller uses the zone's **combined**
     * variant instead — one divided pill, which packs the same inputs into the width of one.
     *
     * This is the handoff's degradation rule (see the class note) and it applies to CT/CL's
     * Start/Select and to LT/RT's shoulders alike. Degrading is always preferable to overlapping:
     * two pills drawn on top of each other are not just ugly, they are ambiguous to hit-test,
     * which is exactly how the ColecoVision keypad bug hid.
     */
    fun fitsSeparate(
        zone: Zone,
        landscape: Boolean,
        count: Int,
        pillW: Float,
        gap: Float = 0.02f,
    ): Boolean {
        if (count <= 1) return true
        val needed = count * pillW + (count - 1) * gap
        return needed <= rect(zone, landscape).w + 1e-4f
    }

    /**
     * Solves a module's centre inside [zone].
     *
     * [span] is the module's extent as a fraction of the pad's shorter edge, used to keep the
     * module fully inside the block rather than letting an anchor push it over an edge — the
     * failure the preset transforms already taught us to avoid (see `Console.fitWithoutOverlap`).
     */
    fun solve(
        zone: Zone,
        landscape: Boolean,
        v: VAnchor = VAnchor.MID,
        h: HAnchor = HAnchor.CENTER,
        span: Float = 0f,
    ): Pair<Float, Float> {
        val box = rect(zone, landscape)
        val half = span / 2f

        // Horizontal: 30 / 50 / 70 across the block, mirrored so OUTER is always the screen edge.
        val frac = when (h) {
            HAnchor.CENTER -> 0.5f
            HAnchor.INNER -> if (isLeft(zone)) 0.70f else 0.30f
            HAnchor.OUTER -> if (isLeft(zone)) 0.30f else 0.70f
        }
        val x = (box.l + frac * box.w).coerceIn(box.l + half, (box.r - half).coerceAtLeast(box.l + half))

        // Vertical: the block's three inner divisions, each module centred in its own.
        val vFrac = when (v) {
            VAnchor.TOP -> 1f / 6f
            VAnchor.MID -> 0.5f
            VAnchor.LOW -> 5f / 6f
        }
        val y = (box.t + vFrac * box.h).coerceIn(box.t + half, (box.b - half).coerceAtLeast(box.t + half))
        return x to y
    }
}
