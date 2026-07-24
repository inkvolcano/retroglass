package com.nvanloo.retroglass.controller

/**
 * Turns a saved [PadLayout] into the flat `ControlDef` list [ControllerView] draws and hit-tests.
 *
 * The slot grid is the whole geometry model: six rows per big column, two overlay zones on the
 * centre line, and a width split that decides how much of the pad each column owns. Everything
 * else — where a cluster ends up, how big it is, whether a shoulder pill is one bar or three —
 * falls out of which module the user dropped into which field.
 *
 * Alignment is applied *after* a module has been emitted, by measuring what it actually produced
 * and sliding the whole group. Estimating a footprint up front would mean every arrangement had
 * to declare its own width and keep that declaration honest; measuring cannot drift.
 */
object PadRenderer {

    /** Slot band tops as fractions of the column's height, and each band's height. */
    private val SLOT_TOPS = floatArrayOf(0.02f, 0.184f, 0.348f, 0.512f, 0.676f, 0.84f)
    private const val SLOT_H = 0.15f

    /** Overlay zone width, centred on the seam — the handoff's 40% centre box. */
    private const val CENTRE_W = 0.40f

    /** Landscape columns are this fraction of their portrait width, hugging the outer edges. */
    private const val LANDSCAPE_COL = 0.40f

    private const val EDGE_INSET = 0.012f

    /** Vertical centre of a module occupying [slots] rows starting at [start] (1-based). */
    fun slotCentreY(start: Int, slots: Int): Float {
        val top = SLOT_TOPS[(start - 1).coerceIn(0, SLOT_TOPS.lastIndex)]
        val lastIx = (start + slots - 2).coerceIn(0, SLOT_TOPS.lastIndex)
        return (top + SLOT_TOPS[lastIx] + SLOT_H) / 2f
    }

    fun slotTop(slot: Int): Float = SLOT_TOPS[(slot - 1).coerceIn(0, SLOT_TOPS.lastIndex)]

    fun slotHeight(): Float = SLOT_H

    /** Left and right edges of a column, as fractions of the pad width. */
    fun columnBounds(layout: PadLayout, zone: String, landscape: Boolean): Pair<Float, Float> {
        val (lf, rf) = layout.splitFractions
        val l = if (landscape) lf * LANDSCAPE_COL else lf
        val r = if (landscape) rf * LANDSCAPE_COL else rf
        return if (zone == PadLayout.ZONE_LC) 0f to l else (1f - r) to 1f
    }

    fun centreBounds(zone: String, landscape: Boolean): Pair<Float, Float> {
        val w = if (landscape) 0.34f else CENTRE_W
        return (0.5f - w / 2f) to (0.5f + w / 2f)
    }

    fun centreY(zone: String, landscape: Boolean): Float = when {
        zone == PadLayout.ZONE_CT && landscape -> 0.085f
        zone == PadLayout.ZONE_CT -> slotCentreY(1, 1)
        landscape -> 0.90f
        else -> slotCentreY(6, 1)
    }

    /**
     * Builds the pad. [extra] controls the designer has no field for are appended untouched, and
     * a gear is added if no system module already carries one, so the in-game menu is always
     * reachable however the user has rearranged things.
     */
    fun render(layout: PadLayout, parts: PadParts, landscape: Boolean): List<ControlDef> {
        val out = mutableListOf<ControlDef>()
        val scale = layout.scaleFactor

        for (zone in listOf(PadLayout.ZONE_CT, PadLayout.ZONE_CL)) {
            val module = PadModules.byId(layout.moduleAt(zone, null)) ?: continue
            val (l, r) = centreBounds(zone, landscape)
            val box = PadModules.Box(
                cx = (l + r) / 2f, cy = centreY(zone, landscape), w = r - l, h = SLOT_H,
            )
            // CL's stick is the one system module that belongs to a column's worth of space;
            // everything else in these zones is pill-sized by definition.
            out += PadModules.emit(module, parts, box, scale, stickIndex = 0)
        }

        var stickIndex = 0
        for (zone in listOf(PadLayout.ZONE_LC, PadLayout.ZONE_RC)) {
            val (left, right) = columnBounds(layout, zone, landscape)
            val side = if (zone == PadLayout.ZONE_LC) 'L' else 'R'
            for ((slot, id) in layout.column(zone).entries.sortedBy { it.key }) {
                val module = PadModules.byId(id) ?: continue
                val box = PadModules.Box(
                    cx = (left + right) / 2f,
                    cy = slotCentreY(slot, module.slots),
                    w = right - left,
                    h = module.slots * SLOT_H,
                )
                val emitted = PadModules.emit(module, parts, box, scale, side, stickIndex)
                if (module.family == PadModules.Family.STICK) stickIndex++
                val align = layout.align[zone + slot] ?: 'c'
                out += alignGroup(emitted, align, left, right)
            }
        }

        out += parts.extra
        if (out.none { it.id == PadModules.MENU_ID }) {
            // Losing the gear would strand the player in the game with no way back to the menu,
            // so it is re-seated at the top centre rather than allowed to go missing.
            val size = (parts.start?.size ?: 0.12f) * scale
            out += PadModules.gear(0.5f, centreY(PadLayout.ZONE_CT, landscape), size)
        }
        return out
    }

    /** Slides an emitted group so it sits left, centred or right within its column. */
    private fun alignGroup(
        defs: List<ControlDef>,
        align: Char,
        left: Float,
        right: Float,
    ): List<ControlDef> {
        if (defs.isEmpty() || align == 'c') return defs
        val minX = defs.minOf { it.x - halfWidth(it) }
        val maxX = defs.maxOf { it.x + halfWidth(it) }
        val dx = when (align) {
            'l' -> left + EDGE_INSET - minX
            'r' -> right - EDGE_INSET - maxX
            else -> 0f
        }
        return defs.map { it.copy(x = it.x + dx) }
    }

    /** A control's half-width in pad-width fractions — pills and bars are wider than they are tall. */
    fun halfWidth(def: ControlDef): Float = when (def.shape) {
        ControlShape.PILL -> def.size / 2f * 1.85f * def.widthScale
        ControlShape.BAR -> def.size / 2f * 1.85f
        else -> def.size / 2f
    }
}
