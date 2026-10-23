package com.musicplayer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musicplayer.app.MusicViewModel
import com.musicplayer.app.QueueItem

/** Fila de reprodução: a música atual e as próximas, na ordem em que vão tocar. */
@Composable
fun QueueScreen(vm: MusicViewModel) {
    // Esta tela tem o fundo do tema; a "Tocando agora" embaixo força ícones claros na barra de status
    StatusBarIcons(dark = MaterialTheme.colorScheme.background.luminance() > 0.5f)

    val queue = vm.queue
    val position = queue.indexOfFirst { it.index == vm.currentQueueIndex }
    val current = queue.getOrNull(position)
    val upcoming = if (position >= 0) queue.drop(position + 1) else queue
    val remainingMs = upcoming.sumOf { it.song.duration }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Impede que toques atravessem para a tela de baixo
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.queueOpen = false }) { Icon(Icons.Rounded.KeyboardArrowDown, "Fechar fila") }
            Column(Modifier.weight(1f)) {
                Text("Fila de reprodução", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (upcoming.isNotEmpty()) {
                    Text(
                        "${upcoming.size} a seguir · ${formatTime(remainingMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (upcoming.isNotEmpty()) TextButton(onClick = vm::clearUpcoming) { Text("Limpar") }
        }

        if (current == null) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(16.dp))
                Text("A fila está vazia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Use \"Adicionar à fila\" no menu ⋮ de qualquer música.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            item { SectionTitle("Tocando agora") }
            item { QueueRow(current, isCurrent = true, onClick = {}, onRemove = null) }

            item {
                SectionTitle(if (upcoming.isEmpty()) "Nada a seguir" else "A seguir")
                if (upcoming.isEmpty()) {
                    Text(
                        "Use \"Adicionar à fila\" no menu ⋮ de qualquer música para tocar depois desta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
            items(upcoming.size, key = { "fila-${upcoming[it].index}-${upcoming[it].song.id}" }) { i ->
                val item = upcoming[i]
                QueueRow(
                    item,
                    isCurrent = false,
                    number = i + 1,
                    onClick = { vm.playQueueItem(item.index) },
                    onRemove = { vm.removeFromQueue(item.index) },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun QueueRow(
    item: QueueItem,
    isCurrent: Boolean,
    number: Int? = null,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !isCurrent, onClick = onClick)
            .then(
                if (isCurrent) Modifier.padding(horizontal = 8.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                else Modifier
            )
            .padding(start = if (isCurrent) 8.dp else 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (number != null) {
            Text(
                "$number",
                modifier = Modifier.width(28.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.width(6.dp))
        }
        AlbumArt(item.song.albumId, Modifier.size(48.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "${item.song.artist} · ${formatTime(item.song.duration)}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isCurrent) {
            Icon(
                Icons.Rounded.GraphicEq, "Tocando",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        } else if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Close, "Tirar da fila", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
