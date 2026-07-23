package com.nvanloo.retroglass.model

import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs
import android.graphics.Color
import android.view.KeyEvent
import com.nvanloo.retroglass.controller.ControlDef
import com.nvanloo.retroglass.controller.ControlShape
import com.nvanloo.retroglass.controller.ControlType
import com.nvanloo.retroglass.controller.ZoneLayout
import com.nvanloo.retroglass.controller.ZoneLayout.Btn

/**
 * The supported consoles. Each carries the libretro core it runs on, the ROM
 * extensions it accepts, and the visual/positional definition of its touch controller.
 *
 * Positions are normalized: x,y in 0..1 of the controller view's width/height
 * (center of the control), size relative to the view's shorter edge (except
 * [ControlShape.BAR], whose size is a fraction of the view width).
 */
enum class Console(
    val displayName: String,
    val coreLibName: String,
    val romExtensions: Set<String>,
    val bodyColor: Int,
    val accentColor: Int,
) {
    NES(
        displayName = "NES",
        coreLibName = "libfceumm.so",
        romExtensions = setOf("nes", "fds", "unf", "unif"),
        bodyColor = Color.parseColor("#8F8A88"),
        accentColor = Color.parseColor("#B02525"),
    ),
    SNES(
        displayName = "SNES",
        coreLibName = "libsnes9x.so",
        romExtensions = setOf("sfc", "smc", "fig", "swc", "bs"),
        bodyColor = Color.parseColor("#B5B2AF"),
        accentColor = Color.parseColor("#4F43AE"),
    ),
    MEGADRIVE(
        displayName = "Mega Drive",
        coreLibName = "libgenesis_plus_gx.so",
        // .sms/.gg now belong to the dedicated Master System / Game Gear systems below.
        romExtensions = setOf("md", "gen", "smd", "sg", "68k", "sgd"),
        bodyColor = Color.parseColor("#1A1A1D"),
        accentColor = Color.parseColor("#C62828"),
    ),
    PSX(
        displayName = "PlayStation",
        coreLibName = "libpcsx_rearmed.so",
        romExtensions = setOf("cue", "chd", "pbp", "img", "iso", "m3u", "exe"),
        bodyColor = Color.parseColor("#44444C"),
        accentColor = Color.parseColor("#2D8CFF"),
    ),
    GAMEBOY(
        displayName = "Game Boy",
        coreLibName = "libgambatte.so",
        romExtensions = setOf("gb", "gbc", "dmg", "sgb"),
        bodyColor = Color.parseColor("#8A8577"),
        accentColor = Color.parseColor("#7B3F97"),
    ),
    GBA(
        displayName = "Game Boy Advance",
        coreLibName = "libmgba.so",
        romExtensions = setOf("gba", "srl"),
        bodyColor = Color.parseColor("#3B2F72"),
        accentColor = Color.parseColor("#8B7FD4"),
    ),
    N64(
        displayName = "Nintendo 64",
        coreLibName = "libmupen64plus_next.so",
        romExtensions = setOf("n64", "z64", "v64", "ndd"),
        bodyColor = Color.parseColor("#242427"),
        accentColor = Color.parseColor("#3FA338"),
    ),
    PCENGINE(
        displayName = "PC Engine",
        coreLibName = "libmednafen_pce_fast.so",
        romExtensions = setOf("pce", "sgx"),
        bodyColor = Color.parseColor("#CFCFCF"),
        accentColor = Color.parseColor("#E67E22"),
    ),
    NGP(
        displayName = "Neo Geo Pocket",
        coreLibName = "libmednafen_ngp.so",
        romExtensions = setOf("ngp", "ngc", "ngpc"),
        bodyColor = Color.parseColor("#2B2B30"),
        accentColor = Color.parseColor("#D24726"),
    ),
    DREAMCAST(
        displayName = "Dreamcast",
        coreLibName = "libflycast.so",
        // .gdi/.cdi are Dreamcast-only; disc images shared with other systems
        // (.chd/.cue/.iso) import as PS1 by default and can be reassigned.
        romExtensions = setOf("gdi", "cdi", "chd", "cue", "iso", "m3u", "elf", "lst"),
        bodyColor = Color.parseColor("#E4E0D6"),
        accentColor = Color.parseColor("#F17022"),
    ),
    PS2(
        displayName = "PlayStation 2",
        coreLibName = "libplay.so",
        romExtensions = setOf("iso", "chd", "cue", "cso", "elf", "mdf", "nrg"),
        bodyColor = Color.parseColor("#17171B"),
        accentColor = Color.parseColor("#2D6BE0"),
    ),
    LYNX(
        displayName = "Atari Lynx",
        coreLibName = "libhandy.so",
        romExtensions = setOf("lnx"),
        bodyColor = Color.parseColor("#232323"),
        accentColor = Color.parseColor("#E8A020"),
    ),
    ATARI2600(
        displayName = "Atari 2600",
        coreLibName = "libstella2023.so",
        romExtensions = setOf("a26"),
        bodyColor = Color.parseColor("#1E1E1E"),
        accentColor = Color.parseColor("#D24A2C"),
    ),
    ATARI7800(
        displayName = "Atari 7800",
        coreLibName = "libprosystem.so",
        romExtensions = setOf("a78"),
        bodyColor = Color.parseColor("#20201E"),
        accentColor = Color.parseColor("#C0392B"),
    ),
    WONDERSWAN(
        displayName = "WonderSwan",
        coreLibName = "libmednafen_wswan.so",
        romExtensions = setOf("ws", "wsc"),
        bodyColor = Color.parseColor("#2C2C34"),
        accentColor = Color.parseColor("#2E86C1"),
    ),
    VIRTUALBOY(
        displayName = "Virtual Boy",
        coreLibName = "libmednafen_vb.so",
        romExtensions = setOf("vb", "vboy"),
        bodyColor = Color.parseColor("#2A1010"),
        accentColor = Color.parseColor("#E03A3A"),
    ),
    PSP(
        displayName = "PSP",
        coreLibName = "libppsspp.so",
        romExtensions = setOf("cso", "pbp", "iso", "chd", "prx"),
        bodyColor = Color.parseColor("#101014"),
        accentColor = Color.parseColor("#5A6BE0"),
    ),
    NDS(
        displayName = "Nintendo DS",
        coreLibName = "libmelonds.so",
        romExtensions = setOf("nds"),
        bodyColor = Color.parseColor("#242428"),
        accentColor = Color.parseColor("#C0392B"),
    ),
    THREEDO(
        displayName = "3DO",
        coreLibName = "libopera.so",
        romExtensions = setOf("iso", "chd", "cue"),
        bodyColor = Color.parseColor("#20201E"),
        accentColor = Color.parseColor("#8E8E8E"),
    ),
    SATURN(
        displayName = "Saturn",
        coreLibName = "libmednafen_saturn.so",
        romExtensions = setOf("chd", "cue", "iso", "ccd", "mds"),
        bodyColor = Color.parseColor("#17171A"),
        accentColor = Color.parseColor("#4A6FA5"),
    ),
    SEGA32X(
        displayName = "Sega 32X",
        coreLibName = "libpicodrive.so",
        romExtensions = setOf("32x"),
        bodyColor = Color.parseColor("#141416"),
        accentColor = Color.parseColor("#E67E22"),
    ),
    COLECO(
        displayName = "ColecoVision",
        coreLibName = "libgearcoleco.so",
        romExtensions = setOf("col"),
        bodyColor = Color.parseColor("#1C1C20"),
        accentColor = Color.parseColor("#C0392B"),
    ),
    INTELLIVISION(
        displayName = "Intellivision",
        coreLibName = "libfreeintv.so",
        romExtensions = setOf("int"),
        bodyColor = Color.parseColor("#20201C"),
        accentColor = Color.parseColor("#B8860B"),
    ),
    VECTREX(
        displayName = "Vectrex",
        coreLibName = "libvecx.so",
        romExtensions = setOf("vec"),
        bodyColor = Color.parseColor("#141414"),
        accentColor = Color.parseColor("#3FA338"),
    ),
    POKEMONMINI(
        displayName = "Pokémon Mini",
        coreLibName = "libpokemini.so",
        romExtensions = setOf("min"),
        bodyColor = Color.parseColor("#2A2438"),
        accentColor = Color.parseColor("#E0A020"),
    ),
    ATARI5200(
        displayName = "Atari 5200",
        coreLibName = "libatari800.so",
        romExtensions = setOf("a52"),
        bodyColor = Color.parseColor("#1E1E1E"),
        accentColor = Color.parseColor("#D24A2C"),
    ),

    /**
     * A keyboard computer, and the one exception to the rule that dropped the others: atari800
     * draws its own on-screen keyboard into the framebuffer, raised by L3. So typing does not
     * need a keyboard device we do not have - the pad navigates the core's own overlay.
     */
    ATARI8BIT(
        displayName = "Atari 8-bit",
        coreLibName = "libatari800.so",
        romExtensions = setOf("atr", "xex", "atx", "cas"),
        bodyColor = Color.parseColor("#7A5C3A"),
        accentColor = Color.parseColor("#C8102E"),
    ),
    ARCADE(
        displayName = "Arcade / Neo Geo",
        coreLibName = "libfbneo.so",
        // Arcade romsets are .zip and must be passed to the core intact (not extracted).
        romExtensions = setOf("zip"),
        bodyColor = Color.parseColor("#101014"),
        accentColor = Color.parseColor("#E74C3C"),
    ),
    MASTERSYSTEM(
        displayName = "Master System",
        coreLibName = "libgenesis_plus_gx.so",
        romExtensions = setOf("sms"),
        bodyColor = Color.parseColor("#141416"),
        accentColor = Color.parseColor("#2E86C1"),
    ),
    GAMEGEAR(
        displayName = "Game Gear",
        coreLibName = "libgenesis_plus_gx.so",
        romExtensions = setOf("gg"),
        bodyColor = Color.parseColor("#232327"),
        accentColor = Color.parseColor("#16A085"),
    ),
    SEGACD(
        displayName = "Sega CD",
        coreLibName = "libgenesis_plus_gx.so",
        romExtensions = setOf("cue", "chd", "iso"),
        bodyColor = Color.parseColor("#0E0E10"),
        accentColor = Color.parseColor("#D64541"),
    ),
    PCECD(
        displayName = "PC Engine CD",
        coreLibName = "libmednafen_pce_fast.so",
        romExtensions = setOf("cue", "chd", "iso"),
        bodyColor = Color.parseColor("#26262A"),
        accentColor = Color.parseColor("#E67E22"),
    ),
    NEOGEOCD(
        displayName = "Neo Geo CD",
        coreLibName = "libneocd.so",
        romExtensions = setOf("cue", "chd"),
        bodyColor = Color.parseColor("#161616"),
        accentColor = Color.parseColor("#E4C000"),
    ),
    NAOMI(
        displayName = "Sega NAOMI",
        coreLibName = "libflycast.so",
        romExtensions = setOf("zip"),
        bodyColor = Color.parseColor("#14161B"),
        accentColor = Color.parseColor("#2E86C1"),
    ),
    ATOMISWAVE(
        displayName = "Atomiswave",
        coreLibName = "libflycast.so",
        romExtensions = setOf("zip"),
        bodyColor = Color.parseColor("#1A1420"),
        accentColor = Color.parseColor("#8E44AD"),
    );

    val prefKey: String get() = name.lowercase()

    /**
     * Typical vertical resolution the system renders at, in scanlines.
     *
     * Used to pick the upscale factor: the useful factor is roughly
     * `panelHeight / nativeHeight`, and it differs wildly across systems — a Dreamcast game
     * at 480p only needs ~2× to fill a 1080p panel, while a 240p NES game needs ~4×. A single
     * fixed factor is therefore wrong for one end of the range or the other.
     *
     * These are the common/most representative modes; several systems have more than one
     * (e.g. PS1 also does 480i), which is fine — the value only steers a 2..4 choice.
     */
    val nativeHeight: Int
        get() = when (this) {
            POKEMONMINI -> 64
            LYNX -> 102
            GAMEBOY, GAMEGEAR, WONDERSWAN -> 144
            NGP -> 152
            GBA -> 160
            MASTERSYSTEM, COLECO, INTELLIVISION -> 192
            ATARI2600 -> 210
            SNES, MEGADRIVE, SATURN, SEGA32X, SEGACD, NEOGEOCD, ARCADE, VIRTUALBOY -> 224
            PSP -> 272
            NDS -> 384 // two 192-line screens stacked
            PS2 -> 448
            DREAMCAST, NAOMI, ATOMISWAVE, VECTREX -> 480
            // NES, PSX, N64, PC Engine (+CD), 3DO, Atari 5200/7800/8-bit and anything else.
            else -> 240
        }

    /** Core options that MUST be forced for this system (applied at load). Used where one
     *  core serves several machines — e.g. atari800 runs both the 5200 console and the 8-bit
     *  computers, selected by the atari800_system option. */
    val forcedCoreVariables: List<Pair<String, String>> get() = when (this) {
        ATARI5200 -> listOf("atari800_system" to "5200")
        ATARI8BIT -> listOf("atari800_system" to "800XL (64K)")
        else -> emptyList()
    }

    /** Extra core options to force when 3+ controllers are assigned, for cores that gate their
     *  multitap behind an option rather than a port device. Device-based multitaps (SNES/NES/
     *  Genesis/Saturn/PC Engine) need no option — they're enabled by scanning getControllers()
     *  for the multitap device type at load. Unknown keys are ignored by the core, so listing
     *  both option names keeps this working across core versions. */
    val multitapCoreVariables: List<Pair<String, String>> get() = when (this) {
        PSX, PS2 -> listOf(
            "pcsx_rearmed_multitap" to "port 1",   // players 1–4 share the port-1 multitap
            "pcsx_rearmed_multitap1" to "enabled", // older option name; harmless if unknown
        )
        else -> emptyList()
    }

    /** Approximate hardware release year, for sorting the library by release date. */
    val year: Int get() = when (this) {
        ATARI2600 -> 1977
        INTELLIVISION, ATARI8BIT -> 1979
        ATARI5200, COLECO, VECTREX -> 1982
        NES -> 1983
        MASTERSYSTEM -> 1985
        ATARI7800 -> 1986
        PCENGINE -> 1987
        MEGADRIVE, PCECD -> 1988
        GAMEBOY, LYNX -> 1989
        SNES, GAMEGEAR, ARCADE -> 1990
        SEGACD -> 1991
        THREEDO -> 1993
        SATURN, SEGA32X, PSX, NEOGEOCD -> 1994
        VIRTUALBOY -> 1995
        N64 -> 1996
        DREAMCAST, NGP, NAOMI -> 1998
        WONDERSWAN -> 1999
        PS2 -> 2000
        GBA, POKEMONMINI -> 2001
        ATOMISWAVE -> 2003
        PSP, NDS -> 2004
    }

    /** How many player ports this system exposes. Handhelds are single-player; systems with 4
     *  native controller ports (N64, Dreamcast) or a common multitap adapter (SNES, NES/Famicom,
     *  Genesis, PS1/PS2, Saturn, PC Engine, 3DO, arcade 4-player cabs) support 4; the rest 2. */
    val maxPlayers: Int get() = when (this) {
        N64, DREAMCAST, SNES, NES, MEGADRIVE, SEGA32X, SEGACD, PSX, PS2, SATURN,
        PCENGINE, PCECD, THREEDO, ARCADE, NAOMI, ATOMISWAVE -> 4
        GAMEBOY, GBA, NGP, WONDERSWAN, LYNX, GAMEGEAR, POKEMONMINI, VIRTUALBOY, NDS -> 1
        else -> 2
    }

    /** The hardware's maker, for grouping the library by creator. */
    val maker: String get() = when (this) {
        NES, SNES, N64, GAMEBOY, GBA, NDS, VIRTUALBOY, POKEMONMINI -> "Nintendo"
        MEGADRIVE, SEGA32X, DREAMCAST, SATURN, MASTERSYSTEM, GAMEGEAR, SEGACD, NAOMI, ATOMISWAVE -> "Sega"
        PSX, PS2, PSP -> "Sony"
        PCENGINE, PCECD -> "NEC"
        NGP, NEOGEOCD -> "SNK"
        LYNX, ATARI2600, ATARI5200, ATARI7800, ATARI8BIT -> "Atari"
        WONDERSWAN -> "Bandai"
        THREEDO -> "3DO / Panasonic"
        COLECO -> "Coleco"
        INTELLIVISION -> "Mattel"
        VECTREX -> "GCE"
        ARCADE -> "Arcade"
    }

    companion object {
        val AMBIGUOUS_BIN = "bin"

        fun forExtension(ext: String, siblingCue: Boolean = false, fileSize: Long = 0): Console? {
            val e = ext.lowercase()
            if (e == AMBIGUOUS_BIN) {
                return if (siblingCue || fileSize > 16L * 1024 * 1024) PSX else MEGADRIVE
            }
            return entries.firstOrNull { e in it.romExtensions }
        }
    }
}

/** A named, selectable arrangement of controls for a console. */
data class LayoutPreset(
    val id: String,
    val name: String,
    val controls: List<ControlDef>,
)

/** Builds the factory controller layouts for a console. */
object ControllerDefs {

    private val LIGHT_TEXT = Color.parseColor("#EDEDF2")
    private val DARK = Color.parseColor("#26262B")
    private val GRAY_BTN = Color.parseColor("#55555F")
    private val SYMBOL = Color.parseColor("#2A2A31")

    /**
     * All layouts available for a console. First entry is the default.
     *
     * Every derived preset goes through [fitWithoutOverlap] against the authored layout, so a
     * transform cannot introduce a collision the base did not have. Applied here rather than
     * inside one transform because they all need it: the bug was first found in `scaled`, but
     * `lowered` reproduced it independently on the SNES diamond.
     */
    fun presetsFor(console: Console): List<LayoutPreset> {
        val base = baseControls(console)
        fun fit(controls: List<ControlDef>) = fitWithoutOverlap(base, controls)
        val presets = mutableListOf(
            LayoutPreset("default", "Default", base),
            LayoutPreset("large", "Large buttons", fit(scaled(base, 1.28f))),
            LayoutPreset("compact", "Compact", fit(scaled(base, 0.82f))),
            LayoutPreset("wide", "Wide (edges)", fit(widened(base))),
            LayoutPreset("bottom", "Bottom-heavy", fit(lowered(base))),
            LayoutPreset("lefty", "Left-handed", fit(mirrored(base))),
            LayoutPreset("fullscreen", "Full-screen", fit(fullscreen(console))),
        )
        // N64-specific: a big D-pad with Z at its centre so one thumb can hold a
        // direction and Z together.
        if (console == Console.N64) {
            presets.add(1, LayoutPreset("n64zdpad", "Z in D-pad", n64ZDpad()))
        }
        return presets
    }

    /** Alt N64 layout: enlarge the D-pad and drop Z into its exact centre (co-centred so a
     *  single finger can press a direction and Z at once — see ControllerView.findControls). */
    private fun n64ZDpad(): List<ControlDef> {
        val cx = 0.26f
        val cy = 0.48f
        val mapped = n64().map {
            when (it.id) {
                "dpad" -> it.copy(x = cx, y = cy, size = 0.44f)
                "z" -> it.copy(x = cx, y = cy, size = 0.20f)
                else -> it
            }
        }
        // Draw Z on top of the D-pad (move it after the dpad in the list).
        val z = mapped.first { it.id == "z" }
        return mapped.filter { it.id != "z" } + z
    }

    fun defaultPresetId(console: Console): String = presetsFor(console).first().id

    fun presetOrDefault(console: Console, id: String?): LayoutPreset {
        val all = presetsFor(console)
        return all.firstOrNull { it.id == id } ?: all.first()
    }

    /** Convenience for callers that only need the factory default arrangement. */
    fun controlsFor(console: Console): List<ControlDef> = baseControls(console)

    /** Debug: serialize every console's default layout to JSON (ground truth for doc previews). */
    fun dumpLayoutsJson(): String {
        fun hex(c: Int): String =
            // Locale.ROOT: this JSON is read by the doc-preview scripts, and a locale with
            // non-ASCII digits would render the hex unusable.
            if (Color.alpha(c) == 0) "transparent"
            else String.format(java.util.Locale.ROOT, "#%06X", 0xFFFFFF and c)
        val sb = StringBuilder("[\n")
        val consoles = Console.entries
        consoles.forEachIndexed { ci, console ->
            sb.append("  {\"console\":\"").append(console.name).append("\",")
            sb.append("\"display\":\"").append(console.displayName.replace("\"", "'")).append("\",")
            sb.append("\"maker\":\"").append(console.maker).append("\",")
            sb.append("\"year\":").append(console.year).append(",")
            sb.append("\"body\":\"").append(hex(console.bodyColor)).append("\",")
            sb.append("\"controls\":[")
            val cs = baseControls(console)
            cs.forEachIndexed { i, d ->
                sb.append("{\"id\":\"").append(d.id).append("\",")
                sb.append("\"type\":\"").append(d.type.name).append("\",")
                sb.append("\"label\":\"").append(d.label.replace("\\", "\\\\").replace("\"", "\\\"")).append("\",")
                sb.append("\"x\":").append(d.x).append(",\"y\":").append(d.y).append(",\"size\":").append(d.size).append(",")
                sb.append("\"shape\":\"").append(d.shape.name).append("\",")
                sb.append("\"fill\":\"").append(hex(d.fillColor)).append("\",")
                sb.append("\"labelColor\":\"").append(hex(d.labelColor)).append("\",")
                sb.append("\"stroke\":\"").append(hex(d.strokeColor)).append("\",")
                sb.append("\"plate\":\"").append(hex(d.plateColor)).append("\"}")
                if (i < cs.size - 1) sb.append(",")
            }
            sb.append("]}")
            if (ci < consoles.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("]\n")
        return sb.toString()
    }

    // -------------------------------------------------------- transforms

    /** Scales button sizes by [factor] and spreads positions out from the layout centre by a
     *  coupled amount, so enlarging buttons keeps clusters (face diamond, twin sticks) from
     *  colliding — and shrinking them tightens the layout. clampCenter keeps everything on-screen. */
    /**
     * Shrinks a transformed layout just enough that no two controls overlap horizontally.
     *
     * The preset transforms scale size faster than they spread position, and anything pushed
     * past the edge is then pulled back by `ControllerView.clampCenter` - which collapses the
     * gap it was clamped into. On a dense grid that is fatal: "Large buttons" overlapped the
     * ColecoVision/Intellivision keypad columns, and "Full-screen" stacked them almost on top
     * of each other, taking out the keys those games need to start.
     *
     * Positions are left alone (they are what makes a preset look like itself) and only the
     * size is trimmed, by the largest uniform factor that clears every pair.
     *
     * Pairs that already touch in the authored layout are exempt. Some are meant to: the SNES
     * diamond has X and A overlapping on the x-axis and separated on the y, and y cannot be
     * compared against size here because one is a fraction of height and the other of width.
     * So the rule is not "nothing overlaps" but "a preset introduces no overlap the authored
     * layout did not already have", which is exactly the defect this fixes.
     */
    private fun fitWithoutOverlap(
        base: List<ControlDef>,
        controls: List<ControlDef>,
    ): List<ControlDef> {
        fun halfX(c: ControlDef, k: Float): Float {
            val r = c.size * k / 2f
            return if (c.shape == ControlShape.PILL || c.shape == ControlShape.BAR) r * 1.85f else r
        }
        fun overlaps(list: List<ControlDef>, i: Int, j: Int, k: Float): Boolean {
            val a = list[i]
            val b = list[j]
            if (a.type == ControlType.DPAD || b.type == ControlType.DPAD) return false
            if (abs(a.y - b.y) > (a.size + b.size) / 2f) return false
            val ah = halfX(a, k)
            val bh = halfX(b, k)
            val ax = a.x.coerceIn(ah, 1f - ah)
            val bx = b.x.coerceIn(bh, 1f - bh)
            return abs(ax - bx) < ah + bh
        }
        // Same order and count as the input, so indices line up with the authored layout.
        val exempt = buildSet {
            for (i in base.indices) for (j in i + 1 until base.size) {
                if (overlaps(base, i, j, 1f)) add(i to j)
            }
        }
        fun clashes(k: Float): Boolean {
            for (i in controls.indices) for (j in i + 1 until controls.size) {
                if ((i to j) in exempt) continue
                if (overlaps(controls, i, j, k)) return true
            }
            return false
        }
        if (!clashes(1f)) return controls
        var lo = 0.5f
        var hi = 1f
        repeat(12) {
            val mid = (lo + hi) / 2f
            if (clashes(mid)) hi = mid else lo = mid
        }
        return controls.map { it.copy(size = it.size * lo) }
    }

    private fun scaled(controls: List<ControlDef>, factor: Float): List<ControlDef> {
        // Spread far enough that a control would leave the screen and ControllerView clamps it
        // back - onto whatever it was spreading away from. Saturn's Z went to x = 1.07 at
        // "Full-screen" and came back pinned against Y. So the spread is capped at the point
        // the outermost control reaches the edge, and everything moves by that same amount.
        val want = 1f + (factor - 1f) * 0.9f
        val spread = controls.fold(want) { k, c ->
            val d = abs(c.x - 0.5f)
            if (d <= 0.0001f || c.shape == ControlShape.BAR) k else {
                val half = c.size * factor / 2f *
                    (if (c.shape == ControlShape.PILL) 1.85f else 1f)
                min(k, ((0.5f - half) / d).coerceAtLeast(1f))
            }
        }
        return controls.map {
            // BAR shoulders are already wide; spreading them outward only shoves their
            // ends (and labels) off the edge, so scale their size but keep their position.
            if (it.shape == ControlShape.BAR) {
                it.copy(size = it.size * factor)
            } else {
                it.copy(
                    size = it.size * factor,
                    x = 0.5f + (it.x - 0.5f) * spread,
                    y = 0.5f + (it.y - 0.5f) * spread,
                )
            }
        }
    }

    private fun mirrored(controls: List<ControlDef>): List<ControlDef> =
        controls.map { it.copy(x = 1f - it.x) }

    /** Pulls controls toward the bottom for easier thumb reach, slightly enlarged. */
    private fun lowered(controls: List<ControlDef>): List<ControlDef> =
        controls.map { it.copy(y = (it.y * 0.7f + 0.32f).coerceIn(0.08f, 0.94f), size = it.size * 1.08f) }

    /** Pushes controls out toward the left/right edges, keeping the centre clear. */
    private fun widened(controls: List<ControlDef>): List<ControlDef> =
        controls.map { it.copy(x = (0.5f + (it.x - 0.5f) * 1.22f).coerceIn(0.06f, 0.94f)) }

    // -------------------------------------------------------- base layouts

    private fun baseControls(console: Console): List<ControlDef> = when (console) {
        Console.NES -> nes()
        Console.SNES -> snes()
        Console.MEGADRIVE -> megadrive()
        Console.PSX -> psx()
        Console.GAMEBOY -> gameboy()
        Console.GBA -> gba()
        Console.N64 -> n64()
        Console.PCENGINE -> pcengine()
        Console.NGP -> ngp()
        Console.DREAMCAST -> dreamcast()
        Console.PS2 -> psx() // DualShock 2 mirrors the PS1 pad
        Console.LYNX -> lynx()
        Console.ATARI2600 -> atari2600()
        Console.ATARI7800 -> atari7800()
        Console.WONDERSWAN -> wonderswan()
        Console.VIRTUALBOY -> virtualboy()
        Console.PSP -> psx() // PSP pad ≈ DualShock (one analog; extra stick unused)
        Console.NDS -> snes() // D-pad + ABXY + L/R + Start/Select
        Console.THREEDO -> threedo()
        Console.SATURN -> saturn()
        Console.SEGA32X -> megadrive() // 3-button Genesis pad
        Console.COLECO -> coleco()
        Console.INTELLIVISION -> intellivision()
        Console.VECTREX -> vectrex()
        Console.POKEMONMINI -> pokemonMini()
        Console.ATARI5200 -> atari5200()
        Console.ATARI8BIT -> atari8bit()
        Console.ARCADE -> arcade()
        Console.MASTERSYSTEM, Console.GAMEGEAR -> sms()
        Console.SEGACD -> megadrive()
        Console.PCECD -> pcengine()
        Console.NEOGEOCD -> neogeo()
        Console.NAOMI, Console.ATOMISWAVE -> arcade()
    }

    private fun fullscreen(console: Console): List<ControlDef> = when (console) {
        Console.PSX, Console.PS2, Console.PSP -> psxFullscreen()
        Console.N64 -> n64Fullscreen()
        else -> scaled(spreadToEdges(baseControls(console)), 1.35f)
    }

    /**
     * Pushes controls toward the screen edges to use more space.
     *
     * The spread is reduced until the outermost control lands inside the margin, rather than
     * spreading everything and clamping each control separately. Per-control clamping pins
     * distinct columns to the *same* coordinate - Saturn's six face buttons collapsed Y onto Z
     * and B onto C that way, and once two controls share an x no amount of shrinking can
     * separate them again.
     */
    private fun spreadToEdges(controls: List<ControlDef>): List<ControlDef> {
        val lo = 0.08f
        val hi = 0.92f
        fun limit(want: Float, values: List<Float>): Float {
            var k = want
            for (v in values) {
                val d = abs(v - 0.5f)
                if (d <= 0.0001f) continue
                val room = if (v > 0.5f) hi - 0.5f else 0.5f - lo
                k = min(k, room / d)
            }
            return max(1f, k)
        }
        val kx = limit(1.18f, controls.map { it.x })
        val ky = limit(1.12f, controls.map { it.y })
        return controls.map {
            it.copy(x = 0.5f + (it.x - 0.5f) * kx, y = 0.5f + (it.y - 0.5f) * ky)
        }
    }

    // ------------------------------------------------------------- NES

    // Converted to the zone system (docs/controls-layout-system.md): cross directional in the
    // left block, the NES two-button row in the right block, Select/Start pills in centre-low.
    private fun nes(): List<ControlDef> {
        val red = Color.parseColor("#B02525")
        return ZoneLayout.pad {
            directional()
            faceRow2(Btn("b", "B", KeyEvent.KEYCODE_BUTTON_B, red, plate = DARK),
                     Btn("a", "A", KeyEvent.KEYCODE_BUTTON_A, red, plate = DARK))
            systemPills(
                Btn("select", "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT, Color.parseColor("#1C1C1E"), red),
                Btn("start", "START", KeyEvent.KEYCODE_BUTTON_START, Color.parseColor("#1C1C1E"), red),
            )
        }
    }

    // ------------------------------------------------------------- SNES

    // Zone system: the SNES four-button diamond on crossed axes, grey L/R shoulders.
    private fun snes(): List<ControlDef> {
        val body = Color.parseColor("#3A3A3E")
        val sh = Color.parseColor("#8D8A92")
        return ZoneLayout.pad {
            directional(cx = 0.262f, cy = 0.50f, size = 0.47f, fill = body)
            faceDiamond4(
                Btn("x", "X", KeyEvent.KEYCODE_BUTTON_X, Color.parseColor("#3F51B5")),
                Btn("a", "A", KeyEvent.KEYCODE_BUTTON_A, Color.parseColor("#D32F2F")),
                Btn("b", "B", KeyEvent.KEYCODE_BUTTON_B, Color.parseColor("#F9A825"), labelColor = DARK),
                Btn("y", "Y", KeyEvent.KEYCODE_BUTTON_Y, Color.parseColor("#388E3C")),
            )
            shoulders(Btn("l", "L", KeyEvent.KEYCODE_BUTTON_L1, sh, labelColor = DARK),
                      Btn("r", "R", KeyEvent.KEYCODE_BUTTON_R1, sh, labelColor = DARK),
                      cy = 0.09f)
            systemPills(
                Btn("select", "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT, body),
                Btn("start", "START", KeyEvent.KEYCODE_BUTTON_START, body),
                cy = 0.845f, size = 0.115f,
            )
        }
    }

    // ------------------------------------------------------------- Mega Drive

    // Zone system: the Genesis three-button arc rides a rising diagonal guide, red-rimmed.
    private fun megadrive(): List<ControlDef> {
        val body = Color.parseColor("#2E2E33")
        val rim = Color.parseColor("#C62828")
        return ZoneLayout.pad {
            directional(cx = 0.282f, cy = 0.55f, size = 0.51f, fill = Color.parseColor("#0E0E10"))
            faceArc3(
                Btn("a", "A", KeyEvent.KEYCODE_BUTTON_Y, body, stroke = rim),
                Btn("b", "B", KeyEvent.KEYCODE_BUTTON_B, body, stroke = rim),
                Btn("c", "C", KeyEvent.KEYCODE_BUTTON_A, body, stroke = rim),
                cx = 0.71f, cy = 0.575f, spread = 0.24f, size = 0.17f,
            )
            systemPills(null, Btn("start", "START", KeyEvent.KEYCODE_BUTTON_START, body), cy = 0.86f, size = 0.13f)
        }
    }

    // ------------------------------------------------------------- PlayStation

    /** Compact PS1 layout. Face cluster sits clear of the shoulder row. */
    // Zone system: PS1 DualShock - stacked L1/L2 + R1/R2 shoulders, the shape diamond on
    // crossed axes (each symbol keeps its colour), Select/Start pills, and twin sticks below.
    private fun psx(): List<ControlDef> {
        val face = 0.16f
        return ZoneLayout.pad {
            directional(cx = 0.257f, cy = 0.42f, size = 0.46f, fill = GRAY_BTN)
            shouldersStacked(
                Btn("l1", "L1", KeyEvent.KEYCODE_BUTTON_L1, GRAY_BTN),
                Btn("l2", "L2", KeyEvent.KEYCODE_BUTTON_L2, GRAY_BTN),
                Btn("r1", "R1", KeyEvent.KEYCODE_BUTTON_R1, GRAY_BTN),
                Btn("r2", "R2", KeyEvent.KEYCODE_BUTTON_R2, GRAY_BTN),
                top = 0.055f, bottom = 0.175f, size = 0.20f,
            )
            faceDiamond4(
                Btn("triangle", "△", KeyEvent.KEYCODE_BUTTON_X, SYMBOL, labelColor = Color.parseColor("#26B57A")),
                Btn("circle", "○", KeyEvent.KEYCODE_BUTTON_A, SYMBOL, labelColor = Color.parseColor("#E4574C")),
                Btn("cross", "✕", KeyEvent.KEYCODE_BUTTON_B, SYMBOL, labelColor = Color.parseColor("#7BA4D9")),
                Btn("square", "□", KeyEvent.KEYCODE_BUTTON_Y, SYMBOL, labelColor = Color.parseColor("#D992BC")),
                cx = 0.73f, cy = 0.42f, hx = 0.14f, vy = 0.095f, size = face,
            )
            pillPair(Btn("select", "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT, SYMBOL),
                     Btn("start", "START", KeyEvent.KEYCODE_BUTTON_START, SYMBOL),
                     cy = 0.11f, size = 0.10f, gap = 0.22f)
            stick("stick_l", 0.33f, 0.82f, 0.26f, label = "L")
            stick("stick_r", 0.67f, 0.82f, 0.26f, label = "R")
        }
    }

    /**
     * Big PS1 layout that fills the phone: full-width shoulder bars stacked at the
     * top, a large D-pad bottom-left, large face cluster bottom-right, twin sticks
     * in the middle, SELECT/START between the shoulders. Matches the reference sketch.
     */
    // Zone system: the full-screen PS1 pad (gamepad fills the phone) - the same DualShock
    // pieces spread out: half-width shoulder bars stacked at the top, a large D-pad
    // bottom-left, the widened shape diamond bottom-right, twin sticks in the middle.
    private fun psxFullscreen(): List<ControlDef> {
        val face = 0.20f
        return ZoneLayout.pad {
            shouldersStacked(
                Btn("l1", "L1", KeyEvent.KEYCODE_BUTTON_L1, GRAY_BTN),
                Btn("l2", "L2", KeyEvent.KEYCODE_BUTTON_L2, GRAY_BTN),
                Btn("r1", "R1", KeyEvent.KEYCODE_BUTTON_R1, GRAY_BTN),
                Btn("r2", "R2", KeyEvent.KEYCODE_BUTTON_R2, GRAY_BTN),
                top = 0.05f, bottom = 0.185f, size = 0.46f, lx = 0.245f, rx = 0.755f,
            )
            pillPair(Btn("select", "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT, SYMBOL),
                     Btn("start", "START", KeyEvent.KEYCODE_BUTTON_START, SYMBOL), cy = 0.34f, size = 0.11f)
            directional(cx = 0.15f, cy = 0.66f, size = 0.66f, fill = GRAY_BTN)
            faceDiamond4(
                Btn("triangle", "△", KeyEvent.KEYCODE_BUTTON_X, SYMBOL, labelColor = Color.parseColor("#26B57A")),
                Btn("circle", "○", KeyEvent.KEYCODE_BUTTON_A, SYMBOL, labelColor = Color.parseColor("#E4574C")),
                Btn("cross", "✕", KeyEvent.KEYCODE_BUTTON_B, SYMBOL, labelColor = Color.parseColor("#7BA4D9")),
                Btn("square", "□", KeyEvent.KEYCODE_BUTTON_Y, SYMBOL, labelColor = Color.parseColor("#D992BC")),
                cx = 0.85f, cy = 0.66f, hx = 0.10f, vy = 0.19f, size = face,
            )
            stick("stick_l", 0.40f, 0.66f, 0.34f, label = "L")
            stick("stick_r", 0.60f, 0.66f, 0.34f, label = "R")
        }
    }

    // ------------------------------------------------------------- Game Boy

    // Zone system: the Game Boy's two buttons sit on a 30 diagonal (B low-left, A high-right)
    // rather than a row, so the directional is nudged in and the pills ride a touch higher.
    private fun gameboy(): List<ControlDef> {
        val body = Color.parseColor("#5A3A7A")
        val btn = Color.parseColor("#7B3F97")
        return ZoneLayout.pad {
            directional(cx = 0.29f, cy = 0.50f, size = 0.50f)
            faceDiag2(Btn("b", "B", KeyEvent.KEYCODE_BUTTON_B, btn),
                      Btn("a", "A", KeyEvent.KEYCODE_BUTTON_A, btn),
                      cx = 0.775f, cy = 0.52f, spread = 0.20f, size = 0.24f)
            systemPills(
                Btn("select", "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT, body),
                Btn("start", "START", KeyEvent.KEYCODE_BUTTON_START, body),
                cy = 0.82f,
            )
        }
    }

    // ------------------------------------------------------------- Game Boy Advance

    // Zone system: GBA adds shoulders (L/R in the top corners) to the Game Boy diagonal.
    private fun gba(): List<ControlDef> {
        val face = Color.parseColor("#8B7FD4")
        val pill = Color.parseColor("#2E2C45")
        val sh = Color.parseColor("#4A3E82")
        return ZoneLayout.pad {
            directional(cx = 0.28f, cy = 0.56f, size = 0.50f, fill = Color.parseColor("#26243A"))
            faceDiag2(Btn("b", "B", KeyEvent.KEYCODE_BUTTON_B, face, labelColor = DARK),
                      Btn("a", "A", KeyEvent.KEYCODE_BUTTON_A, face, labelColor = DARK),
                      cx = 0.765f, cy = 0.50f, spread = 0.21f, size = 0.20f)
            shoulders(Btn("l", "L", KeyEvent.KEYCODE_BUTTON_L1, sh),
                      Btn("r", "R", KeyEvent.KEYCODE_BUTTON_R1, sh),
                      cy = 0.10f, size = 0.17f, lx = 0.20f, rx = 0.80f)
            systemPills(
                Btn("select", "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT, pill),
                Btn("start", "START", KeyEvent.KEYCODE_BUTTON_START, pill),
                cy = 0.84f, size = 0.115f,
            )
        }
    }

    // ------------------------------------------------------------- PC Engine

    // Zone system: PC Engine - two-button row (II, I), Sel/Run pills.
    private fun pcengine(): List<ControlDef> {
        val btn = Color.parseColor("#E67E22")
        return ZoneLayout.pad {
            directional(cx = 0.27f, cy = 0.47f, size = 0.48f, fill = Color.parseColor("#2A2A2E"))
            faceRow2(Btn("two", "II", KeyEvent.KEYCODE_BUTTON_A, btn, labelColor = DARK),
                     Btn("one", "I", KeyEvent.KEYCODE_BUTTON_B, btn, labelColor = DARK),
                     cx = 0.74f, cy = 0.47f, gap = 0.22f, size = 0.20f)
            systemPills(
                Btn("select", "SEL", KeyEvent.KEYCODE_BUTTON_SELECT, Color.parseColor("#2A2A2E")),
                Btn("run", "RUN", KeyEvent.KEYCODE_BUTTON_START, Color.parseColor("#2A2A2E")),
                cy = 0.82f, size = 0.11f,
            )
        }
    }

    // ------------------------------------------------------------- Nintendo 64

    /**
     * N64 mapping (mupen64plus_next default RetroPad): A=A, B=B, Start=Start, L=L, R=R,
     * Z=L2, the single analog stick = left analog, and the four discrete yellow C-buttons
     * (ids "c_*") = the right analog (handled in ControllerView.sendCButtons). This mirrors
     * the physical pad: one centred stick, D-pad upper-left, big A + B, small C diamond.
     */
    // Zone system: N64 - shoulders + Z + Start across the top, D-pad upper-left, big B/A, the
    // four yellow C-buttons as a small diamond (they drive the right analog), one centred stick.
    private fun n64(): List<ControlDef> {
        val yellow = Color.parseColor("#E8B800")
        val cSize = 0.115f
        return ZoneLayout.pad {
            shoulders(Btn("l", "L", KeyEvent.KEYCODE_BUTTON_L1, GRAY_BTN),
                      Btn("r", "R", KeyEvent.KEYCODE_BUTTON_R1, GRAY_BTN),
                      cy = 0.06f, size = 0.18f, lx = 0.13f, rx = 0.87f)
            systemPills(null, Btn("start", "START", KeyEvent.KEYCODE_BUTTON_START, Color.parseColor("#C0392B")),
                cy = 0.06f)
            faceButton(Btn("z", "Z", KeyEvent.KEYCODE_BUTTON_L2, Color.parseColor("#2E2E33")), 0.50f, 0.20f, 0.13f)
            directional(cx = 0.227f, cy = 0.33f, size = 0.40f, fill = Color.parseColor("#3A3A3E"))
            faceButton(Btn("b", "B", KeyEvent.KEYCODE_BUTTON_B, Color.parseColor("#2E7D32")), 0.58f, 0.54f, 0.17f)
            faceButton(Btn("a", "A", KeyEvent.KEYCODE_BUTTON_A, Color.parseColor("#1565C0")), 0.78f, 0.62f, 0.20f)
            faceDiamond4(
                Btn("c_up", "C", 0, yellow, labelColor = DARK),
                Btn("c_right", "C", 0, yellow, labelColor = DARK),
                Btn("c_down", "C", 0, yellow, labelColor = DARK),
                Btn("c_left", "C", 0, yellow, labelColor = DARK),
                cx = 0.80f, cy = 0.38f, hx = 0.10f, vy = 0.08f, size = cSize,
            )
            stick("stick_l", 0.50f, 0.81f, 0.34f)
        }
    }

    private fun n64Fullscreen(): List<ControlDef> = scaled(spreadToEdges(n64()), 1.15f)

    // ------------------------------------------------------------- Atari Lynx

    /** Lynx: D-pad, A, B, two Option buttons (shoulder bars), Pause = Start. */
    // Zone system: Lynx - handheld diagonal, OPT1/OPT2 shoulders, a lone Pause pill.
    private fun lynx(): List<ControlDef> {
        val btn = Color.parseColor("#E8A020")
        return ZoneLayout.pad {
            directional(cx = 0.302f, cy = 0.55f, size = 0.55f)
            faceDiag2(Btn("b", "B", KeyEvent.KEYCODE_BUTTON_B, btn, labelColor = DARK),
                      Btn("a", "A", KeyEvent.KEYCODE_BUTTON_A, btn, labelColor = DARK))
            shoulders(Btn("opt1", "OPT 1", KeyEvent.KEYCODE_BUTTON_L1, GRAY_BTN),
                      Btn("opt2", "OPT 2", KeyEvent.KEYCODE_BUTTON_R1, GRAY_BTN), cy = 0.10f)
            systemPills(null, Btn("pause", "PAUSE", KeyEvent.KEYCODE_BUTTON_START, Color.parseColor("#2A2A2E")))
        }
    }

    // ------------------------------------------------------------- Atari 2600

    /** 2600: one joystick, one Fire button, plus console Select and Reset (=Start). */
    // Zone system: 2600 - one big Fire button, Select/Reset console pills.
    private fun atari2600(): List<ControlDef> = ZoneLayout.pad {
        directional(cx = 0.302f, cy = 0.54f, size = 0.55f)
        faceFire1(Btn("fire", "FIRE", KeyEvent.KEYCODE_BUTTON_B, Color.parseColor("#D24A2C")))
        systemPills(
            Btn("select", "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT, Color.parseColor("#2A2A2E")),
            Btn("reset", "RESET", KeyEvent.KEYCODE_BUTTON_START, Color.parseColor("#2A2A2E")),
            cy = 0.84f, size = 0.115f,
        )
    }

    // ------------------------------------------------------------- Atari 7800

    /** 7800: D-pad, two fire buttons, Select, Pause (=Start). */
    // Zone system: 7800 - two fire buttons (1,2) on the diagonal, Select/Pause pills.
    private fun atari7800(): List<ControlDef> {
        val btn = Color.parseColor("#C0392B")
        return ZoneLayout.pad {
            directional(cx = 0.302f, cy = 0.54f, size = 0.55f)
            faceDiag2(Btn("b", "1", KeyEvent.KEYCODE_BUTTON_B, btn),
                      Btn("a", "2", KeyEvent.KEYCODE_BUTTON_A, btn))
            systemPills(
                Btn("select", "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT, Color.parseColor("#2A2A2E")),
                Btn("pause", "PAUSE", KeyEvent.KEYCODE_BUTTON_START, Color.parseColor("#2A2A2E")),
                cy = 0.84f, size = 0.115f,
            )
        }
    }

    // ------------------------------------------------------------- WonderSwan

    /** WonderSwan: D-pad (X-pad), A, B, Start. */
    // Zone system: WonderSwan - handheld diagonal, single Start pill.
    private fun wonderswan(): List<ControlDef> {
        val blue = Color.parseColor("#2E86C1")
        return ZoneLayout.pad {
            directional(cx = 0.302f, cy = 0.54f, size = 0.55f, fill = Color.parseColor("#22222A"))
            faceDiag2(Btn("b", "B", KeyEvent.KEYCODE_BUTTON_B, blue),
                      Btn("a", "A", KeyEvent.KEYCODE_BUTTON_A, blue))
            systemPills(null, Btn("start", "START", KeyEvent.KEYCODE_BUTTON_START, blue))
        }
    }

    // ------------------------------------------------------------- Virtual Boy

    /** Virtual Boy: left D-pad, A, B, L/R shoulders, Select, Start. */
    // Zone system: Virtual Boy - handheld diagonal, L/R shoulders, Select/Start pills.
    private fun virtualboy(): List<ControlDef> {
        val red = Color.parseColor("#E03A3A")
        val body = Color.parseColor("#3A1414")
        return ZoneLayout.pad {
            directional(cx = 0.287f, cy = 0.56f, size = 0.52f, fill = body)
            faceDiag2(Btn("b", "B", KeyEvent.KEYCODE_BUTTON_B, red),
                      Btn("a", "A", KeyEvent.KEYCODE_BUTTON_A, red))
            shoulders(Btn("l", "L", KeyEvent.KEYCODE_BUTTON_L1, Color.parseColor("#7A2020")),
                      Btn("r", "R", KeyEvent.KEYCODE_BUTTON_R1, Color.parseColor("#7A2020")),
                      cy = 0.10f, size = 0.17f, lx = 0.14f, rx = 0.86f)
            systemPills(
                Btn("select", "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT, body),
                Btn("start", "START", KeyEvent.KEYCODE_BUTTON_START, body),
                cy = 0.85f, size = 0.11f,
            )
        }
    }

    // ------------------------------------------------------------- 3DO

    // Zone system: 3DO - three face buttons (C B A) on a rising diagonal, L/R shoulders,
    // and the Play/Stop pills (Play on the left, so an explicit pill pair).
    private fun threedo(): List<ControlDef> {
        val btn = Color.parseColor("#3A3A3E")
        return ZoneLayout.pad {
            directional(cx = 0.287f, cy = 0.55f, size = 0.52f, fill = Color.parseColor("#2A2A2E"))
            faceDiagonal(listOf(
                Btn("c", "C", KeyEvent.KEYCODE_BUTTON_X, btn),
                Btn("b", "B", KeyEvent.KEYCODE_BUTTON_B, btn),
                Btn("a", "A", KeyEvent.KEYCODE_BUTTON_A, btn),
            ), cx = 0.815f, cy = 0.55f, spread = 0.25f, size = 0.17f)
            shoulders(Btn("l", "L", KeyEvent.KEYCODE_BUTTON_L1, GRAY_BTN),
                      Btn("r", "R", KeyEvent.KEYCODE_BUTTON_R1, GRAY_BTN),
                      cy = 0.10f, size = 0.17f, lx = 0.14f, rx = 0.86f)
            pillPair(Btn("p", "P", KeyEvent.KEYCODE_BUTTON_START, btn),
                     Btn("x", "X", KeyEvent.KEYCODE_BUTTON_SELECT, btn), cy = 0.85f, size = 0.11f)
        }
    }

    // ------------------------------------------------------------- Saturn

    /** Saturn: 6 face buttons (X Y Z / A B C), L/R shoulders, Start. */
    // Zone system: Saturn - six face buttons as two shallow diagonal rows (X Y Z over A B C),
    // L/R shoulders and a centred Start.
    private fun saturn(): List<ControlDef> {
        val top = Color.parseColor("#37414F")
        val bot = Color.parseColor("#2E2E34")
        return ZoneLayout.pad {
            directional(cx = 0.277f, cy = 0.56f, size = 0.50f, fill = Color.parseColor("#22262E"))
            faceDiagonal(listOf(
                Btn("x", "X", KeyEvent.KEYCODE_BUTTON_Y, top),
                Btn("y", "Y", KeyEvent.KEYCODE_BUTTON_X, top),
                Btn("z", "Z", KeyEvent.KEYCODE_BUTTON_L2, top),
            ), cx = 0.79f, cy = 0.353f, spread = 0.28f, size = 0.13f, slope = 0.32f)
            faceDiagonal(listOf(
                Btn("a", "A", KeyEvent.KEYCODE_BUTTON_B, bot),
                Btn("b", "B", KeyEvent.KEYCODE_BUTTON_A, bot),
                Btn("c", "C", KeyEvent.KEYCODE_BUTTON_R2, bot),
            ), cx = 0.79f, cy = 0.573f, spread = 0.28f, size = 0.13f, slope = 0.32f)
            shoulders(Btn("l", "L", KeyEvent.KEYCODE_BUTTON_L1, GRAY_BTN),
                      Btn("r", "R", KeyEvent.KEYCODE_BUTTON_R1, GRAY_BTN),
                      cy = 0.09f, size = 0.17f, lx = 0.14f, rx = 0.86f)
            systemPills(null, Btn("start", "START", KeyEvent.KEYCODE_BUTTON_START, bot), cy = 0.88f, size = 0.11f)
        }
    }

    // ------------------------------------------------------------- ColecoVision

    /**
     * ColecoVision: joystick, two fire buttons, 12-key keypad. The keypad is not a garnish -
     * games use it at the title screen to choose game mode and skill level, so a pad without
     * it cannot start much of the library.
     *
     * Bindings come from gearcoleco's own input descriptors rather than guesswork: 1=Y, 2=X,
     * 3=L, 4=R, 5=L2, 6=R2, 7=L3, 8=R3, *=START, #=SELECT. Keys 0 and 9 have no RetroPad
     * button left over, so the core reads them off the left analog axes - see
     * ControllerView.COLECO_KEYPAD. The previous layout bound "1" to X and "2" to Y, which
     * are the codes for 2 and 1: the two keys most games use, swapped.
     */
    // Zone system: ColecoVision - joystick, two fire buttons, and the 12-key numeric keypad
    // as a 3x4 grid. Bindings are gearcoleco's own (kp_9 / kp_0 carry no keycode - the core
    // reads them off the left analog, see ControllerView.COLECO_KEYPAD).
    private fun coleco(): List<ControlDef> {
        val key = Color.parseColor("#2E2E33")
        val fire = Color.parseColor("#C0392B")
        return ZoneLayout.pad {
            directional(cx = 0.252f, cy = 0.32f, size = 0.45f)
            faceButton(Btn("lfire", "L", KeyEvent.KEYCODE_BUTTON_B, fire), 0.13f, 0.62f, 0.19f)
            faceButton(Btn("rfire", "R", KeyEvent.KEYCODE_BUTTON_A, fire), 0.36f, 0.62f, 0.19f)
            keypad(listOf(
                listOf(Btn("kp_1", "1", KeyEvent.KEYCODE_BUTTON_Y, key),
                       Btn("kp_2", "2", KeyEvent.KEYCODE_BUTTON_X, key),
                       Btn("kp_3", "3", KeyEvent.KEYCODE_BUTTON_L1, key)),
                listOf(Btn("kp_4", "4", KeyEvent.KEYCODE_BUTTON_R1, key),
                       Btn("kp_5", "5", KeyEvent.KEYCODE_BUTTON_L2, key),
                       Btn("kp_6", "6", KeyEvent.KEYCODE_BUTTON_R2, key)),
                listOf(Btn("kp_7", "7", KeyEvent.KEYCODE_BUTTON_THUMBL, key),
                       Btn("kp_8", "8", KeyEvent.KEYCODE_BUTTON_THUMBR, key),
                       Btn("kp_9", "9", 0, key)),
                listOf(Btn("kp_star", "*", KeyEvent.KEYCODE_BUTTON_START, key),
                       Btn("kp_0", "0", 0, key),
                       Btn("kp_hash", "#", KeyEvent.KEYCODE_BUTTON_SELECT, key)),
            ), cx0 = 0.62f, cy0 = 0.26f, colGap = 0.14f, rowGap = 0.18f, size = 0.115f)
        }
    }

    // ------------------------------------------------------------- Intellivision

    /**
     * Intellivision hand controller: 16-way disc + three side action buttons (Top/Left/Right)
     * + a 12-key numeric keypad (games overlaid printed cards on it). FreeIntv mapping:
     * Y=Top, B=Left, A=Right; keypad 1-4/6-9 -> right analog (a 3x3 disc, handled by
     * ControllerView.sendKeypad), 5=R3, 0=L3, Clear=L2, Enter=R2.
     */
    // Zone system: Intellivision - the disc, three gold side buttons, an off-centre Start, and
    // the 12-key keypad (1-4/6-9 carry no keycode: ControllerView.sendKeypad routes them to the
    // right analog as a 3x3 disc; 5/0/Clear/Enter are ordinary buttons).
    private fun intellivision(): List<ControlDef> {
        val gold = Color.parseColor("#B8860B")
        val key = Color.parseColor("#3A3A40")
        return ZoneLayout.pad {
            directional(cx = 0.252f, cy = 0.30f, size = 0.45f)
            faceButton(Btn("act_left", "L", KeyEvent.KEYCODE_BUTTON_B, gold, labelColor = DARK), 0.09f, 0.52f, 0.13f)
            faceButton(Btn("act_top", "T", KeyEvent.KEYCODE_BUTTON_Y, gold, labelColor = DARK), 0.235f, 0.52f, 0.13f)
            faceButton(Btn("act_right", "R", KeyEvent.KEYCODE_BUTTON_A, gold, labelColor = DARK), 0.38f, 0.52f, 0.13f)
            pillAt(Btn("start", "START", KeyEvent.KEYCODE_BUTTON_START, Color.parseColor("#2A2A2E")), 0.235f, 0.85f, 0.11f)
            keypad(listOf(
                listOf(Btn("kp_1", "1", 0, key), Btn("kp_2", "2", 0, key), Btn("kp_3", "3", 0, key)),
                listOf(Btn("kp_4", "4", 0, key), Btn("kp_5", "5", KeyEvent.KEYCODE_BUTTON_THUMBR, key), Btn("kp_6", "6", 0, key)),
                listOf(Btn("kp_7", "7", 0, key), Btn("kp_8", "8", 0, key), Btn("kp_9", "9", 0, key)),
                listOf(Btn("kp_clear", "C", KeyEvent.KEYCODE_BUTTON_L2, Color.parseColor("#7A2A2A")),
                       Btn("kp_0", "0", KeyEvent.KEYCODE_BUTTON_THUMBL, key),
                       Btn("kp_enter", "E", KeyEvent.KEYCODE_BUTTON_R2, Color.parseColor("#2A6A2A"))),
            ), cx0 = 0.62f, cy0 = 0.26f, colGap = 0.14f, rowGap = 0.18f, size = 0.115f)
        }
    }

    // ------------------------------------------------------------- Vectrex

    // Zone system: Vectrex - three buttons in a row (1 2 3) with a fourth (4) centred above.
    private fun vectrex(): List<ControlDef> {
        val btn = Color.parseColor("#2E7D5A")
        return ZoneLayout.pad {
            directional(cx = 0.277f, cy = 0.55f, size = 0.50f, fill = Color.parseColor("#141414"))
            faceRowN(listOf(
                Btn("b1", "1", KeyEvent.KEYCODE_BUTTON_Y, btn),
                Btn("b2", "2", KeyEvent.KEYCODE_BUTTON_B, btn),
                Btn("b3", "3", KeyEvent.KEYCODE_BUTTON_A, btn),
            ), cx = 0.735f, cy = 0.62f, gap = 0.145f, size = 0.125f)
            faceButton(Btn("b4", "4", KeyEvent.KEYCODE_BUTTON_X, btn), 0.735f, 0.42f, 0.125f)
            systemPills(null, Btn("start", "START", KeyEvent.KEYCODE_BUTTON_START, Color.parseColor("#222222")),
                cy = 0.87f)
        }
    }

    // ------------------------------------------------------------- Pokémon Mini

    // Zone system: Pokemon Mini - handheld diagonal, a single right shoulder (C), Power pill.
    private fun pokemonMini(): List<ControlDef> {
        val btn = Color.parseColor("#E0A020")
        return ZoneLayout.pad {
            directional(cx = 0.302f, cy = 0.54f, size = 0.55f, fill = Color.parseColor("#241E33"))
            faceDiag2(Btn("b", "B", KeyEvent.KEYCODE_BUTTON_B, btn, labelColor = DARK),
                      Btn("a", "A", KeyEvent.KEYCODE_BUTTON_A, btn, labelColor = DARK))
            shoulders(null, Btn("c", "C", KeyEvent.KEYCODE_BUTTON_R1, GRAY_BTN), cy = 0.10f, size = 0.16f, rx = 0.86f)
            systemPills(null, Btn("start", "POWER", KeyEvent.KEYCODE_BUTTON_START, Color.parseColor("#2A2438")))
        }
    }

    // ------------------------------------------------------------- Atari 5200

    /**
     * Atari 8-bit. START/SELECT/OPTION are real keys on the machine, so they are real buttons
     * here. KBD raises atari800's own on-screen keyboard (L3), and that button is the reason
     * this system is supported at all: everything needing typing goes through the overlay.
     */
    // Zone system: Atari 8-bit - joystick, Fire + a second fire, RET, the three console keys
    // (Start/Select/Option) as a low pill row, and KBD (L3) for atari800's on-screen keyboard.
    private fun atari8bit(): List<ControlDef> {
        val pill = Color.parseColor("#2A2A2E")
        return ZoneLayout.pad {
            directional(cx = 0.28f, cy = 0.50f, size = 0.52f, fill = Color.parseColor("#2A2018"))
            faceButton(Btn("fire1", "FIRE", KeyEvent.KEYCODE_BUTTON_B, Color.parseColor("#C8102E")), 0.87f, 0.47f, 0.22f)
            faceButton(Btn("fire2", "2", KeyEvent.KEYCODE_BUTTON_A, Color.parseColor("#8A3A2A")), 0.69f, 0.60f, 0.19f)
            faceButton(Btn("ret", "RET", KeyEvent.KEYCODE_BUTTON_X, Color.parseColor("#3A3A40")), 0.69f, 0.31f, 0.13f)
            pillAt(Btn("kbd", "KBD", KeyEvent.KEYCODE_BUTTON_THUMBL, Color.parseColor("#C8102E")), 0.87f, 0.22f, 0.13f)
            pillAt(Btn("start", "START", KeyEvent.KEYCODE_BUTTON_START, pill), 0.20f, 0.87f, 0.11f)
            pillAt(Btn("select", "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT, pill), 0.40f, 0.87f, 0.11f)
            pillAt(Btn("option", "OPTION", KeyEvent.KEYCODE_BUTTON_L1, pill), 0.60f, 0.87f, 0.11f)
        }
    }

    // Zone system: Atari 5200 - joystick, two fire buttons on the diagonal, Start/Pause pills,
    // and KEYS (R3) which raises atari800's on-screen keyboard for the 12-key keypad.
    private fun atari5200(): List<ControlDef> {
        val fire = Color.parseColor("#D24A2C")
        return ZoneLayout.pad {
            directional(cx = 0.302f, cy = 0.54f, size = 0.55f)
            faceDiag2(Btn("fire2", "2", KeyEvent.KEYCODE_BUTTON_A, fire),
                      Btn("fire1", "1", KeyEvent.KEYCODE_BUTTON_B, fire))
            pillAt(Btn("kbd", "KEYS", KeyEvent.KEYCODE_BUTTON_THUMBR, Color.parseColor("#3A3A40")), 0.53f, 0.30f, 0.12f)
            pillPair(Btn("start", "START", KeyEvent.KEYCODE_BUTTON_START, Color.parseColor("#2A2A2E")),
                     Btn("pause", "PAUSE", KeyEvent.KEYCODE_BUTTON_SELECT, Color.parseColor("#2A2A2E")),
                     cy = 0.84f, size = 0.11f, gap = 0.26f)
        }
    }

    // ------------------------------------------------------------- Arcade / Neo Geo

    /** Arcade: D-pad, six buttons (SF layout), Coin (=Select), Start. */

    // Zone system: arcade - the 2x3 punch/kick cluster as two shallow diagonal rows (LP MP HP
    // over LK MK HK), Coin/Start pills.
    private fun arcade(): List<ControlDef> {
        val punch = Color.parseColor("#2E86C1")
        val kick = Color.parseColor("#E67E22")
        val face = 0.13f
        return ZoneLayout.pad {
            directional(cx = 0.287f, cy = 0.55f, size = 0.52f)
            faceDiagonal(listOf(
                Btn("lp", "LP", KeyEvent.KEYCODE_BUTTON_Y, punch),
                Btn("mp", "MP", KeyEvent.KEYCODE_BUTTON_X, punch),
                Btn("hp", "HP", KeyEvent.KEYCODE_BUTTON_L1, punch),
            ), cx = 0.77f, cy = 0.377f, spread = 0.30f, size = face, slope = 0.27f)
            faceDiagonal(listOf(
                Btn("lk", "LK", KeyEvent.KEYCODE_BUTTON_B, kick),
                Btn("mk", "MK", KeyEvent.KEYCODE_BUTTON_A, kick),
                Btn("hk", "HK", KeyEvent.KEYCODE_BUTTON_R1, kick),
            ), cx = 0.77f, cy = 0.597f, spread = 0.30f, size = face, slope = 0.27f)
            systemPills(
                Btn("coin", "COIN", KeyEvent.KEYCODE_BUTTON_SELECT, Color.parseColor("#2A2A2E")),
                Btn("start", "START", KeyEvent.KEYCODE_BUTTON_START, Color.parseColor("#2A2A2E")),
                cy = 0.88f, size = 0.10f, gap = 0.25f,
            )
        }
    }

    // ---------------------------------------------- Master System / Game Gear

    /** Two-button pad (1 / 2) + Start/Pause. genesis_plus_gx maps SMS "1"→B, "2"→A. */
    // Zone system: Master System / Game Gear - two-button row (1,2), a lone Start pill.
    private fun sms(): List<ControlDef> {
        val btn = Color.parseColor("#B02525")
        return ZoneLayout.pad {
            directional(cx = 0.277f, cy = 0.54f, size = 0.50f)
            faceRow2(Btn("b1", "1", KeyEvent.KEYCODE_BUTTON_B, btn),
                     Btn("b2", "2", KeyEvent.KEYCODE_BUTTON_A, btn),
                     cx = 0.76f, cy = 0.55f, gap = 0.22f, size = 0.20f)
            systemPills(null, Btn("start", "START", KeyEvent.KEYCODE_BUTTON_START, Color.parseColor("#2A2A2E")),
                cy = 0.87f)
        }
    }

    // ---------------------------------------------- Neo Geo (CD)

    /** Neo Geo four-button pad A/B/C/D in the classic arc. neocd maps A→B, B→A, C→Y, D→X. */
    // Zone system: Neo Geo CD - four buttons (A B C D) on one rising diagonal, Select/Start.
    private fun neogeo(): List<ControlDef> = ZoneLayout.pad {
        directional(cx = 0.257f, cy = 0.55f, size = 0.46f)
        faceDiagonal(listOf(
            Btn("ng_a", "A", KeyEvent.KEYCODE_BUTTON_B, Color.parseColor("#C0392B")),
            Btn("ng_b", "B", KeyEvent.KEYCODE_BUTTON_A, Color.parseColor("#E4C000"), labelColor = DARK),
            Btn("ng_c", "C", KeyEvent.KEYCODE_BUTTON_Y, Color.parseColor("#27AE60")),
            Btn("ng_d", "D", KeyEvent.KEYCODE_BUTTON_X, Color.parseColor("#2980B9")),
        ), cx = 0.73f, cy = 0.54f, spread = 0.36f, size = 0.155f)
        systemPills(
            Btn("select", "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT, Color.parseColor("#2A2A2E")),
            Btn("start", "START", KeyEvent.KEYCODE_BUTTON_START, Color.parseColor("#2A2A2E")),
            cy = 0.88f, size = 0.10f,
        )
    }

    // ---------------------------------------------- Home computers (joystick)


    // ------------------------------------------------------------- Dreamcast

    /**
     * Dreamcast: D-pad, one analog stick, four face buttons (Y top, X left, B right,
     * A bottom), analog L/R triggers, and Start. Flycast reads A/B/X/Y directly and
     * the triggers as L2/R2.
     */
    // Zone system: Dreamcast - one stick, L/R triggers, the Y/X/B/A diamond, orange Start.
    private fun dreamcast(): List<ControlDef> {
        val face = 0.14f
        val btn = Color.parseColor("#3A3A40")
        return ZoneLayout.pad {
            directional(cx = 0.287f, cy = 0.38f, size = 0.52f, fill = Color.parseColor("#2A2A2E"))
            stick("stick_l", 0.29f, 0.78f, 0.44f)
            shoulders(Btn("l", "L", KeyEvent.KEYCODE_BUTTON_L2, GRAY_BTN),
                      Btn("r", "R", KeyEvent.KEYCODE_BUTTON_R2, GRAY_BTN),
                      cy = 0.06f, size = 0.17f)
            systemPills(null, Btn("start", "START", KeyEvent.KEYCODE_BUTTON_START,
                Color.parseColor("#F17022"), labelColor = DARK), cy = 0.11f, size = 0.12f)
            faceDiamond4(
                Btn("y", "Y", KeyEvent.KEYCODE_BUTTON_Y, btn),
                Btn("b", "B", KeyEvent.KEYCODE_BUTTON_B, btn),
                Btn("a", "A", KeyEvent.KEYCODE_BUTTON_A, btn),
                Btn("x", "X", KeyEvent.KEYCODE_BUTTON_X, btn),
                cx = 0.81f, cy = 0.52f, hx = 0.11f, vy = 0.08f, size = face,
            )
        }
    }

    // ------------------------------------------------------------- Neo Geo Pocket

    /** NGP: D-pad plus A, B, and Option (mapped to Start). */
    // Zone system: Neo Geo Pocket - handheld diagonal, single Option pill.
    private fun ngp(): List<ControlDef> {
        val btn = Color.parseColor("#D24726")
        return ZoneLayout.pad {
            directional(cx = 0.302f, cy = 0.54f, size = 0.55f)
            faceDiag2(Btn("b", "B", KeyEvent.KEYCODE_BUTTON_B, btn),
                      Btn("a", "A", KeyEvent.KEYCODE_BUTTON_A, btn))
            systemPills(null, Btn("option", "OPTION", KeyEvent.KEYCODE_BUTTON_START, Color.parseColor("#2A2A2E")),
                cy = 0.84f)
        }
    }
}
