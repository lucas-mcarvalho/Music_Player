package com.musicplayer.app

import android.app.Application
import android.content.ComponentName
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.musicplayer.app.data.MusicRepository
import com.musicplayer.app.data.PlaybackSession
import com.musicplayer.app.data.Playlist
import com.musicplayer.app.data.PlaylistStore
import com.musicplayer.app.data.Song
import com.musicplayer.app.data.toMediaItem
import com.musicplayer.app.online.Downloads
import com.musicplayer.app.online.OnlineResult
import com.musicplayer.app.online.YtDlp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import com.musicplayer.app.playback.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.random.Random

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class DetailKind { ARTIST, ALBUM, FOLDER, PLAYLIST, FAVORITES }

/** Uma tela de detalhe (álbum, artista, pasta ou playlist) empilhada sobre a tela inicial. */
data class Detail(val kind: DetailKind, val key: String, val title: String)

/** Uma música da fila; [index] é a posição dela no player (pode diferir da ordem com o aleatório ligado). */
data class QueueItem(val index: Int, val song: Song)

data class PlayerState(
    val song: Song? = null,
    val isPlaying: Boolean = false,
    val position: Long = 0,
    val duration: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
)

class MusicViewModel(app: Application) : AndroidViewModel(app) {
    private val store = PlaylistStore(app)

    var songs by mutableStateOf<List<Song>>(emptyList())
        private set
    var loaded by mutableStateOf(false)
        private set
    var player by mutableStateOf(PlayerState())
        private set
    var playlists by mutableStateOf(store.loadPlaylists())
        private set
    var favorites by mutableStateOf(store.loadFavorites())
        private set

    var themeMode by mutableStateOf(
        store.loadTheme()?.let { saved -> ThemeMode.entries.find { it.name == saved } } ?: ThemeMode.SYSTEM
    )
        private set

    /** Texto da barra de busca da tela inicial. */
    var query by mutableStateOf("")

    /** Aba aberta na tela inicial (sobrevive a abrir um álbum e voltar). */
    var homePage by mutableStateOf(0)

    val backStack = mutableStateListOf<Detail>()

    /** Fila na ordem em que vai tocar (respeita o aleatório) e posição da música atual nela. */
    var queue by mutableStateOf<List<QueueItem>>(emptyList())
        private set
    var currentQueueIndex by mutableStateOf(C.INDEX_UNSET)
        private set
    var queueOpen by mutableStateOf(false)

    // ---- Busca online (yt-dlp) ----
    var onlineOpen by mutableStateOf(false)
    var downloadsOpen by mutableStateOf(false)
    var onlineQuery by mutableStateOf("")
    var onlineResults by mutableStateOf<List<OnlineResult>>(emptyList())
        private set
    /** Mensagem de carregamento ("Buscando…"); null quando não está carregando. */
    var onlineStatus by mutableStateOf<String?>(null)
        private set
    var onlineError by mutableStateOf<String?>(null)
        private set
    var onlineSearched by mutableStateOf(false)
        private set
    private var searchJob: Job? = null
    var songsForPlaylistDialog by mutableStateOf<List<Song>?>(null)

    /** IDs das músicas marcadas no modo de seleção (vazio = modo normal). */
    var selection by mutableStateOf<Set<Long>>(emptySet())
        private set

    /** Músicas aguardando a confirmação do sistema para serem apagadas. */
    var pendingDelete: Set<Long> = emptySet()

    private var songsById: Map<Long, Song> = emptyMap()
    private var controller: MediaController? = null
    private var sessionRestored = false
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync()
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                )
            ) syncQueue()
            // Só salva depois de restaurar, para não sobrescrever a sessão anterior com uma fila vazia
            if (sessionRestored && events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_POSITION_DISCONTINUITY,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED, Player.EVENT_REPEAT_MODE_CHANGED,
                )
            ) saveSession()
        }
    }

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            controller = future.get().also { it.addListener(listener) }
            sync()
            restoreSession()
        }, ContextCompat.getMainExecutor(app))

        // Música baixada da internet: recarrega a biblioteca para ela aparecer
        Downloads.onSongSaved = { loadSongs() }

        // Atualiza a barra de progresso enquanto toca
        viewModelScope.launch {
            var ticks = 0
            while (isActive) {
                delay(500)
                controller?.takeIf { it.isPlaying }?.let {
                    player = player.copy(position = it.currentPosition)
                    // A cada ~5 s guarda a posição, para retomar dali se o app for fechado
                    if (++ticks % 10 == 0 && sessionRestored) store.savePosition(it.currentPosition)
                }
            }
        }
    }

    fun loadSongs() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { MusicRepository.loadSongs(getApplication()) }
            songs = list
            songsById = list.associateBy { it.id }
            loaded = true
            sync()
            restoreSession()
            syncQueue()
        }
    }

    // ---- Lembrar a última música ----

    private fun saveSession() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return
        store.saveSession(
            PlaybackSession(
                queue = (0 until c.mediaItemCount).mapNotNull { c.getMediaItemAt(it).mediaId.toLongOrNull() },
                index = c.currentMediaItemIndex,
                positionMs = c.currentPosition,
                shuffle = c.shuffleModeEnabled,
                repeatMode = c.repeatMode,
            )
        )
    }

    /**
     * Ao abrir o app: se o player estiver vazio (o serviço tinha sido encerrado), recarrega a última fila
     * pausada na mesma posição. Precisa do player conectado e da biblioteca carregada, por isso é chamada
     * pelos dois lados e só roda uma vez.
     */
    private fun restoreSession() {
        val c = controller ?: return
        if (!loaded || sessionRestored) return
        sessionRestored = true
        if (c.mediaItemCount > 0) return // ainda estava tocando: nada a restaurar

        val session = store.loadSession() ?: return
        val currentId = session.queue.getOrNull(session.index)
        // Ignora músicas apagadas desde a última vez
        val queue = session.queue.mapNotNull { songsById[it] }
        if (queue.isEmpty()) return
        val index = queue.indexOfFirst { it.id == currentId }
        c.shuffleModeEnabled = session.shuffle
        c.repeatMode = session.repeatMode
        c.setMediaItems(queue.map { it.toMediaItem() }, index.coerceAtLeast(0), if (index >= 0) session.positionMs else 0L)
        c.prepare()
    }

    /** Monta a fila na ordem de reprodução, a partir do player. */
    private fun syncQueue() {
        val c = controller ?: return
        val timeline = c.currentTimeline
        currentQueueIndex = c.currentMediaItemIndex
        if (timeline.isEmpty) {
            queue = emptyList()
            return
        }
        val shuffle = c.shuffleModeEnabled
        val items = mutableListOf<QueueItem>()
        var i = timeline.getFirstWindowIndex(shuffle)
        while (i != C.INDEX_UNSET && items.size < timeline.windowCount) {
            c.getMediaItemAt(i).mediaId.toLongOrNull()?.let { songsById[it] }?.let { items += QueueItem(i, it) }
            i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, shuffle)
        }
        queue = items
    }

    private fun sync() {
        val c = controller ?: return
        val song = c.currentMediaItem?.mediaId?.toLongOrNull()?.let { songsById[it] }
        player = PlayerState(
            song = song,
            isPlaying = c.isPlaying,
            position = c.currentPosition,
            duration = c.duration.takeIf { it > 0 } ?: song?.duration ?: 0,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
        )
    }

    // ---- Controles do player ----

    fun play(list: List<Song>, index: Int = 0, shuffle: Boolean = false) {
        val c = controller ?: return
        if (list.isEmpty()) return
        if (shuffle) c.shuffleModeEnabled = true
        val start = if (shuffle) Random.nextInt(list.size) else index
        c.setMediaItems(list.map { it.toMediaItem() }, start, 0L)
        c.prepare()
        c.play()
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() { controller?.seekToNext() }
    fun previous() { controller?.seekToPrevious() }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
        player = player.copy(position = ms)
    }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    // ---- Fila ----

    /** Coloca músicas no fim da fila. Se nada estiver tocando, começa a tocar. */
    fun addToQueue(songs: List<Song>) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        if (c.mediaItemCount == 0) play(songs) else c.addMediaItems(songs.map { it.toMediaItem() })
        toast(if (songs.size == 1) "Adicionada à fila" else "${songs.size} músicas adicionadas à fila")
    }

    fun addSelectedToQueue(visible: List<Song>) {
        addToQueue(selectedIn(visible))
        clearSelection()
    }

    /** Pula direto para uma música da fila. */
    fun playQueueItem(index: Int) {
        val c = controller ?: return
        c.seekToDefaultPosition(index)
        c.play()
    }

    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (c.mediaItemCount <= 1) closePlayer() else c.removeMediaItem(index)
    }

    /** Tira da fila tudo menos a música atual. */
    fun clearUpcoming() {
        val c = controller ?: return
        val current = c.currentMediaItemIndex
        if (current == C.INDEX_UNSET) return
        if (current + 1 < c.mediaItemCount) c.removeMediaItems(current + 1, c.mediaItemCount)
        if (current > 0) c.removeMediaItems(0, current)
    }

    /** O "✕" do mini player: para a música, esvazia a fila e esquece a sessão salva. */
    fun closePlayer() {
        val c = controller ?: return
        c.stop()
        c.clearMediaItems()
        store.clearSession()
        queueOpen = false
    }

    fun playNext(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) play(listOf(song))
        else c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
    }

    // ---- Biblioteca ----

    fun toggleFavorite(song: Song) {
        favorites = if (song.id in favorites) favorites - song.id else favorites + song.id
        store.saveFavorites(favorites)
    }

    fun createPlaylist(name: String): Playlist {
        val p = Playlist(UUID.randomUUID().toString(), name.trim(), emptyList())
        updatePlaylists(playlists + p)
        return p
    }

    fun addToPlaylist(playlistId: String, toAdd: List<Song>) {
        val playlist = playlists.find { it.id == playlistId } ?: return
        val newIds = toAdd.map { it.id }.filter { it !in playlist.songIds }.distinct()
        updatePlaylists(playlists.map { if (it.id == playlistId) it.copy(songIds = it.songIds + newIds) else it })
        toast(
            if (newIds.isEmpty()) "Já estão em \"${playlist.name}\""
            else "${newIds.size} ${if (newIds.size == 1) "música adicionada" else "músicas adicionadas"} a \"${playlist.name}\""
        )
    }

    fun removeFromPlaylist(playlistId: String, song: Song) = updatePlaylists(playlists.map {
        if (it.id == playlistId) it.copy(songIds = it.songIds - song.id) else it
    })

    fun deletePlaylist(playlistId: String) {
        updatePlaylists(playlists.filterNot { it.id == playlistId })
        backStack.removeAll { it.kind == DetailKind.PLAYLIST && it.key == playlistId }
    }

    private fun updatePlaylists(list: List<Playlist>) {
        playlists = list
        store.savePlaylists(list)
    }

    fun songsFor(d: Detail): List<Song> = when (d.kind) {
        DetailKind.ARTIST -> songs.filter { it.artist == d.key }
        DetailKind.ALBUM -> songs.filter { it.albumId.toString() == d.key }
        DetailKind.FOLDER -> songs.filter { it.folder == d.key }
        DetailKind.PLAYLIST -> playlists.find { it.id == d.key }?.songIds?.mapNotNull { songsById[it] }.orEmpty()
        DetailKind.FAVORITES -> songs.filter { it.id in favorites }
    }

    fun updateThemeMode(mode: ThemeMode) {
        themeMode = mode
        store.saveTheme(mode.name)
    }

    // ---- Online ----

    fun searchOnline() {
        val q = onlineQuery.trim()
        if (q.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            onlineError = null
            onlineStatus = "Buscando…"
            try {
                onlineResults = YtDlp.search(getApplication(), q) { msg -> viewModelScope.launch { onlineStatus = msg } }
                onlineSearched = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onlineError = e.message?.lines()?.lastOrNull { it.contains("ERROR:") }?.substringAfter("ERROR:")?.trim()
                    ?: "Não foi possível buscar. Verifique a internet e tente de novo."
            } finally {
                onlineStatus = null
            }
        }
    }

    /**
     * Chamado ao abrir a tela Online: prepara e, se preciso, atualiza o yt-dlp em segundo plano,
     * para o primeiro download não ter que esperar por isso.
     */
    fun prewarmOnline() {
        viewModelScope.launch { runCatching { YtDlp.ensureReady(getApplication()) } }
    }

    /** Toca uma música recém-baixada (pelo ID no MediaStore), se ela já estiver na biblioteca. */
    fun playDownloaded(mediaId: Long?) {
        val song = mediaId?.let { songsById[it] }
        if (song == null) {
            toast("A música ainda está sendo adicionada à biblioteca")
            return
        }
        play(listOf(song))
    }

    fun clearFinishedDownloads() = Downloads.clearFinished()

    fun downloadOnline(item: OnlineResult) = Downloads.enqueue(getApplication(), item)
    fun cancelDownload(id: String) = Downloads.cancel(id)

    fun updateYtDlp() {
        viewModelScope.launch {
            toast("Atualizando o yt-dlp…")
            runCatching { YtDlp.update(getApplication()) }
                .onSuccess { toast("yt-dlp atualizado (versão $it)") }
                .onFailure { toast("Não foi possível atualizar o yt-dlp") }
        }
    }

    // ---- Seleção múltipla ----

    fun toggleSelect(song: Song) {
        selection = if (song.id in selection) selection - song.id else selection + song.id
    }

    fun selectAll(visible: List<Song>) {
        val ids = visible.map { it.id }.toSet()
        selection = if (selection.containsAll(ids)) emptySet() else ids
    }

    fun clearSelection() { selection = emptySet() }

    /** Músicas selecionadas, na ordem em que aparecem na lista informada. */
    fun selectedIn(visible: List<Song>): List<Song> = visible.filter { it.id in selection }.distinctBy { it.id }

    fun playSelected(visible: List<Song>) {
        play(selectedIn(visible))
        clearSelection()
    }

    fun playSelectedNext(visible: List<Song>) {
        val c = controller ?: return
        val chosen = selectedIn(visible)
        if (c.mediaItemCount == 0) play(chosen)
        else c.addMediaItems(c.currentMediaItemIndex + 1, chosen.map { it.toMediaItem() })
        toast("${chosen.size} ${if (chosen.size == 1) "música vai" else "músicas vão"} tocar a seguir")
        clearSelection()
    }

    fun addSelectedToPlaylist(visible: List<Song>) {
        songsForPlaylistDialog = selectedIn(visible)
    }

    /** Tira as selecionadas de uma playlist ou das favoritas (não apaga os arquivos). */
    fun removeSelectedFrom(detail: Detail) {
        when (detail.kind) {
            DetailKind.PLAYLIST -> updatePlaylists(playlists.map {
                if (it.id == detail.key) it.copy(songIds = it.songIds.filterNot { id -> id in selection }) else it
            })
            DetailKind.FAVORITES -> {
                favorites = favorites - selection
                store.saveFavorites(favorites)
            }
            else -> return
        }
        clearSelection()
    }

    // ---- Apagar arquivos ----

    /** Android 11+: o sistema mostra a própria confirmação. Retorna null em versões anteriores. */
    fun deleteRequest(toDelete: List<Song>): IntentSender? {
        if (Build.VERSION.SDK_INT < 30) return null
        pendingDelete = toDelete.map { it.id }.toSet()
        return MediaStore.createDeleteRequest(getApplication<Application>().contentResolver, toDelete.map { it.uri }).intentSender
    }

    /** Android 8–10: apaga direto (o app já mostrou a confirmação). */
    fun deleteDirectly(toDelete: List<Song>) {
        viewModelScope.launch {
            val resolver = getApplication<Application>().contentResolver
            val deleted = withContext(Dispatchers.IO) {
                toDelete.filter { song ->
                    runCatching { resolver.delete(song.uri, null, null) > 0 }.getOrDefault(false)
                }.map { it.id }.toSet()
            }
            onDeleted(deleted)
            if (deleted.size < toDelete.size) toast("Não foi possível apagar ${toDelete.size - deleted.size} música(s)")
        }
    }

    fun onDeleted(ids: Set<Long>) {
        pendingDelete = emptySet()
        if (ids.isEmpty()) return
        controller?.let { c ->
            for (i in c.mediaItemCount - 1 downTo 0) {
                if (c.getMediaItemAt(i).mediaId.toLongOrNull() in ids) c.removeMediaItem(i)
            }
        }
        songs = songs.filterNot { it.id in ids }
        songsById = songs.associateBy { it.id }
        favorites = favorites - ids
        store.saveFavorites(favorites)
        updatePlaylists(playlists.map { p -> p.copy(songIds = p.songIds.filterNot { it in ids }) })
        clearSelection()
        sync()
        toast("${ids.size} ${if (ids.size == 1) "música apagada" else "músicas apagadas"}")
    }

    private fun toast(msg: String) = Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()

    // ---- Navegação ----

    fun open(d: Detail) {
        clearSelection()
        backStack.add(d)
    }

    fun back() {
        clearSelection()
        if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex)
    }

    override fun onCleared() {
        if (sessionRestored) saveSession()
        Downloads.onSongSaved = null
        controller?.run {
            removeListener(listener)
            release()
        }
    }
}
