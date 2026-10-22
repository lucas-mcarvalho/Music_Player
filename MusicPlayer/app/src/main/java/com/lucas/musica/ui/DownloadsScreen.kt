package com.lucas.musica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lucas.musica.MusicViewModel
import com.lucas.musica.online.DownloadState
import com.lucas.musica.online.DownloadStatus
import com.lucas.musica.online.Downloads

/** Progresso dos downloads em andamento, a fila e o histórico desta sessão. */
@Composable
fun DownloadsScreen(vm: MusicViewModel) {
    val all = Downloads.ordered()
    val running = all.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.CONVERTING }
    val queued = all.filter { it.status == DownloadStatus.QUEUED }
    val done = all.filter { it.status == DownloadStatus.DONE }.reversed() // mais recentes primeiro
    val problems = all.filter { it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELED }.reversed()
    val hasFinished = done.isNotEmpty() || problems.isNotEmpty()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.downloadsOpen = false }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar") }
            Column(Modifier.weight(1f)) {
                Text("Downloads", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                val summary = listOfNotNull(
                    running.size.takeIf { it > 0 }?.let { "$it baixando" },
                    queued.size.takeIf { it > 0 }?.let { "$it na fila" },
                    done.size.takeIf { it > 0 }?.let { "$it ${if (it == 1) "concluído" else "concluídos"}" },
                ).joinToString(" · ")
                if (summary.isNotEmpty()) {
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (hasFinished) {
                IconButton(onClick = vm::clearFinishedDownloads) { Icon(Icons.Rounded.DeleteSweep, "Limpar concluídos") }
            }
        }

        if (all.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Download, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
                }
                Spacer(Modifier.height(20.dp))
                Text("Nenhum download", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "As músicas que você baixar na busca online aparecem aqui, com o progresso e a fila.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                FilledTonalButton(onClick = { vm.downloadsOpen = false; vm.onlineOpen = true }) { Text("Procurar músicas online") }
            }
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            section("Baixando agora", running) { RunningRow(it, onCancel = { vm.cancelDownload(it.item.id) }) }
            section("Na fila", queued) { state ->
                val position = queued.indexOf(state) + 1
                SimpleRow(state, subtitle = "${position}º na fila") {
                    IconButton(onClick = { vm.cancelDownload(state.item.id) }) { Icon(Icons.Rounded.Close, "Cancelar") }
                }
            }
            section("Concluídos", done) { state ->
                SimpleRow(state, subtitle = "Salva na biblioteca", subtitleColor = MaterialTheme.colorScheme.primary) {
                    IconButton(onClick = { vm.playDownloaded(state.mediaId) }) {
                        Icon(Icons.Rounded.PlayArrow, "Tocar", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            section("Com erro ou cancelados", problems) { state ->
                SimpleRow(
                    state,
                    subtitle = if (state.status == DownloadStatus.CANCELED) "Cancelado" else state.error ?: "Falhou",
                    subtitleColor = MaterialTheme.colorScheme.error,
                ) {
                    IconButton(onClick = { vm.downloadOnline(state.item) }) { Icon(Icons.Rounded.Refresh, "Tentar de novo") }
                }
            }
        }
    }
}

private fun LazyListScope.section(title: String, items: List<DownloadState>, row: @Composable (DownloadState) -> Unit) {
    if (items.isEmpty()) return
    item(key = "titulo-$title") {
        Text(
            "$title (${items.size})",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
        )
    }
    items(items.size, key = { "$title-${items[it].item.id}" }) { row(items[it]) }
}

@Composable
private fun Thumbnail(state: DownloadState, size: Int) {
    AsyncImage(
        model = state.item.thumbnail,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/** Download em andamento: capa maior, barra de progresso, porcentagem e tempo restante. */
@Composable
private fun RunningRow(state: DownloadState, onCancel: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Thumbnail(state, 64)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(state.item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(
                state.item.channel, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            val known = state.status == DownloadStatus.DOWNLOADING && state.progress >= 0
            if (known) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    drawStopIndicator = {},
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    state.status == DownloadStatus.CONVERTING -> "Finalizando…"
                    !known -> "Preparando…"
                    state.etaSec > 0 -> "${(state.progress * 100).toInt()}% · faltam ${formatTime(state.etaSec * 1000)}"
                    else -> "${(state.progress * 100).toInt()}%"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, "Cancelar") }
    }
}

@Composable
private fun SimpleRow(
    state: DownloadState,
    subtitle: String,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    action: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Thumbnail(state, 48)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(state.item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = subtitleColor)
        }
        action()
    }
}
