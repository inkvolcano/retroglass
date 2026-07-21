package com.nvanloo.retroglass.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Archive classification, pinned against the failures that produced it.
 *
 * Every case here is either a real import bug that had to be diagnosed by pulling the archive
 * off the phone, or the rule added to stop it recurring. The logic is deliberately strict —
 * a blind whole-storage scan must not file a docs bundle or a PC ISO as a game — and it is the
 * strictness that keeps breaking on legitimate ROMs, so both directions are worth holding.
 */
class ArchiveClassificationTest {

    private fun classify(vararg entries: Pair<String, Long>): Console? =
        RomLibrary.classifyArchiveEntries(entries.toList())

    private val big = 4L * 1024 * 1024
    private val small = 8L * 1024

    @Test
    fun `cartridge extension names its console`() {
        assertEquals(Console.NES, classify("Duck Tales (USA).nes" to big))
        assertEquals(Console.SNES, classify("Super Mario World.sfc" to big))
    }

    @Test
    fun `a multi-megabyte md is a Mega Drive ROM, not Markdown`() {
        // Sonic & Knuckles never imported: ".md" sits in the docs blacklist, so the whole
        // archive was rejected as a README bundle.
        assertEquals(Console.MEGADRIVE, classify("Sonic & Knuckles.md" to big))
    }

    @Test
    fun `a small md is still Markdown`() {
        // The size test is the only thing separating the two, so the other side has to hold:
        // a README must not import as a Mega Drive game.
        assertNull(classify("README.md" to small))
    }

    @Test
    fun `the md size test runs before the docs rejection`() {
        // Ordering matters: the "clean archive" check below would throw the archive out as a
        // docs bundle before the size test ever ran.
        assertEquals(
            Console.MEGADRIVE,
            classify("README.md" to small, "Sonic & Knuckles.md" to big),
        )
    }

    @Test
    fun `disc containers default to PlayStation`() {
        // Shared across every disc system; the real console is decided by sniffing the content
        // after extraction, so the archive-level answer is only a starting point.
        assertEquals(Console.PSX, classify("Final Fantasy VIII.cue" to small, "FF8.bin" to big))
        assertEquals(Console.PSX, classify("Crazy Taxi.gdi" to small))
    }

    @Test
    fun `an ecm image is refused rather than half-imported`() {
        // .bin.ecm needs Reed-Solomon reconstruction we deliberately do not implement; letting
        // it through produces a disc that fails much later and far more confusingly.
        assertNull(classify("Metal Gear Solid (Disc 1).bin.ecm" to big))
    }

    @Test
    fun `a flat archive of unknown files is an arcade romset`() {
        // FBNeo reads the zip itself, so an arcade set is exactly the case where we must not
        // require a recognisable extension.
        assertEquals(Console.ARCADE, classify("068.bin" to small, "prom1" to small))
    }

    @Test
    fun `an archive with folders is not an arcade romset`() {
        assertNull(classify("romset/068.bin" to small, "romset/prom1" to small))
    }

    @Test
    fun `an archive holding documents or code is not a romset`() {
        assertNull(classify("readme.txt" to small, "setup.exe" to small))
        assertNull(classify("build.gradle" to small, "src.java" to small))
    }

    @Test
    fun `an empty archive classifies as nothing`() {
        assertNull(RomLibrary.classifyArchiveEntries(emptyList()))
    }

    @Test
    fun `a cartridge ROM wins over surrounding clutter`() {
        assertEquals(
            Console.GBA,
            classify("readme.txt" to small, "cover.png" to small, "game.gba" to big),
        )
    }
}
