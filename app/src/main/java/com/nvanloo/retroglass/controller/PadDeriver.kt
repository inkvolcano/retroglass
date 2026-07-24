package com.nvanloo.retroglass.controller

import kotlin.math.abs

/**
 * Reads a console's shipped layout and writes down where each group *already* sits, in the
 * designer's slot vocabulary.
 *
 * This is what makes the switch to a slot model invisible. A user who opens the designer for the
 * first time sees their console exactly as it has always looked, because the starting design is
 * derived from the authored coordinates rather than being some house default they now have to
 * undo. Face clusters keep [PadModules.ARRANGE_AUTHORED] for the same reason: the hand-tuned
 * geometry of a Vectrex or an Intellivision keypad survives untouched until the user deliberately
 * picks another arrangement.
 */
object PadDeriver {

    fun derive(parts: PadParts): PadDesign {
        val layout = deriveLayout(parts)
        // Both orientations start from the same slot map; the renderer re-solves the column
        // bounds for each, and from here on the two are edited independently.
        return PadDesign(portrait = layout, landscape = layout)
    }

    private fun deriveLayout(parts: PadParts): PadLayout {
        val lc = sortedMapOfSlots()
        val rc = sortedMapOfSlots()

        parts.dpad?.let { place(lc, slotFor(it.y, 2), 2, dpadModuleFor(it)) }

        if (parts.face.isNotEmpty()) {
            val cy = parts.face.map { it.y }.average().toFloat()
            place(rc, slotFor(cy, 2), 2, PadModules.ARRANGE_AUTHORED)
        }

        parts.sticks.forEachIndexed { i, stick ->
            val column = if (stick.x <= 0.5f) lc else rc
            val id = "stick" + STICK_DESIGNS.getOrElse(stick.design) { STICK_DESIGNS[0] }
            place(column, slotFor(stick.y, 2), 2, id)
        }

        shoulderModule(parts.shouldersL)?.let { place(lc, 1, 1, it, preferLow = false) }
        shoulderModule(parts.shouldersR)?.let { place(rc, 1, 1, it, preferLow = false) }

        val system = systemModule(parts)
        val systemY = parts.system.map { it.y }.average().toFloat()
        val onTop = parts.system.isNotEmpty() && systemY < 0.5f

        return PadLayout(
            ct = if (onTop) system else null,
            cl = if (onTop) null else system,
            lc = lc.toMap(),
            rc = rc.toMap(),
        )
    }

    private val DPAD_DESIGNS = listOf("Cross", "Disc", "Octa", "Split", "Plate", "Dish")
    private val STICK_DESIGNS = listOf("Concentric", "Dish", "Ring", "Gate", "Dimple", "Knurl")

    private fun dpadModuleFor(def: ControlDef) =
        "dpad" + DPAD_DESIGNS.getOrElse(def.design) { DPAD_DESIGNS[0] }

    private fun shoulderModule(side: List<ControlDef>): String? {
        val bumpers = side.count { !it.id.endsWith("3") }
        return when {
            bumpers >= 3 -> "shTriple"
            bumpers == 2 -> "shDouble"
            bumpers == 1 -> "shSingle"
            side.isNotEmpty() -> "shL3"
            else -> null
        }
    }

    /**
     * Which system module the console's pills already amount to. A lone START keeps the gear
     * beneath it, which is the arrangement the handoff pairs with single-pill consoles; two pills
     * become the dual row and the user can collapse them into the combined pill from the list.
     */
    private fun systemModule(parts: PadParts): String? = when {
        parts.system.isEmpty() -> "sysGearOnly"
        parts.system.size == 1 -> "sysGearBelow"
        else -> "sysDual"
    }

    private fun sortedMapOfSlots() = sortedMapOf<Int, String>()

    /**
     * Seats [module] at [preferred], or at the nearest free start if that would overlap something
     * already placed. A console whose stick and directional both round to the same band still
     * ends up with both, one row apart, rather than silently losing one.
     */
    private fun place(
        column: java.util.SortedMap<Int, String>,
        preferred: Int,
        slots: Int,
        module: String,
        preferLow: Boolean = true,
    ) {
        val maxStart = PadLayout.SLOT_COUNT - slots + 1
        val candidates = (1..maxStart).sortedWith(
            compareBy({ abs(it - preferred) }, { if (preferLow) it else -it }),
        )
        val start = candidates.firstOrNull { free(column, it, slots) } ?: return
        column[start] = module
    }

    private fun free(column: java.util.SortedMap<Int, String>, start: Int, slots: Int): Boolean =
        column.none { (s, id) ->
            val k = PadModules.byId(id)?.slots ?: 1
            s + k - 1 >= start && s <= start + slots - 1
        }

    /** The slot whose band centre sits closest to an authored y. */
    private fun slotFor(y: Float, slots: Int): Int =
        (1..(PadLayout.SLOT_COUNT - slots + 1)).minByOrNull {
            abs(PadRenderer.slotCentreY(it, slots) - y)
        } ?: 1
}
