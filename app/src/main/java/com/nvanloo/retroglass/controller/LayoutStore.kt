package com.nvanloo.retroglass.controller

import android.content.Context
import com.nvanloo.retroglass.model.Console

/**
 * Persists controller state in SharedPreferences:
 *  - which layout preset is selected per console
 *  - per-control position/scale tweaks the user made on top of a preset
 *  - the global video-scale value for the external display
 *
 * Per-control overrides are keyed by console + preset so switching presets and
 * tweaking them don't clobber each other.
 */
class LayoutStore(context: Context) {

    private val prefs = context.getSharedPreferences("controller_layouts", Context.MODE_PRIVATE)

    // -------------------------------------------------- selected preset

    fun selectedPreset(console: Console): String? =
        prefs.getString("preset/${console.prefKey}", null)

    fun setSelectedPreset(console: Console, presetId: String) {
        prefs.edit().putString("preset/${console.prefKey}", presetId).apply()
    }

    // -------------------------------------------------- per-control tweaks

    private fun overridePrefix(console: Console, presetId: String) =
        "${console.prefKey}/$presetId/"

    fun load(console: Console, presetId: String): MutableMap<String, ControlPlacement> {
        val result = mutableMapOf<String, ControlPlacement>()
        val prefix = overridePrefix(console, presetId)
        for ((key, value) in prefs.all) {
            if (!key.startsWith(prefix)) continue
            val parts = (value as? String)?.split(';') ?: continue
            if (parts.size != 3) continue
            val cx = parts[0].toFloatOrNull() ?: continue
            val cy = parts[1].toFloatOrNull() ?: continue
            val scale = parts[2].toFloatOrNull() ?: continue
            result[key.removePrefix(prefix)] = ControlPlacement(cx, cy, scale)
        }
        return result
    }

    fun save(console: Console, presetId: String, placements: Map<String, ControlPlacement>) {
        val editor = prefs.edit()
        val prefix = overridePrefix(console, presetId)
        for ((id, p) in placements) {
            editor.putString("$prefix$id", "${p.cx};${p.cy};${p.scale}")
        }
        editor.apply()
    }

    fun reset(console: Console, presetId: String) {
        val editor = prefs.edit()
        val prefix = overridePrefix(console, presetId)
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        editor.apply()
    }

    // -------------------------------------------------- video scale

    /** Fraction of the external display the game fills, 0.4..1.0 (glasses). */
    fun videoScale(): Float = prefs.getFloat("video_scale", 1.0f).coerceIn(0.4f, 1.0f)

    fun setVideoScale(value: Float) {
        prefs.edit().putFloat("video_scale", value.coerceIn(0.4f, 1.0f)).apply()
    }

    /** Fraction of the phone the game fills when no external display is present. */
    fun localVideoScale(): Float = prefs.getFloat("video_scale_local", 0.62f).coerceIn(0.3f, 1.0f)

    fun setLocalVideoScale(value: Float) {
        prefs.edit().putFloat("video_scale_local", value.coerceIn(0.3f, 1.0f)).apply()
    }

    /** Portrait split: fraction of the height given to the game screen (top). */
    fun portraitScreenFraction(): Float = prefs.getFloat("portrait_frac", 0.42f).coerceIn(0.25f, 0.7f)

    fun setPortraitScreenFraction(value: Float) {
        prefs.edit().putFloat("portrait_frac", value.coerceIn(0.25f, 0.7f)).apply()
    }

    /** Where/how the game is shown, chosen in the library. See LayoutStore.SCREEN_* constants:
     *  Auto (follow display + rotation), Internal portrait, Internal landscape, External display,
     *  Fullscreen landscape (game fills the phone, physical gamepad drives — no touch pad). */
    fun screenMode(): Int = prefs.getInt("screen_mode", SCREEN_AUTO)
    fun setScreenMode(v: Int) { prefs.edit().putInt("screen_mode", v).apply() }

    /** Screen rotation in degrees: 0, 90, 180, 270. */
    fun videoRotation(): Int = prefs.getInt("video_rotation", 0)

    fun setVideoRotation(value: Int) {
        prefs.edit().putInt("video_rotation", ((value % 360) + 360) % 360).apply()
    }

    /** Screen position offset as a fraction of the container, -0.5..0.5. */
    fun videoOffsetX(): Float = prefs.getFloat("video_off_x", 0f).coerceIn(-0.5f, 0.5f)
    fun videoOffsetY(): Float = prefs.getFloat("video_off_y", 0f).coerceIn(-0.5f, 0.5f)

    fun setVideoOffset(x: Float, y: Float) {
        prefs.edit()
            .putFloat("video_off_x", x.coerceIn(-0.5f, 0.5f))
            .putFloat("video_off_y", y.coerceIn(-0.5f, 0.5f))
            .apply()
    }

    /** Rumble → phone vibration. */
    fun rumbleEnabled(): Boolean = prefs.getBoolean("rumble", true)
    fun setRumbleEnabled(v: Boolean) { prefs.edit().putBoolean("rumble", v).apply() }

    /** Video filter: 0=Off/Default, 1=CRT, 2=LCD, 3=Sharp. */
    fun shaderIndex(): Int = prefs.getInt("shader", 0)
    fun setShaderIndex(v: Int) { prefs.edit().putInt("shader", v).apply() }

    /**
     * A stacked video-filter combo as an ordered list of tokens (e.g. ["fsr1","crt"]).
     * When non-empty it takes precedence over [shaderIndex]; empty means use the single
     * filter. Stored comma-separated.
     */
    fun comboFilters(): List<String> =
        prefs.getString("shader_combo", "")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

    fun setComboFilters(tokens: List<String>) {
        prefs.edit().putString("shader_combo", tokens.joinToString(",")).apply()
    }

    // Per-console filter settings. Each system remembers its own look (SNES keeps SABR while
    // PS1 keeps de-dither+FSR1); the old global value is the fallback so existing installs
    // carry their current choice over to every system on first use.

    fun shaderIndex(console: Console): Int =
        prefs.getInt("shader/${console.prefKey}", prefs.getInt("shader", 0))

    fun setShaderIndex(console: Console, v: Int) {
        prefs.edit().putInt("shader/${console.prefKey}", v).apply()
    }

    fun comboFilters(console: Console): List<String> {
        val stored = prefs.getString("shader_combo/${console.prefKey}", null)
            ?: prefs.getString("shader_combo", "")
        return stored?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    }

    fun setComboFilters(console: Console, tokens: List<String>) {
        prefs.edit().putString("shader_combo/${console.prefKey}", tokens.joinToString(",")).apply()
    }

    // Named filter presets ("my CRT look"). The blob is an opaque key=value;… string owned
    // by the caller, so adding new tunables later doesn't need a store change.

    private val presetPrefix = "preset/"

    fun presetNames(): List<String> = prefs.all.keys
        .filter { it.startsWith(presetPrefix) }
        .map { it.removePrefix(presetPrefix) }
        .sorted()

    fun savePreset(name: String, blob: String) {
        prefs.edit().putString(presetPrefix + name, blob).apply()
    }

    fun loadPreset(name: String): String? = prefs.getString(presetPrefix + name, null)

    fun deletePreset(name: String) {
        prefs.edit().remove(presetPrefix + name).apply()
    }

    /** A 0..1 tuning value for a named filter parameter (glow, scanline depth, …). */
    fun filterParam(key: String, def: Float): Float =
        prefs.getFloat("fparam/$key", def).coerceIn(0f, 1f)

    fun setFilterParam(key: String, v: Float) {
        prefs.edit().putFloat("fparam/$key", v.coerceIn(0f, 1f)).apply()
    }

    /** Sharpen amount for the CAS / FSR1 filters, 0..1 (higher = sharper). */
    fun filterSharpness(): Float = prefs.getFloat("filter_sharpness", 0.5f).coerceIn(0f, 1f)
    fun setFilterSharpness(v: Float) {
        prefs.edit().putFloat("filter_sharpness", v.coerceIn(0f, 1f)).apply()
    }

    /** Local co-op: keep the phone touch pad as Player 1 and route gamepads to P2+. */
    fun localMultiplayer(): Boolean = prefs.getBoolean("local_mp", false)
    fun setLocalMultiplayer(v: Boolean) { prefs.edit().putBoolean("local_mp", v).apply() }

    /** Show a live FPS counter over the game. */
    fun fpsOverlay(): Boolean = prefs.getBoolean("fps_overlay", false)
    fun setFpsOverlay(v: Boolean) { prefs.edit().putBoolean("fps_overlay", v).apply() }

    /** What the phone shows while the game is on an external display. See PHONE_PANEL_* constants:
     *  Auto (dashboard when a physical gamepad drives and the phone isn't a player, else touch pad),
     *  Controller (always the touch pad), Dashboard (always the stats + mapping companion). */
    fun phonePanelMode(): Int = prefs.getInt("phone_panel", PHONE_PANEL_AUTO)
    fun setPhonePanelMode(v: Int) { prefs.edit().putInt("phone_panel", v).apply() }

    /** Bezel/background behind the game: 0=None, 1=Dark, 2=Gradient, 3=Custom image. */
    fun bezelMode(): Int = prefs.getInt("bezel_mode", 1)
    fun setBezelMode(v: Int) { prefs.edit().putInt("bezel_mode", v).apply() }

    /** Absolute path of the user's custom bezel image (used when bezelMode == 3). */
    fun bezelImagePath(): String? = prefs.getString("bezel_image", null)
    fun setBezelImagePath(path: String?) { prefs.edit().putString("bezel_image", path).apply() }

    /** Gyro aiming: feed the phone's motion to the right analog stick. */
    fun gyroAim(): Boolean = prefs.getBoolean("gyro_aim", false)
    fun setGyroAim(v: Boolean) { prefs.edit().putBoolean("gyro_aim", v).apply() }

    /** Gyro sensitivity multiplier, 0.2..3.0. */
    fun gyroSensitivity(): Float = prefs.getFloat("gyro_sens", 1.0f).coerceIn(0.2f, 3.0f)
    fun setGyroSensitivity(v: Float) { prefs.edit().putFloat("gyro_sens", v.coerceIn(0.2f, 3.0f)).apply() }

    /** Control ids set to turbo/autofire, per console. */
    fun turboButtons(console: Console): Set<String> =
        prefs.getStringSet("turbo/${console.prefKey}", emptySet())?.toSet() ?: emptySet()

    fun setTurbo(console: Console, id: String, on: Boolean) {
        val s = turboButtons(console).toMutableSet()
        if (on) s.add(id) else s.remove(id)
        prefs.edit().putStringSet("turbo/${console.prefKey}", s).apply()
    }

    companion object {
        const val SCREEN_AUTO = 0          // follow connected display + device rotation (default)
        const val SCREEN_INT_PORTRAIT = 1  // on the phone, portrait: game on top, pad below
        const val SCREEN_INT_LANDSCAPE = 2 // on the phone, landscape: game centred, pad frames it
        const val SCREEN_EXTERNAL = 3      // game on an external display, phone is the pad
        const val SCREEN_FULLSCREEN = 4    // game fills the phone (landscape), physical gamepad, no touch pad

        const val PHONE_PANEL_AUTO = 0        // dashboard when a gamepad drives and the phone isn't a player
        const val PHONE_PANEL_CONTROLLER = 1  // always the touch pad
        const val PHONE_PANEL_DASHBOARD = 2   // always the stats + mapping companion
    }
}
