package com.musicplayer.app.data

import android.content.ContentUris
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val uri: Uri,
    val folder: String,
    /** ID do vídeo, quando a música está tocando direto da internet (não está na biblioteca). */
    val onlineId: String? = null,
    /** Capa vinda da internet (miniatura do vídeo), usada no lugar da capa do álbum. */
    val artUrl: String? = null,
) {
    val isOnline get() = onlineId != null
}

/** Prefixo do mediaId das músicas tocadas direto da internet. */
const val ONLINE_MEDIA_PREFIX = "online:"

data class Playlist(
    val id: String,
    val name: String,
    val songIds: List<Long>,
)

fun albumArtUri(albumId: Long): Uri =
    ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

fun Song.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(albumArtUri(albumId))
                .build()
        )
        .build()
