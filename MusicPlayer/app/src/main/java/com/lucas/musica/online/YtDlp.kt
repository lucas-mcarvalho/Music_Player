package com.lucas.musica.online

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Um vídeo encontrado na busca online. */
data class OnlineResult(
    val id: String,
    val title: String,
    val channel: String,
    val durationSec: Long,
) {
    val url get() = "https://www.youtube.com/watch?v=$id"
    val thumbnail get() = "https://i.ytimg.com/vi/$id/mqdefault.jpg"
}

/** Acesso ao yt-dlp (via youtubedl-android) para buscar, baixar e se atualizar. */
object YtDlp {
    private const val UPDATE_INTERVAL_MS = 24 * 60 * 60 * 1000L
    private const val SEARCH_PROCESS = "busca"

    private val initLock = Mutex()
    @Volatile private var ready = false

    /**
     * Extrai o Python/yt-dlp na primeira vez e atualiza o yt-dlp se a última atualização tiver mais de 24 h.
     * [onStatus] recebe mensagens para mostrar na tela enquanto isso acontece.
     */
    suspend fun ensureReady(context: Context, onStatus: (String) -> Unit = {}) = initLock.withLock {
        val app = context.applicationContext
        withContext(Dispatchers.IO) {
            if (!ready) {
                onStatus("Preparando o yt-dlp (só na primeira vez)…")
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
                ready = true
            }
            val prefs = app.getSharedPreferences("online", Context.MODE_PRIVATE)
            if (System.currentTimeMillis() - prefs.getLong("ultimaAtualizacao", 0) > UPDATE_INTERVAL_MS) {
                onStatus("Atualizando o yt-dlp…")
                // Sem internet ou GitHub fora do ar: segue com a versão atual
                runCatching { YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel._STABLE) }
                    .onSuccess { prefs.edit().putLong("ultimaAtualizacao", System.currentTimeMillis()).apply() }
            }
        }
    }

    /** Força a atualização do yt-dlp. Retorna a versão instalada depois disso. */
    suspend fun update(context: Context): String = withContext(Dispatchers.IO) {
        ensureReady(context)
        val app = context.applicationContext
        YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel._STABLE)
        app.getSharedPreferences("online", Context.MODE_PRIVATE).edit()
            .putLong("ultimaAtualizacao", System.currentTimeMillis()).apply()
        version(app)
    }

    fun version(context: Context): String =
        runCatching { YoutubeDL.getInstance().version(context.applicationContext) }.getOrNull() ?: "?"

    suspend fun search(context: Context, query: String, limit: Int = 20, onStatus: (String) -> Unit = {}): List<OnlineResult> {
        ensureReady(context, onStatus)
        onStatus("Buscando…")
        return withContext(Dispatchers.IO) {
            YoutubeDL.getInstance().destroyProcessById(SEARCH_PROCESS)
            // Um link é aberto direto; qualquer outro texto vira uma busca no YouTube
            val isLink = query.startsWith("http://") || query.startsWith("https://")
            val request = YoutubeDLRequest(if (isLink) query else "ytsearch$limit:$query")
                .addOption("--flat-playlist")
                .addOption("--dump-single-json")
                .addOption("--no-warnings")
            parseSearch(YoutubeDL.getInstance().execute(request, SEARCH_PROCESS).out)
        }
    }

    private fun parseSearch(json: String): List<OnlineResult> {
        val root = JSONObject(json)
        // Busca e playlists vêm em "entries"; o link de um vídeo vem como um objeto só
        val entries = root.optJSONArray("entries")
        val objects = if (entries != null) (0 until entries.length()).mapNotNull { entries.optJSONObject(it) } else listOf(root)
        return objects.mapNotNull { e ->
            if (e.optString("live_status") == "is_live") return@mapNotNull null
            val id = e.optString("id").ifEmpty { return@mapNotNull null }
            OnlineResult(
                id = id,
                title = e.optString("title", "Sem título"),
                channel = e.optString("channel").ifEmpty { e.optString("uploader", "") },
                durationSec = e.optDouble("duration", 0.0).toLong(),
            )
        }
    }

    /**
     * Baixa só o áudio em MP3, com título, artista e capa embutidos. O arquivo fica no cache do app
     * até ser copiado para a pasta Música. [onProgress] recebe de 0 a 100.
     */
    suspend fun download(context: Context, item: OnlineResult, onProgress: (Float) -> Unit): File {
        ensureReady(context)
        return withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "downloads").apply { mkdirs() }
            dir.listFiles()?.filter { it.name.startsWith(item.id) }?.forEach { it.delete() }

            val request = YoutubeDLRequest(item.url)
                .addOption("--no-playlist")
                .addOption("-x")
                .addOption("--audio-format", "mp3")
                .addOption("--audio-quality", "0")
                .addOption("--embed-metadata")
                .addOption("--embed-thumbnail")
                .addOption("--convert-thumbnails", "jpg")
                // Cada música vira seu próprio "álbum", para cada uma ter a própria capa na biblioteca
                .addOption("--parse-metadata", "title:%(album)s")
                .addOption("--no-mtime")
                .addOption("-o", File(dir, "%(id)s.%(ext)s").absolutePath)

            YoutubeDL.getInstance().execute(request, item.id) { progress, _, _ -> onProgress(progress) }

            File(dir, "${item.id}.mp3").takeIf { it.exists() }
                ?: throw IOException("O yt-dlp terminou, mas o arquivo MP3 não foi gerado")
        }
    }

    fun cancel(id: String) = YoutubeDL.getInstance().destroyProcessById(id)
}
