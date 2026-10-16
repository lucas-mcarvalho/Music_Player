package com.lucas.musica.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import com.lucas.musica.MusicViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun NowPlayingScreen(vm: MusicViewModel, onClose: () -> Unit) {
    val state = vm.player
    val song = state.song ?: return
    LightStatusBarIcons()

    // Cor de fundo tirada da capa do álbum
    val bitmap = rememberAlbumBitmap(song.albumId)
    var dominant by remember { mutableStateOf(Color(0xFF3A3A3A)) }
    LaunchedEffect(bitmap) {
        dominant = bitmap?.let { bmp ->
            withContext(Dispatchers.Default) {
                val p = Palette.from(bmp).generate()
                (p.darkVibrantSwatch ?: p.darkMutedSwatch ?: p.dominantSwatch)?.rgb?.let { Color(it) }
            }
        } ?: Color.hsv(albumHue(song.albumId), 0.5f, 0.45f)
    }
    val bg by animateColorAsState(dominant, tween(600), label = "bg")

    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = state.duration.coerceAtLeast(1)
    val shown = dragging ?: (state.position.toFloat() / duration).coerceIn(0f, 1f)

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bg, Color(0xFF0E0E0E))))
            // Impede que toques atravessem para a tela de baixo
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
    ) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Fechar", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Text(
                    "Tocando agora", color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleSmall,
                )
                IconButton(onClick = { vm.songsForPlaylistDialog = listOf(song) }) {
                    Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Adicionar à playlist", tint = Color.White)
                }
            }

            Spacer(Modifier.weight(1f))
            AlbumArt(
                song.albumId,
                Modifier.fillMaxWidth().aspectRatio(1f).shadow(24.dp, MaterialTheme.shapes.large),
                corner = 20.dp,
            )
            Spacer(Modifier.weight(1f))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        song.title, color = Color.White, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        song.artist, color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                val fav = song.id in vm.favorites
                IconButton(onClick = { vm.toggleFavorite(song) }) {
                    Icon(
                        if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if (fav) "Remover das favoritas" else "Favoritar",
                        tint = if (fav) Color(0xFFFF5252) else Color.White,
                    )
                }
            }

            Spacer(Modifier.size(16.dp))
            Slider(
                value = shown,
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { vm.seekTo((it * duration).toLong()) }
                    dragging = null
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                ),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatTime((shown * duration).toLong()), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                Text(formatTime(state.duration), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.size(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = vm::toggleShuffle) {
                    Icon(
                        Icons.Rounded.Shuffle, "Aleatório",
                        tint = if (state.shuffle) Accent else Color.White.copy(alpha = 0.7f),
                    )
                }
                IconButton(onClick = vm::previous, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Anterior", tint = Color.White, modifier = Modifier.size(40.dp))
                }
                Box(
                    Modifier.size(76.dp).clip(CircleShape).background(Color.White).clickable(onClick = vm::togglePlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (state.isPlaying) "Pausar" else "Tocar",
                        tint = Color.Black, modifier = Modifier.size(44.dp),
                    )
                }
                IconButton(onClick = vm::next, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Próxima", tint = Color.White, modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = vm::cycleRepeat) {
                    Icon(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        "Repetir",
                        tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) Color.White.copy(alpha = 0.7f) else Accent,
                    )
                }
            }
            Spacer(Modifier.size(32.dp))
        }
    }
}
