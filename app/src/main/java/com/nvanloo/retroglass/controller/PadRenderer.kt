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

    /**
     * Row geometry, derived rather than tabulated (handoff §7.1).
     *
     * Row 1 keeps a fixed height whatever the count, because it is the shoulder row and has to
     * stay aligned with CT — a shoulder that grew or shrank with the row count would break the
     * LT · CT · RT line that the whole top of the pad is built around. The remaining rows divide
     * what is left evenly.
     */
    private const val FIRST_TOP = 0.02f
    private const val FIRST_H = 0.15f
    private const val ROW_GAP = 0.014f
    private const val COLUMN_BOTTOM = 0.99f
    private const val SLOT_H = FIRST_H

    /** Overlay zone width, centred on the seam — the handoff's 40% centre box. */
    private const val CENTRE_W = 0.40f

    /** Width of the centre overlay zones in landscape. */
    private const val CENTRE_LAND = 0.34f

    /**
     * How much of the pad the two landscape columns share between them.
     *
     * The handoff defines the screen as what LC/RC/CT/CL leave, so the columns own everything
     * outside the centre band — `1 - CENTRE_LAND`, split between them in the layout's own ratio.
     * Sizing them any narrower than that leaves a strip nobody owns between the column and the
     * picture, and since a module centres in its column, every control ends up shoved against the
     * outer edge with the slack stranded on the inside.
     */
    private const val LANDSCAPE_SPAN = 1f - CENTRE_LAND

    private const val EDGE_INSET = 0.012f

    /** Height of every row after the first, for a column of [zones] rows. */
    private fun restHeight(zones: Int): Float {
        val rest = zones - 1
        if (rest <= 0) return FIRST_H
        val available = COLUMN_BOTTOM - (FIRST_TOP + FIRST_H + ROW_GAP)
        return (available - ROW_GAP * (rest - 1)) / rest
    }

    fun slotTop(slot: Int, zones: Int = PadLayout.DEFAULT_ZONES): Float {
        val n = slot.coerceIn(1, zones)
        if (n == 1) return FIRST_TOP
        val h = restHeight(zones)
        return FIRST_TOP + FIRST_H + ROW_GAP + (n - 2) * (h + ROW_GAP)
    }

    fun slotHeight(slot: Int = 1, zones: Int = PadLayout.DEFAULT_ZONES): Float =
        if (slot <= 1) FIRST_H else restHeight(zones)

    /** Vertical centre of a module occupying [slots] rows starting at [start] (1-based). */
    fun slotCentreY(start: Int, slots: Int, zones: Int = PadLayout.DEFAULT_ZONES): Float {
        val last = (start + slots - 1).coerceIn(1, zones)
        return (slotTop(start, zones) + slotTop(last, zones) + slotHeight(last, zones)) / 2f
    }

    /** Total height a module of [slots] rows starting at [start] is given. */
    fun slotSpan(start: Int, slots: Int, zones: Int = PadLayout.DEFAULT_ZONES): Float {
        val last = (start + slots - 1).coerceIn(1, zones)
        return slotTop(last, zones) + slotHeight(last, zones) - slotTop(start, zones)
    }

    /**
     * Left and right edges of a column, as fractions of the pad width.
     *
     * With the picture on the phone, the landscape columns share only what the centre band leaves.
     * Reflowed — picture on the glasses — there is no centre band to leave, so they meet in the
     * middle and the split divides the whole width, which is what the ratio says it should do.
     */
    fun columnBounds(layout: PadLayout, zone: String, landscape: Boolean): Pair<Float, Float> {
        val (lf, rf) = layout.splitFractions
        val span = if (!landscape || layout.reflowing) 1f else LANDSCAPE_SPAN
        val l = lf * span
        val r = rf * span
        return if (zone == PadLayout.ZONE_LC) 0f to l else (1f - r) to 1f
    }

    fun centreBounds(zone: String, landscape: Boolean): Pair<Float, Float> {
        val w = if (landscape) CENTRE_LAND else CENTRE_W
        return (0.5f - w / 2f) to (0.5f + w / 2f)
    }

    fun centreY(zone: String, landscape: Boolean, layout: PadLayout? = null): Float {
        val zones = layout?.zones ?: PadLayout.DEFAULT_ZONES
        return when {
            // CT follows row 1 wherever it goes, since they are the same row.
            zone == PadLayout.ZONE_CT && landscape -> 0.085f
            zone == PadLayout.ZONE_CT -> slotCentreY(1, 1, zones)
            landscape -> 0.90f
            else -> slotCentreY(zones, 1, zones)
        }
    }

    /**
     * Builds the pad. [extra] controls the designer has no field for are appended untouched, and
     * a gear is added if no system module already carries one, so the in-game menu is always
     * reachable however the user has rearranged things.
     */
    fun render(
        layout: PadLayout,
        parts: PadParts,
        landscape: Boolean,
        aspect: Float = PadModules.PORTRAIT_ASPECT,
    ): List<ControlDef> {
        val out = mutableListOf<ControlDef>()
        val scale = layout.scaleFactor
        val (mx, my) = PadModules.Box.units(aspect)

        for (zone in listOf(PadLayout.ZONE_CT, PadLayout.ZONE_CL)) {
            val module = PadModules.byId(layout.moduleAt(zone, null)) ?: continue
            val (l, r) = centreBounds(zone, landscape)
            val box = PadModules.Box(
                cx = (l + r) / 2f, cy = centreY(zone, landscape, layout), w = r - l, h = FIRST_H,
                mx = mx, my = my,
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
                val widen = widenedBand(layout, parts, zone, slot, module, landscape, aspect)
                val l = widen?.first ?: left
                val r = widen?.second ?: right
                val box = PadModules.Box(
                    cx = (l + r) / 2f,
                    cy = if (widen != null) centreY(PadLayout.ZONE_CT, landscape, layout)
                    else slotCentreY(slot, module.slots, layout.zones),
                    w = r - l,
                    h = slotSpan(slot, module.slots, layout.zones),
                    mx = mx, my = my,
                    stretch = widen != null,
                )
                val emitted = PadModules.emit(module, parts, box, scale, side, stickIndex)
                if (module.family == PadModules.Family.STICK) stickIndex++
                val align = layout.align[zone + slot] ?: 'c'
                // Centre by what the module actually occupies before anything else moves it.
                val centred = centreInBox(emitted, box.cx, box.cy, mx, my, centreX = align == 'c')
                out += keepInside(alignGroup(centred, align, l, r, mx), mx, my)
            }
        }

        out += parts.extra
        if (out.none { it.id == PadModules.MENU_ID }) {
            // Losing the gear would strand the player in the game with no way back to the menu,
            // so it is re-seated at the top centre rather than allowed to go missing.
            val size = (parts.start?.size ?: 0.12f) * scale
            out += PadModules.gear(0.5f, centreY(PadLayout.ZONE_CT, landscape, layout), size)
        }
        return out
    }

    /**
     * The slot-1 band a shoulder actually gets, or null when it simply takes its whole column.
     *
     * CT and slot 1 are the same row, and a shoulder fills the width it is given — so with CT
     * occupied the two would be drawn straight through each other. The band therefore runs from
     * the pad edge to the CT zone and stops there, which is both handoff §6.6's widening (in
     * landscape the column is narrower than that, so the shoulder grows to meet CT) and its
     * mirror image in portrait (the column is wider, so the shoulder gives way to it). LT · CT ·
     * RT read as one row either way.
     *
     * With CT empty there is nothing to avoid and the shoulder keeps the full column — in
     * landscape that row is the picture, which the widening must not cover.
     */
    fun widenedBand(
        layout: PadLayout,
        parts: PadParts,
        zone: String,
        slot: Int,
        module: PadModules.Module,
        landscape: Boolean,
        aspect: Float = PadModules.PORTRAIT_ASPECT,
    ): Pair<Float, Float>? {
        if (slot != 1 || layout.ct == null) return null
        if (module.cat != PadModules.Cat.SHOULDER) return null
        // Reflowed landscape columns already meet in the middle: there is no band to grow into,
        // and growing anyway would drive the two shoulders straight through each other.
        if (landscape && layout.reflowing) return null
        val (mx, my) = PadModules.Box.units(aspect)
        val (left, right) = columnBounds(layout, zone, landscape)
        // Yield to what CT actually holds, not to the zone it may occupy: a single START pill
        // leaves most of that 40% box empty, and stopping at the box's edge throws away reach the
        // shoulder could have had. Measured from the real module so it tracks whatever is placed.
        val (ctL, ctR) = ctSpan(layout, parts, landscape, mx, my)
            ?: centreBounds(PadLayout.ZONE_CT, landscape)
        val gap = EDGE_INSET * 2f
        return if (zone == PadLayout.ZONE_LC) left to minOf(right, ctL - gap)
        else maxOf(left, ctR + gap) to right
    }

    /** Horizontal extent of whatever CT holds, in width fractions, or null when it is empty. */
    private fun ctSpan(
        layout: PadLayout,
        parts: PadParts,
        landscape: Boolean,
        mx: Float,
        my: Float,
    ): Pair<Float, Float>? {
        val module = PadModules.byId(layout.ct) ?: return null
        val (l, r) = centreBounds(PadLayout.ZONE_CT, landscape)
        val box = PadModules.Box(
            cx = (l + r) / 2f, cy = centreY(PadLayout.ZONE_CT, landscape, layout),
            w = r - l, h = FIRST_H, mx = mx, my = my,
        )
        val defs = PadModules.emit(module, parts, box, layout.scaleFactor)
        if (defs.isEmpty()) return null
        return defs.minOf { it.x - halfWidth(it, mx) } to defs.maxOf { it.x + halfWidth(it, mx) }
    }

    /**
     * Centres a group on its field by the box it actually occupies.
     *
     * Modules are built around a centre point, but a cluster is rarely symmetrical about it: its
     * buttons differ in size and count, so the average of their centres is not the middle of the
     * shape you see. Kept as-is, an asymmetric cluster — a Genesis arc, the N64 faces, any
     * hand-authored pad — sits visibly high or low in its rows. Measuring the group's real extent
     * and centring that instead puts every module where its field says it should be.
     */
    private fun centreInBox(
        defs: List<ControlDef>,
        cx: Float,
        cy: Float,
        mx: Float,
        my: Float,
        centreX: Boolean,
    ): List<ControlDef> {
        if (defs.isEmpty()) return defs
        val minY = defs.minOf { it.y - halfHeight(it, my) }
        val maxY = defs.maxOf { it.y + halfHeight(it, my) }
        val dy = cy - (minY + maxY) / 2f
        val dx = if (!centreX) 0f else {
            val minX = defs.minOf { it.x - halfWidth(it, mx) }
            val maxX = defs.maxOf { it.x + halfWidth(it, mx) }
            cx - (minX + maxX) / 2f
        }
        return if (dx == 0f && dy == 0f) defs
        else defs.map { it.copy(x = it.x + dx, y = it.y + dy) }
    }

    /** Slides an emitted group so it sits left, centred or right within its column. */
    private fun alignGroup(
        defs: List<ControlDef>,
        align: Char,
        left: Float,
        right: Float,
        mx: Float,
    ): List<ControlDef> {
        if (defs.isEmpty() || align == 'c') return defs
        val minX = defs.minOf { it.x - halfWidth(it, mx) }
        val maxX = defs.maxOf { it.x + halfWidth(it, mx) }
        val dx = when (align) {
            'l' -> left + EDGE_INSET - minX
            'r' -> right - EDGE_INSET - maxX
            else -> 0f
        }
        return defs.map { it.copy(x = it.x + dx) }
    }

    /**
     * A control's half-width as a fraction of the pad *width*, matching how ControllerView
     * actually measures each shape.
     *
     * A BAR is the odd one out: its length is taken against the view width, while every other
     * shape is sized from the shorter edge. Treating them alike is what made a stretched shoulder
     * overshoot its band by the whole aspect ratio.
     */
    fun halfWidth(def: ControlDef, mx: Float = 1f): Float = when (def.shape) {
        ControlShape.BAR -> def.size / 2f
        ControlShape.PILL -> def.size / 2f * 1.85f * def.widthScale * mx
        else -> def.size / 2f * mx
    }

    /** A control's half-height as a fraction of the pad height. */
    private fun halfHeight(def: ControlDef, my: Float): Float = when (def.shape) {
        ControlShape.BAR -> 0.062f * my
        else -> def.size / 2f * my
    }

    /**
     * Nudges a group back inside the pad if it hangs over an edge.
     *
     * Slot bands are fractions of the pad, but a module's size is not, so a big module in an end
     * slot can reach past the pad itself — which is how the analog stick ended up with its lower
     * third cut off the bottom of the screen in landscape. Shifting the whole group keeps the
     * cluster's internal geometry intact, which clamping each control separately would destroy.
     */
    private fun keepInside(defs: List<ControlDef>, mx: Float, my: Float): List<ControlDef> {
        if (defs.isEmpty()) return defs
        val minX = defs.minOf { it.x - halfWidth(it, mx) }
        val maxX = defs.maxOf { it.x + halfWidth(it, mx) }
        val minY = defs.minOf { it.y - halfHeight(it, my) }
        val maxY = defs.maxOf { it.y + halfHeight(it, my) }
        var dx = 0f
        var dy = 0f
        if (minX < 0f) dx = -minX else if (maxX > 1f) dx = 1f - maxX
        if (minY < 0f) dy = -minY else if (maxY > 1f) dy = 1f - maxY
        return if (dx == 0f && dy == 0f) defs
        else defs.map { it.copy(x = it.x + dx, y = it.y + dy) }
    }
}
