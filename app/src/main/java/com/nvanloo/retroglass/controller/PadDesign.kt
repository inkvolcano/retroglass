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
    /** Column width split: "5050" (default), "4060", "6040", "402040". */
    val split: String = SPLIT_EVEN,
    val shadowScreen: Boolean = false,
    val shadowButtons: Boolean = true,
    val shadowDpad: Boolean = true,
    val shadowStick: Boolean = true,
    /** Global module scale: 's' 0.8× · 'n' 1× · 'l' 1.25×. */
    val scale: Char = 'n',
) {
    fun moduleAt(zone: String, slot: Int?): String? = when {
        slot == null -> if (zone == ZONE_CT) ct else cl
        else -> column(zone)[slot]
    }

    fun column(zone: String): Map<Int, String> = if (zone == ZONE_LC) lc else rc

    fun withColumn(zone: String, value: Map<Int, String>): PadLayout =
        if (zone == ZONE_LC) copy(lc = value) else copy(rc = value)

    fun withZone(zone: String, value: String?): PadLayout =
        if (zone == ZONE_CT) copy(ct = value) else copy(cl = value)

    /** The slot a module placed *at* [slot] actually starts at, given it needs [slots] rows. */
    fun clampStart(slot: Int, slots: Int): Int = minOf(slot, SLOT_COUNT - slots + 1)

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

    val scaleFactor: Float get() = when (scale) {
        's' -> 0.8f
        'l' -> 1.25f
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
            "sp=$split",
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
        const val SLOT_COUNT = 6

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
            return PadLayout(
                ct = map["ct"]?.takeIf { it != "-" },
                cl = map["cl"]?.takeIf { it != "-" },
                lc = col("lc"),
                rc = col("rc"),
                align = map["al"].orEmpty().split(',').mapNotNull { e ->
                    val kv = e.split(':', limit = 2)
                    if (kv.size == 2 && kv[1].isNotEmpty()) kv[0] to kv[1][0] else null
                }.toMap(),
                split = map["sp"]?.takeIf { it in SPLITS } ?: SPLIT_EVEN,
                shadowScreen = sh[0] == '1',
                shadowButtons = sh[1] == '1',
                shadowDpad = sh[2] == '1',
                shadowStick = sh[3] == '1',
                scale = map["sc"]?.firstOrNull()?.takeIf { it in "snl" } ?: 'n',
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
