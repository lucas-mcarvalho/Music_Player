package com.musicplayer.app.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.musicplayer.app.MusicViewModel
import com.musicplayer.app.data.Song

@Composable
fun MusicApp(vm: MusicViewModel) {
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var destination by rememberSaveable { mutableStateOf(2) }
    var confirmDelete by remember { mutableStateOf<List<Song>?>(null) }
    val detail = vm.backStack.lastOrNull()
    val selecting = vm.selection.isNotEmpty()

    // Android 11+: o resultado chega depois que a pessoa confirma no diálogo do sistema
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        vm.onDeleted(if (result.resultCode == Activity.RESULT_OK) vm.pendingDelete else emptySet())
    }
    val startDelete = { songs: List<Song> ->
        val request = vm.deleteRequest(songs)
        if (request != null) deleteLauncher.launch(IntentSenderRequest.Builder(request).build())
        else confirmDelete = songs
    }

    BackHandler(enabled = selecting || vm.queueOpen || showPlayer || vm.downloadsOpen || vm.onlineOpen || detail != null || vm.query.isNotEmpty()) {
        when {
            selecting -> vm.clearSelection()
            vm.queueOpen -> vm.queueOpen = false
            showPlayer -> showPlayer = false
            vm.downloadsOpen -> vm.downloadsOpen = false
            vm.onlineOpen -> { vm.onlineOpen = false; if (destination == 1) destination = 2 }
            detail != null -> vm.back()
            else -> vm.query = ""
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when {
                    vm.downloadsOpen -> DownloadsScreen(vm)
                    vm.onlineOpen -> OnlineScreen(vm)
                    detail == null && destination == 0 -> DiscoverScreen(vm, onLibrary = { destination = 2 })
                    detail == null -> HomeScreen(vm)
                    else -> DetailScreen(vm, detail)
                }
            }
            if (selecting) {
                SelectionActions(vm, if (detail == null) vm.songs else vm.songsFor(detail), detail, onDelete = startDelete)
            } else {
                MiniPlayer(vm, onOpen = { showPlayer = true }, bottomNavigation = true)
            }
            if (!selecting) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(Triple("Início", Icons.Rounded.Home, 0), Triple("Buscar", Icons.Rounded.Search, 1), Triple("Biblioteca", Icons.Rounded.LibraryMusic, 2)).forEach { (label, icon, index) ->
                        NavigationBarItem(
                            selected = if (index == 1) vm.onlineOpen else destination == index && !vm.onlineOpen && !vm.downloadsOpen,
                            onClick = {
                                vm.onlineOpen = index == 1
                                vm.downloadsOpen = false
                                while (vm.backStack.isNotEmpty()) vm.back()
                                vm.query = ""
                                destination = index
                            },
                            icon = { Icon(icon, label, Modifier.size(22.dp)) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = androidx.compose.ui.graphics.Color.Transparent),
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showPlayer && vm.player.song != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            NowPlayingScreen(vm, onClose = { showPlayer = false })
        }

        // A fila abre por cima de tudo (normalmente a partir da tela "Tocando agora")
        AnimatedVisibility(
            visible = vm.queueOpen,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            QueueScreen(vm)
        }
    }

    vm.songsForPlaylistDialog?.let { AddToPlaylistDialog(vm, it) }
    confirmDelete?.let { songs ->
        ConfirmDeleteDialog(
            count = songs.size,
            onConfirm = { confirmDelete = null; vm.deleteDirectly(songs) },
            onDismiss = { confirmDelete = null },
        )
    }
}
