package com.lucas.musica.ui

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lucas.musica.MusicViewModel
import com.lucas.musica.data.Song
import com.lucas.musica.data.albumArtUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------- Capas de álbum ----------

private val artCache = object : LruCache<Long, Bitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: Long, value: Bitmap) = value.byteCount
}
private val missingArt = mutableSetOf<Long>()

private fun loadArt(context: Context, albumId: Long): Bitmap? = try {
    if (Build.VERSION.SDK_INT >= 29) {
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId)
        context.contentResolver.loadThumbnail(uri, Size(400, 400), null)
    } else {
        context.contentResolver.openInputStream(albumArtUri(albumId))?.use { BitmapFactory.decodeStream(it) }
    }
} catch (e: Exception) {
    null
}

@Composable
fun rememberAlbumBitmap(albumId: Long): Bitmap? {
    val context = LocalContext.current
    var bitmap by remember(albumId) { mutableStateOf(artCache.get(albumId)) }
    LaunchedEffect(albumId) {
        if (bitmap == null && albumId !in missingArt) {
            val loaded = withContext(Dispatchers.IO) { loadArt(context, albumId) }
            if (loaded != null) artCache.put(albumId, loaded) else missingArt += albumId
            bitmap = loaded
        }
    }
    return bitmap
}

/** Matiz (0..360) estável para cada álbum, usado quando não há capa. */
fun albumHue(albumId: Long): Float = Math.floorMod(albumId * 47, 360L).toFloat()

@Composable
fun AlbumArt(albumId: Long, modifier: Modifier = Modifier, corner: Dp = 8.dp) {
    val bitmap = rememberAlbumBitmap(albumId)
    val hue = albumHue(albumId)
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(
                Brush.linearGradient(listOf(Color.hsv(hue, 0.45f, 0.85f), Color.hsv((hue + 40) % 360, 0.6f, 0.6f)))
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.fillMaxSize(0.45f))
        }
    }
}

fun formatTime(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

// ---------- Lista de músicas ----------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRemove: (() -> Unit)? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick?.let { lc -> { haptics.performHapticFeedback(HapticFeedbackType.LongPress); lc() } },
            )
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArt(song.albumId, Modifier.size(48.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                "${song.artist} · ${song.album}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onClick() }, modifier = Modifier.padding(end = 4.dp))
        } else Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreVert, "Opções", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Tocar a seguir") }, onClick = { menu = false; onPlayNext() })
                DropdownMenuItem(text = { Text("Adicionar à playlist") }, onClick = { menu = false; onAddToPlaylist() })
                if (onRemove != null) {
                    DropdownMenuItem(text = { Text("Remover da playlist") }, onClick = { menu = false; onRemove() })
                }
            }
        }
    }
}

@Composable
fun SongList(
    vm: MusicViewModel,
    songs: List<Song>,
    modifier: Modifier = Modifier,
    playlistId: String? = null,
    header: (@Composable () -> Unit)? = null,
) {
    val selectionMode = vm.selection.isNotEmpty()
    LazyColumn(modifier.fillMaxSize()) {
        if (header != null) item { header() }
        items(songs.size) { i ->
            val song = songs[i]
            SongRow(
                song = song,
                isCurrent = vm.player.song?.id == song.id,
                onClick = { if (selectionMode) vm.toggleSelect(song) else vm.play(songs, i) },
                onPlayNext = { vm.playNext(song) },
                onAddToPlaylist = { vm.songsForPlaylistDialog = listOf(song) },
                onRemove = playlistId?.let { id -> { vm.removeFromPlaylist(id, song) } },
                selectionMode = selectionMode,
                selected = song.id in vm.selection,
                onLongClick = { vm.toggleSelect(song) },
            )
        }
    }
}

@Composable
fun EmptyMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------- Mini player ----------

@Composable
fun MiniPlayer(vm: MusicViewModel, onOpen: () -> Unit) {
    val state = vm.player
    val song = state.song ?: return
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.navigationBarsPadding()) {
            LinearProgressIndicator(
                progress = { if (state.duration > 0) state.position.toFloat() / state.duration else 0f },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 0.dp),
                trackColor = Color.Transparent,
                drawStopIndicator = {},
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AlbumArt(song.albumId, Modifier.size(44.dp), corner = 22.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(
                        song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = vm::togglePlay) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (state.isPlaying) "Pausar" else "Tocar",
                        modifier = Modifier.size(32.dp),
                    )
                }
                IconButton(onClick = vm::next) { Icon(Icons.Rounded.SkipNext, "Próxima") }
            }
        }
    }
}

// ---------- Diálogos ----------

@Composable
fun NewPlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova playlist") },
        text = {
            OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("Nome da playlist") })
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Criar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun AddToPlaylistDialog(vm: MusicViewModel, songs: List<Song>) {
    var creating by remember { mutableStateOf(false) }
    val close = { vm.songsForPlaylistDialog = null }
    val add = { playlistId: String ->
        vm.addToPlaylist(playlistId, songs)
        vm.clearSelection()
        close()
    }
    if (creating) {
        NewPlaylistDialog(onDismiss = close) { name -> add(vm.createPlaylist(name).id) }
        return
    }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (songs.size == 1) "Adicionar à playlist" else "Adicionar ${songs.size} músicas") },
        text = {
            LazyColumn {
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable { creating = true }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Nova playlist", color = MaterialTheme.colorScheme.primary)
                    }
                }
                items(vm.playlists.size) { i ->
                    val p = vm.playlists[i]
                    Row(
                        Modifier.fillMaxWidth().clickable { add(p.id) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null)
                        Spacer(Modifier.width(12.dp))
                        Text(p.name, Modifier.weight(1f))
                        Text("${p.songIds.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("Fechar") } },
    )
}

@Composable
fun PermissionScreen(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.size(24.dp))
        Text("Acesso às suas músicas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(8.dp))
        Text(
            "Para tocar as músicas salvas no celular, o app precisa de permissão para ler seus arquivos de áudio.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.size(24.dp))
        Button(onClick = onRequest) { Text("Permitir acesso") }
    }
}
