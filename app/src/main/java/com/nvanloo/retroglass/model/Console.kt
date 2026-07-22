package com.nvanloo.retroglass.model

import android.graphics.Color
import android.view.KeyEvent
import com.nvanloo.retroglass.controller.ControlDef
import com.nvanloo.retroglass.controller.ControlShape
import com.nvanloo.retroglass.controller.ControlType

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
        INTELLIVISION -> 1979
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
        LYNX, ATARI2600, ATARI5200, ATARI7800 -> "Atari"
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

    /** All layouts available for a console. First entry is the default. */
    fun presetsFor(console: Console): List<LayoutPreset> {
        val base = baseControls(console)
        val presets = mutableListOf(
            LayoutPreset("default", "Default", base),
            LayoutPreset("large", "Large buttons", scaled(base, 1.28f)),
            LayoutPreset("compact", "Compact", scaled(base, 0.82f)),
            LayoutPreset("wide", "Wide (edges)", widened(base)),
            LayoutPreset("bottom", "Bottom-heavy", lowered(base)),
            LayoutPreset("lefty", "Left-handed", mirrored(base)),
            LayoutPreset("fullscreen", "Full-screen", fullscreen(console)),
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
    private fun scaled(controls: List<ControlDef>, factor: Float): List<ControlDef> {
        val spread = 1f + (factor - 1f) * 0.9f
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

    /** Pushes controls toward the screen edges to use more space. */
    private fun spreadToEdges(controls: List<ControlDef>): List<ControlDef> =
        controls.map {
            val nx = 0.5f + (it.x - 0.5f) * 1.18f
            val ny = 0.5f + (it.y - 0.5f) * 1.12f
            it.copy(x = nx.coerceIn(0.08f, 0.92f), y = ny.coerceIn(0.08f, 0.92f))
        }

    // ------------------------------------------------------------- NES

    private fun nes(): List<ControlDef> = listOf(
        ControlDef("dpad", ControlType.DPAD, "", x = 0.257f, y = 0.52f, size = 0.46f,
            shape = ControlShape.PSX_CROSS,
            fillColor = Color.parseColor("#1C1C1E"), labelColor = LIGHT_TEXT),
        ControlDef("select", ControlType.BUTTON, "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT,
            x = 0.38f, y = 0.86f, size = 0.12f, shape = ControlShape.PILL,
            fillColor = Color.parseColor("#1C1C1E"), labelColor = Color.parseColor("#B02525")),
        ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
            x = 0.62f, y = 0.86f, size = 0.12f, shape = ControlShape.PILL,
            fillColor = Color.parseColor("#1C1C1E"), labelColor = Color.parseColor("#B02525")),
        ControlDef("b", ControlType.BUTTON, "B", KeyEvent.KEYCODE_BUTTON_B,
            x = 0.625f, y = 0.52f, size = 0.19f, shape = ControlShape.CIRCLE,
            fillColor = Color.parseColor("#B02525"), labelColor = LIGHT_TEXT, plateColor = DARK),
        ControlDef("a", ControlType.BUTTON, "A", KeyEvent.KEYCODE_BUTTON_A,
            x = 0.872f, y = 0.52f, size = 0.19f, shape = ControlShape.CIRCLE,
            fillColor = Color.parseColor("#B02525"), labelColor = LIGHT_TEXT, plateColor = DARK),
    )

    // ------------------------------------------------------------- SNES

    private fun snes(): List<ControlDef> {
        val face = 0.18f
        return listOf(
            ControlDef("dpad", ControlType.DPAD, "", x = 0.262f, y = 0.50f, size = 0.47f,
                shape = ControlShape.PSX_CROSS,
                fillColor = Color.parseColor("#3A3A3E"), labelColor = LIGHT_TEXT),
            ControlDef("l", ControlType.BUTTON, "L", KeyEvent.KEYCODE_BUTTON_L1,
                x = 0.15f, y = 0.09f, size = 0.18f, shape = ControlShape.BAR,
                fillColor = Color.parseColor("#8D8A92"), labelColor = DARK),
            ControlDef("r", ControlType.BUTTON, "R", KeyEvent.KEYCODE_BUTTON_R1,
                x = 0.85f, y = 0.09f, size = 0.18f, shape = ControlShape.BAR,
                fillColor = Color.parseColor("#8D8A92"), labelColor = DARK),
            ControlDef("select", ControlType.BUTTON, "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT,
                x = 0.38f, y = 0.855f, size = 0.115f, shape = ControlShape.PILL,
                fillColor = Color.parseColor("#3A3A3E"), labelColor = LIGHT_TEXT),
            ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
                x = 0.62f, y = 0.835f, size = 0.115f, shape = ControlShape.PILL,
                fillColor = Color.parseColor("#3A3A3E"), labelColor = LIGHT_TEXT),
            // Symmetric diamond: X top / B bottom on the centre line, Y left / A right on
            // the vertical mid-point (0.50) so all four are equidistant from the centre.
            ControlDef("x", ControlType.BUTTON, "X", KeyEvent.KEYCODE_BUTTON_X,
                x = 0.75f, y = 0.40f, size = face, shape = ControlShape.CIRCLE,
                fillColor = Color.parseColor("#3F51B5"), labelColor = LIGHT_TEXT),
            ControlDef("a", ControlType.BUTTON, "A", KeyEvent.KEYCODE_BUTTON_A,
                x = 0.89f, y = 0.50f, size = face, shape = ControlShape.CIRCLE,
                fillColor = Color.parseColor("#D32F2F"), labelColor = LIGHT_TEXT),
            ControlDef("b", ControlType.BUTTON, "B", KeyEvent.KEYCODE_BUTTON_B,
                x = 0.75f, y = 0.60f, size = face, shape = ControlShape.CIRCLE,
                fillColor = Color.parseColor("#F9A825"), labelColor = DARK),
            ControlDef("y", ControlType.BUTTON, "Y", KeyEvent.KEYCODE_BUTTON_Y,
                x = 0.61f, y = 0.50f, size = face, shape = ControlShape.CIRCLE,
                fillColor = Color.parseColor("#388E3C"), labelColor = LIGHT_TEXT),
        )
    }

    // ------------------------------------------------------------- Mega Drive

    private fun megadrive(): List<ControlDef> = listOf(
        ControlDef("dpad", ControlType.DPAD, "", x = 0.282f, y = 0.55f, size = 0.51f,
            shape = ControlShape.PSX_CROSS,
            fillColor = Color.parseColor("#0E0E10"), labelColor = LIGHT_TEXT),
        ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
            x = 0.50f, y = 0.86f, size = 0.13f, shape = ControlShape.PILL,
            fillColor = Color.parseColor("#2E2E33"), labelColor = LIGHT_TEXT),
        // A/B/C arc — spaced so the circles clear each other and C stays on-screen.
        ControlDef("a", ControlType.BUTTON, "A", KeyEvent.KEYCODE_BUTTON_Y,
            x = 0.59f, y = 0.68f, size = 0.17f, shape = ControlShape.CIRCLE,
            fillColor = Color.parseColor("#2E2E33"), labelColor = LIGHT_TEXT, strokeColor = Color.parseColor("#C62828")),
        ControlDef("b", ControlType.BUTTON, "B", KeyEvent.KEYCODE_BUTTON_B,
            x = 0.70f, y = 0.57f, size = 0.17f, shape = ControlShape.CIRCLE,
            fillColor = Color.parseColor("#2E2E33"), labelColor = LIGHT_TEXT, strokeColor = Color.parseColor("#C62828")),
        ControlDef("c", ControlType.BUTTON, "C", KeyEvent.KEYCODE_BUTTON_A,
            x = 0.83f, y = 0.47f, size = 0.17f, shape = ControlShape.CIRCLE,
            fillColor = Color.parseColor("#2E2E33"), labelColor = LIGHT_TEXT, strokeColor = Color.parseColor("#C62828")),
    )

    // ------------------------------------------------------------- PlayStation

    /** Compact PS1 layout. Face cluster sits clear of the shoulder row. */
    private fun psx(): List<ControlDef> {
        val face = 0.16f
        return listOf(
            ControlDef("dpad", ControlType.DPAD, "", x = 0.257f, y = 0.42f, size = 0.46f,
                shape = ControlShape.PSX_CROSS,
                fillColor = GRAY_BTN, labelColor = LIGHT_TEXT),
            // Shoulder bars pushed out to the screen edges.
            ControlDef("l1", ControlType.BUTTON, "L1", KeyEvent.KEYCODE_BUTTON_L1,
                x = 0.15f, y = 0.055f, size = 0.20f, shape = ControlShape.BAR,
                fillColor = GRAY_BTN, labelColor = LIGHT_TEXT),
            ControlDef("l2", ControlType.BUTTON, "L2", KeyEvent.KEYCODE_BUTTON_L2,
                x = 0.15f, y = 0.175f, size = 0.20f, shape = ControlShape.BAR,
                fillColor = GRAY_BTN, labelColor = LIGHT_TEXT),
            ControlDef("r1", ControlType.BUTTON, "R1", KeyEvent.KEYCODE_BUTTON_R1,
                x = 0.85f, y = 0.055f, size = 0.20f, shape = ControlShape.BAR,
                fillColor = GRAY_BTN, labelColor = LIGHT_TEXT),
            ControlDef("r2", ControlType.BUTTON, "R2", KeyEvent.KEYCODE_BUTTON_R2,
                x = 0.85f, y = 0.175f, size = 0.20f, shape = ControlShape.BAR,
                fillColor = GRAY_BTN, labelColor = LIGHT_TEXT),
            ControlDef("select", ControlType.BUTTON, "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT,
                x = 0.39f, y = 0.11f, size = 0.10f, shape = ControlShape.PILL,
                fillColor = SYMBOL, labelColor = LIGHT_TEXT),
            ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
                x = 0.61f, y = 0.11f, size = 0.10f, shape = ControlShape.PILL,
                fillColor = SYMBOL, labelColor = LIGHT_TEXT),
            // Shape diamond, widened so the buttons don't touch.
            ControlDef("triangle", ControlType.BUTTON, "△", KeyEvent.KEYCODE_BUTTON_X,
                x = 0.73f, y = 0.325f, size = face, shape = ControlShape.CIRCLE,
                fillColor = SYMBOL, labelColor = Color.parseColor("#26B57A")),
            ControlDef("circle", ControlType.BUTTON, "○", KeyEvent.KEYCODE_BUTTON_A,
                x = 0.87f, y = 0.42f, size = face, shape = ControlShape.CIRCLE,
                fillColor = SYMBOL, labelColor = Color.parseColor("#E4574C")),
            ControlDef("cross", ControlType.BUTTON, "✕", KeyEvent.KEYCODE_BUTTON_B,
                x = 0.73f, y = 0.515f, size = face, shape = ControlShape.CIRCLE,
                fillColor = SYMBOL, labelColor = Color.parseColor("#7BA4D9")),
            ControlDef("square", ControlType.BUTTON, "□", KeyEvent.KEYCODE_BUTTON_Y,
                x = 0.59f, y = 0.42f, size = face, shape = ControlShape.CIRCLE,
                fillColor = SYMBOL, labelColor = Color.parseColor("#D992BC")),
            // Twin sticks, moved apart and a little lower.
            ControlDef("stick_l", ControlType.STICK, "L", x = 0.33f, y = 0.82f, size = 0.26f,
                shape = ControlShape.STICK,
                fillColor = Color.parseColor("#3A3A41"), labelColor = LIGHT_TEXT),
            ControlDef("stick_r", ControlType.STICK, "R", x = 0.67f, y = 0.82f, size = 0.26f,
                shape = ControlShape.STICK,
                fillColor = Color.parseColor("#3A3A41"), labelColor = LIGHT_TEXT),
        )
    }

    /**
     * Big PS1 layout that fills the phone: full-width shoulder bars stacked at the
     * top, a large D-pad bottom-left, large face cluster bottom-right, twin sticks
     * in the middle, SELECT/START between the shoulders. Matches the reference sketch.
     */
    private fun psxFullscreen(): List<ControlDef> {
        val face = 0.20f
        return listOf(
            // Shoulder bars — each spans roughly half the width.
            ControlDef("l1", ControlType.BUTTON, "L1", KeyEvent.KEYCODE_BUTTON_L1,
                x = 0.245f, y = 0.05f, size = 0.46f, shape = ControlShape.BAR,
                fillColor = GRAY_BTN, labelColor = LIGHT_TEXT),
            ControlDef("r1", ControlType.BUTTON, "R1", KeyEvent.KEYCODE_BUTTON_R1,
                x = 0.755f, y = 0.05f, size = 0.46f, shape = ControlShape.BAR,
                fillColor = GRAY_BTN, labelColor = LIGHT_TEXT),
            ControlDef("l2", ControlType.BUTTON, "L2", KeyEvent.KEYCODE_BUTTON_L2,
                x = 0.245f, y = 0.185f, size = 0.46f, shape = ControlShape.BAR,
                fillColor = GRAY_BTN, labelColor = LIGHT_TEXT),
            ControlDef("r2", ControlType.BUTTON, "R2", KeyEvent.KEYCODE_BUTTON_R2,
                x = 0.755f, y = 0.185f, size = 0.46f, shape = ControlShape.BAR,
                fillColor = GRAY_BTN, labelColor = LIGHT_TEXT),
            ControlDef("select", ControlType.BUTTON, "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT,
                x = 0.38f, y = 0.34f, size = 0.11f, shape = ControlShape.PILL,
                fillColor = SYMBOL, labelColor = LIGHT_TEXT),
            ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
                x = 0.62f, y = 0.34f, size = 0.11f, shape = ControlShape.PILL,
                fillColor = SYMBOL, labelColor = LIGHT_TEXT),
            // Big D-pad bottom-left.
            ControlDef("dpad", ControlType.DPAD, "", x = 0.15f, y = 0.66f, size = 0.66f,
                shape = ControlShape.PSX_CROSS,
                fillColor = GRAY_BTN, labelColor = LIGHT_TEXT),
            // Big face cluster bottom-right.
            ControlDef("triangle", ControlType.BUTTON, "△", KeyEvent.KEYCODE_BUTTON_X,
                x = 0.85f, y = 0.47f, size = face, shape = ControlShape.CIRCLE,
                fillColor = SYMBOL, labelColor = Color.parseColor("#26B57A")),
            ControlDef("circle", ControlType.BUTTON, "○", KeyEvent.KEYCODE_BUTTON_A,
                x = 0.95f, y = 0.66f, size = face, shape = ControlShape.CIRCLE,
                fillColor = SYMBOL, labelColor = Color.parseColor("#E4574C")),
            ControlDef("cross", ControlType.BUTTON, "✕", KeyEvent.KEYCODE_BUTTON_B,
                x = 0.85f, y = 0.85f, size = face, shape = ControlShape.CIRCLE,
                fillColor = SYMBOL, labelColor = Color.parseColor("#7BA4D9")),
            ControlDef("square", ControlType.BUTTON, "□", KeyEvent.KEYCODE_BUTTON_Y,
                x = 0.75f, y = 0.66f, size = face, shape = ControlShape.CIRCLE,
                fillColor = SYMBOL, labelColor = Color.parseColor("#D992BC")),
            // Twin sticks in the middle.
            ControlDef("stick_l", ControlType.STICK, "L", x = 0.40f, y = 0.66f, size = 0.34f,
                shape = ControlShape.STICK,
                fillColor = Color.parseColor("#3A3A41"), labelColor = LIGHT_TEXT),
            ControlDef("stick_r", ControlType.STICK, "R", x = 0.60f, y = 0.66f, size = 0.34f,
                shape = ControlShape.STICK,
                fillColor = Color.parseColor("#3A3A41"), labelColor = LIGHT_TEXT),
        )
    }

    // ------------------------------------------------------------- Game Boy

    private fun gameboy(): List<ControlDef> {
        val body = Color.parseColor("#5A3A7A")
        return listOf(
            ControlDef("dpad", ControlType.DPAD, "", x = 0.29f, y = 0.50f, size = 0.50f,
                shape = ControlShape.PSX_CROSS,
                fillColor = Color.parseColor("#1C1C1E"), labelColor = LIGHT_TEXT),
            ControlDef("select", ControlType.BUTTON, "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT,
                x = 0.38f, y = 0.82f, size = 0.12f, shape = ControlShape.PILL,
                fillColor = body, labelColor = LIGHT_TEXT),
            ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
                x = 0.62f, y = 0.82f, size = 0.12f, shape = ControlShape.PILL,
                fillColor = body, labelColor = LIGHT_TEXT),
            ControlDef("b", ControlType.BUTTON, "B", KeyEvent.KEYCODE_BUTTON_B,
                x = 0.68f, y = 0.58f, size = 0.24f, shape = ControlShape.CIRCLE,
                fillColor = Color.parseColor("#7B3F97"), labelColor = LIGHT_TEXT),
            ControlDef("a", ControlType.BUTTON, "A", KeyEvent.KEYCODE_BUTTON_A,
                x = 0.87f, y = 0.46f, size = 0.24f, shape = ControlShape.CIRCLE,
                fillColor = Color.parseColor("#7B3F97"), labelColor = LIGHT_TEXT),
        )
    }

    // ------------------------------------------------------------- Game Boy Advance

    private fun gba(): List<ControlDef> = listOf(
        ControlDef("dpad", ControlType.DPAD, "", x = 0.28f, y = 0.56f, size = 0.50f,
            shape = ControlShape.PSX_CROSS,
            fillColor = Color.parseColor("#26243A"), labelColor = LIGHT_TEXT),
        ControlDef("l", ControlType.BUTTON, "L", KeyEvent.KEYCODE_BUTTON_L1,
            x = 0.20f, y = 0.10f, size = 0.17f, shape = ControlShape.BAR,
            fillColor = Color.parseColor("#4A3E82"), labelColor = LIGHT_TEXT),
        ControlDef("r", ControlType.BUTTON, "R", KeyEvent.KEYCODE_BUTTON_R1,
            x = 0.80f, y = 0.10f, size = 0.17f, shape = ControlShape.BAR,
            fillColor = Color.parseColor("#4A3E82"), labelColor = LIGHT_TEXT),
        ControlDef("select", ControlType.BUTTON, "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT,
            x = 0.37f, y = 0.84f, size = 0.115f, shape = ControlShape.PILL,
            fillColor = Color.parseColor("#2E2C45"), labelColor = LIGHT_TEXT),
        ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
            x = 0.63f, y = 0.84f, size = 0.115f, shape = ControlShape.PILL,
            fillColor = Color.parseColor("#2E2C45"), labelColor = LIGHT_TEXT),
        // Real GBA A/B sit on a gentle upward slant (A higher-right than B).
        ControlDef("b", ControlType.BUTTON, "B", KeyEvent.KEYCODE_BUTTON_B,
            x = 0.66f, y = 0.545f, size = 0.20f, shape = ControlShape.CIRCLE,
            fillColor = Color.parseColor("#8B7FD4"), labelColor = DARK),
        ControlDef("a", ControlType.BUTTON, "A", KeyEvent.KEYCODE_BUTTON_A,
            x = 0.87f, y = 0.455f, size = 0.20f, shape = ControlShape.CIRCLE,
            fillColor = Color.parseColor("#8B7FD4"), labelColor = DARK),
    )

    // ------------------------------------------------------------- PC Engine

    private fun pcengine(): List<ControlDef> = listOf(
        ControlDef("dpad", ControlType.DPAD, "", x = 0.27f, y = 0.47f, size = 0.48f,
            shape = ControlShape.PSX_CROSS,
            fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
        ControlDef("select", ControlType.BUTTON, "SEL", KeyEvent.KEYCODE_BUTTON_SELECT,
            x = 0.38f, y = 0.82f, size = 0.11f, shape = ControlShape.PILL,
            fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
        ControlDef("run", ControlType.BUTTON, "RUN", KeyEvent.KEYCODE_BUTTON_START,
            x = 0.62f, y = 0.82f, size = 0.11f, shape = ControlShape.PILL,
            fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
        ControlDef("two", ControlType.BUTTON, "II", KeyEvent.KEYCODE_BUTTON_A,
            x = 0.63f, y = 0.47f, size = 0.20f, shape = ControlShape.CIRCLE,
            fillColor = Color.parseColor("#E67E22"), labelColor = DARK),
        ControlDef("one", ControlType.BUTTON, "I", KeyEvent.KEYCODE_BUTTON_B,
            x = 0.85f, y = 0.47f, size = 0.20f, shape = ControlShape.CIRCLE,
            fillColor = Color.parseColor("#E67E22"), labelColor = DARK),
    )

    // ------------------------------------------------------------- Nintendo 64

    /**
     * N64 mapping (mupen64plus_next default RetroPad): A=A, B=B, Start=Start, L=L, R=R,
     * Z=L2, the single analog stick = left analog, and the four discrete yellow C-buttons
     * (ids "c_*") = the right analog (handled in ControllerView.sendCButtons). This mirrors
     * the physical pad: one centred stick, D-pad upper-left, big A + B, small C diamond.
     */
    private fun n64(): List<ControlDef> {
        val yellow = Color.parseColor("#E8B800")
        val cSize = 0.115f
        return listOf(
            ControlDef("l", ControlType.BUTTON, "L", KeyEvent.KEYCODE_BUTTON_L1,
                x = 0.13f, y = 0.06f, size = 0.18f, shape = ControlShape.BAR,
                fillColor = GRAY_BTN, labelColor = LIGHT_TEXT),
            ControlDef("r", ControlType.BUTTON, "R", KeyEvent.KEYCODE_BUTTON_R1,
                x = 0.87f, y = 0.06f, size = 0.18f, shape = ControlShape.BAR,
                fillColor = GRAY_BTN, labelColor = LIGHT_TEXT),
            ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
                x = 0.50f, y = 0.06f, size = 0.12f, shape = ControlShape.PILL,
                fillColor = Color.parseColor("#C0392B"), labelColor = LIGHT_TEXT),
            ControlDef("z", ControlType.BUTTON, "Z", KeyEvent.KEYCODE_BUTTON_L2,
                x = 0.50f, y = 0.20f, size = 0.13f, shape = ControlShape.CIRCLE,
                fillColor = Color.parseColor("#2E2E33"), labelColor = LIGHT_TEXT),
            // D-pad upper-left.
            ControlDef("dpad", ControlType.DPAD, "", x = 0.227f, y = 0.33f, size = 0.40f,
                shape = ControlShape.PSX_CROSS,
                fillColor = Color.parseColor("#3A3A3E"), labelColor = LIGHT_TEXT),
            // Big B / A on the right.
            ControlDef("b", ControlType.BUTTON, "B", KeyEvent.KEYCODE_BUTTON_B,
                x = 0.58f, y = 0.54f, size = 0.17f, shape = ControlShape.CIRCLE,
                fillColor = Color.parseColor("#2E7D32"), labelColor = LIGHT_TEXT),
            ControlDef("a", ControlType.BUTTON, "A", KeyEvent.KEYCODE_BUTTON_A,
                x = 0.78f, y = 0.62f, size = 0.20f, shape = ControlShape.CIRCLE,
                fillColor = Color.parseColor("#1565C0"), labelColor = LIGHT_TEXT),
            // Four small yellow C-buttons in a diamond (drive the right analog).
            ControlDef("c_up", ControlType.BUTTON, "C", x = 0.80f, y = 0.30f, size = cSize,
                shape = ControlShape.CIRCLE, fillColor = yellow, labelColor = DARK),
            ControlDef("c_left", ControlType.BUTTON, "C", x = 0.70f, y = 0.38f, size = cSize,
                shape = ControlShape.CIRCLE, fillColor = yellow, labelColor = DARK),
            ControlDef("c_right", ControlType.BUTTON, "C", x = 0.90f, y = 0.38f, size = cSize,
                shape = ControlShape.CIRCLE, fillColor = yellow, labelColor = DARK),
            ControlDef("c_down", ControlType.BUTTON, "C", x = 0.80f, y = 0.46f, size = cSize,
                shape = ControlShape.CIRCLE, fillColor = yellow, labelColor = DARK),
            // The one analog stick, centred at the bottom.
            ControlDef("stick_l", ControlType.STICK, "", x = 0.50f, y = 0.81f, size = 0.34f,
                shape = ControlShape.STICK,
                fillColor = Color.parseColor("#3A3A41"), labelColor = LIGHT_TEXT),
        )
    }

    private fun n64Fullscreen(): List<ControlDef> = scaled(spreadToEdges(n64()), 1.15f)

    // ------------------------------------------------------------- Atari Lynx

    /** Lynx: D-pad, A, B, two Option buttons (shoulder bars), Pause = Start. */
    private fun lynx(): List<ControlDef> {
        val btn = Color.parseColor("#E8A020")
        return listOf(
            ControlDef("dpad", ControlType.DPAD, "", x = 0.302f, y = 0.55f, size = 0.55f,
                shape = ControlShape.PSX_CROSS,
                fillColor = Color.parseColor("#1C1C1E"), labelColor = LIGHT_TEXT),
            ControlDef("opt1", ControlType.BUTTON, "OPT 1", KeyEvent.KEYCODE_BUTTON_L1,
                x = 0.15f, y = 0.10f, size = 0.18f, shape = ControlShape.BAR,
                fillColor = GRAY_BTN, labelColor = LIGHT_TEXT),
            ControlDef("opt2", ControlType.BUTTON, "OPT 2", KeyEvent.KEYCODE_BUTTON_R1,
                x = 0.85f, y = 0.10f, size = 0.18f, shape = ControlShape.BAR,
                fillColor = GRAY_BTN, labelColor = LIGHT_TEXT),
            ControlDef("pause", ControlType.BUTTON, "PAUSE", KeyEvent.KEYCODE_BUTTON_START,
                x = 0.50f, y = 0.86f, size = 0.12f, shape = ControlShape.PILL,
                fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
            ControlDef("b", ControlType.BUTTON, "B", KeyEvent.KEYCODE_BUTTON_B,
                x = 0.70f, y = 0.60f, size = 0.22f, shape = ControlShape.CIRCLE,
                fillColor = btn, labelColor = DARK),
            ControlDef("a", ControlType.BUTTON, "A", KeyEvent.KEYCODE_BUTTON_A,
                x = 0.87f, y = 0.47f, size = 0.22f, shape = ControlShape.CIRCLE,
                fillColor = btn, labelColor = DARK),
        )
    }

    // ------------------------------------------------------------- Atari 2600

    /** 2600: one joystick, one Fire button, plus console Select and Reset (=Start). */
    private fun atari2600(): List<ControlDef> = listOf(
        ControlDef("dpad", ControlType.DPAD, "", x = 0.302f, y = 0.54f, size = 0.55f,
            shape = ControlShape.PSX_CROSS,
            fillColor = Color.parseColor("#1C1C1E"), labelColor = LIGHT_TEXT),
        ControlDef("select", ControlType.BUTTON, "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT,
            x = 0.37f, y = 0.84f, size = 0.115f, shape = ControlShape.PILL,
            fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
        ControlDef("reset", ControlType.BUTTON, "RESET", KeyEvent.KEYCODE_BUTTON_START,
            x = 0.63f, y = 0.84f, size = 0.115f, shape = ControlShape.PILL,
            fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
        ControlDef("fire", ControlType.BUTTON, "FIRE", KeyEvent.KEYCODE_BUTTON_B,
            x = 0.85f, y = 0.56f, size = 0.30f, shape = ControlShape.CIRCLE,
            fillColor = Color.parseColor("#D24A2C"), labelColor = LIGHT_TEXT),
    )

    // ------------------------------------------------------------- Atari 7800

    /** 7800: D-pad, two fire buttons, Select, Pause (=Start). */
    private fun atari7800(): List<ControlDef> = listOf(
        ControlDef("dpad", ControlType.DPAD, "", x = 0.302f, y = 0.54f, size = 0.55f,
            shape = ControlShape.PSX_CROSS,
            fillColor = Color.parseColor("#1C1C1E"), labelColor = LIGHT_TEXT),
        ControlDef("select", ControlType.BUTTON, "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT,
            x = 0.37f, y = 0.84f, size = 0.115f, shape = ControlShape.PILL,
            fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
        ControlDef("pause", ControlType.BUTTON, "PAUSE", KeyEvent.KEYCODE_BUTTON_START,
            x = 0.63f, y = 0.84f, size = 0.115f, shape = ControlShape.PILL,
            fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
        ControlDef("b", ControlType.BUTTON, "1", KeyEvent.KEYCODE_BUTTON_B,
            x = 0.70f, y = 0.60f, size = 0.22f, shape = ControlShape.CIRCLE,
            fillColor = Color.parseColor("#C0392B"), labelColor = LIGHT_TEXT),
        ControlDef("a", ControlType.BUTTON, "2", KeyEvent.KEYCODE_BUTTON_A,
            x = 0.87f, y = 0.47f, size = 0.22f, shape = ControlShape.CIRCLE,
            fillColor = Color.parseColor("#C0392B"), labelColor = LIGHT_TEXT),
    )

    // ------------------------------------------------------------- WonderSwan

    /** WonderSwan: D-pad (X-pad), A, B, Start. */
    private fun wonderswan(): List<ControlDef> = listOf(
        ControlDef("dpad", ControlType.DPAD, "", x = 0.302f, y = 0.54f, size = 0.55f,
            shape = ControlShape.PSX_CROSS,
            fillColor = Color.parseColor("#22222A"), labelColor = LIGHT_TEXT),
        ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
            x = 0.50f, y = 0.86f, size = 0.12f, shape = ControlShape.PILL,
            fillColor = Color.parseColor("#2E86C1"), labelColor = LIGHT_TEXT),
        ControlDef("b", ControlType.BUTTON, "B", KeyEvent.KEYCODE_BUTTON_B,
            x = 0.70f, y = 0.60f, size = 0.22f, shape = ControlShape.CIRCLE,
            fillColor = Color.parseColor("#2E86C1"), labelColor = LIGHT_TEXT),
        ControlDef("a", ControlType.BUTTON, "A", KeyEvent.KEYCODE_BUTTON_A,
            x = 0.87f, y = 0.47f, size = 0.22f, shape = ControlShape.CIRCLE,
            fillColor = Color.parseColor("#2E86C1"), labelColor = LIGHT_TEXT),
    )

    // ------------------------------------------------------------- Virtual Boy

    /** Virtual Boy: left D-pad, A, B, L/R shoulders, Select, Start. */
    private fun virtualboy(): List<ControlDef> {
        val red = Color.parseColor("#E03A3A")
        return listOf(
            ControlDef("dpad", ControlType.DPAD, "", x = 0.287f, y = 0.56f, size = 0.52f,
                shape = ControlShape.PSX_CROSS,
                fillColor = Color.parseColor("#3A1414"), labelColor = LIGHT_TEXT),
            ControlDef("l", ControlType.BUTTON, "L", KeyEvent.KEYCODE_BUTTON_L1,
                x = 0.14f, y = 0.10f, size = 0.17f, shape = ControlShape.BAR,
                fillColor = Color.parseColor("#7A2020"), labelColor = LIGHT_TEXT),
            ControlDef("r", ControlType.BUTTON, "R", KeyEvent.KEYCODE_BUTTON_R1,
                x = 0.86f, y = 0.10f, size = 0.17f, shape = ControlShape.BAR,
                fillColor = Color.parseColor("#7A2020"), labelColor = LIGHT_TEXT),
            ControlDef("select", ControlType.BUTTON, "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT,
                x = 0.37f, y = 0.85f, size = 0.11f, shape = ControlShape.PILL,
                fillColor = Color.parseColor("#3A1414"), labelColor = LIGHT_TEXT),
            ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
                x = 0.63f, y = 0.85f, size = 0.11f, shape = ControlShape.PILL,
                fillColor = Color.parseColor("#3A1414"), labelColor = LIGHT_TEXT),
            ControlDef("b", ControlType.BUTTON, "B", KeyEvent.KEYCODE_BUTTON_B,
                x = 0.70f, y = 0.60f, size = 0.22f, shape = ControlShape.CIRCLE,
                fillColor = red, labelColor = LIGHT_TEXT),
            ControlDef("a", ControlType.BUTTON, "A", KeyEvent.KEYCODE_BUTTON_A,
                x = 0.87f, y = 0.47f, size = 0.22f, shape = ControlShape.CIRCLE,
                fillColor = red, labelColor = LIGHT_TEXT),
        )
    }

    // ------------------------------------------------------------- 3DO

    private fun threedo(): List<ControlDef> {
        val btn = Color.parseColor("#3A3A3E")
        return listOf(
            ControlDef("dpad", ControlType.DPAD, "", x = 0.287f, y = 0.55f, size = 0.52f,
                shape = ControlShape.PSX_CROSS, fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
            ControlDef("l", ControlType.BUTTON, "L", KeyEvent.KEYCODE_BUTTON_L1,
                x = 0.14f, y = 0.10f, size = 0.17f, shape = ControlShape.BAR, fillColor = GRAY_BTN, labelColor = LIGHT_TEXT),
            ControlDef("r", ControlType.BUTTON, "R", KeyEvent.KEYCODE_BUTTON_R1,
                x = 0.86f, y = 0.10f, size = 0.17f, shape = ControlShape.BAR, fillColor = GRAY_BTN, labelColor = LIGHT_TEXT),
            ControlDef("p", ControlType.BUTTON, "P", KeyEvent.KEYCODE_BUTTON_START,
                x = 0.37f, y = 0.85f, size = 0.11f, shape = ControlShape.PILL, fillColor = btn, labelColor = LIGHT_TEXT),
            ControlDef("x", ControlType.BUTTON, "X", KeyEvent.KEYCODE_BUTTON_SELECT,
                x = 0.63f, y = 0.85f, size = 0.11f, shape = ControlShape.PILL, fillColor = btn, labelColor = LIGHT_TEXT),
            ControlDef("c", ControlType.BUTTON, "C", KeyEvent.KEYCODE_BUTTON_X,
                x = 0.69f, y = 0.66f, size = 0.17f, shape = ControlShape.CIRCLE, fillColor = btn, labelColor = LIGHT_TEXT),
            ControlDef("b", ControlType.BUTTON, "B", KeyEvent.KEYCODE_BUTTON_B,
                x = 0.81f, y = 0.55f, size = 0.17f, shape = ControlShape.CIRCLE, fillColor = btn, labelColor = LIGHT_TEXT),
            ControlDef("a", ControlType.BUTTON, "A", KeyEvent.KEYCODE_BUTTON_A,
                x = 0.94f, y = 0.44f, size = 0.17f, shape = ControlShape.CIRCLE, fillColor = btn, labelColor = LIGHT_TEXT),
        )
    }

    // ------------------------------------------------------------- Saturn

    /** Saturn: 6 face buttons (X Y Z / A B C), L/R shoulders, Start. */
    private fun saturn(): List<ControlDef> {
        val top = Color.parseColor("#37414F")
        val bot = Color.parseColor("#2E2E34")
        return listOf(
            ControlDef("dpad", ControlType.DPAD, "", x = 0.277f, y = 0.56f, size = 0.50f,
                shape = ControlShape.PSX_CROSS, fillColor = Color.parseColor("#22262E"), labelColor = LIGHT_TEXT),
            ControlDef("l", ControlType.BUTTON, "L", KeyEvent.KEYCODE_BUTTON_L1,
                x = 0.14f, y = 0.09f, size = 0.17f, shape = ControlShape.BAR, fillColor = GRAY_BTN, labelColor = LIGHT_TEXT),
            ControlDef("r", ControlType.BUTTON, "R", KeyEvent.KEYCODE_BUTTON_R1,
                x = 0.86f, y = 0.09f, size = 0.17f, shape = ControlShape.BAR, fillColor = GRAY_BTN, labelColor = LIGHT_TEXT),
            ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
                x = 0.50f, y = 0.88f, size = 0.11f, shape = ControlShape.PILL, fillColor = bot, labelColor = LIGHT_TEXT),
            ControlDef("x", ControlType.BUTTON, "X", KeyEvent.KEYCODE_BUTTON_Y,
                x = 0.65f, y = 0.40f, size = 0.13f, shape = ControlShape.CIRCLE, fillColor = top, labelColor = LIGHT_TEXT),
            ControlDef("y", ControlType.BUTTON, "Y", KeyEvent.KEYCODE_BUTTON_X,
                x = 0.79f, y = 0.35f, size = 0.13f, shape = ControlShape.CIRCLE, fillColor = top, labelColor = LIGHT_TEXT),
            ControlDef("z", ControlType.BUTTON, "Z", KeyEvent.KEYCODE_BUTTON_L2,
                x = 0.93f, y = 0.31f, size = 0.13f, shape = ControlShape.CIRCLE, fillColor = top, labelColor = LIGHT_TEXT),
            ControlDef("a", ControlType.BUTTON, "A", KeyEvent.KEYCODE_BUTTON_B,
                x = 0.65f, y = 0.62f, size = 0.13f, shape = ControlShape.CIRCLE, fillColor = bot, labelColor = LIGHT_TEXT),
            ControlDef("b", ControlType.BUTTON, "B", KeyEvent.KEYCODE_BUTTON_A,
                x = 0.79f, y = 0.57f, size = 0.13f, shape = ControlShape.CIRCLE, fillColor = bot, labelColor = LIGHT_TEXT),
            ControlDef("c", ControlType.BUTTON, "C", KeyEvent.KEYCODE_BUTTON_R2,
                x = 0.93f, y = 0.53f, size = 0.13f, shape = ControlShape.CIRCLE, fillColor = bot, labelColor = LIGHT_TEXT),
        )
    }

    // ------------------------------------------------------------- ColecoVision

    private fun coleco(): List<ControlDef> = listOf(
        ControlDef("dpad", ControlType.DPAD, "", x = 0.274f, y = 0.54f, size = 0.49f,
            shape = ControlShape.PSX_CROSS, fillColor = Color.parseColor("#1C1C1E"), labelColor = LIGHT_TEXT),
        ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
            x = 0.50f, y = 0.86f, size = 0.12f, shape = ControlShape.PILL, fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
        ControlDef("k1", ControlType.BUTTON, "1", KeyEvent.KEYCODE_BUTTON_X,
            x = 0.60f, y = 0.68f, size = 0.13f, shape = ControlShape.CIRCLE, fillColor = Color.parseColor("#2E2E33"), labelColor = LIGHT_TEXT),
        ControlDef("k2", ControlType.BUTTON, "2", KeyEvent.KEYCODE_BUTTON_Y,
            x = 0.60f, y = 0.45f, size = 0.13f, shape = ControlShape.CIRCLE, fillColor = Color.parseColor("#2E2E33"), labelColor = LIGHT_TEXT),
        ControlDef("lfire", ControlType.BUTTON, "L", KeyEvent.KEYCODE_BUTTON_B,
            x = 0.78f, y = 0.62f, size = 0.20f, shape = ControlShape.CIRCLE, fillColor = Color.parseColor("#C0392B"), labelColor = LIGHT_TEXT),
        ControlDef("rfire", ControlType.BUTTON, "R", KeyEvent.KEYCODE_BUTTON_A,
            x = 0.92f, y = 0.48f, size = 0.20f, shape = ControlShape.CIRCLE, fillColor = Color.parseColor("#C0392B"), labelColor = LIGHT_TEXT),
    )

    // ------------------------------------------------------------- Intellivision

    /**
     * Intellivision hand controller: 16-way disc + three side action buttons (Top/Left/Right)
     * + a 12-key numeric keypad (games overlaid printed cards on it). FreeIntv mapping:
     * Y=Top, B=Left, A=Right; keypad 1-4/6-9 -> right analog (a 3x3 disc, handled by
     * ControllerView.sendKeypad), 5=R3, 0=L3, Clear=L2, Enter=R2.
     */
    private fun intellivision(): List<ControlDef> {
        val gold = Color.parseColor("#B8860B")
        val key = Color.parseColor("#3A3A40")
        val ksz = 0.115f
        return listOf(
            ControlDef("dpad", ControlType.DPAD, "", x = 0.252f, y = 0.30f, size = 0.45f,
                shape = ControlShape.PSX_CROSS, fillColor = Color.parseColor("#1C1C1E"), labelColor = LIGHT_TEXT),
            // Three side action buttons (Top=Y, Left=B, Right=A per FreeIntv).
            ControlDef("act_left", ControlType.BUTTON, "L", KeyEvent.KEYCODE_BUTTON_B,
                x = 0.09f, y = 0.52f, size = 0.13f, shape = ControlShape.CIRCLE, fillColor = gold, labelColor = DARK),
            ControlDef("act_top", ControlType.BUTTON, "T", KeyEvent.KEYCODE_BUTTON_Y,
                x = 0.235f, y = 0.52f, size = 0.13f, shape = ControlShape.CIRCLE, fillColor = gold, labelColor = DARK),
            ControlDef("act_right", ControlType.BUTTON, "R", KeyEvent.KEYCODE_BUTTON_A,
                x = 0.38f, y = 0.52f, size = 0.13f, shape = ControlShape.CIRCLE, fillColor = gold, labelColor = DARK),
            ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
                x = 0.235f, y = 0.85f, size = 0.11f, shape = ControlShape.PILL,
                fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
            // 12-key numeric keypad. 1-4/6-9 drive the right analog (isKeypadDir); 5/0/Clear/Enter are buttons.
            ControlDef("kp_1", ControlType.BUTTON, "1", x = 0.62f, y = 0.26f, size = ksz, shape = ControlShape.CIRCLE, fillColor = key, labelColor = LIGHT_TEXT),
            ControlDef("kp_2", ControlType.BUTTON, "2", x = 0.76f, y = 0.26f, size = ksz, shape = ControlShape.CIRCLE, fillColor = key, labelColor = LIGHT_TEXT),
            ControlDef("kp_3", ControlType.BUTTON, "3", x = 0.90f, y = 0.26f, size = ksz, shape = ControlShape.CIRCLE, fillColor = key, labelColor = LIGHT_TEXT),
            ControlDef("kp_4", ControlType.BUTTON, "4", x = 0.62f, y = 0.44f, size = ksz, shape = ControlShape.CIRCLE, fillColor = key, labelColor = LIGHT_TEXT),
            ControlDef("kp_5", ControlType.BUTTON, "5", KeyEvent.KEYCODE_BUTTON_THUMBR, x = 0.76f, y = 0.44f, size = ksz, shape = ControlShape.CIRCLE, fillColor = key, labelColor = LIGHT_TEXT),
            ControlDef("kp_6", ControlType.BUTTON, "6", x = 0.90f, y = 0.44f, size = ksz, shape = ControlShape.CIRCLE, fillColor = key, labelColor = LIGHT_TEXT),
            ControlDef("kp_7", ControlType.BUTTON, "7", x = 0.62f, y = 0.62f, size = ksz, shape = ControlShape.CIRCLE, fillColor = key, labelColor = LIGHT_TEXT),
            ControlDef("kp_8", ControlType.BUTTON, "8", x = 0.76f, y = 0.62f, size = ksz, shape = ControlShape.CIRCLE, fillColor = key, labelColor = LIGHT_TEXT),
            ControlDef("kp_9", ControlType.BUTTON, "9", x = 0.90f, y = 0.62f, size = ksz, shape = ControlShape.CIRCLE, fillColor = key, labelColor = LIGHT_TEXT),
            ControlDef("kp_clear", ControlType.BUTTON, "C", KeyEvent.KEYCODE_BUTTON_L2, x = 0.62f, y = 0.80f, size = ksz, shape = ControlShape.CIRCLE, fillColor = Color.parseColor("#7A2A2A"), labelColor = LIGHT_TEXT),
            ControlDef("kp_0", ControlType.BUTTON, "0", KeyEvent.KEYCODE_BUTTON_THUMBL, x = 0.76f, y = 0.80f, size = ksz, shape = ControlShape.CIRCLE, fillColor = key, labelColor = LIGHT_TEXT),
            ControlDef("kp_enter", ControlType.BUTTON, "E", KeyEvent.KEYCODE_BUTTON_R2, x = 0.90f, y = 0.80f, size = ksz, shape = ControlShape.CIRCLE, fillColor = Color.parseColor("#2A6A2A"), labelColor = LIGHT_TEXT),
        )
    }

    // ------------------------------------------------------------- Vectrex

    private fun vectrex(): List<ControlDef> {
        val btn = Color.parseColor("#2E7D5A")
        return listOf(
            ControlDef("dpad", ControlType.DPAD, "", x = 0.277f, y = 0.55f, size = 0.50f,
                shape = ControlShape.PSX_CROSS, fillColor = Color.parseColor("#141414"), labelColor = LIGHT_TEXT),
            ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
                x = 0.50f, y = 0.87f, size = 0.12f, shape = ControlShape.PILL, fillColor = Color.parseColor("#222222"), labelColor = LIGHT_TEXT),
            ControlDef("b1", ControlType.BUTTON, "1", KeyEvent.KEYCODE_BUTTON_Y,
                x = 0.59f, y = 0.62f, size = 0.125f, shape = ControlShape.CIRCLE, fillColor = btn, labelColor = LIGHT_TEXT),
            ControlDef("b2", ControlType.BUTTON, "2", KeyEvent.KEYCODE_BUTTON_B,
                x = 0.735f, y = 0.62f, size = 0.125f, shape = ControlShape.CIRCLE, fillColor = btn, labelColor = LIGHT_TEXT),
            ControlDef("b3", ControlType.BUTTON, "3", KeyEvent.KEYCODE_BUTTON_A,
                x = 0.88f, y = 0.62f, size = 0.125f, shape = ControlShape.CIRCLE, fillColor = btn, labelColor = LIGHT_TEXT),
            ControlDef("b4", ControlType.BUTTON, "4", KeyEvent.KEYCODE_BUTTON_X,
                x = 0.735f, y = 0.42f, size = 0.125f, shape = ControlShape.CIRCLE, fillColor = btn, labelColor = LIGHT_TEXT),
        )
    }

    // ------------------------------------------------------------- Pokémon Mini

    private fun pokemonMini(): List<ControlDef> = listOf(
        ControlDef("dpad", ControlType.DPAD, "", x = 0.302f, y = 0.54f, size = 0.55f,
            shape = ControlShape.PSX_CROSS, fillColor = Color.parseColor("#241E33"), labelColor = LIGHT_TEXT),
        ControlDef("start", ControlType.BUTTON, "POWER", KeyEvent.KEYCODE_BUTTON_START,
            x = 0.50f, y = 0.86f, size = 0.12f, shape = ControlShape.PILL, fillColor = Color.parseColor("#2A2438"), labelColor = LIGHT_TEXT),
        ControlDef("c", ControlType.BUTTON, "C", KeyEvent.KEYCODE_BUTTON_R1,
            x = 0.86f, y = 0.10f, size = 0.16f, shape = ControlShape.BAR, fillColor = GRAY_BTN, labelColor = LIGHT_TEXT),
        ControlDef("b", ControlType.BUTTON, "B", KeyEvent.KEYCODE_BUTTON_B,
            x = 0.70f, y = 0.60f, size = 0.22f, shape = ControlShape.CIRCLE, fillColor = Color.parseColor("#E0A020"), labelColor = DARK),
        ControlDef("a", ControlType.BUTTON, "A", KeyEvent.KEYCODE_BUTTON_A,
            x = 0.87f, y = 0.47f, size = 0.22f, shape = ControlShape.CIRCLE, fillColor = Color.parseColor("#E0A020"), labelColor = DARK),
    )

    // ------------------------------------------------------------- Atari 5200

    private fun atari5200(): List<ControlDef> = listOf(
        ControlDef("dpad", ControlType.DPAD, "", x = 0.302f, y = 0.54f, size = 0.55f,
            shape = ControlShape.PSX_CROSS, fillColor = Color.parseColor("#1C1C1E"), labelColor = LIGHT_TEXT),
        ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
            x = 0.37f, y = 0.84f, size = 0.11f, shape = ControlShape.PILL, fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
        ControlDef("pause", ControlType.BUTTON, "PAUSE", KeyEvent.KEYCODE_BUTTON_SELECT,
            x = 0.63f, y = 0.84f, size = 0.11f, shape = ControlShape.PILL, fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
        ControlDef("fire2", ControlType.BUTTON, "2", KeyEvent.KEYCODE_BUTTON_A,
            x = 0.70f, y = 0.60f, size = 0.22f, shape = ControlShape.CIRCLE, fillColor = Color.parseColor("#D24A2C"), labelColor = LIGHT_TEXT),
        ControlDef("fire1", ControlType.BUTTON, "1", KeyEvent.KEYCODE_BUTTON_B,
            x = 0.87f, y = 0.47f, size = 0.22f, shape = ControlShape.CIRCLE, fillColor = Color.parseColor("#D24A2C"), labelColor = LIGHT_TEXT),
    )

    // ------------------------------------------------------------- Arcade / Neo Geo

    /** Arcade: D-pad, six buttons (SF layout), Coin (=Select), Start. */

    private fun arcade(): List<ControlDef> {
        val punch = Color.parseColor("#2E86C1")
        val kick = Color.parseColor("#E67E22")
        val face = 0.13f
        return listOf(
            ControlDef("dpad", ControlType.DPAD, "", x = 0.287f, y = 0.55f, size = 0.52f,
                shape = ControlShape.PSX_CROSS, fillColor = Color.parseColor("#1C1C1E"), labelColor = LIGHT_TEXT),
            ControlDef("coin", ControlType.BUTTON, "COIN", KeyEvent.KEYCODE_BUTTON_SELECT,
                x = 0.37f, y = 0.88f, size = 0.10f, shape = ControlShape.PILL,
                fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
            ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
                x = 0.62f, y = 0.88f, size = 0.10f, shape = ControlShape.PILL,
                fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
            // Top row: light/medium/heavy punch
            ControlDef("lp", ControlType.BUTTON, "LP", KeyEvent.KEYCODE_BUTTON_Y,
                x = 0.62f, y = 0.42f, size = face, shape = ControlShape.CIRCLE, fillColor = punch, labelColor = LIGHT_TEXT),
            ControlDef("mp", ControlType.BUTTON, "MP", KeyEvent.KEYCODE_BUTTON_X,
                x = 0.77f, y = 0.37f, size = face, shape = ControlShape.CIRCLE, fillColor = punch, labelColor = LIGHT_TEXT),
            ControlDef("hp", ControlType.BUTTON, "HP", KeyEvent.KEYCODE_BUTTON_L1,
                x = 0.92f, y = 0.34f, size = face, shape = ControlShape.CIRCLE, fillColor = punch, labelColor = LIGHT_TEXT),
            // Bottom row: light/medium/heavy kick
            ControlDef("lk", ControlType.BUTTON, "LK", KeyEvent.KEYCODE_BUTTON_B,
                x = 0.62f, y = 0.64f, size = face, shape = ControlShape.CIRCLE, fillColor = kick, labelColor = LIGHT_TEXT),
            ControlDef("mk", ControlType.BUTTON, "MK", KeyEvent.KEYCODE_BUTTON_A,
                x = 0.77f, y = 0.59f, size = face, shape = ControlShape.CIRCLE, fillColor = kick, labelColor = LIGHT_TEXT),
            ControlDef("hk", ControlType.BUTTON, "HK", KeyEvent.KEYCODE_BUTTON_R1,
                x = 0.92f, y = 0.56f, size = face, shape = ControlShape.CIRCLE, fillColor = kick, labelColor = LIGHT_TEXT),
        )
    }

    // ---------------------------------------------- Master System / Game Gear

    /** Two-button pad (1 / 2) + Start/Pause. genesis_plus_gx maps SMS "1"→B, "2"→A. */
    private fun sms(): List<ControlDef> {
        val btn = Color.parseColor("#B02525")
        return listOf(
            ControlDef("dpad", ControlType.DPAD, "", x = 0.277f, y = 0.54f, size = 0.50f,
                shape = ControlShape.PSX_CROSS, fillColor = Color.parseColor("#1C1C1E"), labelColor = LIGHT_TEXT),
            ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
                x = 0.50f, y = 0.87f, size = 0.12f, shape = ControlShape.PILL,
                fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
            ControlDef("b1", ControlType.BUTTON, "1", KeyEvent.KEYCODE_BUTTON_B,
                x = 0.65f, y = 0.55f, size = 0.20f, shape = ControlShape.CIRCLE, fillColor = btn, labelColor = LIGHT_TEXT),
            ControlDef("b2", ControlType.BUTTON, "2", KeyEvent.KEYCODE_BUTTON_A,
                x = 0.87f, y = 0.55f, size = 0.20f, shape = ControlShape.CIRCLE, fillColor = btn, labelColor = LIGHT_TEXT),
        )
    }

    // ---------------------------------------------- Neo Geo (CD)

    /** Neo Geo four-button pad A/B/C/D in the classic arc. neocd maps A→B, B→A, C→Y, D→X. */
    private fun neogeo(): List<ControlDef> {
        val sz = 0.155f
        return listOf(
            ControlDef("dpad", ControlType.DPAD, "", x = 0.257f, y = 0.55f, size = 0.46f,
                shape = ControlShape.PSX_CROSS, fillColor = Color.parseColor("#1C1C1E"), labelColor = LIGHT_TEXT),
            ControlDef("select", ControlType.BUTTON, "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT,
                x = 0.37f, y = 0.88f, size = 0.10f, shape = ControlShape.PILL,
                fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
            ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
                x = 0.62f, y = 0.88f, size = 0.10f, shape = ControlShape.PILL,
                fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
            ControlDef("ng_a", ControlType.BUTTON, "A", KeyEvent.KEYCODE_BUTTON_B,
                x = 0.55f, y = 0.66f, size = sz, shape = ControlShape.CIRCLE,
                fillColor = Color.parseColor("#C0392B"), labelColor = LIGHT_TEXT),
            ControlDef("ng_b", ControlType.BUTTON, "B", KeyEvent.KEYCODE_BUTTON_A,
                x = 0.67f, y = 0.58f, size = sz, shape = ControlShape.CIRCLE,
                fillColor = Color.parseColor("#E4C000"), labelColor = DARK),
            ControlDef("ng_c", ControlType.BUTTON, "C", KeyEvent.KEYCODE_BUTTON_Y,
                x = 0.79f, y = 0.50f, size = sz, shape = ControlShape.CIRCLE,
                fillColor = Color.parseColor("#27AE60"), labelColor = LIGHT_TEXT),
            ControlDef("ng_d", ControlType.BUTTON, "D", KeyEvent.KEYCODE_BUTTON_X,
                x = 0.91f, y = 0.42f, size = sz, shape = ControlShape.CIRCLE,
                fillColor = Color.parseColor("#2980B9"), labelColor = LIGHT_TEXT),
        )
    }

    // ---------------------------------------------- Home computers (joystick)


    // ------------------------------------------------------------- Dreamcast

    /**
     * Dreamcast: D-pad, one analog stick, four face buttons (Y top, X left, B right,
     * A bottom), analog L/R triggers, and Start. Flycast reads A/B/X/Y directly and
     * the triggers as L2/R2.
     */
    private fun dreamcast(): List<ControlDef> {
        val face = 0.14f
        val btn = Color.parseColor("#3A3A40")
        return listOf(
            // START moved up top so the D-pad + analog can grow.
            ControlDef("dpad", ControlType.DPAD, "", x = 0.287f, y = 0.38f, size = 0.52f,
                shape = ControlShape.PSX_CROSS,
                fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
            ControlDef("stick_l", ControlType.STICK, "", x = 0.29f, y = 0.78f, size = 0.44f,
                shape = ControlShape.STICK,
                fillColor = Color.parseColor("#3A3A41"), labelColor = LIGHT_TEXT),
            ControlDef("l", ControlType.BUTTON, "L", KeyEvent.KEYCODE_BUTTON_L2,
                x = 0.15f, y = 0.06f, size = 0.17f, shape = ControlShape.BAR,
                fillColor = GRAY_BTN, labelColor = LIGHT_TEXT),
            ControlDef("r", ControlType.BUTTON, "R", KeyEvent.KEYCODE_BUTTON_R2,
                x = 0.85f, y = 0.06f, size = 0.17f, shape = ControlShape.BAR,
                fillColor = GRAY_BTN, labelColor = LIGHT_TEXT),
            ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
                x = 0.50f, y = 0.11f, size = 0.12f, shape = ControlShape.PILL,
                fillColor = Color.parseColor("#F17022"), labelColor = DARK),
            // Even diamond: equal spacing top/bottom and left/right.
            ControlDef("y", ControlType.BUTTON, "Y", KeyEvent.KEYCODE_BUTTON_Y,
                x = 0.81f, y = 0.44f, size = face, shape = ControlShape.CIRCLE,
                fillColor = btn, labelColor = LIGHT_TEXT),
            ControlDef("x", ControlType.BUTTON, "X", KeyEvent.KEYCODE_BUTTON_X,
                x = 0.70f, y = 0.52f, size = face, shape = ControlShape.CIRCLE,
                fillColor = btn, labelColor = LIGHT_TEXT),
            ControlDef("b", ControlType.BUTTON, "B", KeyEvent.KEYCODE_BUTTON_B,
                x = 0.92f, y = 0.52f, size = face, shape = ControlShape.CIRCLE,
                fillColor = btn, labelColor = LIGHT_TEXT),
            ControlDef("a", ControlType.BUTTON, "A", KeyEvent.KEYCODE_BUTTON_A,
                x = 0.81f, y = 0.60f, size = face, shape = ControlShape.CIRCLE,
                fillColor = btn, labelColor = LIGHT_TEXT),
        )
    }

    // ------------------------------------------------------------- Neo Geo Pocket

    /** NGP: D-pad plus A, B, and Option (mapped to Start). */
    private fun ngp(): List<ControlDef> = listOf(
        ControlDef("dpad", ControlType.DPAD, "", x = 0.302f, y = 0.54f, size = 0.55f,
            shape = ControlShape.PSX_CROSS,
            fillColor = Color.parseColor("#1C1C1E"), labelColor = LIGHT_TEXT),
        ControlDef("option", ControlType.BUTTON, "OPTION", KeyEvent.KEYCODE_BUTTON_START,
            x = 0.50f, y = 0.84f, size = 0.12f, shape = ControlShape.PILL,
            fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
        ControlDef("b", ControlType.BUTTON, "B", KeyEvent.KEYCODE_BUTTON_B,
            x = 0.70f, y = 0.60f, size = 0.22f, shape = ControlShape.CIRCLE,
            fillColor = Color.parseColor("#D24726"), labelColor = LIGHT_TEXT),
        ControlDef("a", ControlType.BUTTON, "A", KeyEvent.KEYCODE_BUTTON_A,
            x = 0.87f, y = 0.47f, size = 0.22f, shape = ControlShape.CIRCLE,
            fillColor = Color.parseColor("#D24726"), labelColor = LIGHT_TEXT),
    )
}
