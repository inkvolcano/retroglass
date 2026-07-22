package com.nvanloo.retroglass.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.nvanloo.retroglass.R

/**
 * The app's line-art sprite sheets, cropped on demand.
 *
 * Two sheets, both original artwork and both laid out 5 across and 7 down in [SHEET_ORDER]:
 * [CONSOLES] draws the machine, [MEDIA] draws what you put in it (cartridge, HuCard, disc, UMD).
 * Silhouettes only - no manufacturer logo, wordmark or lettering on any of them, which is
 * deliberate and explained in THIRD_PARTY_LICENSES.
 *
 * The art is generated and lands only *near* a 212 px grid, so a plain width/5 crop clips most
 * of it. Each sheet therefore carries a derived top-left per console, all sharing one box so the
 * library does not jitter as it scrolls. Do not hand-edit those tables - regenerate with
 * `python scripts/derive_console_cells.py <sheet.png>`, which reads the gutters back out.
 */
object ConsoleImages {

    /** Row-major, 5 per row. Both sheets follow it, and so does the artwork itself. */
    private val SHEET_ORDER = listOf(
        Console.NES, Console.GAMEBOY, Console.SNES, Console.VIRTUALBOY, Console.N64,
        Console.GBA, Console.POKEMONMINI, Console.NDS, Console.MASTERSYSTEM, Console.MEGADRIVE,
        Console.GAMEGEAR, Console.SEGACD, Console.SEGA32X, Console.SATURN, Console.DREAMCAST,
        Console.NAOMI, Console.ATOMISWAVE, Console.PSX, Console.PS2, Console.PSP,
        Console.ATARI2600, Console.ATARI8BIT, Console.ATARI5200, Console.ATARI7800, Console.LYNX,
        Console.PCENGINE, Console.PCECD, Console.THREEDO, Console.NGP, Console.INTELLIVISION,
        Console.COLECO, Console.VECTREX, Console.NEOGEOCD, Console.ARCADE, Console.WONDERSWAN,
    )

    /**
     * One sprite sheet. Decodes once, caches every crop, and clamps rather than trusting the
     * table - a regenerated sheet at a different size should look wrong, not throw.
     */
    class Sheet(
        private val drawable: Int,
        private val boxW: Int,
        private val boxH: Int,
        private val origins: IntArray,
    ) {
        private var bitmap: Bitmap? = null
        private var loaded = false
        private val cache = HashMap<Console, Bitmap>()

        fun has(console: Console): Boolean {
            val i = SHEET_ORDER.indexOf(console)
            return i >= 0 && i * 2 + 1 < origins.size
        }

        fun get(context: Context, console: Console): Bitmap? {
            val i = SHEET_ORDER.indexOf(console)
            if (i < 0 || i * 2 + 1 >= origins.size) return null
            cache[console]?.let { return it }

            if (!loaded) {
                loaded = true
                bitmap = runCatching {
                    BitmapFactory.decodeResource(context.resources, drawable)
                }.getOrNull()
            }
            val sh = bitmap ?: return null

            val x = origins[i * 2].coerceIn(0, (sh.width - boxW).coerceAtLeast(0))
            val y = origins[i * 2 + 1].coerceIn(0, (sh.height - boxH).coerceAtLeast(0))
            val w = boxW.coerceAtMost(sh.width - x)
            val h = boxH.coerceAtMost(sh.height - y)
            if (w <= 0 || h <= 0) return null

            return Bitmap.createBitmap(sh, x, y, w, h).also { cache[console] = it }
        }
    }

    /** The machine itself, for the library's console carousel. */
    val CONSOLES = Sheet(
        R.drawable.console_line, 182, 171,
        intArrayOf(
            45, 65, 241, 63, 440, 75, 627, 72, 818, 81,
            32, 259, 222, 262, 416, 259, 622, 264, 831, 263,
            28, 441, 229, 446, 422, 444, 626, 446, 828, 449,
            31, 628, 229, 632, 430, 633, 623, 628, 827, 645,
            26, 819, 223, 817, 425, 819, 625, 817, 828, 828,
            28, 996, 219, 1000, 426, 1004, 626, 1005, 824, 1009,
            27, 1195, 213, 1191, 425, 1192, 620, 1197, 824, 1208,
        ),
    )

    /** What the game came on, for the per-game rows in a console's list. */
    val MEDIA = Sheet(
        R.drawable.media_line, 177, 147,
        intArrayOf(
            41, 72, 247, 71, 454, 81, 651, 91, 845, 89,
            32, 278, 231, 282, 423, 277, 636, 280, 841, 278,
            28, 476, 234, 477, 435, 478, 635, 479, 836, 481,
            31, 671, 226, 671, 431, 673, 633, 673, 834, 679,
            32, 856, 228, 856, 436, 856, 636, 861, 836, 871,
            24, 1036, 219, 1041, 423, 1041, 627, 1039, 843, 1039,
            28, 1222, 228, 1221, 433, 1224, 632, 1226, 844, 1233,
        ),
    )

    /** True if console artwork exists (else callers should show a placeholder). */
    fun hasPhoto(console: Console): Boolean = CONSOLES.has(console)

    /** The cropped console drawing, or null if none is available. Cached. */
    fun photo(context: Context, console: Console): Bitmap? = CONSOLES.get(context, console)

    /** The cropped cartridge/disc drawing for this console, or null. Cached. */
    fun media(context: Context, console: Console): Bitmap? = MEDIA.get(context, console)
}
