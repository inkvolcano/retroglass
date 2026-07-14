package com.nvanloo.retroglass.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

data class RomEntry(
    val file: File,
    val console: Console,
) {
    val displayName: String get() = file.nameWithoutExtension
}

object RomLibrary {

    private val ALL_ROM_EXTENSIONS: Set<String> =
        Console.entries.flatMap { it.romExtensions }.toSet() + "bin"

    /** Non-playable companion files copied next to PS1 discs (LibCrypt subchannel). */
    private val PSX_SIDECAR_EXTENSIONS = setOf("sbi", "sub", "m3u")

    fun romsDir(context: Context, console: Console): File =
        File(context.filesDir, "roms/${console.prefKey}").apply { mkdirs() }

    fun systemDir(context: Context): File =
        File(context.filesDir, "system").apply { mkdirs() }

    fun savesDir(context: Context): File =
        File(context.filesDir, "saves").apply { mkdirs() }

    fun statesDir(context: Context): File =
        File(context.filesDir, "states").apply { mkdirs() }

    fun scan(context: Context): List<RomEntry> {
        val entries = mutableListOf<RomEntry>()
        for (console in Console.entries) {
            val dir = romsDir(context, console)
            val files = dir.listFiles()?.sortedBy { it.name.lowercase() } ?: continue
            val hasAnyCue = files.any { it.extension.equals("cue", true) }
            for (f in files) {
                val ext = f.extension.lowercase()
                val playable = when {
                    ext in console.romExtensions -> true
                    // .bin: always playable on Mega Drive; on PS1 only when it isn't
                    // disc data referenced by a .cue sheet
                    ext == "bin" -> console != Console.PSX || !hasAnyCue
                    else -> false
                }
                if (playable) entries.add(RomEntry(f, console))
            }
        }
        return entries
    }

    data class ImportResult(val imported: List<String>, val skipped: List<String>)

    /**
     * Copies the picked documents into the per-console ROM folders.
     * A batch containing a .cue marks sibling .bin files as PS1 disc data.
     */
    fun importAll(context: Context, uris: List<Uri>): ImportResult {
        val resolver = context.contentResolver
        val named = uris.mapNotNull { uri ->
            val name = resolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
            } ?: uri.lastPathSegment?.substringAfterLast('/')
            name?.let { uri to it }
        }
        val cueStems = named.map { it.second }
            .filter { it.endsWith(".cue", true) }
            .map { it.substringBeforeLast('.').lowercase() }

        val imported = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        // Import playable ROMs before sidecar files (.sbi) so a same-batch subchannel
        // file can be matched to the disc that was just written.
        val ordered = named.sortedBy {
            it.second.substringAfterLast('.', "").lowercase() in PSX_SIDECAR_EXTENSIONS
        }
        for ((uri, name) in ordered) {
            try {
                val ok = resolver.openInputStream(uri)?.use { input ->
                    importStream(context, input, name, cueStems)
                } ?: false
                if (ok) imported.add(name) else skipped.add(name)
            } catch (e: Exception) {
                skipped.add("$name (${e.message})")
            }
        }
        return ImportResult(imported, skipped)
    }

    private fun normalizeKey(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    private val PSX_DISC_EXTENSIONS = setOf("chd", "pbp", "cue", "iso", "img", "exe")

    /**
     * A LibCrypt subchannel file only works if its basename matches the disc image.
     * Rename it to the game whose name matches ignoring case/punctuation, so Redump
     * .sbi names line up with however the disc was stored.
     */
    private fun matchSidecarName(context: Context, sidecarName: String): String {
        val ext = sidecarName.substringAfterLast('.', "")
        val key = normalizeKey(sidecarName.substringBeforeLast('.'))
        if (key.isEmpty()) return sidecarName
        val match = romsDir(context, Console.PSX).listFiles()
            ?.firstOrNull {
                it.extension.lowercase() in PSX_DISC_EXTENSIONS &&
                    normalizeKey(it.nameWithoutExtension) == key
            }
        return if (match != null) "${match.nameWithoutExtension}.$ext" else sidecarName
    }

    /**
     * True when a .bin file belongs to a PS1 disc described by one of [cueStems].
     * Multi-track rips name tracks "Game (Track 2).bin" next to "Game.cue",
     * so match on stem prefixes rather than exact names.
     */
    private fun binMatchesCue(binName: String, cueStems: List<String>): Boolean {
        val stem = binName.substringBeforeLast('.').lowercase()
        return cueStems.any { stem.startsWith(it) || it.startsWith(stem) }
    }

    private fun importStream(
        context: Context,
        input: InputStream,
        name: String,
        cueStems: List<String>,
    ): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext == "zip") return importZip(context, input, name)
        // LibCrypt subchannel files live next to the PS1 disc so PCSX-ReARMed finds them.
        if (ext in PSX_SIDECAR_EXTENSIONS) {
            // Rename subchannel files to the matching disc; leave playlists (.m3u) as-is.
            val targetName = if (ext == "sbi" || ext == "sub") matchSidecarName(context, name) else name
            File(romsDir(context, Console.PSX), targetName).outputStream().use { input.copyTo(it) }
            return true
        }
        if (ext !in ALL_ROM_EXTENSIONS) return false

        // Stage to a temp file first so we can use the size for .bin disambiguation.
        val temp = File(context.cacheDir, "import_$name")
        temp.outputStream().use { input.copyTo(it) }
        val console = Console.forExtension(
            ext,
            siblingCue = ext == "bin" && binMatchesCue(name, cueStems),
            fileSize = temp.length(),
        )
        if (console == null) {
            temp.delete()
            return false
        }
        val dest = File(romsDir(context, console), name)
        temp.copyTo(dest, overwrite = true)
        temp.delete()
        return true
    }

    private val CONSOLE_ROM_EXTENSIONS: Set<String> = ALL_ROM_EXTENSIONS - "zip"

    /**
     * A .zip is either a compressed console ROM (extract it) or an arcade romset
     * that FBNeo must receive intact (keep the .zip). We tell them apart by peeking:
     * if the archive contains a recognizable console ROM, extract; otherwise it's an
     * arcade set and the whole .zip is stored under the Arcade system.
     */
    private fun importZip(context: Context, input: InputStream, originalName: String): Boolean {
        val temp = File(context.cacheDir, "import_$originalName")
        temp.outputStream().use { input.copyTo(it) }
        val hasConsoleRom = runCatching {
            java.util.zip.ZipFile(temp).use { zf ->
                zf.entries().asSequence().any {
                    !it.isDirectory &&
                        it.name.substringAfterLast('.', "").lowercase() in CONSOLE_ROM_EXTENSIONS
                }
            }
        }.getOrDefault(false)

        val result = if (hasConsoleRom) {
            extractConsoleZip(context, temp)
        } else {
            temp.copyTo(File(romsDir(context, Console.ARCADE), originalName), overwrite = true)
            true
        }
        temp.delete()
        return result
    }

    private fun extractConsoleZip(context: Context, zipFile: File): Boolean {
        // Extract ROM-like entries to temp files first so .bin entries can be
        // classified with knowledge of any .cue sheets in the same archive.
        val staged = mutableListOf<Pair<String, File>>()
        ZipInputStream(zipFile.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val entryName = entry.name.substringAfterLast('/')
                val ext = entryName.substringAfterLast('.', "").lowercase()
                if (!entry.isDirectory && ext in CONSOLE_ROM_EXTENSIONS) {
                    val temp = File(context.cacheDir, "import_$entryName")
                    temp.outputStream().use { out -> zip.copyTo(out) }
                    staged.add(entryName to temp)
                }
                entry = zip.nextEntry
            }
        }
        val cueStems = staged.map { it.first }
            .filter { it.endsWith(".cue", true) }
            .map { it.substringBeforeLast('.').lowercase() }

        var importedAny = false
        for ((entryName, temp) in staged) {
            val ext = entryName.substringAfterLast('.', "").lowercase()
            val console = Console.forExtension(
                ext,
                siblingCue = ext == "bin" && binMatchesCue(entryName, cueStems),
                fileSize = temp.length(),
            )
            if (console != null) {
                temp.copyTo(File(romsDir(context, console), entryName), overwrite = true)
                importedAny = true
            }
            temp.delete()
        }
        return importedAny
    }

    fun importBios(context: Context, uri: Uri): String? {
        val resolver = context.contentResolver
        val name = resolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
        } ?: return null
        resolver.openInputStream(uri)?.use { input ->
            File(systemDir(context), name).outputStream().use { input.copyTo(it) }
        } ?: return null
        return name
    }

    /** Deletes a ROM plus same-basename sidecar files (cue/bin pairs). */
    fun delete(entry: RomEntry) {
        val base = entry.file.nameWithoutExtension.lowercase()
        entry.file.parentFile?.listFiles()
            ?.filter { it.nameWithoutExtension.lowercase() == base }
            ?.forEach { it.delete() }
    }

    /**
     * Moves a game (and its same-basename companions — .bin tracks, .sbi, etc.) to
     * another console's folder. Disc formats are shared across PS1/PS2/Dreamcast, so
     * this lets the user correct how a disc image was auto-classified.
     */
    fun moveToConsole(context: Context, entry: RomEntry, target: Console) {
        if (target == entry.console) return
        val base = entry.file.nameWithoutExtension.lowercase()
        val destDir = romsDir(context, target)
        entry.file.parentFile?.listFiles()
            ?.filter { it.nameWithoutExtension.lowercase() == base }
            ?.forEach { f ->
                val dest = File(destDir, f.name)
                if (!f.renameTo(dest)) {
                    f.copyTo(dest, overwrite = true)
                    f.delete()
                }
            }
    }

    /** Consoles a game of this file type could plausibly belong to (for reassignment). */
    fun candidateConsoles(entry: RomEntry): List<Console> {
        val ext = entry.file.extension.lowercase()
        val byExt = Console.entries.filter { ext in it.romExtensions }
        return if (byExt.size > 1) byExt else Console.entries.toList()
    }
}
