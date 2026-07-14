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
        romExtensions = setOf("md", "gen", "smd", "sms", "gg", "sg", "68k", "sgd"),
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
    MSX(
        displayName = "MSX",
        coreLibName = "libbluemsx.so",
        romExtensions = setOf("mx1", "mx2", "cas", "dsk"),
        bodyColor = Color.parseColor("#1A1A22"),
        accentColor = Color.parseColor("#2E86C1"),
    ),
    C64(
        displayName = "Commodore 64",
        coreLibName = "libvice_x64.so",
        romExtensions = setOf("d64", "t64", "prg", "crt", "g64"),
        bodyColor = Color.parseColor("#3A3226"),
        accentColor = Color.parseColor("#8B7355"),
    ),
    AMIGA(
        displayName = "Amiga",
        coreLibName = "libpuae.so",
        romExtensions = setOf("adf", "hdf", "lha", "ipf", "uae"),
        bodyColor = Color.parseColor("#20242A"),
        accentColor = Color.parseColor("#E64A19"),
    ),
    SPECTRUM(
        displayName = "ZX Spectrum",
        coreLibName = "libfuse.so",
        romExtensions = setOf("tzx", "tap", "z80"),
        bodyColor = Color.parseColor("#181818"),
        accentColor = Color.parseColor("#D81E5B"),
    ),
    AMSTRAD(
        displayName = "Amstrad CPC",
        coreLibName = "libcap32.so",
        romExtensions = setOf("cdt", "sna", "cpr"),
        bodyColor = Color.parseColor("#1A1A1A"),
        accentColor = Color.parseColor("#F1C40F"),
    ),
    ARCADE(
        displayName = "Arcade / Neo Geo",
        coreLibName = "libfbneo.so",
        // Arcade romsets are .zip and must be passed to the core intact (not extracted).
        romExtensions = setOf("zip"),
        bodyColor = Color.parseColor("#101014"),
        accentColor = Color.parseColor("#E74C3C"),
    );

    val prefKey: String get() = name.lowercase()

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
        return listOf(
            LayoutPreset("default", "Default", base),
            LayoutPreset("large", "Large buttons", scaled(base, 1.28f)),
            LayoutPreset("compact", "Compact", scaled(base, 0.82f)),
            LayoutPreset("wide", "Wide (edges)", widened(base)),
            LayoutPreset("bottom", "Bottom-heavy", lowered(base)),
            LayoutPreset("lefty", "Left-handed", mirrored(base)),
            LayoutPreset("fullscreen", "Full-screen", fullscreen(console)),
        )
    }

    fun defaultPresetId(console: Console): String = presetsFor(console).first().id

    fun presetOrDefault(console: Console, id: String?): LayoutPreset {
        val all = presetsFor(console)
        return all.firstOrNull { it.id == id } ?: all.first()
    }

    /** Convenience for callers that only need the factory default arrangement. */
    fun controlsFor(console: Console): List<ControlDef> = baseControls(console)

    // -------------------------------------------------------- transforms

    private fun scaled(controls: List<ControlDef>, factor: Float): List<ControlDef> =
        controls.map { it.copy(size = it.size * factor) }

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
        Console.MSX, Console.C64, Console.AMIGA, Console.SPECTRUM, Console.AMSTRAD ->
            computerJoystick(console.accentColor)
        Console.ARCADE -> arcade()
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
        ControlDef("dpad", ControlType.DPAD, "", x = 0.155f, y = 0.52f, size = 0.55f,
            shape = ControlShape.CROSS,
            fillColor = Color.parseColor("#1C1C1E"), labelColor = LIGHT_TEXT),
        ControlDef("select", ControlType.BUTTON, "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT,
            x = 0.42f, y = 0.80f, size = 0.13f, shape = ControlShape.PILL,
            fillColor = Color.parseColor("#1C1C1E"), labelColor = Color.parseColor("#B02525")),
        ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
            x = 0.58f, y = 0.80f, size = 0.13f, shape = ControlShape.PILL,
            fillColor = Color.parseColor("#1C1C1E"), labelColor = Color.parseColor("#B02525")),
        ControlDef("b", ControlType.BUTTON, "B", KeyEvent.KEYCODE_BUTTON_B,
            x = 0.74f, y = 0.60f, size = 0.26f, shape = ControlShape.CIRCLE,
            fillColor = Color.parseColor("#B02525"), labelColor = LIGHT_TEXT, plateColor = DARK),
        ControlDef("a", ControlType.BUTTON, "A", KeyEvent.KEYCODE_BUTTON_A,
            x = 0.90f, y = 0.52f, size = 0.26f, shape = ControlShape.CIRCLE,
            fillColor = Color.parseColor("#B02525"), labelColor = LIGHT_TEXT, plateColor = DARK),
    )

    // ------------------------------------------------------------- SNES

    private fun snes(): List<ControlDef> {
        val face = 0.21f
        return listOf(
            ControlDef("dpad", ControlType.DPAD, "", x = 0.15f, y = 0.56f, size = 0.52f,
                shape = ControlShape.CROSS,
                fillColor = Color.parseColor("#3A3A3E"), labelColor = LIGHT_TEXT),
            ControlDef("l", ControlType.BUTTON, "L", KeyEvent.KEYCODE_BUTTON_L1,
                x = 0.13f, y = 0.10f, size = 0.15f, shape = ControlShape.BAR,
                fillColor = Color.parseColor("#8D8A92"), labelColor = DARK),
            ControlDef("r", ControlType.BUTTON, "R", KeyEvent.KEYCODE_BUTTON_R1,
                x = 0.87f, y = 0.10f, size = 0.15f, shape = ControlShape.BAR,
                fillColor = Color.parseColor("#8D8A92"), labelColor = DARK),
            ControlDef("select", ControlType.BUTTON, "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT,
                x = 0.42f, y = 0.82f, size = 0.115f, shape = ControlShape.PILL,
                fillColor = Color.parseColor("#3A3A3E"), labelColor = LIGHT_TEXT),
            ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
                x = 0.58f, y = 0.82f, size = 0.115f, shape = ControlShape.PILL,
                fillColor = Color.parseColor("#3A3A3E"), labelColor = LIGHT_TEXT),
            ControlDef("x", ControlType.BUTTON, "X", KeyEvent.KEYCODE_BUTTON_X,
                x = 0.845f, y = 0.30f, size = face, shape = ControlShape.CIRCLE,
                fillColor = Color.parseColor("#3F51B5"), labelColor = LIGHT_TEXT),
            ControlDef("a", ControlType.BUTTON, "A", KeyEvent.KEYCODE_BUTTON_A,
                x = 0.945f, y = 0.54f, size = face, shape = ControlShape.CIRCLE,
                fillColor = Color.parseColor("#D32F2F"), labelColor = LIGHT_TEXT),
            ControlDef("b", ControlType.BUTTON, "B", KeyEvent.KEYCODE_BUTTON_B,
                x = 0.845f, y = 0.78f, size = face, shape = ControlShape.CIRCLE,
                fillColor = Color.parseColor("#F9A825"), labelColor = DARK),
            ControlDef("y", ControlType.BUTTON, "Y", KeyEvent.KEYCODE_BUTTON_Y,
                x = 0.745f, y = 0.54f, size = face, shape = ControlShape.CIRCLE,
                fillColor = Color.parseColor("#388E3C"), labelColor = LIGHT_TEXT),
        )
    }

    // ------------------------------------------------------------- Mega Drive

    private fun megadrive(): List<ControlDef> = listOf(
        ControlDef("dpad", ControlType.DPAD, "", x = 0.15f, y = 0.56f, size = 0.55f,
            shape = ControlShape.CROSS,
            fillColor = Color.parseColor("#0E0E10"), labelColor = LIGHT_TEXT),
        ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
            x = 0.50f, y = 0.82f, size = 0.13f, shape = ControlShape.PILL,
            fillColor = Color.parseColor("#2E2E33"), labelColor = LIGHT_TEXT),
        ControlDef("a", ControlType.BUTTON, "A", KeyEvent.KEYCODE_BUTTON_Y,
            x = 0.72f, y = 0.66f, size = 0.22f, shape = ControlShape.CIRCLE,
            fillColor = Color.parseColor("#2E2E33"), labelColor = LIGHT_TEXT, strokeColor = Color.parseColor("#C62828")),
        ControlDef("b", ControlType.BUTTON, "B", KeyEvent.KEYCODE_BUTTON_B,
            x = 0.83f, y = 0.55f, size = 0.22f, shape = ControlShape.CIRCLE,
            fillColor = Color.parseColor("#2E2E33"), labelColor = LIGHT_TEXT, strokeColor = Color.parseColor("#C62828")),
        ControlDef("c", ControlType.BUTTON, "C", KeyEvent.KEYCODE_BUTTON_A,
            x = 0.94f, y = 0.44f, size = 0.22f, shape = ControlShape.CIRCLE,
            fillColor = Color.parseColor("#2E2E33"), labelColor = LIGHT_TEXT, strokeColor = Color.parseColor("#C62828")),
    )

    // ------------------------------------------------------------- PlayStation

    /** Compact PS1 layout. Face cluster sits clear of the shoulder row. */
    private fun psx(): List<ControlDef> {
        val face = 0.175f
        return listOf(
            ControlDef("dpad", ControlType.DPAD, "", x = 0.14f, y = 0.46f, size = 0.46f,
                shape = ControlShape.PSX_CROSS,
                fillColor = GRAY_BTN, labelColor = LIGHT_TEXT),
            ControlDef("l1", ControlType.BUTTON, "L1", KeyEvent.KEYCODE_BUTTON_L1,
                x = 0.13f, y = 0.09f, size = 0.16f, shape = ControlShape.BAR,
                fillColor = GRAY_BTN, labelColor = LIGHT_TEXT),
            ControlDef("l2", ControlType.BUTTON, "L2", KeyEvent.KEYCODE_BUTTON_L2,
                x = 0.13f, y = 0.21f, size = 0.16f, shape = ControlShape.BAR,
                fillColor = GRAY_BTN, labelColor = LIGHT_TEXT),
            ControlDef("r1", ControlType.BUTTON, "R1", KeyEvent.KEYCODE_BUTTON_R1,
                x = 0.87f, y = 0.09f, size = 0.16f, shape = ControlShape.BAR,
                fillColor = GRAY_BTN, labelColor = LIGHT_TEXT),
            ControlDef("r2", ControlType.BUTTON, "R2", KeyEvent.KEYCODE_BUTTON_R2,
                x = 0.87f, y = 0.21f, size = 0.16f, shape = ControlShape.BAR,
                fillColor = GRAY_BTN, labelColor = LIGHT_TEXT),
            ControlDef("select", ControlType.BUTTON, "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT,
                x = 0.44f, y = 0.86f, size = 0.10f, shape = ControlShape.PILL,
                fillColor = SYMBOL, labelColor = LIGHT_TEXT),
            ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
                x = 0.56f, y = 0.86f, size = 0.10f, shape = ControlShape.PILL,
                fillColor = SYMBOL, labelColor = LIGHT_TEXT),
            ControlDef("triangle", ControlType.BUTTON, "△", KeyEvent.KEYCODE_BUTTON_X,
                x = 0.86f, y = 0.40f, size = face, shape = ControlShape.CIRCLE,
                fillColor = SYMBOL, labelColor = Color.parseColor("#26B57A")),
            ControlDef("circle", ControlType.BUTTON, "○", KeyEvent.KEYCODE_BUTTON_A,
                x = 0.955f, y = 0.57f, size = face, shape = ControlShape.CIRCLE,
                fillColor = SYMBOL, labelColor = Color.parseColor("#E4574C")),
            ControlDef("cross", ControlType.BUTTON, "✕", KeyEvent.KEYCODE_BUTTON_B,
                x = 0.86f, y = 0.74f, size = face, shape = ControlShape.CIRCLE,
                fillColor = SYMBOL, labelColor = Color.parseColor("#7BA4D9")),
            ControlDef("square", ControlType.BUTTON, "□", KeyEvent.KEYCODE_BUTTON_Y,
                x = 0.765f, y = 0.57f, size = face, shape = ControlShape.CIRCLE,
                fillColor = SYMBOL, labelColor = Color.parseColor("#D992BC")),
            ControlDef("stick_l", ControlType.STICK, "L", x = 0.37f, y = 0.62f, size = 0.30f,
                shape = ControlShape.STICK,
                fillColor = Color.parseColor("#3A3A41"), labelColor = LIGHT_TEXT),
            ControlDef("stick_r", ControlType.STICK, "R", x = 0.63f, y = 0.62f, size = 0.30f,
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
                x = 0.44f, y = 0.34f, size = 0.11f, shape = ControlShape.PILL,
                fillColor = SYMBOL, labelColor = LIGHT_TEXT),
            ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
                x = 0.56f, y = 0.34f, size = 0.11f, shape = ControlShape.PILL,
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
            ControlDef("dpad", ControlType.DPAD, "", x = 0.155f, y = 0.54f, size = 0.55f,
                shape = ControlShape.CROSS,
                fillColor = Color.parseColor("#1C1C1E"), labelColor = LIGHT_TEXT),
            ControlDef("select", ControlType.BUTTON, "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT,
                x = 0.42f, y = 0.82f, size = 0.12f, shape = ControlShape.PILL,
                fillColor = body, labelColor = LIGHT_TEXT),
            ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
                x = 0.58f, y = 0.82f, size = 0.12f, shape = ControlShape.PILL,
                fillColor = body, labelColor = LIGHT_TEXT),
            ControlDef("b", ControlType.BUTTON, "B", KeyEvent.KEYCODE_BUTTON_B,
                x = 0.76f, y = 0.62f, size = 0.24f, shape = ControlShape.CIRCLE,
                fillColor = Color.parseColor("#7B3F97"), labelColor = LIGHT_TEXT),
            ControlDef("a", ControlType.BUTTON, "A", KeyEvent.KEYCODE_BUTTON_A,
                x = 0.91f, y = 0.52f, size = 0.24f, shape = ControlShape.CIRCLE,
                fillColor = Color.parseColor("#7B3F97"), labelColor = LIGHT_TEXT),
        )
    }

    // ------------------------------------------------------------- Game Boy Advance

    private fun gba(): List<ControlDef> = listOf(
        ControlDef("dpad", ControlType.DPAD, "", x = 0.15f, y = 0.56f, size = 0.52f,
            shape = ControlShape.CROSS,
            fillColor = Color.parseColor("#26243A"), labelColor = LIGHT_TEXT),
        ControlDef("l", ControlType.BUTTON, "L", KeyEvent.KEYCODE_BUTTON_L1,
            x = 0.14f, y = 0.10f, size = 0.17f, shape = ControlShape.BAR,
            fillColor = Color.parseColor("#4A3E82"), labelColor = LIGHT_TEXT),
        ControlDef("r", ControlType.BUTTON, "R", KeyEvent.KEYCODE_BUTTON_R1,
            x = 0.86f, y = 0.10f, size = 0.17f, shape = ControlShape.BAR,
            fillColor = Color.parseColor("#4A3E82"), labelColor = LIGHT_TEXT),
        ControlDef("select", ControlType.BUTTON, "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT,
            x = 0.42f, y = 0.84f, size = 0.115f, shape = ControlShape.PILL,
            fillColor = Color.parseColor("#2E2C45"), labelColor = LIGHT_TEXT),
        ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
            x = 0.58f, y = 0.84f, size = 0.115f, shape = ControlShape.PILL,
            fillColor = Color.parseColor("#2E2C45"), labelColor = LIGHT_TEXT),
        ControlDef("b", ControlType.BUTTON, "B", KeyEvent.KEYCODE_BUTTON_B,
            x = 0.78f, y = 0.62f, size = 0.23f, shape = ControlShape.CIRCLE,
            fillColor = Color.parseColor("#8B7FD4"), labelColor = DARK),
        ControlDef("a", ControlType.BUTTON, "A", KeyEvent.KEYCODE_BUTTON_A,
            x = 0.92f, y = 0.50f, size = 0.23f, shape = ControlShape.CIRCLE,
            fillColor = Color.parseColor("#8B7FD4"), labelColor = DARK),
    )

    // ------------------------------------------------------------- PC Engine

    private fun pcengine(): List<ControlDef> = listOf(
        ControlDef("dpad", ControlType.DPAD, "", x = 0.155f, y = 0.54f, size = 0.55f,
            shape = ControlShape.CROSS,
            fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
        ControlDef("select", ControlType.BUTTON, "SEL", KeyEvent.KEYCODE_BUTTON_SELECT,
            x = 0.42f, y = 0.82f, size = 0.11f, shape = ControlShape.PILL,
            fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
        ControlDef("run", ControlType.BUTTON, "RUN", KeyEvent.KEYCODE_BUTTON_START,
            x = 0.58f, y = 0.82f, size = 0.11f, shape = ControlShape.PILL,
            fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
        // PC Engine: II = RetroPad A, I = RetroPad B (I is the primary right button).
        ControlDef("two", ControlType.BUTTON, "II", KeyEvent.KEYCODE_BUTTON_A,
            x = 0.76f, y = 0.62f, size = 0.25f, shape = ControlShape.CIRCLE,
            fillColor = Color.parseColor("#E67E22"), labelColor = DARK),
        ControlDef("one", ControlType.BUTTON, "I", KeyEvent.KEYCODE_BUTTON_B,
            x = 0.91f, y = 0.52f, size = 0.25f, shape = ControlShape.CIRCLE,
            fillColor = Color.parseColor("#E67E22"), labelColor = DARK),
    )

    // ------------------------------------------------------------- Nintendo 64

    /**
     * N64 mapping (mupen64plus_next default RetroPad): A=A, B=B, Start=Start,
     * L=L, R=R, Z=L2, analog stick = left analog, C-buttons = right analog.
     */
    private fun n64(): List<ControlDef> = listOf(
        ControlDef("dpad", ControlType.DPAD, "", x = 0.13f, y = 0.34f, size = 0.34f,
            shape = ControlShape.CROSS,
            fillColor = Color.parseColor("#3A3A3E"), labelColor = LIGHT_TEXT),
        ControlDef("stick_l", ControlType.STICK, "", x = 0.30f, y = 0.66f, size = 0.40f,
            shape = ControlShape.STICK,
            fillColor = Color.parseColor("#3A3A41"), labelColor = LIGHT_TEXT),
        ControlDef("l", ControlType.BUTTON, "L", KeyEvent.KEYCODE_BUTTON_L1,
            x = 0.14f, y = 0.07f, size = 0.16f, shape = ControlShape.BAR,
            fillColor = GRAY_BTN, labelColor = LIGHT_TEXT),
        ControlDef("r", ControlType.BUTTON, "R", KeyEvent.KEYCODE_BUTTON_R1,
            x = 0.86f, y = 0.07f, size = 0.16f, shape = ControlShape.BAR,
            fillColor = GRAY_BTN, labelColor = LIGHT_TEXT),
        ControlDef("z", ControlType.BUTTON, "Z", KeyEvent.KEYCODE_BUTTON_L2,
            x = 0.50f, y = 0.30f, size = 0.14f, shape = ControlShape.CIRCLE,
            fillColor = Color.parseColor("#2E2E33"), labelColor = LIGHT_TEXT),
        ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
            x = 0.50f, y = 0.86f, size = 0.12f, shape = ControlShape.PILL,
            fillColor = Color.parseColor("#C0392B"), labelColor = LIGHT_TEXT),
        // C-buttons cluster = right analog.
        ControlDef("cbuttons", ControlType.STICK, "C", x = 0.70f, y = 0.66f, size = 0.34f,
            shape = ControlShape.STICK,
            fillColor = Color.parseColor("#B8860B"), labelColor = DARK),
        ControlDef("b", ControlType.BUTTON, "B", KeyEvent.KEYCODE_BUTTON_B,
            x = 0.86f, y = 0.44f, size = 0.19f, shape = ControlShape.CIRCLE,
            fillColor = Color.parseColor("#2E7D32"), labelColor = LIGHT_TEXT),
        ControlDef("a", ControlType.BUTTON, "A", KeyEvent.KEYCODE_BUTTON_A,
            x = 0.95f, y = 0.58f, size = 0.21f, shape = ControlShape.CIRCLE,
            fillColor = Color.parseColor("#1565C0"), labelColor = LIGHT_TEXT),
    )

    private fun n64Fullscreen(): List<ControlDef> = scaled(spreadToEdges(n64()), 1.15f)

    // ------------------------------------------------------------- Atari Lynx

    /** Lynx: D-pad, A, B, two Option buttons (shoulder bars), Pause = Start. */
    private fun lynx(): List<ControlDef> {
        val btn = Color.parseColor("#E8A020")
        return listOf(
            ControlDef("dpad", ControlType.DPAD, "", x = 0.155f, y = 0.55f, size = 0.55f,
                shape = ControlShape.CROSS,
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
                x = 0.77f, y = 0.62f, size = 0.24f, shape = ControlShape.CIRCLE,
                fillColor = btn, labelColor = DARK),
            ControlDef("a", ControlType.BUTTON, "A", KeyEvent.KEYCODE_BUTTON_A,
                x = 0.91f, y = 0.52f, size = 0.24f, shape = ControlShape.CIRCLE,
                fillColor = btn, labelColor = DARK),
        )
    }

    // ------------------------------------------------------------- Atari 2600

    /** 2600: one joystick, one Fire button, plus console Select and Reset (=Start). */
    private fun atari2600(): List<ControlDef> = listOf(
        ControlDef("dpad", ControlType.DPAD, "", x = 0.155f, y = 0.54f, size = 0.55f,
            shape = ControlShape.CROSS,
            fillColor = Color.parseColor("#1C1C1E"), labelColor = LIGHT_TEXT),
        ControlDef("select", ControlType.BUTTON, "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT,
            x = 0.42f, y = 0.84f, size = 0.115f, shape = ControlShape.PILL,
            fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
        ControlDef("reset", ControlType.BUTTON, "RESET", KeyEvent.KEYCODE_BUTTON_START,
            x = 0.58f, y = 0.84f, size = 0.115f, shape = ControlShape.PILL,
            fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
        ControlDef("fire", ControlType.BUTTON, "FIRE", KeyEvent.KEYCODE_BUTTON_B,
            x = 0.85f, y = 0.56f, size = 0.30f, shape = ControlShape.CIRCLE,
            fillColor = Color.parseColor("#D24A2C"), labelColor = LIGHT_TEXT),
    )

    // ------------------------------------------------------------- Atari 7800

    /** 7800: D-pad, two fire buttons, Select, Pause (=Start). */
    private fun atari7800(): List<ControlDef> = listOf(
        ControlDef("dpad", ControlType.DPAD, "", x = 0.155f, y = 0.54f, size = 0.55f,
            shape = ControlShape.CROSS,
            fillColor = Color.parseColor("#1C1C1E"), labelColor = LIGHT_TEXT),
        ControlDef("select", ControlType.BUTTON, "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT,
            x = 0.42f, y = 0.84f, size = 0.115f, shape = ControlShape.PILL,
            fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
        ControlDef("pause", ControlType.BUTTON, "PAUSE", KeyEvent.KEYCODE_BUTTON_START,
            x = 0.58f, y = 0.84f, size = 0.115f, shape = ControlShape.PILL,
            fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
        ControlDef("b", ControlType.BUTTON, "1", KeyEvent.KEYCODE_BUTTON_B,
            x = 0.77f, y = 0.62f, size = 0.25f, shape = ControlShape.CIRCLE,
            fillColor = Color.parseColor("#C0392B"), labelColor = LIGHT_TEXT),
        ControlDef("a", ControlType.BUTTON, "2", KeyEvent.KEYCODE_BUTTON_A,
            x = 0.91f, y = 0.52f, size = 0.25f, shape = ControlShape.CIRCLE,
            fillColor = Color.parseColor("#C0392B"), labelColor = LIGHT_TEXT),
    )

    // ------------------------------------------------------------- WonderSwan

    /** WonderSwan: D-pad (X-pad), A, B, Start. */
    private fun wonderswan(): List<ControlDef> = listOf(
        ControlDef("dpad", ControlType.DPAD, "", x = 0.155f, y = 0.54f, size = 0.55f,
            shape = ControlShape.CROSS,
            fillColor = Color.parseColor("#22222A"), labelColor = LIGHT_TEXT),
        ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
            x = 0.50f, y = 0.86f, size = 0.12f, shape = ControlShape.PILL,
            fillColor = Color.parseColor("#2E86C1"), labelColor = LIGHT_TEXT),
        ControlDef("b", ControlType.BUTTON, "B", KeyEvent.KEYCODE_BUTTON_B,
            x = 0.77f, y = 0.62f, size = 0.24f, shape = ControlShape.CIRCLE,
            fillColor = Color.parseColor("#2E86C1"), labelColor = LIGHT_TEXT),
        ControlDef("a", ControlType.BUTTON, "A", KeyEvent.KEYCODE_BUTTON_A,
            x = 0.91f, y = 0.52f, size = 0.24f, shape = ControlShape.CIRCLE,
            fillColor = Color.parseColor("#2E86C1"), labelColor = LIGHT_TEXT),
    )

    // ------------------------------------------------------------- Virtual Boy

    /** Virtual Boy: left D-pad, A, B, L/R shoulders, Select, Start. */
    private fun virtualboy(): List<ControlDef> {
        val red = Color.parseColor("#E03A3A")
        return listOf(
            ControlDef("dpad", ControlType.DPAD, "", x = 0.15f, y = 0.56f, size = 0.52f,
                shape = ControlShape.CROSS,
                fillColor = Color.parseColor("#3A1414"), labelColor = LIGHT_TEXT),
            ControlDef("l", ControlType.BUTTON, "L", KeyEvent.KEYCODE_BUTTON_L1,
                x = 0.14f, y = 0.10f, size = 0.17f, shape = ControlShape.BAR,
                fillColor = Color.parseColor("#7A2020"), labelColor = LIGHT_TEXT),
            ControlDef("r", ControlType.BUTTON, "R", KeyEvent.KEYCODE_BUTTON_R1,
                x = 0.86f, y = 0.10f, size = 0.17f, shape = ControlShape.BAR,
                fillColor = Color.parseColor("#7A2020"), labelColor = LIGHT_TEXT),
            ControlDef("select", ControlType.BUTTON, "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT,
                x = 0.42f, y = 0.85f, size = 0.11f, shape = ControlShape.PILL,
                fillColor = Color.parseColor("#3A1414"), labelColor = LIGHT_TEXT),
            ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
                x = 0.58f, y = 0.85f, size = 0.11f, shape = ControlShape.PILL,
                fillColor = Color.parseColor("#3A1414"), labelColor = LIGHT_TEXT),
            ControlDef("b", ControlType.BUTTON, "B", KeyEvent.KEYCODE_BUTTON_B,
                x = 0.78f, y = 0.62f, size = 0.22f, shape = ControlShape.CIRCLE,
                fillColor = red, labelColor = LIGHT_TEXT),
            ControlDef("a", ControlType.BUTTON, "A", KeyEvent.KEYCODE_BUTTON_A,
                x = 0.92f, y = 0.52f, size = 0.22f, shape = ControlShape.CIRCLE,
                fillColor = red, labelColor = LIGHT_TEXT),
        )
    }

    // ------------------------------------------------------------- 3DO

    private fun threedo(): List<ControlDef> {
        val btn = Color.parseColor("#3A3A3E")
        return listOf(
            ControlDef("dpad", ControlType.DPAD, "", x = 0.15f, y = 0.55f, size = 0.52f,
                shape = ControlShape.CROSS, fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
            ControlDef("l", ControlType.BUTTON, "L", KeyEvent.KEYCODE_BUTTON_L1,
                x = 0.14f, y = 0.10f, size = 0.17f, shape = ControlShape.BAR, fillColor = GRAY_BTN, labelColor = LIGHT_TEXT),
            ControlDef("r", ControlType.BUTTON, "R", KeyEvent.KEYCODE_BUTTON_R1,
                x = 0.86f, y = 0.10f, size = 0.17f, shape = ControlShape.BAR, fillColor = GRAY_BTN, labelColor = LIGHT_TEXT),
            ControlDef("p", ControlType.BUTTON, "P", KeyEvent.KEYCODE_BUTTON_START,
                x = 0.42f, y = 0.85f, size = 0.11f, shape = ControlShape.PILL, fillColor = btn, labelColor = LIGHT_TEXT),
            ControlDef("x", ControlType.BUTTON, "X", KeyEvent.KEYCODE_BUTTON_SELECT,
                x = 0.58f, y = 0.85f, size = 0.11f, shape = ControlShape.PILL, fillColor = btn, labelColor = LIGHT_TEXT),
            ControlDef("c", ControlType.BUTTON, "C", KeyEvent.KEYCODE_BUTTON_X,
                x = 0.74f, y = 0.66f, size = 0.20f, shape = ControlShape.CIRCLE, fillColor = btn, labelColor = LIGHT_TEXT),
            ControlDef("b", ControlType.BUTTON, "B", KeyEvent.KEYCODE_BUTTON_B,
                x = 0.85f, y = 0.55f, size = 0.20f, shape = ControlShape.CIRCLE, fillColor = btn, labelColor = LIGHT_TEXT),
            ControlDef("a", ControlType.BUTTON, "A", KeyEvent.KEYCODE_BUTTON_A,
                x = 0.96f, y = 0.44f, size = 0.20f, shape = ControlShape.CIRCLE, fillColor = btn, labelColor = LIGHT_TEXT),
        )
    }

    // ------------------------------------------------------------- Saturn

    /** Saturn: 6 face buttons (X Y Z / A B C), L/R shoulders, Start. */
    private fun saturn(): List<ControlDef> {
        val top = Color.parseColor("#37414F")
        val bot = Color.parseColor("#2E2E34")
        return listOf(
            ControlDef("dpad", ControlType.DPAD, "", x = 0.15f, y = 0.56f, size = 0.50f,
                shape = ControlShape.CROSS, fillColor = Color.parseColor("#22262E"), labelColor = LIGHT_TEXT),
            ControlDef("l", ControlType.BUTTON, "L", KeyEvent.KEYCODE_BUTTON_L1,
                x = 0.14f, y = 0.09f, size = 0.17f, shape = ControlShape.BAR, fillColor = GRAY_BTN, labelColor = LIGHT_TEXT),
            ControlDef("r", ControlType.BUTTON, "R", KeyEvent.KEYCODE_BUTTON_R1,
                x = 0.86f, y = 0.09f, size = 0.17f, shape = ControlShape.BAR, fillColor = GRAY_BTN, labelColor = LIGHT_TEXT),
            ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
                x = 0.50f, y = 0.88f, size = 0.11f, shape = ControlShape.PILL, fillColor = bot, labelColor = LIGHT_TEXT),
            ControlDef("x", ControlType.BUTTON, "X", KeyEvent.KEYCODE_BUTTON_Y,
                x = 0.70f, y = 0.42f, size = 0.155f, shape = ControlShape.CIRCLE, fillColor = top, labelColor = LIGHT_TEXT),
            ControlDef("y", ControlType.BUTTON, "Y", KeyEvent.KEYCODE_BUTTON_X,
                x = 0.83f, y = 0.36f, size = 0.155f, shape = ControlShape.CIRCLE, fillColor = top, labelColor = LIGHT_TEXT),
            ControlDef("z", ControlType.BUTTON, "Z", KeyEvent.KEYCODE_BUTTON_L2,
                x = 0.96f, y = 0.32f, size = 0.155f, shape = ControlShape.CIRCLE, fillColor = top, labelColor = LIGHT_TEXT),
            ControlDef("a", ControlType.BUTTON, "A", KeyEvent.KEYCODE_BUTTON_B,
                x = 0.70f, y = 0.66f, size = 0.155f, shape = ControlShape.CIRCLE, fillColor = bot, labelColor = LIGHT_TEXT),
            ControlDef("b", ControlType.BUTTON, "B", KeyEvent.KEYCODE_BUTTON_A,
                x = 0.83f, y = 0.60f, size = 0.155f, shape = ControlShape.CIRCLE, fillColor = bot, labelColor = LIGHT_TEXT),
            ControlDef("c", ControlType.BUTTON, "C", KeyEvent.KEYCODE_BUTTON_R2,
                x = 0.96f, y = 0.56f, size = 0.155f, shape = ControlShape.CIRCLE, fillColor = bot, labelColor = LIGHT_TEXT),
        )
    }

    // ------------------------------------------------------------- ColecoVision

    private fun coleco(): List<ControlDef> = listOf(
        ControlDef("dpad", ControlType.DPAD, "", x = 0.155f, y = 0.54f, size = 0.52f,
            shape = ControlShape.CROSS, fillColor = Color.parseColor("#1C1C1E"), labelColor = LIGHT_TEXT),
        ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
            x = 0.50f, y = 0.86f, size = 0.12f, shape = ControlShape.PILL, fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
        ControlDef("k1", ControlType.BUTTON, "1", KeyEvent.KEYCODE_BUTTON_X,
            x = 0.66f, y = 0.75f, size = 0.15f, shape = ControlShape.CIRCLE, fillColor = Color.parseColor("#2E2E33"), labelColor = LIGHT_TEXT),
        ControlDef("k2", ControlType.BUTTON, "2", KeyEvent.KEYCODE_BUTTON_Y,
            x = 0.66f, y = 0.50f, size = 0.15f, shape = ControlShape.CIRCLE, fillColor = Color.parseColor("#2E2E33"), labelColor = LIGHT_TEXT),
        ControlDef("lfire", ControlType.BUTTON, "L", KeyEvent.KEYCODE_BUTTON_B,
            x = 0.82f, y = 0.62f, size = 0.24f, shape = ControlShape.CIRCLE, fillColor = Color.parseColor("#C0392B"), labelColor = LIGHT_TEXT),
        ControlDef("rfire", ControlType.BUTTON, "R", KeyEvent.KEYCODE_BUTTON_A,
            x = 0.94f, y = 0.50f, size = 0.24f, shape = ControlShape.CIRCLE, fillColor = Color.parseColor("#C0392B"), labelColor = LIGHT_TEXT),
    )

    // ------------------------------------------------------------- Intellivision

    private fun intellivision(): List<ControlDef> = listOf(
        ControlDef("dpad", ControlType.DPAD, "", x = 0.155f, y = 0.54f, size = 0.55f,
            shape = ControlShape.CROSS, fillColor = Color.parseColor("#1C1C1E"), labelColor = LIGHT_TEXT),
        ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
            x = 0.50f, y = 0.86f, size = 0.12f, shape = ControlShape.PILL, fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
        ControlDef("top", ControlType.BUTTON, "TOP", KeyEvent.KEYCODE_BUTTON_X,
            x = 0.85f, y = 0.36f, size = 0.19f, shape = ControlShape.CIRCLE, fillColor = Color.parseColor("#B8860B"), labelColor = DARK),
        ControlDef("bl", ControlType.BUTTON, "◣", KeyEvent.KEYCODE_BUTTON_B,
            x = 0.77f, y = 0.62f, size = 0.19f, shape = ControlShape.CIRCLE, fillColor = Color.parseColor("#B8860B"), labelColor = DARK),
        ControlDef("br", ControlType.BUTTON, "◢", KeyEvent.KEYCODE_BUTTON_A,
            x = 0.93f, y = 0.62f, size = 0.19f, shape = ControlShape.CIRCLE, fillColor = Color.parseColor("#B8860B"), labelColor = DARK),
    )

    // ------------------------------------------------------------- Vectrex

    private fun vectrex(): List<ControlDef> {
        val btn = Color.parseColor("#2E7D5A")
        return listOf(
            ControlDef("dpad", ControlType.DPAD, "", x = 0.155f, y = 0.55f, size = 0.55f,
                shape = ControlShape.CROSS, fillColor = Color.parseColor("#141414"), labelColor = LIGHT_TEXT),
            ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
                x = 0.50f, y = 0.87f, size = 0.12f, shape = ControlShape.PILL, fillColor = Color.parseColor("#222"), labelColor = LIGHT_TEXT),
            ControlDef("b1", ControlType.BUTTON, "1", KeyEvent.KEYCODE_BUTTON_Y,
                x = 0.66f, y = 0.60f, size = 0.16f, shape = ControlShape.CIRCLE, fillColor = btn, labelColor = LIGHT_TEXT),
            ControlDef("b2", ControlType.BUTTON, "2", KeyEvent.KEYCODE_BUTTON_B,
                x = 0.78f, y = 0.60f, size = 0.16f, shape = ControlShape.CIRCLE, fillColor = btn, labelColor = LIGHT_TEXT),
            ControlDef("b3", ControlType.BUTTON, "3", KeyEvent.KEYCODE_BUTTON_A,
                x = 0.90f, y = 0.60f, size = 0.16f, shape = ControlShape.CIRCLE, fillColor = btn, labelColor = LIGHT_TEXT),
            ControlDef("b4", ControlType.BUTTON, "4", KeyEvent.KEYCODE_BUTTON_X,
                x = 0.78f, y = 0.40f, size = 0.16f, shape = ControlShape.CIRCLE, fillColor = btn, labelColor = LIGHT_TEXT),
        )
    }

    // ------------------------------------------------------------- Pokémon Mini

    private fun pokemonMini(): List<ControlDef> = listOf(
        ControlDef("dpad", ControlType.DPAD, "", x = 0.155f, y = 0.54f, size = 0.55f,
            shape = ControlShape.CROSS, fillColor = Color.parseColor("#241E33"), labelColor = LIGHT_TEXT),
        ControlDef("start", ControlType.BUTTON, "POWER", KeyEvent.KEYCODE_BUTTON_START,
            x = 0.50f, y = 0.86f, size = 0.12f, shape = ControlShape.PILL, fillColor = Color.parseColor("#2A2438"), labelColor = LIGHT_TEXT),
        ControlDef("c", ControlType.BUTTON, "C", KeyEvent.KEYCODE_BUTTON_R1,
            x = 0.86f, y = 0.10f, size = 0.16f, shape = ControlShape.BAR, fillColor = GRAY_BTN, labelColor = LIGHT_TEXT),
        ControlDef("b", ControlType.BUTTON, "B", KeyEvent.KEYCODE_BUTTON_B,
            x = 0.77f, y = 0.62f, size = 0.24f, shape = ControlShape.CIRCLE, fillColor = Color.parseColor("#E0A020"), labelColor = DARK),
        ControlDef("a", ControlType.BUTTON, "A", KeyEvent.KEYCODE_BUTTON_A,
            x = 0.91f, y = 0.52f, size = 0.24f, shape = ControlShape.CIRCLE, fillColor = Color.parseColor("#E0A020"), labelColor = DARK),
    )

    // ------------------------------------------------------------- Atari 5200

    private fun atari5200(): List<ControlDef> = listOf(
        ControlDef("dpad", ControlType.DPAD, "", x = 0.155f, y = 0.54f, size = 0.55f,
            shape = ControlShape.CROSS, fillColor = Color.parseColor("#1C1C1E"), labelColor = LIGHT_TEXT),
        ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
            x = 0.42f, y = 0.84f, size = 0.11f, shape = ControlShape.PILL, fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
        ControlDef("pause", ControlType.BUTTON, "PAUSE", KeyEvent.KEYCODE_BUTTON_SELECT,
            x = 0.58f, y = 0.84f, size = 0.11f, shape = ControlShape.PILL, fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
        ControlDef("fire2", ControlType.BUTTON, "2", KeyEvent.KEYCODE_BUTTON_A,
            x = 0.77f, y = 0.62f, size = 0.24f, shape = ControlShape.CIRCLE, fillColor = Color.parseColor("#D24A2C"), labelColor = LIGHT_TEXT),
        ControlDef("fire1", ControlType.BUTTON, "1", KeyEvent.KEYCODE_BUTTON_B,
            x = 0.91f, y = 0.52f, size = 0.24f, shape = ControlShape.CIRCLE, fillColor = Color.parseColor("#D24A2C"), labelColor = LIGHT_TEXT),
    )

    // ------------------------------------------------------------- Arcade / Neo Geo

    /** Arcade: D-pad, six buttons (SF layout), Coin (=Select), Start. */
    private fun arcade(): List<ControlDef> {
        val punch = Color.parseColor("#2E86C1")
        val kick = Color.parseColor("#E67E22")
        val face = 0.155f
        return listOf(
            ControlDef("dpad", ControlType.DPAD, "", x = 0.14f, y = 0.55f, size = 0.52f,
                shape = ControlShape.CROSS, fillColor = Color.parseColor("#1C1C1E"), labelColor = LIGHT_TEXT),
            ControlDef("coin", ControlType.BUTTON, "COIN", KeyEvent.KEYCODE_BUTTON_SELECT,
                x = 0.42f, y = 0.88f, size = 0.10f, shape = ControlShape.PILL,
                fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
            ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
                x = 0.56f, y = 0.88f, size = 0.10f, shape = ControlShape.PILL,
                fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
            // Top row: light/medium/heavy punch
            ControlDef("lp", ControlType.BUTTON, "LP", KeyEvent.KEYCODE_BUTTON_Y,
                x = 0.66f, y = 0.42f, size = face, shape = ControlShape.CIRCLE, fillColor = punch, labelColor = LIGHT_TEXT),
            ControlDef("mp", ControlType.BUTTON, "MP", KeyEvent.KEYCODE_BUTTON_X,
                x = 0.80f, y = 0.36f, size = face, shape = ControlShape.CIRCLE, fillColor = punch, labelColor = LIGHT_TEXT),
            ControlDef("hp", ControlType.BUTTON, "HP", KeyEvent.KEYCODE_BUTTON_L1,
                x = 0.94f, y = 0.34f, size = face, shape = ControlShape.CIRCLE, fillColor = punch, labelColor = LIGHT_TEXT),
            // Bottom row: light/medium/heavy kick
            ControlDef("lk", ControlType.BUTTON, "LK", KeyEvent.KEYCODE_BUTTON_B,
                x = 0.66f, y = 0.64f, size = face, shape = ControlShape.CIRCLE, fillColor = kick, labelColor = LIGHT_TEXT),
            ControlDef("mk", ControlType.BUTTON, "MK", KeyEvent.KEYCODE_BUTTON_A,
                x = 0.80f, y = 0.58f, size = face, shape = ControlShape.CIRCLE, fillColor = kick, labelColor = LIGHT_TEXT),
            ControlDef("hk", ControlType.BUTTON, "HK", KeyEvent.KEYCODE_BUTTON_R1,
                x = 0.94f, y = 0.56f, size = face, shape = ControlShape.CIRCLE, fillColor = kick, labelColor = LIGHT_TEXT),
        )
    }

    // ---------------------------------------------- Home computers (joystick)

    /**
     * Joystick + fire layout shared by the keyboard computers (MSX, C64, Amiga,
     * ZX Spectrum, Amstrad). Covers joystick games; a full virtual keyboard is a
     * separate feature still to come.
     */
    private fun computerJoystick(accent: Int): List<ControlDef> = listOf(
        ControlDef("dpad", ControlType.DPAD, "", x = 0.155f, y = 0.54f, size = 0.55f,
            shape = ControlShape.CROSS, fillColor = Color.parseColor("#1C1C1E"), labelColor = LIGHT_TEXT),
        ControlDef("select", ControlType.BUTTON, "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT,
            x = 0.42f, y = 0.85f, size = 0.11f, shape = ControlShape.PILL, fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
        ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
            x = 0.58f, y = 0.85f, size = 0.11f, shape = ControlShape.PILL, fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
        ControlDef("jump", ControlType.BUTTON, "▲", KeyEvent.KEYCODE_BUTTON_A,
            x = 0.77f, y = 0.62f, size = 0.22f, shape = ControlShape.CIRCLE, fillColor = accent, labelColor = LIGHT_TEXT),
        ControlDef("fire", ControlType.BUTTON, "FIRE", KeyEvent.KEYCODE_BUTTON_B,
            x = 0.91f, y = 0.52f, size = 0.24f, shape = ControlShape.CIRCLE, fillColor = accent, labelColor = LIGHT_TEXT),
    )

    // ------------------------------------------------------------- Dreamcast

    /**
     * Dreamcast: D-pad, one analog stick, four face buttons (Y top, X left, B right,
     * A bottom), analog L/R triggers, and Start. Flycast reads A/B/X/Y directly and
     * the triggers as L2/R2.
     */
    private fun dreamcast(): List<ControlDef> {
        val face = 0.185f
        val btn = Color.parseColor("#3A3A40")
        return listOf(
            ControlDef("dpad", ControlType.DPAD, "", x = 0.13f, y = 0.36f, size = 0.36f,
                shape = ControlShape.CROSS,
                fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
            ControlDef("stick_l", ControlType.STICK, "", x = 0.28f, y = 0.68f, size = 0.38f,
                shape = ControlShape.STICK,
                fillColor = Color.parseColor("#3A3A41"), labelColor = LIGHT_TEXT),
            ControlDef("l", ControlType.BUTTON, "L", KeyEvent.KEYCODE_BUTTON_L2,
                x = 0.15f, y = 0.07f, size = 0.17f, shape = ControlShape.BAR,
                fillColor = GRAY_BTN, labelColor = LIGHT_TEXT),
            ControlDef("r", ControlType.BUTTON, "R", KeyEvent.KEYCODE_BUTTON_R2,
                x = 0.85f, y = 0.07f, size = 0.17f, shape = ControlShape.BAR,
                fillColor = GRAY_BTN, labelColor = LIGHT_TEXT),
            ControlDef("start", ControlType.BUTTON, "START", KeyEvent.KEYCODE_BUTTON_START,
                x = 0.50f, y = 0.88f, size = 0.12f, shape = ControlShape.PILL,
                fillColor = Color.parseColor("#F17022"), labelColor = DARK),
            ControlDef("y", ControlType.BUTTON, "Y", KeyEvent.KEYCODE_BUTTON_Y,
                x = 0.84f, y = 0.34f, size = face, shape = ControlShape.CIRCLE,
                fillColor = btn, labelColor = LIGHT_TEXT),
            ControlDef("x", ControlType.BUTTON, "X", KeyEvent.KEYCODE_BUTTON_X,
                x = 0.75f, y = 0.54f, size = face, shape = ControlShape.CIRCLE,
                fillColor = btn, labelColor = LIGHT_TEXT),
            ControlDef("b", ControlType.BUTTON, "B", KeyEvent.KEYCODE_BUTTON_B,
                x = 0.945f, y = 0.54f, size = face, shape = ControlShape.CIRCLE,
                fillColor = btn, labelColor = LIGHT_TEXT),
            ControlDef("a", ControlType.BUTTON, "A", KeyEvent.KEYCODE_BUTTON_A,
                x = 0.845f, y = 0.74f, size = face, shape = ControlShape.CIRCLE,
                fillColor = btn, labelColor = LIGHT_TEXT),
        )
    }

    // ------------------------------------------------------------- Neo Geo Pocket

    /** NGP: D-pad plus A, B, and Option (mapped to Start). */
    private fun ngp(): List<ControlDef> = listOf(
        ControlDef("dpad", ControlType.DPAD, "", x = 0.155f, y = 0.54f, size = 0.55f,
            shape = ControlShape.CROSS,
            fillColor = Color.parseColor("#1C1C1E"), labelColor = LIGHT_TEXT),
        ControlDef("option", ControlType.BUTTON, "OPTION", KeyEvent.KEYCODE_BUTTON_START,
            x = 0.50f, y = 0.84f, size = 0.12f, shape = ControlShape.PILL,
            fillColor = Color.parseColor("#2A2A2E"), labelColor = LIGHT_TEXT),
        ControlDef("b", ControlType.BUTTON, "B", KeyEvent.KEYCODE_BUTTON_B,
            x = 0.77f, y = 0.62f, size = 0.25f, shape = ControlShape.CIRCLE,
            fillColor = Color.parseColor("#D24726"), labelColor = LIGHT_TEXT),
        ControlDef("a", ControlType.BUTTON, "A", KeyEvent.KEYCODE_BUTTON_A,
            x = 0.91f, y = 0.52f, size = 0.25f, shape = ControlShape.CIRCLE,
            fillColor = Color.parseColor("#D24726"), labelColor = LIGHT_TEXT),
    )
}
