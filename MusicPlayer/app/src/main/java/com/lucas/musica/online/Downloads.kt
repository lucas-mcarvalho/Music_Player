package com.lucas.musica.online

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DownloadStatus { QUEUED, DOWNLOADING, CONVERTING, DONE, FAILED, CANCELED }

data class DownloadState(
    val item: OnlineResult,
    val status: DownloadStatus,
    /** De 0 a 1, ou negativo enquanto o yt-dlp ainda não sabe o tamanho do arquivo. */
    val progress: Float = -1f,
    val error: String? = null,
) {
    val active get() = status == DownloadStatus.QUEUED || status == DownloadStatus.DOWNLOADING || status == DownloadStatus.CONVERTING
}

/**
 * Fila de downloads, um por vez. Vive enquanto o processo do app existir; o [DownloadService]
 * mantém o processo vivo (e mostra a notificação) enquanto houver downloads ativos.
 */
object Downloads {
    /** Estado de cada download, por ID do vídeo. Lido diretamente pela interface. */
    val states = mutableStateMapOf<String, DownloadState>()

    /** Chamado (na thread principal) sempre que uma música nova é salva. */
    var onSongSaved: (() -> Unit)? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<OnlineResult>(Channel.UNLIMITED)
    private var worker: Job? = null

    fun enqueue(context: Context, item: OnlineResult) {
        if (states[item.id]?.active == true) return
        states[item.id] = DownloadState(item, DownloadStatus.QUEUED)
        val app = context.applicationContext
        if (worker?.isActive != true) {
            worker = scope.launch { for (next in queue) process(app, next) }
        }
        queue.trySend(item)
        DownloadService.start(app)
    }

    fun cancel(id: String) {
        val state = states[id] ?: return
        if (!state.active) return
        states[id] = state.copy(status = DownloadStatus.CANCELED)
        if (state.status != DownloadStatus.QUEUED) YtDlp.cancel(id)
    }

    private suspend fun process(app: Context, item: OnlineResult) {
        if (states[item.id]?.status != DownloadStatus.QUEUED) return // cancelado enquanto esperava
        set(item.id) { it.copy(status = DownloadStatus.DOWNLOADING, progress = -1f) }
        try {
            val file = YtDlp.download(app, item) { p ->
                scope.launch(Dispatchers.Main) {
                    set(item.id) {
                        if (it.status == DownloadStatus.CANCELED) it
                        // Ao chegar em 100% o yt-dlp ainda converte para MP3 e embute a capa
                        else it.copy(status = if (p >= 100f) DownloadStatus.CONVERTING else DownloadStatus.DOWNLOADING, progress = p / 100f)
                    }
                }
            }
            set(item.id) { it.copy(status = DownloadStatus.CONVERTING, progress = 1f) }
            MediaPublisher.publish(app, file, item.title)
            set(item.id) { it.copy(status = DownloadStatus.DONE) }
            withContext(Dispatchers.Main) { onSongSaved?.invoke() }
        } catch (e: Exception) {
            set(item.id) {
                if (it.status == DownloadStatus.CANCELED || e is YoutubeDL.CanceledException) it.copy(status = DownloadStatus.CANCELED)
                else it.copy(status = DownloadStatus.FAILED, error = friendlyError(e))
            }
        }
    }

    private suspend fun set(id: String, change: (DownloadState) -> DownloadState) = withContext(Dispatchers.Main) {
        states[id]?.let { states[id] = change(it) }
    }

    /** Pega a linha "ERROR: ..." do yt-dlp, que é a que explica o problema. */
    private fun friendlyError(e: Exception): String {
        val msg = e.message.orEmpty()
        val line = msg.lines().lastOrNull { it.contains("ERROR:") }?.substringAfter("ERROR:")?.trim()
        return when {
            line != null -> line.take(160)
            msg.contains("Unable to resolve host", true) || msg.contains("network", true) -> "Sem conexão com a internet"
            else -> msg.lines().firstOrNull { it.isNotBlank() }?.take(160) ?: "Erro desconhecido"
        }
    }
}
