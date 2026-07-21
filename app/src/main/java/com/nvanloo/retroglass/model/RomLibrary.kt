package com.nvanloo.retroglass.model

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
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
            // Discs named inside an .m3u are hidden — the playlist is the single entry.
            val playlistRefs = files.filter { it.extension.equals("m3u", true) }
                .flatMap { runCatching { it.readLines() }.getOrDefault(emptyList()) }
                .map { it.trim().substringAfterLast('/').substringAfterLast('\\').lowercase() }
                .filter { it.isNotEmpty() }
                .toSet()
            for (f in files) {
                val ext = f.extension.lowercase()
                if (ext != "m3u" && f.name.lowercase() in playlistRefs) continue
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
        // External games referenced in place. references() already drops any whose file is gone,
        // so items on an unplugged SD card / USB drive vanish from the library until it's back.
        val known = entries.mapTo(HashSet()) { it.file.absolutePath }
        for (ref in references(context)) {
            if (known.add(ref.file.absolutePath)) entries.add(ref)
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
        reclassifyDiscsByContent(context)
        generateMultiDiscPlaylists(context)
        return ImportResult(imported, skipped)
    }

    /** Extensions we actually try to import; anything else in a scanned folder is ignored. */
    private fun isImportable(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext == "zip" || ext in ALL_ROM_EXTENSIONS || ext in PSX_SIDECAR_EXTENSIONS ||
            BiosCatalog.isBios(name)
    }

    /**
     * Recursively imports a whole folder tree (SAF) into the unified library: every ROM is
     * routed to its system's folder, recognised BIOS files go to the system directory, and
     * per-system subfolders (e.g. "PS2", "Dreamcast") disambiguate shared disc images.
     * Multi-disc sets are stitched into .m3u playlists afterwards.
     */
    fun importTree(context: Context, treeUri: Uri): ImportResult {
        val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
            ?: return ImportResult(emptyList(), emptyList())
        val files = mutableListOf<androidx.documentfile.provider.DocumentFile>()
        collectFiles(root, files)

        // Cue stems across the entire tree, so .bin tracks classify correctly wherever they sit.
        val cueStems = files.mapNotNull { it.name }
            .filter { it.endsWith(".cue", true) }
            .map { it.substringBeforeLast('.').lowercase() }

        val imported = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val resolver = context.contentResolver
        // ROMs before sidecars so a subchannel file matches a disc written in the same pass.
        val ordered = files.sortedBy {
            (it.name?.substringAfterLast('.', "")?.lowercase() ?: "") in PSX_SIDECAR_EXTENSIONS
        }
        for (doc in ordered) {
            val name = doc.name ?: continue
            if (!isImportable(name)) continue // silently ignore readmes, art, etc.
            try {
                if (BiosCatalog.isBios(name)) {
                    resolver.openInputStream(doc.uri)?.use { input ->
                        File(systemDir(context), name).outputStream().use { input.copyTo(it) }
                    }
                    imported.add(name)
                    continue
                }
                val hint = folderHint(parentName(doc))
                val ok = resolver.openInputStream(doc.uri)?.use { input ->
                    importStream(context, input, name, cueStems, hint)
                } ?: false
                if (ok) imported.add(name) else skipped.add(name)
            } catch (e: Exception) {
                skipped.add("$name (${e.message})")
            }
        }
        reclassifyDiscsByContent(context)
        generateMultiDiscPlaylists(context)
        adoptSiblingCovers(context, files)
        return ImportResult(imported, skipped)
    }

    private val IMAGE_EXTS = setOf("png", "jpg", "jpeg", "webp")

    /** Adopts an image that sat next to a ROM (same base name) as that game's cover. */
    private fun adoptSiblingCovers(
        context: Context,
        files: List<androidx.documentfile.provider.DocumentFile>,
    ) {
        val imagesByBase = files.mapNotNull { doc ->
            val n = doc.name ?: return@mapNotNull null
            if (n.substringAfterLast('.', "").lowercase() in IMAGE_EXTS) {
                n.substringBeforeLast('.').lowercase() to doc.uri
            } else null
        }.toMap()
        if (imagesByBase.isEmpty()) return
        for (entry in scan(context)) {
            val key = entry.file.absolutePath
            if (GameCovers.has(context, key)) continue
            val img = imagesByBase[entry.file.nameWithoutExtension.lowercase()] ?: continue
            GameCovers.setFromUri(context, key, img)
        }
    }

    private fun parentName(doc: androidx.documentfile.provider.DocumentFile): String? =
        doc.parentFile?.name

    private fun collectFiles(
        dir: androidx.documentfile.provider.DocumentFile,
        out: MutableList<androidx.documentfile.provider.DocumentFile>,
    ) {
        for (child in dir.listFiles()) {
            if (child.isDirectory) collectFiles(child, out) else out.add(child)
        }
    }

    // Matches a disc token like "(Disc 1)", "(Disk 2)", "(CD 3)" — case-insensitive.
    private val DISC_TOKEN = Regex("""\((?:dis[ck]|cd)\s*(\d+)\)""", RegexOption.IGNORE_CASE)
    private val PLAYLIST_DISC_EXTS = setOf("chd", "cue", "pbp", "iso", "img")

    private fun discBase(nameNoExt: String): String =
        DISC_TOKEN.replace(nameNoExt, "").trim().trimEnd('-', '_', ' ', '(').trim()

    private fun discNumber(nameNoExt: String): Int =
        DISC_TOKEN.find(nameNoExt)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0

    /**
     * Detects multi-disc sets by their "(Disc N)" filenames and writes an .m3u playlist per
     * set, so the game shows as a single library entry with in-game disc swapping. Existing
     * playlists are left untouched, and single-disc games are ignored.
     */
    fun generateMultiDiscPlaylists(context: Context) {
        for (console in DISC_CONSOLES) {
            val dir = romsDir(context, console)
            val discFiles = dir.listFiles()
                ?.filter {
                    it.extension.lowercase() in PLAYLIST_DISC_EXTS &&
                        DISC_TOKEN.containsMatchIn(it.nameWithoutExtension)
                } ?: continue
            val groups = discFiles.groupBy { discBase(it.nameWithoutExtension).lowercase() }
            for ((_, discs) in groups) {
                if (discs.size < 2) continue
                val sorted = discs.sortedBy { discNumber(it.nameWithoutExtension) }
                val playlistBase = discBase(sorted.first().nameWithoutExtension)
                if (playlistBase.isEmpty()) continue
                val m3u = File(dir, "$playlistBase.m3u")
                if (m3u.exists()) continue
                m3u.writeText(sorted.joinToString("\n") { it.name })
            }
        }
    }

    private fun normalizeKey(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    private val PSX_DISC_EXTENSIONS = setOf("chd", "pbp", "cue", "iso", "img", "exe")

    /** Disc-based systems that share the .chd/.cue/.iso container formats. */
    private val DISC_CONSOLES = setOf(
        Console.PSX, Console.PS2, Console.DREAMCAST, Console.SATURN, Console.THREEDO,
        Console.SEGACD, Console.PCECD, Console.NEOGEOCD,
    )

    /** Container extensions that could belong to any disc system (need a hint to place). */
    private val SHARED_DISC_EXTS = setOf("chd", "iso", "cue", "img", "pbp")

    /**
     * A folder name can disambiguate shared disc images — e.g. a "PS2" or "Dreamcast"
     * subfolder tells us its .chd/.iso files aren't PlayStation. Used by folder import.
     */
    private fun folderHint(folderName: String?): Console? {
        val n = folderName?.lowercase() ?: return null
        return when {
            "ps2" in n || "playstation 2" in n || "playstation2" in n -> Console.PS2
            "dreamcast" in n || n == "dc" -> Console.DREAMCAST
            "saturn" in n -> Console.SATURN
            "3do" in n -> Console.THREEDO
            "segacd" in n || "sega cd" in n || "mega cd" in n || "megacd" in n -> Console.SEGACD
            "pcecd" in n || "pce cd" in n || "pc engine cd" in n || "turbografx cd" in n || "tgcd" in n -> Console.PCECD
            "neogeocd" in n || "neo geo cd" in n || "neocd" in n -> Console.NEOGEOCD
            "naomi" in n -> Console.NAOMI
            "atomiswave" in n -> Console.ATOMISWAVE
            "ps1" in n || "psx" in n || "playstation" in n -> Console.PSX
            else -> null
        }
    }

    // -------------------------------------------------- disc content sniffing

    /** Uncompressed disc data we can read a header from (CHD/PBP are compressed → skipped). */
    private val SNIFFABLE_DISC_EXTS = setOf("cue", "iso", "bin", "img")

    /**
     * Identifies a disc's true system by scanning its data track for magic strings
     * (Dreamcast/Saturn/Sega-CD headers, the PS2 SYSTEM.CNF BOOT2, the PS1 licence/BOOT).
     * Returns null for formats it can't read (.chd/.pbp) or when nothing matches.
     */
    private fun detectDiscConsole(dataFile: File): Console? {
        if (!dataFile.exists() || dataFile.length() < 4096) return null
        val len = minOf(dataFile.length(), 2L * 1024 * 1024).toInt()
        val buf = ByteArray(len)
        val read = runCatching {
            dataFile.inputStream().use { input ->
                var off = 0
                while (off < buf.size) {
                    val r = input.read(buf, off, buf.size - off)
                    if (r < 0) break
                    off += r
                }
                off
            }
        }.getOrDefault(0)
        if (read <= 0) return null
        val t = String(buf, 0, read, Charsets.ISO_8859_1)
        return when {
            "SEGA SEGAKATANA" in t -> Console.DREAMCAST
            "SEGADISCSYSTEM" in t || "SEGABOOTDISC" in t -> Console.SEGACD
            "SEGA SEGASATURN" in t -> Console.SATURN
            "BOOT2" in t -> Console.PS2
            "PLAYSTATION" in t || "cdrom:" in t -> Console.PSX
            "IPL.TXT" in t -> Console.NEOGEOCD
            else -> null
        }
    }

    /** The first BINARY data file referenced by a .cue sheet, resolved next to it. */
    private fun cueDataFile(cueFile: File): File? {
        val text = runCatching { cueFile.readText() }.getOrNull() ?: return null
        val name = Regex("""FILE\s+"([^"]+)"""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)
            ?: Regex("""FILE\s+(\S+)\s+BINARY""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)
            ?: return null
        val base = name.substringAfterLast('/').substringAfterLast('\\')
        return File(cueFile.parentFile, base).takeIf { it.exists() }
    }

    /**
     * After import, re-files disc images that landed on the wrong system (shared .cue/.iso/.bin
     * default to PlayStation) by reading their content — so PS1/PS2/Dreamcast/Saturn/Sega-CD
     * are told apart automatically. .chd stays where it was put (can't be inspected).
     */
    fun reclassifyDiscsByContent(context: Context) {
        for (console in DISC_CONSOLES) {
            val dir = romsDir(context, console)
            val files = dir.listFiles()?.filter { it.extension.lowercase() in SNIFFABLE_DISC_EXTS } ?: continue
            val hasCue = files.any { it.extension.equals("cue", true) }
            for (f in files) {
                if (!f.exists()) continue
                val ext = f.extension.lowercase()
                // A .bin is disc data for a .cue — classify via the .cue, not the .bin.
                if (ext == "bin" && hasCue) continue
                val dataFile = if (ext == "cue") cueDataFile(f) ?: continue else f
                val detected = detectDiscConsole(dataFile) ?: continue
                if (detected != console) moveToConsole(context, RomEntry(f, console), detected)
            }
        }
    }

    /** Picks the console for a ROM, letting a folder hint override shared disc formats. */
    private fun classify(
        ext: String,
        name: String,
        cueStems: List<String>,
        size: Long,
        folderHint: Console?,
    ): Console? {
        if (folderHint != null && folderHint in DISC_CONSOLES && ext in SHARED_DISC_EXTS) return folderHint
        return Console.forExtension(
            ext,
            siblingCue = ext == "bin" && binMatchesCue(name, cueStems),
            fileSize = size,
        )
    }

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
        folderHint: Console? = null,
    ): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext == "zip") return importZip(context, input, name, folderHint)
        // LibCrypt subchannel files live next to the PS1 disc so PCSX-ReARMed finds them.
        if (ext in PSX_SIDECAR_EXTENSIONS) {
            // Playlists (.m3u) follow a disc-system folder hint; subchannel files are PS1-only.
            val discTarget = if (folderHint in DISC_CONSOLES) folderHint!! else Console.PSX
            val (target, targetName) = when (ext) {
                "sbi", "sub" -> Console.PSX to matchSidecarName(context, name)
                else -> discTarget to name // .m3u
            }
            File(romsDir(context, target), targetName).outputStream().use { input.copyTo(it) }
            return true
        }
        if (ext !in ALL_ROM_EXTENSIONS) return false

        // Stage to a temp file first so we can use the size for .bin disambiguation.
        val temp = File(context.cacheDir, "import_$name")
        temp.outputStream().use { input.copyTo(it) }
        val console = classify(ext, name, cueStems, temp.length(), folderHint)
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
    private val ARCADE_ZIP_CONSOLES = setOf(Console.ARCADE, Console.NAOMI, Console.ATOMISWAVE)

    private fun importZip(
        context: Context,
        input: InputStream,
        originalName: String,
        folderHint: Console? = null,
    ): Boolean {
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
            // Arcade-style romset kept intact. A folder hint (naomi/atomiswave subfolder)
            // routes it to that system; otherwise it defaults to Arcade (FBNeo) and can be
            // reassigned via long-press → Change system.
            val target = if (folderHint in ARCADE_ZIP_CONSOLES) folderHint!! else Console.ARCADE
            temp.copyTo(File(romsDir(context, target), originalName), overwrite = true)
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

    /** Deletes a ROM plus same-basename sidecar files (cue/bin pairs). For an .m3u playlist
     *  entry, also deletes every disc it references (and their track/sidecar companions). */
    fun delete(context: Context, entry: RomEntry) {
        // In-place reference (outside the app's private storage): just forget it — never delete
        // the user's original file that lives on their SD card / external drive / chosen folder.
        if (!entry.file.absolutePath.startsWith(context.filesDir.absolutePath)) {
            removeReference(context, entry.file.absolutePath)
            return
        }
        val dir = entry.file.parentFile
        val bases = mutableSetOf(entry.file.nameWithoutExtension.lowercase())
        if (entry.file.extension.equals("m3u", true)) {
            runCatching { entry.file.readLines() }.getOrDefault(emptyList())
                .map { it.trim().substringAfterLast('/').substringAfterLast('\\') }
                .filter { it.isNotEmpty() }
                .forEach { bases.add(it.substringBeforeLast('.').lowercase()) }
        }
        dir?.listFiles()
            ?.filter { it.nameWithoutExtension.lowercase() in bases }
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

    // ================================================= all-storage scan + in-place references

    private fun refsFile(context: Context) = File(context.filesDir, "library_refs.txt")

    /** Games referenced in place (never copied). Only those whose file currently exists are
     *  returned, so entries from an unplugged SD card / external drive are hidden until it returns. */
    fun references(context: Context): List<RomEntry> {
        val f = refsFile(context)
        if (!f.exists()) return emptyList()
        return runCatching { f.readLines() }.getOrDefault(emptyList()).mapNotNull { line ->
            val i = line.indexOf('\t')
            if (i <= 0) return@mapNotNull null
            val console = Console.entries.firstOrNull { it.prefKey == line.substring(0, i) } ?: return@mapNotNull null
            val file = File(line.substring(i + 1))
            if (file.exists()) RomEntry(file, console) else null
        }
    }

    private fun refLines(context: Context): MutableList<String> =
        refsFile(context).takeIf { it.exists() }
            ?.let { runCatching { it.readLines() }.getOrDefault(emptyList()).toMutableList() }
            ?: mutableListOf()

    private fun addReferences(context: Context, entries: List<Pair<Console, File>>) {
        if (entries.isEmpty()) return
        val lines = refLines(context)
        val have = lines.mapTo(HashSet()) { it.substringAfter('\t') }
        for ((console, file) in entries) {
            val path = file.absolutePath
            if (have.add(path)) lines.add("${console.prefKey}\t$path")
        }
        refsFile(context).writeText(lines.joinToString("\n"))
    }

    private fun removeReference(context: Context, path: String) {
        val f = refsFile(context)
        if (!f.exists()) return
        f.writeText(f.readLines().filter { it.substringAfter('\t') != path }.joinToString("\n"))
    }

    /** True when we can read the raw filesystem (All-files access on R+, legacy storage below). */
    fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else true

    /** Readable storage volume roots: internal shared storage plus any SD card / USB drive. */
    fun storageRoots(context: Context): List<File> {
        val roots = LinkedHashSet<File>()
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        sm?.storageVolumes?.forEach { v ->
            val dir = if (Build.VERSION.SDK_INT >= 30) v.directory
                      else runCatching { v.javaClass.getMethod("getPathFile").invoke(v) as? File }.getOrNull()
            if (dir != null && dir.isDirectory && dir.canRead()) roots.add(dir)
        }
        if (roots.isEmpty()) Environment.getExternalStorageDirectory()?.takeIf { it.isDirectory }?.let { roots.add(it) }
        return roots.toList()
    }

    data class Found(val file: File, val console: Console?, val isBios: Boolean)

    /** Walks every readable storage volume for ROMs / BIOS that aren't already in the library. */
    fun scanAllStorage(context: Context): List<Found> {
        val appPath = context.filesDir.absolutePath
        // "name:size" of everything already known, so we don't re-offer what's already imported.
        val known = HashSet<String>()
        fun key(f: File) = f.name.lowercase() + ":" + runCatching { f.length() }.getOrDefault(0L)
        Console.entries.forEach { c -> romsDir(context, c).listFiles()?.forEach { known.add(key(it)) } }
        systemDir(context).listFiles()?.forEach { known.add(key(it)) }
        references(context).forEach { known.add(key(it.file)) }

        val out = ArrayList<Found>()
        val seenPaths = HashSet<String>()
        for (root in storageRoots(context)) {
            root.walkTopDown()
                .onEnter { dir -> !dir.absolutePath.startsWith(appPath) && dir.name != "Android" && !dir.name.startsWith(".") }
                .onFail { _, _ -> } // skip unreadable directories rather than aborting the scan
                .forEach { f ->
                    if (!f.isFile) return@forEach
                    if (f.absolutePath.startsWith(appPath)) return@forEach
                    if (!seenPaths.add(f.absolutePath)) return@forEach
                    val ext = f.extension.lowercase()
                    val bios = BiosCatalog.isBios(f.name)
                    val isRom = ext == "zip" || ext in ALL_ROM_EXTENSIONS
                    if (!bios && !isRom) return@forEach
                    if (key(f) in known) return@forEach
                    val console = if (bios) null else classifyFile(context, f) ?: return@forEach
                    out.add(Found(f, console, bios))
                }
        }
        return out
    }

    // Cartridge ROM extensions that unambiguously mark a console ROM inside a .zip (the broad
    // CONSOLE_ROM_EXTENSIONS is unusable here — it includes bin/iso/cue, which are arcade chips
    // or disc data).
    // Cartridge extensions that unambiguously mark a console ROM inside a .zip. We drop the ones
    // that collide with everyday files: md=Markdown, dmg=macOS image (plus the disc/firmware set).
    private val UNAMBIGUOUS_ROM_EXTS = CONSOLE_ROM_EXTENSIONS -
        setOf("bin", "exe", "iso", "img", "chd", "pbp", "cue", "m3u", "md", "dmg")

    // Extensions that mark a .zip as a normal archive (software / media / docs / project), never a romset.
    private val NON_ROM_ZIP_EXTS = setOf(
        "exe", "dll", "so", "apk", "msi", "dmg", "app", "bat", "sh", "cmd", "ps1",
        "html", "htm", "js", "mjs", "ts", "tsx", "jsx", "css", "scss", "less", "json", "xml",
        "py", "java", "kt", "c", "cpp", "h", "hpp", "cs", "go", "rb", "php", "rs", "swift", "dart",
        "vue", "class", "jar", "gradle", "sln", "sql", "yml", "yaml", "toml", "ini", "cfg", "lock",
        "properties", "gitignore", "gitattributes", "env", "md", "log", "bak",
        "pdf", "doc", "docx", "xls", "xlsx", "xlsm", "ppt", "pptx", "odt", "rtf", "epub",
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "psd", "ai", "ico", "tiff",
        "mp3", "wav", "flac", "ogg", "aac", "m4a", "mp4", "mkv", "avi", "mov", "webm", "wmv",
        "rar", "7z", "tar", "gz", "bz2", "cab", "ttf", "otf", "woff", "csv",
    )

    /**
     * Peeks a .zip's entry list (central directory only — no decompression) to decide whether it
     * is a real ROM: a recognisable console cartridge inside → that console; otherwise an arcade
     * romset only if the archive is FLAT (no folders) and holds no software/media/doc files — which
     * rejects source repos (nested with .md/.py/etc.) and normal archives. Else null.
     */
    private fun classifyZip(zip: File): Console? {
        val names = runCatching {
            java.util.zip.ZipFile(zip).use { zf ->
                zf.entries().asSequence().filter { !it.isDirectory }.map { it.name }.toList()
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        val exts = names.map { it.substringAfterLast('/').substringAfterLast('.', "").lowercase() }
        exts.firstNotNullOfOrNull { if (it in UNAMBIGUOUS_ROM_EXTS) Console.forExtension(it) else null }
            ?.let { return it }
        val flat = names.none { it.contains('/') }
        val clean = exts.none { it in NON_ROM_ZIP_EXTS }
        return if (flat && clean) Console.ARCADE else null
    }

    /**
     * Conservative file classification for a blind whole-storage scan — deliberately strict so
     * random archives / playlists / firmware aren't mistaken for games:
     *  - .sbi/.sub/.m3u (sidecars/playlists), standalone .bin, .exe → ignored
     *  - .zip → peeked (classifyZip): a real console ROM or arcade romset only, never a plain archive
     *  - shared disc images (.iso/.cue/.img) → must sniff to a known system or sit in a hinting
     *    folder, else ignored (so a PC ISO isn't filed as PlayStation); .chd/.pbp default to PS1
     *  - plain cartridge ROMs (.nes/.sfc/.gba/…) → classified by extension as usual
     */
    private fun classifyFile(context: Context, f: File): Console? {
        val ext = f.extension.lowercase()
        // Ignore ambiguous types in a blind scan: sidecars/playlists, disc-track/firmware .bin,
        // and .exe (a PS1 homebrew type that collides with Windows installers on shared storage).
        if (ext in PSX_SIDECAR_EXTENSIONS || ext == "bin" || ext == "exe") return null
        if (ext == "zip") return classifyZip(f)
        if (ext in SHARED_DISC_EXTS) {
            val hinted = folderHint(f.parentFile?.name)?.takeIf { it in DISC_CONSOLES }
            if (ext == "chd" || ext == "pbp") return hinted ?: Console.PSX
            val dataFile = if (ext == "cue") cueDataFile(f) else f
            val sniffed = dataFile?.let { detectDiscConsole(it) }
            return sniffed ?: hinted // else null: can't prove it's a console disc → skip
        }
        val cueStems = f.parentFile?.listFiles()
            ?.filter { it.extension.equals("cue", true) }?.map { it.nameWithoutExtension.lowercase() } ?: emptyList()
        return classify(ext, f.name, cueStems, f.length(), folderHint(f.parentFile?.name))
    }

    /** Unified consolidation folder on primary storage: <sd>/RetroGlass/roms/<console>/. */
    fun unifiedDir(context: Context): File = File(Environment.getExternalStorageDirectory(), "RetroGlass")

    /**
     * Adds scanned items to the library. BIOS files are always copied into the app's system dir
     * (that's where the cores look). ROMs are either MOVED into the unified RetroGlass folder
     * (move=true) or REFERENCED in place. Returns how many were added.
     */
    fun applyScan(context: Context, found: List<Found>, move: Boolean): Int {
        var count = 0
        val refs = ArrayList<Pair<Console, File>>()
        for (item in found) {
            try {
                if (item.isBios) {
                    val dest = File(systemDir(context), item.file.name)
                    if (dest.absolutePath != item.file.absolutePath && !dest.exists()) {
                        item.file.copyTo(dest, overwrite = true)
                    }
                    count++
                    continue
                }
                val console = item.console ?: continue
                // A .zip holding a console cartridge/disc ROM must be extracted into the
                // library — the cores read the raw ROM, not the archive. (Arcade romsets are
                // the exception: FBNeo needs the .zip intact, so those fall through and are
                // referenced/moved as-is.)
                if (item.file.extension.equals("zip", true) && console !in ARCADE_ZIP_CONSOLES) {
                    if (extractConsoleZip(context, item.file)) count++
                    continue
                }
                if (move) {
                    val destDir = File(unifiedDir(context), "roms/${console.prefKey}").apply { mkdirs() }
                    val base = item.file.nameWithoutExtension.lowercase()
                    // Move the ROM and its same-basename companions (.bin tracks, .sbi, etc.).
                    item.file.parentFile?.listFiles()
                        ?.filter { it.nameWithoutExtension.lowercase() == base }
                        ?.forEach { comp ->
                            val d = File(destDir, comp.name)
                            if (comp.absolutePath != d.absolutePath && !comp.renameTo(d)) {
                                comp.copyTo(d, overwrite = true); comp.delete()
                            }
                        }
                    refs.add(console to File(destDir, item.file.name))
                } else {
                    refs.add(console to item.file)
                }
                count++
            } catch (_: Exception) {}
        }
        addReferences(context, refs)
        return count
    }
}
