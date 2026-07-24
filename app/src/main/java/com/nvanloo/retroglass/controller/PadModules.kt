package com.nvanloo.retroglass.controller

import android.graphics.Color

/**
 * The designer's module catalogue (docs/controls-layout-handoff.md §6.7).
 *
 * A module is an **arrangement**, not a set of buttons. The buttons themselves always come from
 * the console — their ids, labels, keycodes and colours are fixed by the hardware being emulated
 * and are never the user's to choose. What the user picks here is how those buttons are laid out:
 * the same four SNES faces can be a diamond, a 2×2 square, a row or a rising diagonal, and the
 * remap, turbo and hit-testing behaviour is identical in all four.
 *
 * That split is why the catalogue stays short even though the library has 35 consoles with wildly
 * different pads. A twelve-key Intellivision keypad and a four-key NeoGeo row are the same module
 * kind — a cluster of N buttons — differing only in N and in which arrangement reads best.
 *
 * [ARRANGE_AUTHORED] is the escape hatch and the default: it keeps the console's own hand-tuned
 * cluster geometry exactly as shipped, moved as a rigid group into whichever slot it now occupies.
 * Every console starts there, so switching to the designer changes nothing until you pick another
 * arrangement.
 */
object PadModules {

    /** Which field kinds a module may occupy. */
    enum class Cat { BLOCK, SHOULDER, SYSTEM }

    /** Which part of the console's controls a module draws its buttons from. */
    enum class Family { FACE, DPAD, STICK, SHOULDER, SYSTEM, NONE }

    data class Module(
        val id: String,
        val name: String,
        val cat: Cat,
        val family: Family,
        /** Rows consumed in an LC/RC column. Directionals and sticks take two. */
        val slots: Int = 1,
        /** Tagged `*variant` in the list — a restyling of the same function. */
        val variant: Boolean = false,
        /**
         * Button count this arrangement needs, or 0 for "any". A cluster of three buttons never
         * offers the diamond, so the list a user sees only ever contains things that fit.
         */
        val arity: Int = 0,
        /** DPAD/STICK design index handed to [ControlDef.design]. */
        val design: Int = 0,
    )

    const val ARRANGE_AUTHORED = "faceAuthored"

    // ---- face arrangements -----------------------------------------------------------------
    // Generic ones accept any N; the named ones declare the N they are drawn for.
    private val FACE = listOf(
        Module(ARRANGE_AUTHORED, "Console default", Cat.BLOCK, Family.FACE, slots = 2),
        Module("faceRow", "Row", Cat.BLOCK, Family.FACE, slots = 1),
        Module("faceDiag", "Rising diagonal", Cat.BLOCK, Family.FACE, slots = 1),
        Module("faceColumn", "Column", Cat.BLOCK, Family.FACE, slots = 2),
        Module("faceArc", "Arc", Cat.BLOCK, Family.FACE, slots = 1),
        Module("faceGrid", "Two rows", Cat.BLOCK, Family.FACE, slots = 2),
        Module("faceSquare", "2×2 square", Cat.BLOCK, Family.FACE, slots = 2, arity = 4),
        Module("faceDiamond", "Diamond", Cat.BLOCK, Family.FACE, slots = 2, arity = 4),
        Module("faceN64", "N64 A/B + C diamond", Cat.BLOCK, Family.FACE, slots = 2, arity = 6),
        Module("faceKeypad", "Keypad (3 per row)", Cat.BLOCK, Family.FACE, slots = 2),
        Module("faceVectrex", "One + row of three", Cat.BLOCK, Family.FACE, slots = 2, arity = 4),
        Module("faceColeco", "Two stacked + one", Cat.BLOCK, Family.FACE, slots = 1, arity = 3),
    )

    private val DPADS = listOf(
        "dpadCross" to "✛ Cross",
        "dpadDisc" to "⬤ Disc (8-way)",
        "dpadOcta" to "⯃ Octagon (8-way)",
        "dpadSplit" to "✧ Split arrows",
        "dpadPlate" to "▣ Square plate (8-way)",
        "dpadDish" to "◎ Dished round (8-way)",
    ).mapIndexed { i, (id, name) ->
        Module(id, name, Cat.BLOCK, Family.DPAD, slots = 2, variant = true, design = i)
    }

    private val STICKS = listOf(
        "stickConcentric" to "◉ Stick — concentric",
        "stickDish" to "◉ Stick — dished cap",
        "stickRing" to "◉ Stick — ring + nub",
        "stickGate" to "◉ Stick — square gate",
        "stickDimple" to "◉ Stick — dimpled cap",
        "stickKnurl" to "◉ Stick — knurled cap",
    ).mapIndexed { i, (id, name) ->
        Module(id, name, Cat.BLOCK, Family.STICK, slots = 2, variant = true, design = i)
    }

    private val SHOULDERS = listOf(
        Module("shSingle", "Single L1 / R1", Cat.SHOULDER, Family.SHOULDER),
        Module("shDouble", "Double stack", Cat.SHOULDER, Family.SHOULDER),
        Module("shTriple", "Triple stack", Cat.SHOULDER, Family.SHOULDER),
        Module("shComb2", "Combined L1 | L2", Cat.SHOULDER, Family.SHOULDER),
        Module("shComb3", "Combined L1 | L2 | L3", Cat.SHOULDER, Family.SHOULDER),
        Module("shL3", "L3 / R3 separate", Cat.SHOULDER, Family.SHOULDER),
    )

    private val SYSTEM = listOf(
        Module("sysStart", "Single START", Cat.SYSTEM, Family.SYSTEM),
        Module("sysGearBelow", "START + ⚙ below", Cat.SYSTEM, Family.SYSTEM),
        Module("sysDual", "SELECT · START", Cat.SYSTEM, Family.SYSTEM),
        Module("sysComb", "Combined SEL | ⚙ | START", Cat.SYSTEM, Family.SYSTEM),
        Module("sysGearOnly", "⚙ only", Cat.SYSTEM, Family.NONE),
        Module("sysFiller", "□ Filler — keeps the screen small", Cat.SYSTEM, Family.NONE),
        Module("sysStick", "◉ Stick (CL only)", Cat.SYSTEM, Family.STICK),
    )

    val ALL: List<Module> = FACE + DPADS + STICKS + SHOULDERS + SYSTEM

    private val BY_ID: Map<String, Module> = ALL.associateBy { it.id }

    fun byId(id: String?): Module? = id?.let { BY_ID[it] }

    /** Multi-button CT modules — the ones the shoulder rule collides with. */
    fun isMultiSystem(id: String?) = id == "sysDual" || id == "sysComb"

    /** Combined shoulder pills, blocked in slot 1 when CT is multi-button. */
    fun isCombinedShoulder(id: String?) = id == "shComb2" || id == "shComb3"

    /** CL is the only zone that may hold a stick. */
    fun isClOnly(id: String?) = id == "sysStick"

    // ---- emission --------------------------------------------------------------------------

    /**
     * A field's box in normalized pad coordinates: [cx]/[cy] centre, [w]/[h] extent. Widths are
     * fractions of the pad width and heights fractions of its height, matching [ControlDef].
     */
    data class Box(val cx: Float, val cy: Float, val w: Float, val h: Float)

    private val LIGHT = Color.parseColor("#EDEDF2")
    private val DARK = Color.parseColor("#1C1C1E")
    private val STICK_FILL = Color.parseColor("#3A3A41")

    /**
     * Lays [module] out inside [box] using the console's own buttons from [parts].
     *
     * [side] is 'L' or 'R' so shoulder modules know which stack to draw, and [stickIndex] picks
     * which analog stick a stick module represents when a console has two.
     */
    fun emit(
        module: Module,
        parts: PadParts,
        box: Box,
        scale: Float,
        side: Char = 'L',
        stickIndex: Int = 0,
    ): List<ControlDef> = when (module.family) {
        Family.DPAD -> emitDpad(module, parts, box, scale)
        Family.STICK -> emitStick(module, parts, box, scale, stickIndex)
        Family.SHOULDER -> emitShoulder(module, parts, box, scale, side)
        Family.SYSTEM -> emitSystem(module, parts, box, scale)
        Family.FACE -> emitFace(module, parts.face, box, scale)
        Family.NONE -> emitGearOnly(module, parts, box, scale)
    }

    private fun emitDpad(m: Module, parts: PadParts, box: Box, scale: Float): List<ControlDef> {
        val dpad = parts.dpad ?: return emptyList()
        val size = dpad.size * scale
        val out = mutableListOf(dpad.copy(x = box.cx, y = box.cy, size = size, design = m.design))
        // The centre button (N64's Z) rides with the pad: ControllerView's co-centre hit test
        // lets one thumb hold a direction and press it, which only works if they stay concentric.
        parts.dpadCenter?.let {
            out += it.copy(x = box.cx, y = box.cy, size = it.size * scale)
        }
        return out
    }

    private fun emitStick(
        m: Module,
        parts: PadParts,
        box: Box,
        scale: Float,
        stickIndex: Int,
    ): List<ControlDef> {
        val stick = parts.sticks.getOrNull(stickIndex) ?: parts.sticks.firstOrNull() ?: return emptyList()
        return listOf(stick.copy(x = box.cx, y = box.cy, size = stick.size * scale, design = m.design))
    }

    private fun emitShoulder(
        m: Module,
        parts: PadParts,
        box: Box,
        scale: Float,
        side: Char,
    ): List<ControlDef> {
        val all = if (side == 'L') parts.shouldersL else parts.shouldersR
        if (all.isEmpty()) return emptyList()
        val stickClicks = all.filter { it.id.endsWith("3") }
        val bumpers = all.filter { it !in stickClicks }
        val take = when (m.id) {
            "shSingle" -> bumpers.take(1)
            "shDouble", "shComb2" -> bumpers.take(2)
            "shTriple", "shComb3" -> bumpers.take(3)
            "shL3" -> stickClicks.take(1)
            else -> bumpers
        }
        if (take.isEmpty()) return emptyList()
        val size = (take.first().size) * scale
        return if (m.id == "shComb2" || m.id == "shComb3") {
            combinedPill(take, box.cx, box.cy, size)
        } else {
            // Stacked: the first bar sits on the box's own line, the rest hang below it.
            val step = size * 1.05f
            val top = box.cy - step * (take.size - 1) / 2f
            take.mapIndexed { i, def ->
                def.copy(x = box.cx, y = top + i * step, size = size, shape = ControlShape.BAR)
            }
        }
    }

    private fun emitSystem(m: Module, parts: PadParts, box: Box, scale: Float): List<ControlDef> {
        val start = parts.start ?: parts.system.lastOrNull()
        val select = parts.select ?: parts.system.firstOrNull()?.takeIf { it != start }
        val size = (start?.size ?: 0.12f) * scale
        return when (m.id) {
            "sysStart" -> listOfNotNull(start?.copy(x = box.cx, y = box.cy, size = size))
            "sysGearBelow" -> listOfNotNull(
                start?.copy(x = box.cx, y = box.cy - size * 0.62f, size = size),
                gear(box.cx, box.cy + size * 0.62f, size),
            )
            "sysDual" -> {
                val gap = size * 2.0f
                listOfNotNull(
                    select?.copy(x = box.cx - gap / 2f, y = box.cy, size = size),
                    start?.copy(x = box.cx + gap / 2f, y = box.cy, size = size),
                )
            }
            // The handoff seats the gear *between* SELECT and START in the combined pill, with
            // dividers on the inner edges only — that is what makes it read as one control.
            "sysComb" -> combinedPill(
                listOfNotNull(select, gear(0f, 0f, size), start), box.cx, box.cy, size,
            )
            else -> emptyList()
        }
    }

    private fun emitGearOnly(m: Module, parts: PadParts, box: Box, scale: Float): List<ControlDef> {
        val size = (parts.start?.size ?: 0.12f) * scale
        // The filler is deliberately invisible in the game: it exists only to hold a zone open so
        // the screen stays small, and the designer draws it as a ghost outline.
        return if (m.id == "sysGearOnly") listOf(gear(box.cx, box.cy, size)) else emptyList()
    }

    fun gear(x: Float, y: Float, size: Float): ControlDef = ControlDef(
        MENU_ID, ControlType.BUTTON, "⚙", 0, x, y, size,
        ControlShape.PILL, fillColor = Color.parseColor("#2E2E33"), labelColor = LIGHT,
        widthScale = 0.62f,
        // Tagged into the system group so that if it ever shares a row with START/SELECT the
        // device-time resolver spreads or fuses them, rather than stacking the gear on top.
        combineGroup = ZoneLayout.SYSTEM_PILLS,
    )

    const val MENU_ID = "_menu"

    private fun combinedPill(
        defs: List<ControlDef>,
        cx: Float,
        cy: Float,
        size: Float,
    ): List<ControlDef> {
        val n = defs.size
        if (n == 0) return emptyList()
        if (n == 1) return listOf(defs[0].copy(x = cx, y = cy, size = size))
        val fullHalfW = size / 2f * 1.85f
        val segHalfW = fullHalfW / n
        return defs.mapIndexed { i, def ->
            def.copy(
                x = cx - fullHalfW + segHalfW * (2 * i + 1),
                y = cy,
                size = size,
                shape = ControlShape.PILL,
                segment = when (i) {
                    0 -> PillSegment.LEFT
                    n - 1 -> PillSegment.RIGHT
                    else -> PillSegment.MID
                },
                widthScale = 1f / n,
            )
        }
    }

    /**
     * Face clusters. Spacing is expressed as a multiple of the button size so an arrangement
     * holds together at any scale, and the vertical multiplier is larger than the horizontal one
     * because the portrait pad is wider than it is tall — equal fractions there would read as an
     * squashed cluster.
     */
    private fun emitFace(
        m: Module,
        face: List<ControlDef>,
        box: Box,
        scale: Float,
    ): List<ControlDef> {
        if (face.isEmpty()) return emptyList()
        val n = face.size
        val size = face.maxOf { it.size } * scale
        val gx = size * 1.30f
        val gy = size * 0.95f
        fun at(i: Int, x: Float, y: Float) = face[i].copy(x = x, y = y, size = size)

        return when (m.id) {
            ARRANGE_AUTHORED -> translateAuthored(face, box, scale)

            "faceRow" -> face.indices.map {
                at(it, box.cx + (it - (n - 1) / 2f) * gx, box.cy)
            }

            "faceColumn" -> face.indices.map {
                at(it, box.cx, box.cy + (it - (n - 1) / 2f) * gy)
            }

            "faceDiag" -> face.indices.map {
                val t = if (n == 1) 0f else it / (n - 1f) - 0.5f
                at(it, box.cx + t * gx * (n - 1), box.cy - t * gy * (n - 1) * 0.62f)
            }

            "faceArc" -> face.indices.map {
                val t = if (n == 1) 0f else it / (n - 1f) - 0.5f
                // A shallow bow: the middle button sits proud of the line through the ends,
                // which is what makes a Genesis face reachable without moving the thumb.
                at(it, box.cx + t * gx * (n - 1), box.cy - (0.25f - t * t) * gy * 1.6f)
            }

            "faceGrid", "faceKeypad" -> {
                val cols = if (m.id == "faceKeypad") 3 else (n + 1) / 2
                val rows = (n + cols - 1) / cols
                face.indices.map {
                    val r = it / cols
                    val c = it % cols
                    val inRow = minOf(cols, n - r * cols)
                    at(
                        it,
                        box.cx + (c - (inRow - 1) / 2f) * gx,
                        box.cy + (r - (rows - 1) / 2f) * gy,
                    )
                }
            }

            "faceSquare" -> listOf(
                at(0, box.cx - gx / 2f, box.cy - gy / 2f),
                at(1, box.cx + gx / 2f, box.cy - gy / 2f),
                at(2, box.cx - gx / 2f, box.cy + gy / 2f),
                at(3, box.cx + gx / 2f, box.cy + gy / 2f),
            )

            "faceDiamond" -> listOf(
                at(0, box.cx, box.cy - gy),
                at(1, box.cx + gx, box.cy),
                at(2, box.cx, box.cy + gy),
                at(3, box.cx - gx, box.cy),
            )

            // Six buttons: four C keys as a diamond up and right, A and B on a rising diagonal
            // clear of it — the arrangement the handoff derives from the real N64 pad.
            "faceN64" -> {
                val c = size * 0.92f
                listOf(
                    at(0, box.cx + c * 0.55f, box.cy - c * 1.30f),
                    at(1, box.cx + c * 0.00f, box.cy - c * 0.45f),
                    at(2, box.cx + c * 1.10f, box.cy - c * 0.45f),
                    at(3, box.cx + c * 0.55f, box.cy + c * 0.40f),
                    at(4, box.cx - c * 1.15f, box.cy + c * 0.30f),
                    at(5, box.cx - c * 0.45f, box.cy + c * 1.25f),
                )
            }

            // Vectrex: the lone action button sits above a row of three.
            "faceVectrex" -> listOf(
                at(0, box.cx, box.cy - gy * 0.9f),
                at(1, box.cx - gx, box.cy + gy * 0.5f),
                at(2, box.cx, box.cy + gy * 0.5f),
                at(3, box.cx + gx, box.cy + gy * 0.5f),
            )

            // ColecoVision: two stacked keys with the third on the horizontal guide between them.
            "faceColeco" -> listOf(
                at(0, box.cx - gx * 0.45f, box.cy - gy * 0.55f),
                at(1, box.cx - gx * 0.45f, box.cy + gy * 0.55f),
                at(2, box.cx + gx * 0.70f, box.cy),
            )

            else -> translateAuthored(face, box, scale)
        }
    }

    /**
     * Moves the console's own cluster as a rigid group so its centre lands in [box], scaling each
     * button about that centre. Nothing about the authored geometry is recomputed — this is the
     * arrangement that guarantees a console looks exactly as it shipped.
     */
    private fun translateAuthored(face: List<ControlDef>, box: Box, scale: Float): List<ControlDef> {
        val cx = face.map { it.x }.average().toFloat()
        val cy = face.map { it.y }.average().toFloat()
        return face.map {
            it.copy(
                x = box.cx + (it.x - cx) * scale,
                y = box.cy + (it.y - cy) * scale,
                size = it.size * scale,
            )
        }
    }

    /**
     * Modules offered for a field, already filtered to what fits: a three-button cluster is never
     * shown the diamond, and a console with no analog stick is never shown one.
     */
    fun optionsFor(cat: Cat, parts: PadParts, zone: String): List<Module> = ALL.filter { m ->
        if (m.cat != cat) return@filter false
        if (isClOnly(m.id) && zone != PadLayout.ZONE_CL) return@filter false
        when (m.family) {
            Family.FACE -> parts.face.isNotEmpty() && (m.arity == 0 || m.arity == parts.face.size)
            Family.DPAD -> parts.dpad != null
            Family.STICK -> parts.sticks.isNotEmpty()
            Family.SHOULDER -> shoulderAvailable(m, parts)
            Family.SYSTEM -> parts.system.isNotEmpty()
            Family.NONE -> true
        }
    }

    private fun shoulderAvailable(m: Module, parts: PadParts): Boolean {
        val bumpers = parts.shouldersL.count { !it.id.endsWith("3") }
        return when (m.id) {
            "shSingle" -> bumpers >= 1
            "shDouble", "shComb2" -> bumpers >= 2
            "shTriple", "shComb3" -> bumpers >= 3
            "shL3" -> parts.shouldersL.any { it.id.endsWith("3") }
            else -> false
        }
    }
}
