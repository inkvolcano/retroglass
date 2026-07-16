package com.nvanloo.retroglass.controller

import android.content.Context
import com.swordfish.libretrodroid.Variable

/**
 * Per-console libretro core options. LibretroDroid exposes the core's live options
 * through GLRetroView.getVariables() (key + current value + description) and lets us
 * override them at load (GLRetroViewData.variables) or live (updateVariables). It does
 * NOT expose the list of *allowed* values, so for the handful of high-value options we
 * ship a curated choice list; every other option still shows up and is editable as text.
 *
 * Overrides are stored per console (a system-wide preference, e.g. "PS1 runs at 2x").
 */
class CoreOptions(context: Context) {

    private val prefs = context.getSharedPreferences("core_options", Context.MODE_PRIVATE)

    /** All stored overrides for a console as key -> value. Persisted one "key=value" per line. */
    fun overrides(consoleName: String): Map<String, String> {
        val raw = prefs.getString(consoleName, "") ?: ""
        return raw.split('\n').mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
        }.toMap()
    }

    fun override(consoleName: String, key: String): String? = overrides(consoleName)[key]

    fun setOverride(consoleName: String, key: String, value: String) {
        val map = overrides(consoleName).toMutableMap()
        map[key] = value
        save(consoleName, map)
    }

    fun clear(consoleName: String) {
        prefs.edit().remove(consoleName).apply()
    }

    private fun save(consoleName: String, map: Map<String, String>) {
        val raw = map.entries.joinToString("\n") { "${it.key}=${it.value}" }
        prefs.edit().putString(consoleName, raw).apply()
    }

    /** The overrides as LibretroDroid Variables, to pass into GLRetroViewData at load. */
    fun loadVariables(consoleName: String): Array<Variable> =
        overrides(consoleName).map { Variable(it.key, it.value, "") }.toTypedArray()

    companion object {
        /**
         * Curated allowed-value lists for well-known, high-impact core option keys, so
         * these get a proper picker instead of free text. label -> value. Keys are stable
         * across core versions; anything not listed here is still editable as raw text.
         */
        val KNOWN_VALUES: Map<String, List<Pair<String, String>>> = mapOf(
            // PS1 (pcsx_rearmed) — internal-resolution enhancement roughly doubles sharpness.
            "pcsx_rearmed_neon_enhancement_enable" to listOf("Off (native)" to "disabled", "On (2x hi-res)" to "enabled"),
            "pcsx_rearmed_neon_enhancement_no_main" to listOf("Off" to "disabled", "On (speed hack)" to "enabled"),
            "pcsx_rearmed_region" to listOf("Auto" to "auto", "NTSC" to "NTSC", "PAL" to "PAL"),
            "pcsx_rearmed_pad1type" to listOf("Digital" to "standard", "Analog" to "analog", "DualShock" to "dualshock"),
            // PSP (ppsspp) — internal render resolution.
            "ppsspp_internal_resolution" to listOf(
                "1x (480x272)" to "480x272", "2x (960x544)" to "960x544",
                "3x (1440x816)" to "1440x816", "4x (1920x1088)" to "1920x1088",
            ),
            "ppsspp_frameskip" to listOf("Off" to "0", "1" to "1", "2" to "2", "3" to "3"),
            // N64 (mupen64plus_next) — 4:3 internal size (GLideN64).
            "mupen64plus-43screensize" to listOf(
                "320x240 (native)" to "320x240", "640x480 (2x)" to "640x480",
                "960x720 (3x)" to "960x720", "1280x960 (4x)" to "1280x960",
            ),
            // SNES (snes9x)
            "snes9x_region" to listOf("Auto" to "auto", "NTSC" to "ntsc", "PAL" to "pal"),
            "snes9x_overscan" to listOf("Auto" to "auto", "Crop" to "disabled", "Show" to "enabled"),
            // Mega Drive (genesis_plus_gx)
            "genesis_plus_gx_region_detect" to listOf("Auto" to "auto", "NTSC-U" to "ntsc-u", "PAL" to "pal", "NTSC-J" to "ntsc-j"),
            // Dreamcast (flycast) — internal upscale.
            "reicast_internal_resolution" to listOf(
                "640x480 (native)" to "640x480", "1280x960 (2x)" to "1280x960", "1920x1440 (3x)" to "1920x1440",
            ),
            "flycast_internal_resolution" to listOf(
                "640x480 (native)" to "640x480", "1280x960 (2x)" to "1280x960", "1920x1440 (3x)" to "1920x1440",
            ),
        )
    }
}
