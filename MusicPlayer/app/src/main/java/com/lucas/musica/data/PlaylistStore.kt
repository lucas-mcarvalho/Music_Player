package com.lucas.musica.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Guarda playlists e favoritas em SharedPreferences (num app final, trocar por Room). */
class PlaylistStore(context: Context) {
    private val prefs = context.getSharedPreferences("biblioteca", Context.MODE_PRIVATE)

    fun loadPlaylists(): List<Playlist> {
        val json = JSONArray(prefs.getString("playlists", "[]"))
        return (0 until json.length()).map { i ->
            val o = json.getJSONObject(i)
            val ids = o.getJSONArray("songs")
            Playlist(o.getString("id"), o.getString("name"), (0 until ids.length()).map { ids.getLong(it) })
        }
    }

    fun savePlaylists(playlists: List<Playlist>) {
        val json = JSONArray()
        playlists.forEach { p ->
            json.put(JSONObject().put("id", p.id).put("name", p.name).put("songs", JSONArray(p.songIds)))
        }
        prefs.edit().putString("playlists", json.toString()).apply()
    }

    fun loadTheme(): String? = prefs.getString("theme", null)

    fun saveTheme(name: String) {
        prefs.edit().putString("theme", name).apply()
    }

    fun loadFavorites(): Set<Long> =
        prefs.getStringSet("favorites", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()

    fun saveFavorites(ids: Set<Long>) {
        prefs.edit().putStringSet("favorites", ids.map { it.toString() }.toSet()).apply()
    }
}
