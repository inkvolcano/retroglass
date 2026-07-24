package com.nvanloo.retroglass.model

import android.content.Context

/**
 * Known BIOS requirements per system. Several cores black-screen without their BIOS,
 * so the library surfaces which files are present in the system directory.
 */
object BiosCatalog {

    data class BiosReq(
        val system: String,
        /**
         * Filenames for this system. By default these are *alternatives* — any one satisfies the
         * requirement, which is right for PlayStation's several BIOS revisions. When
         * [requiresAll] is set they are instead all mandatory, as for Dreamcast's boot ROM plus
         * flash, or Intellivision's exec plus grom.
         */
        val files: List<String>,
        val note: String,
        /** True when every name in [files] must be present, not just one of them. */
        val requiresAll: Boolean = false,
    )

    val requirements: List<BiosReq> = listOf(
        BiosReq("PlayStation", listOf("scph5501.bin", "scph1001.bin", "scph7001.bin", "psxonpsp660.bin"),
            "Recommended (HLE works without)"),
        BiosReq("PlayStation 2", listOf("scph39001.bin", "ps2-0230a-20080220.bin", "ps2_bios.bin"),
            "Required for most games"),
        // Flycast needs the flash/NVRAM image as well as the boot ROM; with dc_boot.bin alone
        // the status screen used to show a green tick anyway. The app now falls back to HLE
        // BIOS when either file is absent.
        BiosReq("Dreamcast", listOf("dc_boot.bin", "dc_flash.bin"),
            "Required together — without both, HLE BIOS is used", requiresAll = true),
        BiosReq("Saturn", listOf("sega_101.bin", "mpr-17933.bin", "saturn_bios.bin"), "Required"),
        BiosReq("3DO", listOf("panafz1.bin", "panafz10.bin", "goldstar.bin"), "Required"),
        BiosReq("ColecoVision", listOf("coleco.rom", "colecovision.rom"), "Required"),
        BiosReq("Intellivision", listOf("exec.bin", "grom.bin"), "Required (needs both)", requiresAll = true),
        BiosReq("Amiga (Kickstart)", listOf("kick34005.a500", "kick40068.a1200", "kick.rom"), "Required"),
        BiosReq("Famicom Disk System", listOf("disksys.rom"), "Only for .fds games"),
        BiosReq("Sega CD / Mega-CD", listOf("bios_CD_U.bin", "bios_CD_E.bin", "bios_CD_J.bin"),
            "Required — region BIOS (US / EU / JP)"),
        BiosReq("PC Engine CD", listOf("syscard3.pce"), "Required — Super System Card 3"),
        BiosReq("Neo Geo CD", listOf("neocd.bin", "neocd_z.bin", "neocd_sz.bin", "neocd_f.bin"),
            "Required — CD BIOS (front / top / CDZ)"),
        BiosReq("Sega NAOMI", listOf("naomi.zip"), "Required — NAOMI BIOS zip"),
        BiosReq("Atomiswave", listOf("awbios.zip"), "Required — Atomiswave BIOS zip"),
    )

    /** Every accepted BIOS filename across all systems, lowercased — for auto-routing on import. */
    val knownBiosNames: Set<String> = requirements.flatMap { it.files }.map { it.lowercase() }.toSet()

    /** True if a picked file is a recognised BIOS that belongs in the system directory. */
    fun isBios(fileName: String): Boolean = fileName.lowercase() in knownBiosNames

    data class Status(val system: String, val present: Boolean, val note: String, val filenames: String)

    fun status(context: Context): List<Status> {
        val present = RomLibrary.systemDir(context).listFiles()
            ?.map { it.name.lowercase() }?.toSet() ?: emptySet()
        return requirements.map { req ->
            // "Needs both" requirements were reported with .any, so one file of two showed a
            // green tick and the game then failed with no explanation.
            val have =
                if (req.requiresAll) req.files.all { it.lowercase() in present }
                else req.files.any { it.lowercase() in present }
            Status(req.system, have, req.note, req.files.joinToString(" + "))
        }
    }

    /** The requirement for a console, matched on its display name, or null if it needs no BIOS. */
    fun requirementFor(console: Console): BiosReq? =
        requirements.firstOrNull { it.system.equals(console.displayName, ignoreCase = true) }

    /**
     * BIOS files this console needs that are not in the system directory. Empty when satisfied
     * (including when only one of several accepted alternatives is present).
     */
    fun missingFiles(context: Context, console: Console): List<String> {
        val req = requirementFor(console) ?: return emptyList()
        val present = RomLibrary.systemDir(context).listFiles()
            ?.map { it.name.lowercase() }?.toSet() ?: emptySet()
        return if (req.requiresAll) {
            req.files.filter { it.lowercase() !in present }
        } else {
            if (req.files.any { it.lowercase() in present }) emptyList() else req.files
        }
    }
}
