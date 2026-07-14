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

    /** Local co-op: keep the phone touch pad as Player 1 and route gamepads to P2+. */
    fun localMultiplayer(): Boolean = prefs.getBoolean("local_mp", false)
    fun setLocalMultiplayer(v: Boolean) { prefs.edit().putBoolean("local_mp", v).apply() }
}
