package com.nvanloo.retroglass.model

import android.view.KeyEvent
import com.nvanloo.retroglass.controller.ControlDef
import com.nvanloo.retroglass.controller.ControlShape
import com.nvanloo.retroglass.controller.ControlType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Invariants over the hand-written touch layouts — one per console, forty-odd of them.
 *
 * Same reasoning as the console table: these are data, and mistakes in them fail quietly. A
 * duplicated id makes two buttons fight over one pointer, a control placed past the edge is
 * simply unreachable, and a button with no key code does nothing when pressed. None of that
 * throws, and none of it is visible unless you happen to open that specific console's pad.
 */
class ControlLayoutTest {

    private fun layouts(): List<Pair<Console, List<com.nvanloo.retroglass.controller.ControlDef>>> =
        Console.entries.map { it to ControllerDefs.controlsFor(it) }

    @Test
    fun `every console has a layout`() {
        for ((console, controls) in layouts()) {
            assertTrue("${console.name} has no controls", controls.isNotEmpty())
        }
    }

    @Test
    fun `control ids are unique within a layout`() {
        // Input dispatch and the turbo set are keyed by id; a duplicate means one control can
        // never be addressed and both would answer to the same turbo toggle.
        for ((console, controls) in layouts()) {
            val dupes = controls.groupBy { it.id }.filter { it.value.size > 1 }.keys
            assertTrue("${console.name} repeats control ids: $dupes", dupes.isEmpty())
        }
    }

    @Test
    fun `controls sit inside the pad`() {
        // x and y are fractions of the pad. Anything outside is drawn off-screen and cannot be
        // pressed - and nothing clamps it at draw time.
        for ((console, controls) in layouts()) {
            for (c in controls) {
                assertTrue(
                    "${console.name}/${c.id} sits at ${c.x},${c.y}",
                    c.x in 0f..1f && c.y in 0f..1f,
                )
            }
        }
    }

    @Test
    fun `control sizes are sane`() {
        for ((console, controls) in layouts()) {
            for (c in controls) {
                assertTrue("${console.name}/${c.id} has size ${c.size}", c.size > 0f && c.size < 1f)
            }
        }
    }

    @Test
    fun `every plain button reports a key`() {
        // Two prefixes are exempt because some of their members are not plain buttons: the
        // N64 C-cluster ("c_") and the keypads ("kp_"). Those are read as analog directions
        // rather than key presses, so they carry no key code of their own - the whole
        // Intellivision keypad, and ColecoVision's 0 and 9, which are the two keys gearcoleco
        // had no RetroPad button left for. ControllerView excludes them by the same prefixes.
        // Anything else pressable must send something or it is a button that visibly does
        // nothing. Per-key bindings are checked below, where the exact codes matter.
        for ((console, controls) in layouts()) {
            val plain = controls.filter {
                it.type == ControlType.BUTTON &&
                    !it.id.startsWith("c_") && !it.id.startsWith("kp_")
            }
            for (c in plain) {
                assertTrue("${console.name}/${c.id} sends no key code", c.keyCode != 0)
            }
        }
    }

    @Test
    fun `the menu button is not part of a console's own layout`() {
        // It is injected by ControllerView (menuControl()) rather than declared per console, so
        // every pad gets one and none of them has to remember. A console defining its own would
        // end up with two.
        for ((console, controls) in layouts()) {
            assertTrue(
                "${console.name} declares its own _menu; the view already adds one",
                controls.none { it.id == "_menu" },
            )
        }
    }

    @Test
    fun `labels are short enough to draw`() {
        // Round buttons scale their glyph down by label length; past a few characters it stops
        // fitting rather than wrapping.
        for ((console, controls) in layouts()) {
            for (c in controls) {
                assertTrue(
                    "${console.name}/${c.id} label '${c.label}' is long",
                    c.label.length <= 8,
                )
            }
        }
    }

    @Test
    fun `the ColecoVision keypad matches gearcoleco's declared bindings`() {
        // Read out of the shipped libgearcoleco.so's retro_input_descriptor table, not from
        // upstream source or memory. This test exists because the first version bound "1" to
        // BUTTON_X and "2" to BUTTON_Y - which are the codes for keypad 2 and keypad 1. The
        // two keys nearly every Coleco game uses to start, silently swapped, and nothing in
        // the app could tell: both are valid keypad presses, just the wrong ones.
        val expected = mapOf(
            "kp_1" to KeyEvent.KEYCODE_BUTTON_Y,
            "kp_2" to KeyEvent.KEYCODE_BUTTON_X,
            "kp_3" to KeyEvent.KEYCODE_BUTTON_L1,
            "kp_4" to KeyEvent.KEYCODE_BUTTON_R1,
            "kp_5" to KeyEvent.KEYCODE_BUTTON_L2,
            "kp_6" to KeyEvent.KEYCODE_BUTTON_R2,
            "kp_7" to KeyEvent.KEYCODE_BUTTON_THUMBL,
            "kp_8" to KeyEvent.KEYCODE_BUTTON_THUMBR,
            "kp_star" to KeyEvent.KEYCODE_BUTTON_START,
            "kp_hash" to KeyEvent.KEYCODE_BUTTON_SELECT,
        )
        val byId = ControllerDefs.controlsFor(Console.COLECO).associateBy { it.id }
        for ((id, code) in expected) {
            val def = byId[id]
            assertNotNull("ColecoVision layout is missing $id", def)
            assertEquals("ColecoVision $id is bound to the wrong RetroPad button", code, def!!.keyCode)
        }
        // 0 and 9 are the two the RetroPad has no button left for: gearcoleco reads them off
        // the left analog, so they must carry no keycode or they would send a button as well.
        for (id in listOf("kp_0", "kp_9")) {
            assertEquals("ColecoVision $id must ride the analog, not a button", 0, byId[id]!!.keyCode)
        }
    }

    @Test
    fun `the systems whose keyboard lives in the core expose the button that raises it`() {
        // atari800 draws its own on-screen keyboard, toggled by L3 in 8-bit mode and R3 in
        // 5200 mode. Those buttons are the only way to reach a keyboard or the 5200 keypad at
        // all, so losing one silently makes the whole system unplayable rather than degraded.
        val eightBit = ControllerDefs.controlsFor(Console.ATARI8BIT)
        assertTrue(
            "Atari 8-bit has no L3 button, so its on-screen keyboard cannot be raised",
            eightBit.any { it.keyCode == KeyEvent.KEYCODE_BUTTON_THUMBL },
        )
        val a5200 = ControllerDefs.controlsFor(Console.ATARI5200)
        assertTrue(
            "Atari 5200 has no R3 button, so its keypad is unreachable",
            a5200.any { it.keyCode == KeyEvent.KEYCODE_BUTTON_THUMBR },
        )
    }


    @Test
    fun `a preset introduces no overlap the authored layout does not have`() {
        // The suite only ever checked the *authored* layouts, never what the presets do to
        // them - which is why "Large buttons" and "Full-screen" could stack the ColecoVision
        // and Intellivision keypads with nothing noticing. Overlapping buttons do not throw:
        // hit-testing resolves every tap to whichever control is declared first, so the
        // covered one silently stops responding.
        //
        // Measured the way ControllerView draws: x and size share a base, and a control
        // pushed past the edge is clamped back inside - which is what collapsed the gaps.
        //
        // The bar is relative, not absolute. Some authored layouts overlap on this axis on
        // purpose (the SNES diamond puts X above A), and y cannot be compared against size
        // here because one is a fraction of height and the other of width. What must never
        // happen is a preset creating a collision the base did not have.
        fun halfX(c: ControlDef): Float {
            val r = c.size / 2f
            return if (c.shape == ControlShape.PILL || c.shape == ControlShape.BAR) r * 1.85f else r
        }
        fun collisions(cs: List<ControlDef>): Set<Pair<String, String>> = buildSet {
            for (i in cs.indices) for (j in i + 1 until cs.size) {
                val a = cs[i]
                val b = cs[j]
                if (a.type == ControlType.DPAD || b.type == ControlType.DPAD) continue
                if (kotlin.math.abs(a.y - b.y) > (a.size + b.size) / 2f) continue
                val ah = halfX(a)
                val bh = halfX(b)
                val ax = a.x.coerceIn(ah, 1f - ah)
                val bx = b.x.coerceIn(bh, 1f - bh)
                if (kotlin.math.abs(ax - bx) < ah + bh) add(a.id to b.id)
            }
        }
        for (console in Console.entries) {
            val base = collisions(ControllerDefs.controlsFor(console))
            for (preset in ControllerDefs.presetsFor(console)) {
                val added = collisions(preset.controls) - base
                assertTrue(
                    "${console.name}/${preset.id} introduces overlapping controls: $added",
                    added.isEmpty(),
                )
            }
        }
    }

}
