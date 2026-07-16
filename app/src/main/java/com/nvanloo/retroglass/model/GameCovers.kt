package com.nvanloo.retroglass.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import java.io.File

/**
 * Per-game cover art, stored fully offline as downscaled PNGs under filesDir/covers/,
 * keyed by a hash of the ROM path. Users set a cover via long-press; a folder scan can
 * also adopt an image that sits next to a ROM with the same base name. No network access.
 */
object GameCovers {

    private const val MAX_EDGE = 256 // covers render small; keep memory tiny
    private val cache = LruCache<String, Bitmap>(64)

    private fun dir(context: Context): File = File(context.filesDir, "covers").apply { mkdirs() }

    private fun keyOf(gameKey: String): String = Integer.toHexString(gameKey.hashCode())

    fun coverFile(context: Context, gameKey: String): File = File(dir(context), keyOf(gameKey) + ".png")

    fun has(context: Context, gameKey: String): Boolean = coverFile(context, gameKey).exists()

    /** Decodes a picked image, downscales it, and stores it as this game's cover. */
    fun setFromUri(context: Context, gameKey: String, src: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(src)?.use { input ->
            val bytes = input.readBytes()
            saveScaled(context, gameKey, bytes)
        } ?: false
    }.getOrDefault(false)

    fun setFromFile(context: Context, gameKey: String, src: File): Boolean =
        runCatching { saveScaled(context, gameKey, src.readBytes()) }.getOrDefault(false)

    private fun saveScaled(context: Context, gameKey: String, bytes: ByteArray): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        var sample = 1
        while (longest / sample > MAX_EDGE * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return false
        coverFile(context, gameKey).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
        cache.remove(keyOf(gameKey))
        return true
    }

    fun remove(context: Context, gameKey: String) {
        coverFile(context, gameKey).delete()
        cache.remove(keyOf(gameKey))
    }

    /** Cached bitmap for display, or null if no cover set. */
    fun load(context: Context, gameKey: String): Bitmap? {
        val k = keyOf(gameKey)
        cache.get(k)?.let { return it }
        val f = coverFile(context, gameKey)
        if (!f.exists()) return null
        val bmp = BitmapFactory.decodeFile(f.absolutePath) ?: return null
        cache.put(k, bmp)
        return bmp
    }
}
