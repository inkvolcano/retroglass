package com.nvanloo.retroglass.controller

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One cheat: a name, the raw code (as the core expects — e.g. GameShark / PAR lines), and enabled flag. */
data class Cheat(val name: String, val code: String, val enabled: Boolean)

/**
 * Per-game cheat list. Applied through GLRetroView.setCheat(index, enabled, code). The
 * libretro cheat index just has to be stable per session, so we use the list position.
 * Stored as JSON per game key (the ROM's absolute path).
 */
class Cheats(context: Context) {

    private val prefs = context.getSharedPreferences("cheats", Context.MODE_PRIVATE)

    fun list(gameKey: String): List<Cheat> {
        val raw = prefs.getString(gameKey, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Cheat(o.getString("name"), o.getString("code"), o.optBoolean("on", false))
            }
        }.getOrDefault(emptyList())
    }

    fun save(gameKey: String, cheats: List<Cheat>) {
        val arr = JSONArray()
        cheats.forEach {
            arr.put(JSONObject().put("name", it.name).put("code", it.code).put("on", it.enabled))
        }
        prefs.edit().putString(gameKey, arr.toString()).apply()
    }

    fun add(gameKey: String, cheat: Cheat) = save(gameKey, list(gameKey) + cheat)

    fun setEnabled(gameKey: String, index: Int, enabled: Boolean) {
        val l = list(gameKey).toMutableList()
        if (index in l.indices) { l[index] = l[index].copy(enabled = enabled); save(gameKey, l) }
    }

    fun removeAt(gameKey: String, index: Int) {
        val l = list(gameKey).toMutableList()
        if (index in l.indices) { l.removeAt(index); save(gameKey, l) }
    }
}
