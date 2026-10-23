package com.musicplayer.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Guarda playlists e favoritas em SharedPreferences (num app final, trocar por Room). */
/** O que estava tocando quando o app foi fechado. */
data class PlaybackSession(
    val queue: List<Long>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
)

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

    fun saveSession(s: PlaybackSession) {
        prefs.edit()
            .putString("sessao_fila", s.queue.joinToString(","))
            .putInt("sessao_indice", s.index)
            .putLong("sessao_posicao", s.positionMs)
            .putBoolean("sessao_aleatorio", s.shuffle)
            .putInt("sessao_repetir", s.repeatMode)
            .apply()
    }

    fun clearSession() {
        prefs.edit()
            .remove("sessao_fila").remove("sessao_indice").remove("sessao_posicao")
            .remove("sessao_aleatorio").remove("sessao_repetir")
            .apply()
    }

    fun savePosition(positionMs: Long) {
        prefs.edit().putLong("sessao_posicao", positionMs).apply()
    }

    fun loadSession(): PlaybackSession? {
        val queue = prefs.getString("sessao_fila", null)?.split(",")?.mapNotNull { it.toLongOrNull() }
        if (queue.isNullOrEmpty()) return null
        return PlaybackSession(
            queue = queue,
            index = prefs.getInt("sessao_indice", 0),
            positionMs = prefs.getLong("sessao_posicao", 0),
            shuffle = prefs.getBoolean("sessao_aleatorio", false),
            repeatMode = prefs.getInt("sessao_repetir", 0),
        )
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
