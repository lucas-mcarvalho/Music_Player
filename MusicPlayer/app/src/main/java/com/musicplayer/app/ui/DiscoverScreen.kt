package com.musicplayer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musicplayer.app.Detail
import com.musicplayer.app.DetailKind
import com.musicplayer.app.MusicViewModel

@Composable
fun DiscoverScreen(vm: MusicViewModel, onLibrary: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceVariant)))
                    .padding(22.dp),
            ) {
                Text("Descubra", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Novas músicas", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                Text("Encontre sua próxima música favorita", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { vm.onlineOpen = true }) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Explorar músicas")
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Suas playlists", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onLibrary) { Text("Ver biblioteca") }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    LibraryCard("Favoritas", "${vm.favorites.size} músicas", vm.songs.firstOrNull { it.id in vm.favorites }?.albumId) {
                        vm.open(Detail(DetailKind.FAVORITES, "fav", "Favoritas"))
                    }
                }
                items(vm.playlists.size) { index ->
                    val playlist = vm.playlists[index]
                    val art = vm.songs.firstOrNull { it.id in playlist.songIds }?.albumId
                    LibraryCard(playlist.name, "${playlist.songIds.size} músicas", art) {
                        vm.open(Detail(DetailKind.PLAYLIST, playlist.id, playlist.name))
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Suas músicas", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onLibrary) { Text("Ver todas") }
            }
        }
        if (vm.songs.isEmpty()) {
            item { Text("Suas músicas aparecerão aqui.", Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(minOf(vm.songs.size, 8)) { index ->
                val song = vm.songs[index]
                SongRow(song, vm.player.song?.id == song.id,
                    onClick = { vm.play(vm.songs, index) },
                    onPlayNext = { vm.playNext(song) },
                    onAddToQueue = { vm.addToQueue(listOf(song)) },
                    onAddToPlaylist = { vm.songsForPlaylistDialog = listOf(song) })
            }
        }
    }
}

@Composable
fun LibraryCard(title: String, subtitle: String, albumId: Long?, onClick: () -> Unit) {
    Column(
        Modifier.width(138.dp).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick).padding(8.dp)
    ) {
        if (albumId != null) AlbumArt(albumId, Modifier.fillMaxWidth().aspectRatio(1f), corner = 10.dp)
        else Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
