package com.nvanloo.retroglass.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.nvanloo.retroglass.R

/**
 * Console artwork, cropped on demand from one sprite sheet in res/drawable-nodpi.
 *
 * The drawings are original line art: silhouettes only, with no manufacturer logo, wordmark or
 * lettering on any of them. That is deliberate rather than stylistic - the previous sheets were
 * photographic renders carrying legible Nintendo, SEGA and Sony marks, which is the kind of
 * thing an emulator listing actually gets pulled for.
 *
 * The layout is 5 across and 7 down in [SHEET_ORDER], but the art is generated and lands only
 * *near* a 212 px grid - a plain width/5 crop clips 33 of the 35. So [ORIGINS] holds a derived
 * top-left per console, all sharing one [BOX_W] x [BOX_H] box so the library grid does not
 * jitter. Do not hand-edit that table: regenerate it with
 * `python scripts/derive_console_cells.py`, which reads the gutters back out of the image.
 */
object ConsoleImages {

    private const val BOX_W = 182
    private const val BOX_H = 171

    /** Row-major, 5 per row. Must stay in step with [ORIGINS] and with the artwork itself. */
    private val SHEET_ORDER = listOf(
        Console.NES, Console.GAMEBOY, Console.SNES, Console.VIRTUALBOY, Console.N64,
        Console.GBA, Console.POKEMONMINI, Console.NDS, Console.MASTERSYSTEM, Console.MEGADRIVE,
        Console.GAMEGEAR, Console.SEGACD, Console.SEGA32X, Console.SATURN, Console.DREAMCAST,
        Console.NAOMI, Console.ATOMISWAVE, Console.PSX, Console.PS2, Console.PSP,
        Console.ATARI2600, Console.ATARI8BIT, Console.ATARI5200, Console.ATARI7800, Console.LYNX,
        Console.PCENGINE, Console.PCECD, Console.THREEDO, Console.NGP, Console.INTELLIVISION,
        Console.COLECO, Console.VECTREX, Console.NEOGEOCD, Console.ARCADE, Console.WONDERSWAN,
    )

    // 35 cells, uniform 182x171 box, derived from console_line.png.
    private val ORIGINS = intArrayOf(
        45, 65,
        241, 63,
        440, 75,
        627, 72,
        818, 81,   // row 0
        32, 259,
        222, 262,
        416, 259,
        622, 264,
        831, 263,   // row 1
        28, 441,
        229, 446,
        422, 444,
        626, 446,
        828, 449,   // row 2
        31, 628,
        229, 632,
        430, 633,
        623, 628,
        827, 645,   // row 3
        26, 819,
        223, 817,
        425, 819,
        625, 817,
        828, 828,   // row 4
        28, 996,
        219, 1000,
        426, 1004,
        626, 1005,
        824, 1009,   // row 5
        27, 1195,
        213, 1191,
        425, 1192,
        620, 1197,
        824, 1208,   // row 6
    )

    private var sheet: Bitmap? = null
    private var sheetLoaded = false
    private val cache = HashMap<Console, Bitmap>()

    /** True if artwork exists for this console (else callers should show a placeholder). */
    fun hasPhoto(console: Console): Boolean = SHEET_ORDER.indexOf(console) >= 0

    /** The cropped console drawing, or null if none is available. Result is cached. */
    fun photo(context: Context, console: Console): Bitmap? {
        val i = SHEET_ORDER.indexOf(console)
        if (i < 0 || i * 2 + 1 >= ORIGINS.size) return null
        cache[console]?.let { return it }

        if (!sheetLoaded) {
            sheetLoaded = true
            sheet = runCatching {
                BitmapFactory.decodeResource(context.resources, R.drawable.console_line)
            }.getOrNull()
        }
        val sh = sheet ?: return null

        // Clamp rather than trust the table: a regenerated sheet at a different size would
        // otherwise throw out of createBitmap instead of just looking wrong.
        val x = ORIGINS[i * 2].coerceIn(0, (sh.width - BOX_W).coerceAtLeast(0))
        val y = ORIGINS[i * 2 + 1].coerceIn(0, (sh.height - BOX_H).coerceAtLeast(0))
        val w = BOX_W.coerceAtMost(sh.width - x)
        val h = BOX_H.coerceAtMost(sh.height - y)
        if (w <= 0 || h <= 0) return null

        val bmp = Bitmap.createBitmap(sh, x, y, w, h)
        cache[console] = bmp
        return bmp
    }
}
