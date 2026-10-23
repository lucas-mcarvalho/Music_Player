package com.musicplayer.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musicplayer.app.Detail
import com.musicplayer.app.DetailKind
import com.musicplayer.app.MusicViewModel

@Composable
fun DetailScreen(vm: MusicViewModel, detail: Detail) {
    val songs = vm.songsFor(detail)
    var confirmDelete by remember { mutableStateOf(false) }
    val subtitle = when (detail.kind) {
        DetailKind.ARTIST -> "Artista"
        DetailKind.ALBUM -> songs.firstOrNull()?.artist ?: "Álbum"
        DetailKind.FOLDER -> detail.key
        DetailKind.PLAYLIST, DetailKind.FAVORITES -> "Playlist"
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (vm.selection.isNotEmpty()) SelectionTopBar(vm, songs)
        else Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar") }
            Spacer(Modifier.weight(1f))
            if (detail.kind == DetailKind.PLAYLIST) {
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Excluir playlist") }
            }
        }

        SongList(
            vm, songs,
            playlistId = detail.key.takeIf { detail.kind == DetailKind.PLAYLIST },
            header = {
                Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AlbumArt(songs.firstOrNull()?.albumId ?: -1L, Modifier.size(120.dp), corner = 16.dp)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                detail.title, style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${songs.size} músicas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.size(16.dp))
                    Row {
                        Button(onClick = { vm.play(songs) }, enabled = songs.isNotEmpty(), modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Tocar")
                        }
                        Spacer(Modifier.width(12.dp))
                        FilledTonalButton(onClick = { vm.play(songs, shuffle = true) }, enabled = songs.isNotEmpty(), modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Shuffle, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Aleatório")
                        }
                    }
                    if (songs.isEmpty()) {
                        Spacer(Modifier.size(24.dp))
                        Text(
                            "Nenhuma música aqui ainda. Use o menu ⋮ de uma música para adicioná-la.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir playlist?") },
            text = { Text("A playlist \"${detail.title}\" será excluída. As músicas continuam no celular.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deletePlaylist(detail.key) }) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}
