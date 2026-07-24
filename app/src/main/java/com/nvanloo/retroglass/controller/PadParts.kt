package com.nvanloo.retroglass.controller

import com.nvanloo.retroglass.model.Console
import com.nvanloo.retroglass.model.ControllerDefs

/**
 * A console's authored controls, split into the groups the designer thinks in.
 *
 * The designer never invents buttons. It rearranges the ones the console already has, so every
 * module needs to know which buttons it is arranging — the directional, the sticks, the face
 * cluster, each side's shoulders, the system pills. This is that decomposition, derived once from
 * the shipped layout and then reused for every render and every module preview.
 *
 * [extra] holds anything that did not fit a group: console-key rows, an on-screen-keyboard toggle,
 * the Atari keypad's mode switch. Those are passed through to the rendered pad untouched at their
 * authored positions, because the designer has no field kind that would own them and dropping them
 * would make those consoles unplayable.
 */
data class PadParts(
    val dpad: ControlDef? = null,
    val dpadCenter: ControlDef? = null,
    val sticks: List<ControlDef> = emptyList(),
    val face: List<ControlDef> = emptyList(),
    val shouldersL: List<ControlDef> = emptyList(),
    val shouldersR: List<ControlDef> = emptyList(),
    val system: List<ControlDef> = emptyList(),
    val extra: List<ControlDef> = emptyList(),
) {
    val start: ControlDef? get() = system.firstOrNull { it.id.contains("start", true) }
        ?: system.lastOrNull()

    val select: ControlDef? get() = system.firstOrNull {
        it.id.contains("select", true) || it.id.contains("mode", true) || it.id.contains("coin", true)
    } ?: system.firstOrNull()?.takeIf { system.size > 1 }

    companion object {

        /** Buttons drawn as bars are shoulders by construction — that is what the shape means. */
        private fun isShoulder(def: ControlDef) = def.shape == ControlShape.BAR

        private fun isSystemPill(def: ControlDef) =
            def.shape == ControlShape.PILL &&
                (def.combineGroup == ZoneLayout.SYSTEM_PILLS || def.segment != PillSegment.NONE)

        fun of(console: Console): PadParts = from(ControllerDefs.controlsFor(console))

        fun from(all: List<ControlDef>): PadParts {
            // The gear is not part of any console: ControllerView adds it, and a system module
            // emits it. Letting it through here would make it look like a second START.
            val controls = all.filter { it.id != PadModules.MENU_ID }
            val dpad = controls.firstOrNull { it.type == ControlType.DPAD }
            // A button sharing the directional's exact centre is its centre button (N64's Z),
            // not a face button — ControllerView already hit-tests the pair as co-centred.
            val centre = dpad?.let { d ->
                controls.firstOrNull {
                    it.type == ControlType.BUTTON && it.x == d.x && it.y == d.y
                }
            }
            val sticks = controls.filter { it.type == ControlType.STICK }.sortedBy { it.x }
            val shoulders = controls.filter { it.type == ControlType.BUTTON && isShoulder(it) }
            val system = controls.filter { it.type == ControlType.BUTTON && isSystemPill(it) }
                .sortedBy { it.x }
            val claimed = buildSet {
                dpad?.let { add(it.id) }
                centre?.let { add(it.id) }
                sticks.forEach { add(it.id) }
                shoulders.forEach { add(it.id) }
                system.forEach { add(it.id) }
            }
            val rest = controls.filter { it.id !in claimed && it.type == ControlType.BUTTON }
            val face = rest.filter { it.shape == ControlShape.CIRCLE }
            val extra = rest.filter { it.shape != ControlShape.CIRCLE }
            return PadParts(
                dpad = dpad,
                dpadCenter = centre,
                sticks = sticks,
                // Authored order is meaningful: it is the order the console's own layout lists
                // its faces in, which is what the row and diagonal arrangements follow.
                face = face,
                shouldersL = shoulders.filter { it.x <= 0.5f }.sortedBy { it.y },
                shouldersR = shoulders.filter { it.x > 0.5f }.sortedBy { it.y },
                system = system,
                extra = extra,
            )
        }
    }
}
