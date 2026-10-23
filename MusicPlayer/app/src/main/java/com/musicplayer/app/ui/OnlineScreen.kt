package com.musicplayer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.musicplayer.app.MusicViewModel
import com.musicplayer.app.online.DownloadState
import com.musicplayer.app.online.DownloadStatus
import com.musicplayer.app.online.Downloads
import com.musicplayer.app.online.OnlineResult
import com.musicplayer.app.online.YtDlp

@Composable
fun OnlineScreen(vm: MusicViewModel) {
    LaunchedEffect(Unit) { vm.prewarmOnline() }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        OnlineTopBar(vm)
        SearchField(
            query = vm.onlineQuery,
            onQueryChange = { vm.onlineQuery = it },
            placeholder = "Música, artista ou link do YouTube",
            onSearch = vm::searchOnline,
        )
        Spacer(Modifier.height(8.dp))

        val status = vm.onlineStatus
        val error = vm.onlineError
        when {
            status != null -> CenterMessage {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            error != null -> CenterMessage {
                Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text(error, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(onClick = vm::searchOnline) { Text("Tentar de novo") }
            }
            !vm.onlineSearched -> CenterMessage {
                Box(
                    Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.TravelExplore, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
                }
                Spacer(Modifier.height(20.dp))
                Text("Procurar músicas online", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Pesquise no YouTube e baixe o áudio. As músicas baixadas aparecem na biblioteca, " +
                        "na pasta \"Música Online\".",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Baixe apenas conteúdo que você tem direito de usar.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            vm.onlineResults.isEmpty() -> EmptyMessage("Nenhum resultado para \"${vm.onlineQuery}\"")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(vm.onlineResults.size) { i ->
                    val item = vm.onlineResults[i]
                    OnlineResultRow(
                        item = item,
                        state = Downloads.states[item.id],
                        onDownload = { vm.downloadOnline(item) },
                        onCancel = { vm.cancelDownload(item.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun OnlineTopBar(vm: MusicViewModel) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { vm.onlineOpen = false }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar") }
        Text(
            "Procurar online",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        // Downloads, com a quantidade em andamento/na fila num selo
        val active = Downloads.states.values.count { it.active }
        IconButton(onClick = { vm.downloadsOpen = true }) {
            BadgedBox(badge = { if (active > 0) Badge { Text("$active") } }) {
                Icon(Icons.Rounded.Download, "Downloads")
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Mais opções") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("Atualizar yt-dlp")
                            Text(
                                "Versão atual: ${remember { YtDlp.version(context) }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                    onClick = { menu = false; vm.updateYtDlp() },
                )
            }
        }
    }
}

@Composable
private fun CenterMessage(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun OnlineResultRow(
    item: OnlineResult,
    state: DownloadState?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(120.dp).height(68.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            AsyncImage(
                model = item.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (item.durationSec > 0) {
                Text(
                    formatTime(item.durationSec * 1000),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            val subtitle = when (state?.status) {
                DownloadStatus.QUEUED -> "Na fila…"
                DownloadStatus.DOWNLOADING ->
                    if (state.progress >= 0) "Baixando ${(state.progress * 100).toInt()}%" else "Preparando…"
                DownloadStatus.CONVERTING -> "Finalizando…"
                DownloadStatus.DONE -> "Salva na biblioteca"
                DownloadStatus.FAILED -> state.error ?: "Falhou"
                else -> item.channel
            }
            Text(
                subtitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = when (state?.status) {
                    DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                    DownloadStatus.DONE, DownloadStatus.DOWNLOADING, DownloadStatus.CONVERTING -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        DownloadButton(state, onDownload, onCancel)
    }
}

@Composable
private fun DownloadButton(state: DownloadState?, onDownload: () -> Unit, onCancel: () -> Unit) {
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        when (state?.status) {
            DownloadStatus.QUEUED, DownloadStatus.CONVERTING -> {
                CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp)
                if (state.status == DownloadStatus.QUEUED) CancelIcon(onCancel)
            }
            DownloadStatus.DOWNLOADING -> {
                if (state.progress >= 0) {
                    CircularProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                } else {
                    CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp)
                }
                CancelIcon(onCancel)
            }
            DownloadStatus.DONE ->
                Icon(Icons.Rounded.CheckCircle, "Baixada", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            DownloadStatus.FAILED ->
                IconButton(onClick = onDownload) { Icon(Icons.Rounded.Refresh, "Tentar de novo", tint = MaterialTheme.colorScheme.error) }
            else ->
                IconButton(onClick = onDownload) { Icon(Icons.Rounded.Download, "Baixar", tint = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun CancelIcon(onCancel: () -> Unit) {
    Icon(
        Icons.Rounded.Close, "Cancelar",
        modifier = Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onCancel).padding(8.dp),
    )
}
