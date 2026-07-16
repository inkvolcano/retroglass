package com.nvanloo.retroglass.model

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Exports/imports all user save data — battery saves (SRAM), save-states, and the app's
 * SharedPreferences (controller layouts, cheats, core options, covers) — as a single .zip
 * the user picks via SAF. Guards against losing everything on an uninstall (app data lives
 * in filesDir). ROMs and BIOS are intentionally excluded (large, re-importable).
 */
object SaveBackup {

    private fun prefsDir(context: Context): File = File(context.applicationInfo.dataDir, "shared_prefs")

    /** Zips saves/, states/ and prefs/ to [outUri]. Returns the number of files written. */
    fun export(context: Context, outUri: Uri): Int {
        val sources = listOf(
            "saves" to RomLibrary.savesDir(context),
            "states" to RomLibrary.statesDir(context),
            "prefs" to prefsDir(context),
        )
        var count = 0
        context.contentResolver.openOutputStream(outUri)?.use { raw ->
            ZipOutputStream(raw.buffered()).use { zip ->
                for ((prefix, dir) in sources) {
                    val files = dir.listFiles()?.filter { it.isFile } ?: continue
                    for (f in files) {
                        zip.putNextEntry(ZipEntry("$prefix/${f.name}"))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        count++
                    }
                }
            }
        }
        return count
    }

    /** Restores a backup .zip. Prefs are rewritten but only take effect after an app restart. */
    fun import(context: Context, inUri: Uri): Int {
        var count = 0
        context.contentResolver.openInputStream(inUri)?.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val dest = destFor(context, entry.name)
                        if (dest != null) {
                            dest.parentFile?.mkdirs()
                            dest.outputStream().use { zip.copyTo(it) }
                            count++
                        }
                    }
                    entry = zip.nextEntry
                }
            }
        }
        return count
    }

    private fun destFor(context: Context, entryName: String): File? {
        // Keep only the basename to avoid zip path-traversal.
        val name = entryName.substringAfterLast('/').substringAfterLast('\\')
        if (name.isEmpty()) return null
        val root = when {
            entryName.startsWith("saves/") -> RomLibrary.savesDir(context)
            entryName.startsWith("states/") -> RomLibrary.statesDir(context)
            entryName.startsWith("prefs/") && name.endsWith(".xml") -> prefsDir(context).apply { mkdirs() }
            else -> return null
        }
        return File(root, name)
    }
}
