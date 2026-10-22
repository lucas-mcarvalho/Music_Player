package com.lucas.musica.ui

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lucas.musica.MusicViewModel
import com.lucas.musica.data.Song

@Composable
fun MusicApp(vm: MusicViewModel) {
    var showPlayer by rememberSaveable { mutableStateOf(false) }
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

    BackHandler(enabled = selecting || showPlayer || vm.downloadsOpen || vm.onlineOpen || detail != null || vm.query.isNotEmpty()) {
        when {
            selecting -> vm.clearSelection()
            showPlayer -> showPlayer = false
            vm.downloadsOpen -> vm.downloadsOpen = false
            vm.onlineOpen -> vm.onlineOpen = false
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
                    detail == null -> HomeScreen(vm)
                    else -> DetailScreen(vm, detail)
                }
            }
            if (selecting) {
                SelectionActions(vm, if (detail == null) vm.songs else vm.songsFor(detail), detail, onDelete = startDelete)
            } else {
                MiniPlayer(vm, onOpen = { showPlayer = true })
            }
        }

        AnimatedVisibility(
            visible = showPlayer && vm.player.song != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            NowPlayingScreen(vm, onClose = { showPlayer = false })
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
