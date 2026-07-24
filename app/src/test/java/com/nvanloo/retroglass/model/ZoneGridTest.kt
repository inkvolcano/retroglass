package com.nvanloo.retroglass.model

import com.nvanloo.retroglass.controller.PillSegment
import com.nvanloo.retroglass.controller.ZoneGrid
import com.nvanloo.retroglass.controller.ZoneLayout
import com.nvanloo.retroglass.controller.ZoneGrid.HAnchor
import com.nvanloo.retroglass.controller.ZoneGrid.VAnchor
import com.nvanloo.retroglass.controller.ZoneGrid.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Invariants over the zone grid (docs/controls-layout-handoff.md §1).
 *
 * The solver is the piece that will replace hand-authored coordinates, so its mistakes would
 * land on all 35 pads at once and — like the layout data it replaces — fail quietly rather than
 * throw. Everything here is pure arithmetic, which is what makes it cheap to pin.
 */
class ZoneGridTest {

    private val orientations = listOf(false, true) // portrait, landscape

    @Test
    fun `every anchor combination solves inside its own zone`() {
        for (landscape in orientations) {
            for (zone in Zone.entries) {
                val box = ZoneGrid.rect(zone, landscape)
                for (v in VAnchor.entries) for (h in HAnchor.entries) {
                    val (x, y) = ZoneGrid.solve(zone, landscape, v, h)
                    assertTrue(
                        "$zone ${if (landscape) "landscape" else "portrait"} $v/$h solved to " +
                            "($x, $y), outside $box",
                        x >= box.l && x <= box.r && y >= box.t && y <= box.b,
                    )
                }
            }
        }
    }

    @Test
    fun `a module with real extent is not pushed over its zone edge`() {
        // The failure the preset transforms already taught us: anchor to an edge, overflow, get
        // clamped back onto whatever you anchored away from.
        val span = 0.30f
        for (landscape in orientations) {
            for (zone in Zone.entries) {
                val box = ZoneGrid.rect(zone, landscape)
                // Only meaningful where the block is actually bigger than the module.
                if (box.w < span || box.h < span) continue
                for (v in VAnchor.entries) for (h in HAnchor.entries) {
                    val (x, y) = ZoneGrid.solve(zone, landscape, v, h, span)
                    val half = span / 2f
                    assertTrue(
                        "$zone $v/$h with span $span overflows $box at ($x, $y)",
                        x - half >= box.l - 1e-4 && x + half <= box.r + 1e-4 &&
                            y - half >= box.t - 1e-4 && y + half <= box.b + 1e-4,
                    )
                }
            }
        }
    }

    @Test
    fun `the horizontal anchor mirrors per side`() {
        // OUTER must mean "toward the screen edge" on both sides, else a right-hand cluster
        // anchored outward would march toward the middle of the pad.
        for (landscape in orientations) {
            val lOuter = ZoneGrid.solve(Zone.LC, landscape, h = HAnchor.OUTER).first
            val lInner = ZoneGrid.solve(Zone.LC, landscape, h = HAnchor.INNER).first
            val rOuter = ZoneGrid.solve(Zone.RC, landscape, h = HAnchor.OUTER).first
            val rInner = ZoneGrid.solve(Zone.RC, landscape, h = HAnchor.INNER).first
            assertTrue("LC OUTER should sit left of LC INNER", lOuter < lInner)
            assertTrue("RC OUTER should sit right of RC INNER", rOuter > rInner)
        }
    }

    @Test
    fun `the vertical anchor orders top, middle, low`() {
        for (landscape in orientations) {
            for (zone in listOf(Zone.LC, Zone.RC)) {
                val top = ZoneGrid.solve(zone, landscape, VAnchor.TOP).second
                val mid = ZoneGrid.solve(zone, landscape, VAnchor.MID).second
                val low = ZoneGrid.solve(zone, landscape, VAnchor.LOW).second
                assertTrue("$zone TOP should be above MID", top < mid)
                assertTrue("$zone MID should be above LOW", mid < low)
            }
        }
    }

    @Test
    fun `the centre boxes straddle the seam and are 40 percent wide`() {
        // Handoff §1: 40% of the pad wide, centred on the seam - which is the same thing as
        // overlapping 40% into each block, since each block is half the pad. The two sentences
        // that looked contradictory were measuring against different things.
        for (landscape in orientations) {
            for (zone in listOf(Zone.CT, Zone.CL)) {
                val box = ZoneGrid.rect(zone, landscape)
                assertEquals("$zone should be 40% wide", 0.40f, box.w, 1e-4f)
                assertEquals("$zone should be centred on the seam", 0.5f, box.cx, 1e-4f)
            }
        }
    }

    @Test
    fun `portrait blocks are half width and do not overlap`() {
        val lc = ZoneGrid.rect(Zone.LC, landscape = false)
        val rc = ZoneGrid.rect(Zone.RC, landscape = false)
        assertEquals("LC should be half the pad wide", 0.5f, lc.w, 1e-4f)
        assertEquals("RC should be half the pad wide", 0.5f, rc.w, 1e-4f)
        assertTrue("LC and RC must meet at the seam, not overlap", lc.r <= rc.l + 1e-4f)
        assertEquals("the blocks should run to the bottom", 1f, lc.b, 1e-4f)
        assertEquals("the blocks should run to the bottom", 1f, rc.b, 1e-4f)
    }

    @Test
    fun `the shoulder row sits above the big blocks in portrait`() {
        val lt = ZoneGrid.rect(Zone.LT, landscape = false)
        val lc = ZoneGrid.rect(Zone.LC, landscape = false)
        assertTrue("LT must sit above LC", lt.b <= lc.t + 1e-4f)
        assertEquals("the shoulder row starts at the top of the pad", 0f, lt.t, 1e-4f)
    }

    @Test
    fun `a combined divided pill tiles exactly one pill's footprint`() {
        // The segments must meet edge to edge: a gap looks like a broken pill, an overlap makes
        // two buttons fight for the same touch. Either failure is silent on screen.
        val size = 0.12f
        for (n in 2..3) {
            val btns = (1..n).map { ZoneLayout.Btn("b$it", "$it", it, 0) }
            val out = ZoneLayout.pad { pillsCombined(btns, cx = 0.5f, cy = 0.8f, size = size) }
            assertEquals("expected $n segments", n, out.size)

            val fullHalfW = size / 2f * 1.85f
            val segHalfW = fullHalfW / n
            out.forEachIndexed { i, d ->
                assertEquals("segment $i width scale", 1f / n, d.widthScale, 1e-5f)
                val expected = when (i) {
                    0 -> PillSegment.LEFT
                    n - 1 -> PillSegment.RIGHT
                    else -> PillSegment.MID
                }
                assertEquals("segment $i position", expected, d.segment)
            }
            // Edge-to-edge, and the whole run is exactly one pill wide.
            for (i in 0 until n - 1) {
                val rightEdge = out[i].x + segHalfW
                val leftEdge = out[i + 1].x - segHalfW
                assertEquals("segments $i/${i + 1} must meet", rightEdge, leftEdge, 1e-5f)
            }
            assertEquals("run should start at the pill's left edge", 0.5f - fullHalfW, out.first().x - segHalfW, 1e-5f)
            assertEquals("run should end at the pill's right edge", 0.5f + fullHalfW, out.last().x + segHalfW, 1e-5f)
        }
    }

    @Test
    fun `a single button never becomes a divided pill`() {
        val out = ZoneLayout.pad {
            pillsCombined(listOf(ZoneLayout.Btn("start", "START", 1, 0)), size = 0.12f)
        }
        assertEquals(1, out.size)
        assertEquals("one button is an ordinary pill", PillSegment.NONE, out[0].segment)
        assertEquals(1f, out[0].widthScale, 1e-5f)
    }
}
