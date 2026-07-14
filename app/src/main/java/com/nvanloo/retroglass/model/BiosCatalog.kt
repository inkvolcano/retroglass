package com.nvanloo.retroglass.model

import android.content.Context

/**
 * Known BIOS requirements per system. Several cores black-screen without their BIOS,
 * so the library surfaces which files are present in the system directory.
 */
object BiosCatalog {

    data class BiosReq(
        val system: String,
        /** Accepted filenames (any one satisfies the requirement). */
        val files: List<String>,
        val note: String,
    )

    val requirements: List<BiosReq> = listOf(
        BiosReq("PlayStation", listOf("scph5501.bin", "scph1001.bin", "scph7001.bin", "psxonpsp660.bin"),
            "Recommended (HLE works without)"),
        BiosReq("PlayStation 2", listOf("scph39001.bin", "ps2-0230a-20080220.bin", "ps2_bios.bin"),
            "Required for most games"),
        BiosReq("Dreamcast", listOf("dc_boot.bin"), "Recommended"),
        BiosReq("Saturn", listOf("sega_101.bin", "mpr-17933.bin", "saturn_bios.bin"), "Required"),
        BiosReq("3DO", listOf("panafz1.bin", "panafz10.bin", "goldstar.bin"), "Required"),
        BiosReq("ColecoVision", listOf("coleco.rom", "colecovision.rom"), "Required"),
        BiosReq("Intellivision", listOf("exec.bin", "grom.bin"), "Required (needs both)"),
        BiosReq("Amiga (Kickstart)", listOf("kick34005.a500", "kick40068.a1200", "kick.rom"), "Required"),
        BiosReq("Famicom Disk System", listOf("disksys.rom"), "Only for .fds games"),
    )

    data class Status(val system: String, val present: Boolean, val note: String, val filenames: String)

    fun status(context: Context): List<Status> {
        val present = RomLibrary.systemDir(context).listFiles()
            ?.map { it.name.lowercase() }?.toSet() ?: emptySet()
        return requirements.map { req ->
            val have = req.files.any { it.lowercase() in present }
            Status(req.system, have, req.note, req.files.first())
        }
    }
}
