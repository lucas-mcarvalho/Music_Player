package com.musicplayer.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musicplayer.app.Detail
import com.musicplayer.app.DetailKind
import com.musicplayer.app.MusicViewModel
import com.musicplayer.app.ThemeMode
import kotlinx.coroutines.launch
import java.io.File

private val tabs = listOf("Músicas", "Artistas", "Álbuns", "Pastas", "Playlists")

@Composable
fun HomeScreen(vm: MusicViewModel) {
    val pager = rememberPagerState(initialPage = vm.homePage) { tabs.size }
    val scope = rememberCoroutineScope()
    val searchResults = remember(vm.query, vm.songs) {
        val q = vm.query.trim().lowercase()
        vm.songs.filter { q in it.title.lowercase() || q in it.artist.lowercase() || q in it.album.lowercase() }
    }
    val showingSearch = vm.query.isNotBlank()

    // Trocar de aba ou entrar/sair da busca cancela a seleção
    LaunchedEffect(pager.currentPage, showingSearch) {
        vm.homePage = pager.currentPage
        vm.clearSelection()
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (vm.selection.isNotEmpty()) {
            SelectionTopBar(vm, if (showingSearch) searchResults else vm.songs)
        } else {
            HomeHeader(vm)
            SearchField(query = vm.query, onQueryChange = { vm.query = it }, placeholder = "Buscar músicas, artistas, álbuns")
        }

        when {
            !vm.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            showingSearch -> {
                Text(
                    "${searchResults.size} ${if (searchResults.size == 1) "resultado" else "resultados"}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
                )
                if (searchResults.isEmpty()) EmptyMessage("Nada encontrado para \"${vm.query}\"")
                else SongList(vm, searchResults)
            }
            else -> {
                TabChips(selected = pager.currentPage) { scope.launch { pager.animateScrollToPage(it) } }
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = vm.selection.isEmpty(),
                    beyondViewportPageCount = 1,
                ) { page ->
                    when (page) {
                        0 -> SongsTab(vm)
                        1 -> ArtistsTab(vm)
                        2 -> AlbumsTab(vm)
                        3 -> FoldersTab(vm)
                        4 -> PlaylistsTab(vm)
                    }
                }
            }
        }
    }
}

// ---------- Cabeçalho ----------

@Composable
private fun HomeHeader(vm: MusicViewModel) {
    val artists = remember(vm.songs) { vm.songs.distinctBy { it.artist }.size }
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Música",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
            )
            if (vm.loaded) {
                Text(
                    "${vm.songs.size} músicas · $artists artistas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        OnlineButton(onClick = { vm.onlineOpen = true })
        Spacer(Modifier.width(8.dp))
        ThemeButton(vm)
    }
}

@Composable
private fun OnlineButton(onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.TravelExplore, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "Online",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun ThemeButton(vm: MusicViewModel) {
    var open by remember { mutableStateOf(false) }
    val icon = when (vm.themeMode) {
        ThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
        ThemeMode.LIGHT -> Icons.Rounded.LightMode
        ThemeMode.DARK -> Icons.Rounded.DarkMode
    }
    Box {
        IconButton(
            onClick = { open = true },
            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) { Icon(icon, "Tema") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                Triple(ThemeMode.SYSTEM, "Padrão do sistema", Icons.Rounded.BrightnessAuto),
                Triple(ThemeMode.LIGHT, "Claro", Icons.Rounded.LightMode),
                Triple(ThemeMode.DARK, "Escuro", Icons.Rounded.DarkMode),
            ).forEach { (mode, label, modeIcon) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = { Icon(modeIcon, null) },
                    trailingIcon = {
                        if (vm.themeMode == mode) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                    },
                    onClick = { vm.updateThemeMode(mode); open = false },
                )
            }
        }
    }
}

/** Barra de pesquisa em pílula. Com [onSearch], a busca só roda ao tocar em "pesquisar" no teclado. */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onSearch: (() -> Unit)? = null,
) {
    val focus = LocalFocusManager.current
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(50.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    placeholder,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); onSearch?.invoke() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AnimatedVisibility(query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
            Icon(
                Icons.Rounded.Cancel, "Limpar busca",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onQueryChange(""); focus.clearFocus() }
                    .padding(4.dp),
            )
        }
    }
}

@Composable
private fun TabChips(selected: Int, onSelect: (Int) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(selected) { listState.animateScrollToItem((selected - 1).coerceAtLeast(0)) }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tabs.size) { i ->
            val isSelected = i == selected
            val bg by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                label = "chipBg",
            )
            val fg by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "chipFg",
            )
            Text(
                tabs[i],
                color = fg,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(bg)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            )
        }
    }
}

// ---------- Abas ----------

@Composable
private fun SongsTab(vm: MusicViewModel) {
    if (vm.songs.isEmpty()) {
        EmptyMessage("Nenhuma música encontrada.\nCopie arquivos MP3 para a pasta Music do celular.")
        return
    }
    SongList(vm, vm.songs, header = {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = { vm.play(vm.songs, shuffle = true) }) {
                Icon(Icons.Rounded.Shuffle, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Tocar aleatório")
            }
            Spacer(Modifier.weight(1f))
            Text(
                "${vm.songs.size} músicas",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    })
}

@Composable
private fun GroupRow(title: String, subtitle: String, onClick: () -> Unit, leading: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ArtistsTab(vm: MusicViewModel) {
    val artists = remember(vm.songs) { vm.songs.groupBy { it.artist }.toSortedMap(String.CASE_INSENSITIVE_ORDER) }
    if (artists.isEmpty()) return EmptyMessage("Nenhum artista")
    val entries = artists.entries.toList()
    LazyColumn(Modifier.fillMaxSize()) {
        items(entries.size) { i ->
            val (name, songs) = entries[i]
            GroupRow(name, "${songs.size} músicas", { vm.open(Detail(DetailKind.ARTIST, name, name)) }) {
                AlbumArt(songs.first().albumId, Modifier.size(52.dp), corner = 26.dp)
            }
        }
    }
}

@Composable
private fun AlbumsTab(vm: MusicViewModel) {
    val albums = remember(vm.songs) { vm.songs.groupBy { it.albumId }.values.sortedBy { it.first().album.lowercase() } }
    if (albums.isEmpty()) return EmptyMessage("Nenhum álbum")
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(albums.size) { i ->
            val first = albums[i].first()
            Column(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { vm.open(Detail(DetailKind.ALBUM, first.albumId.toString(), first.album)) }
            ) {
                AlbumArt(first.albumId, Modifier.fillMaxWidth().aspectRatio(1f), corner = 12.dp)
                Spacer(Modifier.size(6.dp))
                Text(first.album, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(
                    first.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FoldersTab(vm: MusicViewModel) {
    val folders = remember(vm.songs) { vm.songs.groupBy { it.folder }.toSortedMap() }
    if (folders.isEmpty()) return EmptyMessage("Nenhuma pasta")
    val entries = folders.entries.toList()
    LazyColumn(Modifier.fillMaxSize()) {
        items(entries.size) { i ->
            val (path, songs) = entries[i]
            val name = File(path).name.ifEmpty { path }
            GroupRow(name, "${songs.size} músicas · $path", { vm.open(Detail(DetailKind.FOLDER, path, name)) }) {
                IconTile(Icons.Rounded.Folder)
            }
        }
    }
}

@Composable
private fun IconTile(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.primary) {
    Box(
        Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint) }
}

@Composable
private fun PlaylistsTab(vm: MusicViewModel) {
    var creating by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            GroupRow("Nova playlist", "Crie uma lista com suas músicas", { creating = true }) { IconTile(Icons.Rounded.Add) }
        }
        item {
            GroupRow("Favoritas", "${vm.favorites.size} músicas", {
                vm.open(Detail(DetailKind.FAVORITES, "fav", "Favoritas"))
            }) { IconTile(Icons.Rounded.Favorite, tint = Color(0xFFE53935)) }
        }
        items(vm.playlists.size) { i ->
            val p = vm.playlists[i]
            GroupRow(p.name, "${p.songIds.size} músicas", { vm.open(Detail(DetailKind.PLAYLIST, p.id, p.name)) }) {
                IconTile(Icons.Rounded.QueueMusic)
            }
        }
    }
    if (creating) {
        NewPlaylistDialog(onDismiss = { creating = false }) {
            vm.createPlaylist(it)
            creating = false
        }
    }
}
