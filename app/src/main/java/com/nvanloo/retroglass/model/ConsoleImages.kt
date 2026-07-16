package com.nvanloo.retroglass.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.nvanloo.retroglass.R

/**
 * Console photos, cropped on demand from a single sprite sheet (res/drawable-nodpi/
 * console_photos.png) — a 5-column x 4-row grid of the first 20 systems, in list order.
 * The remaining 20 have no photo yet (a placeholder is shown until the second sheet lands).
 */
object ConsoleImages {

    private const val COLS = 5
    private const val ROWS = 4

    /** Cell index in the sprite (left-to-right, top-to-bottom). Absent = no photo yet. */
    private val INDEX: Map<Console, Int> = mapOf(
        Console.NES to 0, Console.GAMEBOY to 1, Console.SNES to 2, Console.VIRTUALBOY to 3, Console.N64 to 4,
        Console.GBA to 5, Console.POKEMONMINI to 6, Console.NDS to 7, Console.MASTERSYSTEM to 8, Console.MEGADRIVE to 9,
        Console.GAMEGEAR to 10, Console.SEGACD to 11, Console.SEGA32X to 12, Console.SATURN to 13, Console.DREAMCAST to 14,
        Console.NAOMI to 15, Console.ATOMISWAVE to 16, Console.PSX to 17, Console.PS2 to 18, Console.PSP to 19,
    )

    private var sheet: Bitmap? = null
    private val cache = HashMap<Console, Bitmap>()

    /** True if a photo exists for this console (else callers should show a placeholder). */
    fun hasPhoto(console: Console): Boolean = INDEX.containsKey(console)

    /** The cropped console photo, or null if none is available yet. Result is cached. */
    fun photo(context: Context, console: Console): Bitmap? {
        val idx = INDEX[console] ?: return null
        cache[console]?.let { return it }
        val sh = sheet ?: runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.console_photos)
        }.getOrNull()?.also { sheet = it } ?: return null
        val cw = sh.width / COLS
        val ch = sh.height / ROWS
        val col = idx % COLS
        val row = idx / COLS
        val bmp = Bitmap.createBitmap(sh, col * cw, row * ch, cw, ch)
        cache[console] = bmp
        return bmp
    }
}
