package com.nvanloo.retroglass.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.nvanloo.retroglass.R

/**
 * Console photos, cropped on demand from two 5-column x 4-row sprite sheets in
 * res/drawable-nodpi: console_photos.png (the first 20 systems) and console_photos2.png
 * (the remaining 20). Each console maps to a (sheet, cell) pair; cells run left-to-right,
 * top-to-bottom, in the order the consoles appear in their source image.
 */
object ConsoleImages {

    private const val COLS = 5
    private const val ROWS = 4

    private data class Cell(val sheet: Int, val index: Int)

    private val CELLS: Map<Console, Cell> = buildMap {
        // Sheet 1 — first 20 systems, in that image's order.
        listOf(
            Console.NES, Console.GAMEBOY, Console.SNES, Console.VIRTUALBOY, Console.N64,
            Console.GBA, Console.POKEMONMINI, Console.NDS, Console.MASTERSYSTEM, Console.MEGADRIVE,
            Console.GAMEGEAR, Console.SEGACD, Console.SEGA32X, Console.SATURN, Console.DREAMCAST,
            Console.NAOMI, Console.ATOMISWAVE, Console.PSX, Console.PS2, Console.PSP,
        ).forEachIndexed { i, c -> put(c, Cell(R.drawable.console_photos, i)) }
        // Sheet 2 — remaining 20 systems, in that image's order.
        listOf(
            Console.ATARI2600, Console.ATARI8BIT, Console.ATARI5200, Console.ATARI7800, Console.LYNX,
            Console.PCENGINE, Console.PCECD, Console.THREEDO, Console.NGP, Console.C64,
            Console.AMIGA, Console.INTELLIVISION, Console.COLECO, Console.VECTREX, Console.SPECTRUM,
            Console.MSX, Console.AMSTRAD, Console.NEOGEOCD, Console.ARCADE, Console.WONDERSWAN,
        ).forEachIndexed { i, c -> put(c, Cell(R.drawable.console_photos2, i)) }
    }

    private val sheets = HashMap<Int, Bitmap?>()
    private val cache = HashMap<Console, Bitmap>()

    /** True if a photo exists for this console (else callers should show a placeholder). */
    fun hasPhoto(console: Console): Boolean = CELLS.containsKey(console)

    /** The cropped console photo, or null if none is available yet. Result is cached. */
    fun photo(context: Context, console: Console): Bitmap? {
        val cell = CELLS[console] ?: return null
        cache[console]?.let { return it }
        val sh = sheets.getOrPut(cell.sheet) {
            runCatching { BitmapFactory.decodeResource(context.resources, cell.sheet) }.getOrNull()
        } ?: return null
        val cw = sh.width / COLS
        val ch = sh.height / ROWS
        val col = cell.index % COLS
        val row = cell.index / COLS
        val bmp = Bitmap.createBitmap(sh, col * cw, row * ch, cw, ch)
        cache[console] = bmp
        return bmp
    }
}
