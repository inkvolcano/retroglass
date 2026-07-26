package com.nvanloo.retroglass.controller

/**
 * The saved shape of one console's touch pad, in the designer's own terms
 * (docs/controls-layout-handoff.md §6.8).
 *
 * This is deliberately *not* a list of coordinates. A layout is a map of **module id per field**,
 * where a field is either an overlay zone (CT/CL) or a numbered slot in one of the two big columns
 * (LC/RC, six slots each). Coordinates are computed from that by [PadRenderer] at draw time, which
 * is what lets the same design re-solve for a different screen without anything being re-authored.
 *
 * Portrait and landscape are **separate layouts**: editing one never touches the other. The handoff
 * is explicit about this, and it matters — a thumb reaches a very different part of a phone held
 * the other way round.
 */
data class PadLayout(
    /** Centre-top overlay zone: system pills / gear, or null for empty. */
    val ct: String? = null,
    /** Centre-low overlay zone: system pills / gear / stick, or null for empty. */
    val cl: String? = null,
    /** Left column: slot number (1..6) → module id. A 2-slot module occupies s and s+1. */
    val lc: Map<Int, String> = emptyMap(),
    /** Right column, same convention. */
    val rc: Map<Int, String> = emptyMap(),
    /** Horizontal alignment within the block, keyed "LC3"/"RC1": 'l', 'c' or 'r'. */
    val align: Map<String, Char> = emptyMap(),
    /** Rows per big column: 5, 6 (default) or 7. */
    val zones: Int = DEFAULT_ZONES,
    /** Column width split: "5050" (default), "4060", "6040", "402040". */
    val split: String = SPLIT_EVEN,
    /**
     * Video goes to an external display, so the pad owns the whole phone.
     *
     * The screen box stops being a picture and becomes a reserved outline — it is still a zone the
     * layout has to respect, because the glasses show the game at that framing, but nothing on the
     * phone renders inside it.
     */
    val noScr: Boolean = false,
    /**
     * Let the columns take back the space the screen was holding. Only means anything with
     * [noScr]: with the picture on the phone there is nothing to reflow into.
     */
    val reflow: Boolean = false,
    val shadowScreen: Boolean = false,
    val shadowButtons: Boolean = true,
    val shadowDpad: Boolean = true,
    val shadowStick: Boolean = true,
    /** Global module scale — see [SCALES]. The two largest are external-screen only. */
    val scale: String = SCALE_NORMAL,
) {
    fun moduleAt(zone: String, slot: Int?): String? = when {
        slot == null -> if (zone == ZONE_CT) ct else cl
        else -> column(zone)[slot]
    }

    fun column(zone: String): Map<Int, String> = if (zone == ZONE_LC) lc else rc

    /** Reflow only applies with the picture off the phone; asking for it otherwise is a no-op. */
    val reflowing: Boolean get() = noScr && reflow

    /**
     * Drops modules that no longer fit, for use after the row count shrinks.
     *
     * A module keeps its start row, so reducing the count strands anything whose footprint now
     * runs past the bottom. Silently leaving them in the map would make them invisible but still
     * occupy their rows, blocking placements for a reason nothing on screen explains.
     */
    fun prunedToZones(): PadLayout {
        fun prune(col: Map<Int, String>) = col.filter { (slot, id) ->
            slot + (PadModules.byId(id)?.slots ?: 1) - 1 <= zones
        }
        return copy(lc = prune(lc), rc = prune(rc))
    }

    fun withColumn(zone: String, value: Map<Int, String>): PadLayout =
        if (zone == ZONE_LC) copy(lc = value) else copy(rc = value)

    fun withZone(zone: String, value: String?): PadLayout =
        if (zone == ZONE_CT) copy(ct = value) else copy(cl = value)

    /** The slot a module placed *at* [slot] actually starts at, given it needs [slots] rows. */
    fun clampStart(slot: Int, slots: Int): Int = minOf(slot, zones - slots + 1)

    /**
     * Slot entries a module of [slots] rows starting at [start] would cover — the set the
     * designer warns about ("replaces …") and then removes on placement.
     */
    fun covering(zone: String, start: Int, slots: Int): List<Pair<Int, String>> =
        column(zone).entries
            .filter { (s, id) ->
                val k = PadModules.byId(id)?.slots ?: 1
                s + k - 1 >= start && s <= start + slots - 1
            }
            .map { it.key to it.value }
            .sortedBy { it.first }

    val scaleFactor: Float get() = when {
        // The two largest only make sense with the picture elsewhere; on-device they would bury
        // the game under the pad, so they fall back rather than being silently honoured.
        scale in EXTERNAL_SCALES && !noScr -> 1f
        scale == SCALE_SMALL -> 0.8f
        scale == SCALE_LARGE -> 1.25f
        scale == SCALE_XXL -> 1.6f
        scale == SCALE_XXXL -> 2f
        else -> 1f
    }

    /** Column widths as fractions of the pad width, left then right. */
    val splitFractions: Pair<Float, Float> get() = when (split) {
        SPLIT_LEFT_NARROW -> 0.40f to 0.60f
        SPLIT_LEFT_WIDE -> 0.60f to 0.40f
        // 40/20/40 leaves an empty band down the middle: the columns keep 40% each and the
        // remaining fifth is simply not addressable, which is the point of the option.
        SPLIT_GAPPED -> 0.40f to 0.40f
        else -> 0.50f to 0.50f
    }

    fun serialize(): String {
        fun col(m: Map<Int, String>) = m.entries.sortedBy { it.key }
            .joinToString(",") { "${it.key}:${it.value}" }
        return listOf(
            "ct=${ct ?: "-"}",
            "cl=${cl ?: "-"}",
            "lc=${col(lc)}",
            "rc=${col(rc)}",
            "al=${align.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }}",
            "zn=$zones",
            "sp=$split",
            "ns=${flag(noScr)}${flag(reflow)}",
            "sh=${flag(shadowScreen)}${flag(shadowButtons)}${flag(shadowDpad)}${flag(shadowStick)}",
            "sc=$scale",
        ).joinToString(";")
    }

    private fun flag(b: Boolean) = if (b) '1' else '0'

    companion object {
        const val ZONE_CT = "CT"
        const val ZONE_CL = "CL"
        const val ZONE_LC = "LC"
        const val ZONE_RC = "RC"
        const val DEFAULT_ZONES = 6
        val ZONE_COUNTS = listOf(5, 6, 7)

        const val SCALE_SMALL = "s"
        const val SCALE_NORMAL = "n"
        const val SCALE_LARGE = "l"
        const val SCALE_XXL = "xxl"
        const val SCALE_XXXL = "xxxl"
        val SCALES = listOf(SCALE_SMALL, SCALE_NORMAL, SCALE_LARGE)
        val EXTERNAL_SCALES = listOf(SCALE_XXL, SCALE_XXXL)

        fun scaleLabel(id: String) = when (id) {
            SCALE_SMALL -> "small"
            SCALE_LARGE -> "large"
            SCALE_XXL -> "XXL"
            SCALE_XXXL -> "XXXL"
            else -> "normal"
        }

        const val SPLIT_EVEN = "5050"
        const val SPLIT_LEFT_NARROW = "4060"
        const val SPLIT_LEFT_WIDE = "6040"
        const val SPLIT_GAPPED = "402040"
        val SPLITS = listOf(SPLIT_LEFT_NARROW, SPLIT_EVEN, SPLIT_LEFT_WIDE, SPLIT_GAPPED)

        fun splitLabel(id: String) = when (id) {
            SPLIT_LEFT_NARROW -> "40 / 60"
            SPLIT_LEFT_WIDE -> "60 / 40"
            SPLIT_GAPPED -> "40 / 20 / 40"
            else -> "50 / 50"
        }

        fun parse(raw: String?): PadLayout? {
            if (raw.isNullOrBlank()) return null
            val map = raw.split(';').mapNotNull {
                val kv = it.split('=', limit = 2)
                if (kv.size == 2) kv[0] to kv[1] else null
            }.toMap()
            fun col(key: String): Map<Int, String> =
                map[key].orEmpty().split(',').mapNotNull { e ->
                    val kv = e.split(':', limit = 2)
                    val slot = kv.getOrNull(0)?.toIntOrNull()
                    if (slot != null && kv.size == 2 && kv[1].isNotBlank()) slot to kv[1] else null
                }.toMap()
            val sh = map["sh"].orEmpty().padEnd(4, '0')
            val ns = map["ns"].orEmpty().padEnd(2, '0')
            return PadLayout(
                ct = map["ct"]?.takeIf { it != "-" },
                cl = map["cl"]?.takeIf { it != "-" },
                lc = col("lc"),
                rc = col("rc"),
                align = map["al"].orEmpty().split(',').mapNotNull { e ->
                    val kv = e.split(':', limit = 2)
                    if (kv.size == 2 && kv[1].isNotEmpty()) kv[0] to kv[1][0] else null
                }.toMap(),
                zones = map["zn"]?.toIntOrNull()?.takeIf { it in ZONE_COUNTS } ?: DEFAULT_ZONES,
                split = map["sp"]?.takeIf { it in SPLITS } ?: SPLIT_EVEN,
                noScr = ns[0] == '1',
                reflow = ns[1] == '1',
                shadowScreen = sh[0] == '1',
                shadowButtons = sh[1] == '1',
                shadowDpad = sh[2] == '1',
                shadowStick = sh[3] == '1',
                scale = map["sc"]?.takeIf { it in SCALES + EXTERNAL_SCALES } ?: SCALE_NORMAL,
            )
        }
    }
}

/** One console's pad in both orientations. The pair is stored and edited as a unit. */
data class PadDesign(
    val portrait: PadLayout = PadLayout(),
    val landscape: PadLayout = PadLayout(),
) {
    fun forOrientation(landscapeMode: Boolean) = if (landscapeMode) landscape else portrait

    fun with(landscapeMode: Boolean, layout: PadLayout) =
        if (landscapeMode) copy(landscape = layout) else copy(portrait = layout)

    fun serialize(): String = "P|${portrait.serialize()}\nL|${landscape.serialize()}"

    companion object {
        fun parse(raw: String?): PadDesign? {
            if (raw.isNullOrBlank()) return null
            var p: PadLayout? = null
            var l: PadLayout? = null
            raw.lineSequence().forEach { line ->
                when {
                    line.startsWith("P|") -> p = PadLayout.parse(line.removePrefix("P|"))
                    line.startsWith("L|") -> l = PadLayout.parse(line.removePrefix("L|"))
                }
            }
            val portrait = p ?: return null
            return PadDesign(portrait, l ?: portrait)
        }
    }
}
