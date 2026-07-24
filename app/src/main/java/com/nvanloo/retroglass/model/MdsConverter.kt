package com.nvanloo.retroglass.model

import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Converts an Alcohol 120% MDS/MDF pair into the cue/bin the disc cores actually read.
 *
 * No core in this app parses MDS: beetle-saturn's own extension list is cue|ccd|chd|toc|m3u|zip,
 * so an imported MDS dump "loads" (the BIOS boots fine) and then reports no disc — the CD-player
 * screen with no way to tell what went wrong. The MDS is just a 2 KB table of contents; the MDF
 * holds the real sectors, usually 2448 bytes each (2352 raw + 96 subchannel), which cue/bin can't
 * express. So: parse the track list out of the MDS, strip each sector to 2352, and write the cue.
 *
 * Format notes (verified against a real dump, Sonic R (U), 21 tracks):
 *  - header: "MEDIA DESCRIPTOR", u32 session-block offset at 0x50
 *  - session block: track count + u32 track-block offset at +20
 *  - track blocks, 0x50 bytes each: mode at +0 (0xA9 audio, 0xAA mode1), point (track number)
 *    at +4, sector size u16 at +0x10, u64 file offset at +0x28. Lead-in blocks use point ≥ 0xA0
 *    and are skipped. Every real block self-validates: file offset divisible by sector size.
 */
object MdsConverter {

    private const val TAG = "MdsConverter"
    private const val TRACK_BLOCK = 0x50
    private const val RAW_SECTOR = 2352

    /**
     * If [dir] holds both halves of the pair for [name] (either half just landed), converts in
     * place: writes `<stem>.cue` + `<stem>.bin`, deletes the mds/mdf. Quiet no-op otherwise —
     * the first half of a pair simply waits for its sibling to arrive.
     */
    fun convertIfPaired(dir: File, name: String) {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext != "mds" && ext != "mdf") return
        val stem = name.substringBeforeLast('.')
        val mds = File(dir, "$stem.mds")
        val mdf = File(dir, "$stem.mdf")
        if (!mds.exists() || !mdf.exists()) return
        runCatching { convert(mds, mdf) }
            .onSuccess { Log.i(TAG, "converted $stem to cue/bin (${it.size} tracks)") }
            .onFailure { Log.w(TAG, "MDS conversion failed for $stem — pair left as imported", it) }
    }

    private data class Track(val number: Int, val audio: Boolean, val fileSector: Long)

    /** Returns the track list on success; throws (leaving the pair untouched) on anything odd. */
    private fun convert(mds: File, mdf: File): List<Track> {
        val d = ByteBuffer.wrap(mds.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        val sig = ByteArray(16).also { d.get(it) }
        check(String(sig, Charsets.US_ASCII) == "MEDIA DESCRIPTOR") { "not an MDS file" }

        val sessionOff = d.getInt(0x50)
        val blockCount = d.get(sessionOff + 10).toInt() and 0xFF
        val tracksOff = d.getInt(sessionOff + 20)

        var sectorSize = 0
        val tracks = ArrayList<Track>()
        for (i in 0 until blockCount) {
            val o = tracksOff + i * TRACK_BLOCK
            val point = d.get(o + 4).toInt() and 0xFF
            if (point !in 1..99) continue // lead-in / lead-out entries
            val mode = d.get(o).toInt() and 0xFF
            val size = d.getShort(o + 0x10).toInt() and 0xFFFF
            val fileOff = d.getLong(o + 0x28)
            check(size == RAW_SECTOR || size == RAW_SECTOR + 96) { "sector size $size" }
            check(sectorSize == 0 || sectorSize == size) { "mixed sector sizes" }
            check(fileOff % size == 0L) { "track $point offset $fileOff not sector-aligned" }
            sectorSize = size
            tracks.add(Track(point, audio = mode == 0xA9, fileSector = fileOff / size))
        }
        check(tracks.isNotEmpty()) { "no tracks found" }
        check(mdf.length() % sectorSize == 0L) { "MDF length not a multiple of $sectorSize" }
        tracks.sortBy { it.number }

        val stem = mds.name.substringBeforeLast('.')
        val bin = File(mds.parentFile, "$stem.bin")
        if (sectorSize == RAW_SECTOR) {
            check(mdf.renameTo(bin)) { "rename to ${bin.name} failed" }
        } else {
            stripSubchannel(mdf, bin, sectorSize)
            mdf.delete()
        }

        File(mds.parentFile, "$stem.cue").writeText(buildString {
            append("FILE \"").append(bin.name).append("\" BINARY\r\n")
            for (t in tracks) {
                val type = if (t.audio) "AUDIO" else "MODE1/2352"
                append("  TRACK %02d %s\r\n".format(t.number, type))
                append("    INDEX 01 %s\r\n".format(msf(t.fileSector)))
            }
        })
        mds.delete()
        return tracks
    }

    private fun msf(sector: Long): String =
        "%02d:%02d:%02d".format(sector / 4500, (sector % 4500) / 75, sector % 75)

    /** Streams [src] into [dst], keeping the first 2352 bytes of each [sectorSize] sector. */
    private fun stripSubchannel(src: File, dst: File, sectorSize: Int) {
        val tmp = File(dst.parentFile, dst.name + ".part")
        runCatching {
            val sector = ByteArray(sectorSize)
            src.inputStream().buffered(sectorSize * 64).use { input ->
                tmp.outputStream().buffered(RAW_SECTOR * 64).use { output ->
                    while (true) {
                        var read = 0
                        while (read < sectorSize) {
                            val n = input.read(sector, read, sectorSize - read)
                            if (n < 0) break
                            read += n
                        }
                        if (read == 0) break
                        check(read == sectorSize) { "truncated final sector ($read bytes)" }
                        output.write(sector, 0, RAW_SECTOR)
                    }
                }
            }
            check(tmp.renameTo(dst)) { "rename to ${dst.name} failed" }
        }.onFailure {
            tmp.delete()
            throw it
        }
    }
}
