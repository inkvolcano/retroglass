package com.nvanloo.retroglass.model

import android.content.Context

/**
 * Tracks favorite games and recently-played order, keyed by absolute ROM path.
 */
class GameHistory(context: Context) {

    private val prefs = context.getSharedPreferences("game_history", Context.MODE_PRIVATE)

    fun favorites(): Set<String> = prefs.getStringSet(KEY_FAV, emptySet()) ?: emptySet()

    fun isFavorite(path: String): Boolean = path in favorites()

    fun toggleFavorite(path: String) {
        val favs = favorites().toMutableSet()
        if (!favs.add(path)) favs.remove(path)
        prefs.edit().putStringSet(KEY_FAV, favs).apply()
    }

    /** Most-recent first. */
    fun recents(): List<String> =
        prefs.getString(KEY_RECENT, "")!!.split('\n').filter { it.isNotBlank() }

    fun recordPlayed(path: String) {
        val list = recents().toMutableList()
        list.remove(path)
        list.add(0, path)
        while (list.size > MAX_RECENT) list.removeAt(list.size - 1)
        prefs.edit().putString(KEY_RECENT, list.joinToString("\n")).apply()
    }

    companion object {
        private const val KEY_FAV = "favorites"
        private const val KEY_RECENT = "recents"
        private const val MAX_RECENT = 10
    }
}
