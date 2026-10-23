package com.musicplayer.app.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import java.io.File

object MusicRepository {

    /** Lê todas as músicas do aparelho pelo MediaStore (ignora áudios com menos de 10s). */
    fun loadSongs(context: Context): List<Song> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 10000"
        val songs = mutableListOf<Song>()

        context.contentResolver.query(collection, projection, selection, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (c.moveToNext()) {
                val path = c.getString(dataCol).orEmpty()
                if (isMessagingAudio(path)) continue
                val id = c.getLong(idCol)
                val artist = c.getString(artistCol)
                songs += Song(
                    id = id,
                    title = c.getString(titleCol) ?: "Sem título",
                    artist = if (artist.isNullOrBlank() || artist == "<unknown>") "Artista desconhecido" else artist,
                    album = c.getString(albumCol) ?: "Álbum desconhecido",
                    albumId = c.getLong(albumIdCol),
                    duration = c.getLong(durationCol),
                    uri = ContentUris.withAppendedId(collection, id),
                    folder = File(path).parent ?: "",
                )
            }
        }
        android.util.Log.d("Musica", "MediaStore retornou ${songs.size} músicas")
        return songs.sortedWith(compareBy(NaturalOrder) { it.title })
    }

    /** Compara textos tratando números como números: "Faixa 2" vem antes de "Faixa 10". */
    private object NaturalOrder : Comparator<String> {
        private val chunk = Regex("""\d+|\D+""")
        override fun compare(a: String, b: String): Int {
            val pa = chunk.findAll(a.lowercase()).map { it.value }.toList()
            val pb = chunk.findAll(b.lowercase()).map { it.value }.toList()
            for (i in 0 until minOf(pa.size, pb.size)) {
                val x = pa[i]; val y = pb[i]
                val c = if (x[0].isDigit() && y[0].isDigit()) {
                    x.trimStart('0').length.compareTo(y.trimStart('0').length).takeIf { it != 0 } ?: x.compareTo(y)
                } else x.compareTo(y)
                if (c != 0) return c
            }
            return pa.size.compareTo(pb.size)
        }
    }

    /**
     * Áudios de apps de mensagem (mensagens de voz, áudios recebidos) não são músicas.
     * Cobre o WhatsApp e o WhatsApp Business, tanto na pasta atual (Android/media) quanto na antiga.
     */
    private fun isMessagingAudio(path: String): Boolean {
        val p = path.lowercase()
        return "/android/media/com.whatsapp" in p || "/whatsapp/media/" in p || "/whatsapp business/media/" in p
    }
}
