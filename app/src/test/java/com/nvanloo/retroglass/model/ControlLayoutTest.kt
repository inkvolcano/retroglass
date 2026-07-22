package com.nvanloo.retroglass.model

import com.nvanloo.retroglass.controller.ControlType
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
        // Two families are deliberately key-less because they are not plain buttons: the N64
        // C-cluster ("c_") and the Intellivision keypad ("kp_") are read as directions from
        // where inside the cluster you touch, the same way a D-pad is, so they carry no key
        // code of their own. ControllerView excludes them by the same prefixes. Anything else
        // pressable must send something or it is a button that visibly does nothing.
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
}
