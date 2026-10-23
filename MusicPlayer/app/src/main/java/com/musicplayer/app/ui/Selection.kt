package com.musicplayer.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.musicplayer.app.Detail
import com.musicplayer.app.DetailKind
import com.musicplayer.app.MusicViewModel
import com.musicplayer.app.data.Song

/** Barra de cima no modo de seleção: fechar, contador e selecionar tudo. */
@Composable
fun SelectionTopBar(vm: MusicViewModel, visible: List<Song>) {
    val allSelected = visible.isNotEmpty() && vm.selection.containsAll(visible.map { it.id })
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = vm::clearSelection) { Icon(Icons.Rounded.Close, "Sair da seleção") }
        Text(
            "${vm.selection.size} ${if (vm.selection.size == 1) "selecionada" else "selecionadas"}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { vm.selectAll(visible) }) {
            Icon(if (allSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll, null, Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (allSelected) "Desmarcar" else "Selecionar tudo")
        }
    }
}

/** Barra de ações embaixo, no lugar do mini player, enquanto há músicas selecionadas. */
@Composable
fun SelectionActions(vm: MusicViewModel, visible: List<Song>, detail: Detail?, onDelete: (List<Song>) -> Unit) {
    val canRemove = detail?.kind == DetailKind.PLAYLIST || detail?.kind == DetailKind.FAVORITES
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ActionItem(Icons.Rounded.PlayArrow, "Tocar") { vm.playSelected(visible) }
            ActionItem(Icons.AutoMirrored.Rounded.PlaylistPlay, "A seguir") { vm.playSelectedNext(visible) }
            ActionItem(Icons.AutoMirrored.Rounded.QueueMusic, "Fila") { vm.addSelectedToQueue(visible) }
            ActionItem(Icons.AutoMirrored.Rounded.PlaylistAdd, "Playlist") { vm.addSelectedToPlaylist(visible) }
            if (canRemove) {
                ActionItem(Icons.Rounded.PlaylistRemove, "Remover") { vm.removeSelectedFrom(detail!!) }
            }
            ActionItem(Icons.Rounded.Delete, "Apagar", tint = MaterialTheme.colorScheme.error) {
                onDelete(vm.selectedIn(visible))
            }
        }
    }
}

@Composable
private fun ActionItem(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
fun ConfirmDeleteDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count == 1) "Apagar música?" else "Apagar $count músicas?") },
        text = { Text("Os arquivos serão apagados do celular. Essa ação não pode ser desfeita.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Apagar do celular", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Manter") } },
    )
}
