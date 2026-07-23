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

        /** Two face buttons on a 30° rising guide (Game Boy / Master System: B low-left, A high-right). */
        fun faceDiag2(
            low: Btn, high: Btn,
            cx: Float = RC_CX + 0.03f, cy: Float = BLOCK_CY, spread: Float = 0.20f, size: Float = 0.24f,
        ) = diagonal(listOf(low, high), cx, cy, spread, size)

        /** Three face buttons on a rising arc (Genesis: A B C). */
        fun faceArc3(
            a: Btn, b: Btn, c: Btn,
            cx: Float = RC_CX - 0.02f, cy: Float = BLOCK_CY + 0.05f, spread: Float = 0.24f, size: Float = 0.17f,
        ) = diagonal(listOf(a, b, c), cx, cy, spread, size, curve = 0.02f)

        /** Start / Select as two pills in the centre-low zone. */
        fun systemPills(
            select: Btn?, start: Btn,
            cy: Float = LOW_Y, size: Float = 0.12f, gap: Float = 0.24f,
        ) {
            if (select != null) {
                pill(select, 0.5f - gap / 2f, cy, size)
                pill(start, 0.5f + gap / 2f, cy, size)
            } else {
                pill(start, 0.5f, cy, size)
            }
        }

        /** Shoulder buttons in the top corners (SNES / GBA L R). */
        fun shoulders(l: Btn, r: Btn, cy: Float = TOP_Y, size: Float = 0.18f) {
            out += bar(l, 0.15f, cy, size)
            out += bar(r, 0.85f, cy, size)
        }

        /** A raw control, for clusters the helpers do not yet cover. */
        fun add(def: ControlDef) { out += def }

        fun build(): List<ControlDef> = out.toList()

        // ---- geometry ---------------------------------------------------------------------

        private fun diagonal(btns: List<Btn>, cx: Float, cy: Float, spread: Float, size: Float, curve: Float = 0f) {
            val n = btns.size
            btns.forEachIndexed { i, b ->
                val t = if (n == 1) 0f else i / (n - 1f) - 0.5f    // -0.5 .. 0.5
                val x = cx + t * spread
                val y = cy - t * spread * 0.72f - curve * (1f - (2f * t) * (2f * t)) * 4f
                button(b, x, y, size)
            }
        }

        private fun button(b: Btn, x: Float, y: Float, size: Float) {
            out += ControlDef(b.id, ControlType.BUTTON, b.label, b.keyCode, x, y, size,
                ControlShape.CIRCLE, fillColor = b.fill, labelColor = b.labelColor, plateColor = DARK)
        }

        private fun pill(b: Btn, x: Float, y: Float, size: Float) {
            out += ControlDef(b.id, ControlType.BUTTON, b.label, b.keyCode, x, y, size,
                ControlShape.PILL, fillColor = b.fill, labelColor = b.labelColor)
        }

        private fun bar(b: Btn, x: Float, y: Float, size: Float) =
            ControlDef(b.id, ControlType.BUTTON, b.label, b.keyCode, x, y, size,
                ControlShape.BAR, fillColor = b.fill, labelColor = b.labelColor)
    }

    /** One button's identity + colour, so the geometry helpers stay position-only. */
    data class Btn(
        val id: String,
        val label: String,
        val keyCode: Int,
        val fill: Int,
        val labelColor: Int = LIGHT,
    )

    /** Build a pad from a declarative block. */
    fun pad(block: Pad.() -> Unit): List<ControlDef> = Pad().apply(block).build()

    // Suppress unused-import cleanup for trig used by future octagon/N64 guides.
    @Suppress("unused") private val keepTrig = cos(0.0) + sin(0.0)
}
