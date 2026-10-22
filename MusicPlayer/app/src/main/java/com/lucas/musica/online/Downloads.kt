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
    /** Tempo restante em segundos, ou -1 se desconhecido. */
    val etaSec: Long = -1,
    /** Ordem em que foi pedido (define a posição na fila). */
    val order: Long = 0,
    /** ID da música salva no MediaStore, para poder tocá-la direto da tela de downloads. */
    val mediaId: Long? = null,
) {
    val active get() = status == DownloadStatus.QUEUED || status == DownloadStatus.DOWNLOADING || status == DownloadStatus.CONVERTING
}

/**
 * Fila de downloads, até [PARALLEL] ao mesmo tempo. Vive enquanto o processo do app existir; o [DownloadService]
 * mantém o processo vivo (e mostra a notificação) enquanto houver downloads ativos.
 */
object Downloads {
    /** Estado de cada download, por ID do vídeo. Lido diretamente pela interface. */
    val states = mutableStateMapOf<String, DownloadState>()

    /** Chamado (na thread principal) sempre que uma música nova é salva. */
    var onSongSaved: (() -> Unit)? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private const val PARALLEL = 2
    private var nextOrder = 0L
    private val queue = Channel<OnlineResult>(Channel.UNLIMITED)
    private var workers: List<Job> = emptyList()

    fun enqueue(context: Context, item: OnlineResult) {
        if (states[item.id]?.active == true) return
        states[item.id] = DownloadState(item, DownloadStatus.QUEUED, order = nextOrder++)
        val app = context.applicationContext
        if (workers.none { it.isActive }) {
            // Vários consumidores no mesmo canal: cada música da fila vai para o primeiro que estiver livre
            workers = List(PARALLEL) { scope.launch { for (next in queue) process(app, next) } }
        }
        queue.trySend(item)
        DownloadService.start(app)
    }

    /** Todos os downloads, na ordem em que foram pedidos. */
    fun ordered(): List<DownloadState> = states.values.sortedBy { it.order }

    /** Tira da lista os downloads que já terminaram (concluídos, com erro ou cancelados). */
    fun clearFinished() {
        states.values.filter { !it.active }.forEach { states.remove(it.item.id) }
    }

    fun cancel(id: String) {
        val state = states[id] ?: return
        if (!state.active) return
        states[id] = state.copy(status = DownloadStatus.CANCELED)
        if (state.status != DownloadStatus.QUEUED) YtDlp.cancel(id)
    }

    private suspend fun process(app: Context, item: OnlineResult) {
        if (states[item.id]?.status != DownloadStatus.QUEUED) return // cancelado enquanto esperava
        set(item.id) { it.copy(status = DownloadStatus.DOWNLOADING, progress = -1f, etaSec = -1, error = null) }
        try {
            val file = YtDlp.download(app, item, isCanceled = { states[item.id]?.status == DownloadStatus.CANCELED }) { p, eta ->
                scope.launch(Dispatchers.Main) {
                    set(item.id) {
                        if (it.status == DownloadStatus.CANCELED) it
                        // Ao chegar em 100% o yt-dlp ainda embute os metadados e a capa
                        else it.copy(
                            status = if (p >= 100f) DownloadStatus.CONVERTING else DownloadStatus.DOWNLOADING,
                            progress = p / 100f,
                            etaSec = eta,
                        )
                    }
                }
            }
            set(item.id) { it.copy(status = DownloadStatus.CONVERTING, progress = 1f) }
            val mediaId = MediaPublisher.publish(app, file, item.title)
            set(item.id) { it.copy(status = DownloadStatus.DONE, mediaId = mediaId) }
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
