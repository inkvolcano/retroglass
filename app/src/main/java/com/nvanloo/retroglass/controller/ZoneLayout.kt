package com.nvanloo.retroglass.controller

import android.graphics.Color
import kotlin.math.cos
import kotlin.math.sin

/**
 * The zone-based layout generator from the "Controls Layout Wireframes" design
 * (docs/controls-layout-system.md).
 *
 * It does not replace [ControllerView] — it feeds it. A console describes *what* drops into each
 * zone and the builder computes the absolute `ControlDef(x, y, size, …)` list the view already
 * draws and hit-tests. The point is that one authored layout re-flows portrait ↔ landscape
 * instead of being hand-placed twice, and that a cluster's geometry (a diamond, a 30° diagonal,
 * an arc) is expressed once as a guide axis rather than as four hand-tuned coordinates.
 *
 * ## Portrait pad zones
 *
 * In portrait the game is a separate view *above* the pad, so the pad owns the whole 0..1 box and
 * there is no SCREEN zone here. The three rows below are measured from the hand-authored layouts
 * this generation replaces, so a converted console lands where its old one did:
 *
 * ```
 *   LT ······· CT ······· RT      y ≈ TOP_Y      shoulders / (rarely) a centre pill
 *   ┌── LC ──┐   ┌── RC ──┐        y ≈ BLOCK_CY   the big blocks: directional | face cluster
 *   └────────┘   └────────┘
 *        CL (start / select)       y ≈ LOW_Y      system pills, optionally a stick
 * ```
 *
 * Horizontal anchors are mirrored per side: on the right block, "outer" means toward x = 1.
 */
object ZoneLayout {

    private val LIGHT = Color.parseColor("#EDEDF2")
    private val DARK = Color.parseColor("#26262B")

    // Row heights, as fractions of the pad's height. Measured off the layouts being replaced.
    private const val TOP_Y = 0.09f
    private const val BLOCK_CY = 0.52f
    private const val LOW_Y = 0.86f

    // The two big blocks' centre x and the reach of a face cluster within one.
    private const val LC_CX = 0.26f
    private const val RC_CX = 0.74f

    /** Where a module sits across its block. Mirrored: OUTER is left in LC, right in RC. */
    enum class HAnchor { INNER, CENTER, OUTER }

    class Pad {
        private val out = mutableListOf<ControlDef>()

        /** The directional module in the left block. [shape] picks the design (cross/disc/…). */
        fun directional(
            shape: ControlShape = ControlShape.PSX_CROSS,
            size: Float = 0.46f,
            fill: Int = Color.parseColor("#1C1C1E"),
            cx: Float = LC_CX,
            cy: Float = BLOCK_CY,
        ) {
            out += ControlDef("dpad", ControlType.DPAD, "", x = cx, y = cy, size = size,
                shape = shape, fillColor = fill, labelColor = LIGHT)
        }

        /** Two face buttons on a horizontal guide (NES: B — A). */
        fun faceRow2(
            left: Btn, right: Btn,
            cx: Float = RC_CX, cy: Float = BLOCK_CY, gap: Float = 0.247f, size: Float = 0.19f,
        ) {
            button(left, cx - gap / 2f, cy, size)
            button(right, cx + gap / 2f, cy, size)
        }

        /**
         * Two face buttons on a 30° rising guide (B low-left, A high-right). The default is the
         * common handheld placement (Lynx, NGP, WonderSwan, Virtual Boy, …); the Game Boy family
         * passes its own tighter geometry.
         */
        fun faceDiag2(
            low: Btn, high: Btn,
            cx: Float = 0.785f, cy: Float = 0.539f, spread: Float = 0.17f, size: Float = 0.22f,
        ) = diagonal(listOf(low, high), cx, cy, spread, size)

        /** Four face buttons on the crossed axes of a diamond (SNES: X top, A right, B bottom, Y left). */
        fun faceDiamond4(
            top: Btn, right: Btn, bottom: Btn, left: Btn,
            cx: Float = 0.75f, cy: Float = BLOCK_CY, hx: Float = 0.14f, vy: Float = 0.10f, size: Float = 0.18f,
        ) {
            button(top, cx, cy - vy, size)
            button(right, cx + hx, cy, size)
            button(bottom, cx, cy + vy, size)
            button(left, cx - hx, cy, size)
        }

        /** A single large fire button (Atari 2600). */
        fun faceFire1(b: Btn, cx: Float = 0.85f, cy: Float = 0.56f, size: Float = 0.30f) =
            button(b, cx, cy, size)

        /**
         * N buttons evenly on one rising diagonal guide, low-left → high-right (Neo Geo's four,
         * 3DO's three). [slope] is rise over run: 0.72 for a steep pad diagonal, ~0.32 for the
         * shallow rows of a six-button face (Saturn).
         */
        fun faceDiagonal(
            btns: List<Btn>, cx: Float, cy: Float, spread: Float, size: Float, slope: Float = 0.72f,
        ) = diagonal(btns, cx, cy, spread, size, slope)

        /** N buttons on one horizontal guide (Vectrex's lower row of three). */
        fun faceRowN(btns: List<Btn>, cx: Float, cy: Float, gap: Float, size: Float) {
            val start = -(btns.size - 1) / 2f
            btns.forEachIndexed { i, b -> button(b, cx + (start + i) * gap, cy, size) }
        }

        /** One button at an explicit spot, for clusters the guides do not cover (Vectrex's 4th). */
        fun faceButton(b: Btn, x: Float, y: Float, size: Float) = button(b, x, y, size)

        /** Three face buttons on a rising diagonal guide (Genesis: A B C). */
        fun faceArc3(
            a: Btn, b: Btn, c: Btn,
            cx: Float = RC_CX - 0.02f, cy: Float = BLOCK_CY + 0.05f, spread: Float = 0.24f, size: Float = 0.17f,
        ) = diagonal(listOf(a, b, c), cx, cy, spread, size)

        /** Start / Select as two pills in the centre-low zone (Select left, Start right). */
        fun systemPills(
            select: Btn?, start: Btn,
            cy: Float = LOW_Y, size: Float = 0.12f, gap: Float = 0.24f,
        ) {
            if (select != null) pillPair(select, start, cy, size, gap) else pill(start, 0.5f, cy, size)
        }

        /** Two centre-low pills in explicit left/right order (3DO puts Play on the left). */
        fun pillPair(left: Btn, right: Btn, cy: Float = LOW_Y, size: Float = 0.12f, gap: Float = 0.24f) {
            pill(left, 0.5f - gap / 2f, cy, size)
            pill(right, 0.5f + gap / 2f, cy, size)
        }

        /** Shoulder buttons in the top corners. Either side may be null (Pokémon Mini has one). */
        fun shoulders(l: Btn?, r: Btn?, cy: Float = TOP_Y, size: Float = 0.18f, lx: Float = 0.15f, rx: Float = 0.85f) {
            if (l != null) out += bar(l, lx, cy, size)
            if (r != null) out += bar(r, rx, cy, size)
        }

        /** A raw control, for clusters the helpers do not yet cover. */
        fun add(def: ControlDef) { out += def }

        fun build(): List<ControlDef> = out.toList()

        // ---- geometry ---------------------------------------------------------------------

        private fun diagonal(btns: List<Btn>, cx: Float, cy: Float, spread: Float, size: Float, slope: Float = 0.72f) {
            val n = btns.size
            btns.forEachIndexed { i, b ->
                val t = if (n == 1) 0f else i / (n - 1f) - 0.5f    // -0.5 .. 0.5, low → high
                button(b, cx + t * spread, cy - t * spread * slope, size)
            }
        }

        private fun button(b: Btn, x: Float, y: Float, size: Float) {
            out += ControlDef(b.id, ControlType.BUTTON, b.label, b.keyCode, x, y, size,
                ControlShape.CIRCLE, fillColor = b.fill, labelColor = b.labelColor,
                strokeColor = b.stroke, plateColor = b.plate)
        }

        private fun pill(b: Btn, x: Float, y: Float, size: Float) {
            out += ControlDef(b.id, ControlType.BUTTON, b.label, b.keyCode, x, y, size,
                ControlShape.PILL, fillColor = b.fill, labelColor = b.labelColor)
        }

        private fun bar(b: Btn, x: Float, y: Float, size: Float) =
            ControlDef(b.id, ControlType.BUTTON, b.label, b.keyCode, x, y, size,
                ControlShape.BAR, fillColor = b.fill, labelColor = b.labelColor)
    }

    /**
     * One button's identity + styling, so the geometry helpers stay position-only. [plate] is
     * the recessed backing plate (NES), [stroke] the coloured rim (Genesis) — both default off.
     */
    data class Btn(
        val id: String,
        val label: String,
        val keyCode: Int,
        val fill: Int,
        val labelColor: Int = LIGHT,
        val plate: Int = Color.TRANSPARENT,
        val stroke: Int = Color.TRANSPARENT,
    )

    /** Build a pad from a declarative block. */
    fun pad(block: Pad.() -> Unit): List<ControlDef> = Pad().apply(block).build()

    // Suppress unused-import cleanup for trig used by future octagon/N64 guides.
    @Suppress("unused") private val keepTrig = cos(0.0) + sin(0.0)
}
