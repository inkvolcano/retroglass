package com.nvanloo.retroglass.controller

import android.content.Context
import android.view.KeyEvent

/**
 * Per-input configuration: which player port each controller drives, a per-controller
 * button remap (physical Android keycode -> RetroPad keycode), and a "left stick acts
 * as D-pad" option. Keyed by a device key ("phone" or a gamepad's stable descriptor).
 */
class InputConfig(context: Context) {

    private val prefs = context.getSharedPreferences("input_config", Context.MODE_PRIVATE)

    companion object {
        const val PHONE = "phone"
        const val PORT_OFF = -1

        /** RetroPad buttons offered for remapping (label -> Android keycode used as the retro id). */
        val RETRO_BUTTONS: List<Pair<String, Int>> = listOf(
            "A" to KeyEvent.KEYCODE_BUTTON_A,
            "B" to KeyEvent.KEYCODE_BUTTON_B,
            "X" to KeyEvent.KEYCODE_BUTTON_X,
            "Y" to KeyEvent.KEYCODE_BUTTON_Y,
            "L" to KeyEvent.KEYCODE_BUTTON_L1,
            "R" to KeyEvent.KEYCODE_BUTTON_R1,
            "L2" to KeyEvent.KEYCODE_BUTTON_L2,
            "R2" to KeyEvent.KEYCODE_BUTTON_R2,
            "L3" to KeyEvent.KEYCODE_BUTTON_THUMBL,
            "R3" to KeyEvent.KEYCODE_BUTTON_THUMBR,
            "Start" to KeyEvent.KEYCODE_BUTTON_START,
            "Select" to KeyEvent.KEYCODE_BUTTON_SELECT,
            "D-Up" to KeyEvent.KEYCODE_DPAD_UP,
            "D-Down" to KeyEvent.KEYCODE_DPAD_DOWN,
            "D-Left" to KeyEvent.KEYCODE_DPAD_LEFT,
            "D-Right" to KeyEvent.KEYCODE_DPAD_RIGHT,
        )
    }

    // ---------------------------------------------------------- player port

    /** Stored port for a device, or null if the user hasn't assigned one. */
    fun storedPort(deviceKey: String): Int? =
        if (prefs.contains("port/$deviceKey")) prefs.getInt("port/$deviceKey", 0) else null

    fun setPort(deviceKey: String, port: Int) {
        prefs.edit().putInt("port/$deviceKey", port).apply()
    }

    // ---------------------------------------------------------- button remap

    /** Physical keycode -> RetroPad keycode override for this device (empty = defaults). */
    fun bindings(deviceKey: String): Map<Int, Int> {
        val raw = prefs.getString("map/$deviceKey", "") ?: ""
        return raw.split(';').mapNotNull {
            val p = it.split(':')
            if (p.size == 2) (p[0].toIntOrNull() ?: return@mapNotNull null) to
                (p[1].toIntOrNull() ?: return@mapNotNull null) else null
        }.toMap()
    }

    fun bind(deviceKey: String, physicalKey: Int, retroKey: Int) {
        val map = bindings(deviceKey).toMutableMap()
        // A physical key drives at most one retro button; drop any prior use.
        map.entries.removeAll { it.value == retroKey }
        map[physicalKey] = retroKey
        prefs.edit().putString("map/$deviceKey", map.entries.joinToString(";") { "${it.key}:${it.value}" }).apply()
    }

    fun clearBindings(deviceKey: String) {
        prefs.edit().remove("map/$deviceKey").apply()
    }

    /** Physical key currently bound to a given retro button, if any. */
    fun physicalFor(deviceKey: String, retroKey: Int): Int? =
        bindings(deviceKey).entries.firstOrNull { it.value == retroKey }?.key

    // ---------------------------------------------------------- stick option

    fun leftStickAsDpad(deviceKey: String): Boolean = prefs.getBoolean("stickdpad/$deviceKey", false)
    fun setLeftStickAsDpad(deviceKey: String, v: Boolean) {
        prefs.edit().putBoolean("stickdpad/$deviceKey", v).apply()
    }
}
