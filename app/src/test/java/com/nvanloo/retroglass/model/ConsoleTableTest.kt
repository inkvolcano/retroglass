package com.nvanloo.retroglass.model

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Invariants over the console table itself.
 *
 * The table is 40-odd hand-written enum entries, and most mistakes in it fail quietly: a typo'd
 * native height silently changes the Auto upscale factor, a duplicated extension silently sends
 * a game to whichever console happens to be declared first. These check the shape of the data
 * rather than any behaviour, which is exactly what nothing else would catch.
 */
class ConsoleTableTest {

    @Test
    fun `every console is described`() {
        for (c in Console.entries) {
            assertTrue("${c.name} has no display name", c.displayName.isNotBlank())
            assertTrue("${c.name} has no maker", c.maker.isNotBlank())
            assertTrue(
                "${c.name} core does not look like a library: ${c.coreLibName}",
                c.coreLibName.startsWith("lib") && c.coreLibName.endsWith(".so"),
            )
        }
    }

    @Test
    fun `every console claims at least one ROM extension`() {
        for (c in Console.entries) {
            assertTrue("${c.name} claims no extensions", c.romExtensions.isNotEmpty())
        }
    }

    @Test
    fun `extensions are lower case`() {
        // forExtension lowercases its argument before comparing, so an upper-case entry in the
        // table could never match anything.
        for (c in Console.entries) {
            for (e in c.romExtensions) {
                assertTrue("${c.name} declares '$e' with upper case", e == e.lowercase())
            }
        }
    }

    @Test
    fun `native heights are plausible`() {
        // Feeds the Auto upscale factor as viewHeight / nativeHeight, clamped to 2..4. A typo
        // by a factor of ten would not crash - it would just quietly pin Auto to one end.
        for (c in Console.entries) {
            val h = c.nativeHeight
            assertTrue("${c.name} native height $h is out of range", h in 32..640)
        }
    }

    @Test
    fun `only genuinely shared extensions are claimed twice`() {
        // forExtension returns the first console declaring the extension, so any overlap makes
        // enum declaration order decide where a game lands. That is fine for disc containers,
        // which are shared across systems by design and get resolved later by sniffing the
        // content - and a bug for anything else.
        val sharedByDesign = setOf(
            "cue", "iso", "chd", "gdi", "cdi", "img", "pbp", "ccd", "mds", "mdf", "nrg", "cso",
            "bin", "zip", "7z", "m3u",
            // Genuinely shared and not resolvable from the bytes: Dreamcast and PS2 homebrew
            // are both ELF executables. A folder naming the system decides; failing that, enum
            // order picks and the user can move the game with "Change system".
            "elf",
        )
        val owners = mutableMapOf<String, MutableList<String>>()
        for (c in Console.entries) {
            for (e in c.romExtensions) owners.getOrPut(e) { mutableListOf() }.add(c.name)
        }
        val unexpected = owners
            .filter { (ext, cs) -> cs.size > 1 && ext !in sharedByDesign }
            .map { (ext, cs) -> "$ext -> ${cs.joinToString()}" }
        assertTrue("extensions claimed by more than one console: $unexpected", unexpected.isEmpty())
    }
}
