package com.musicplayer.app.online

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
    private const val TAG = "YtDlp"
    private const val UPDATE_INTERVAL_MS = 24 * 60 * 60 * 1000L
    private const val SEARCH_PROCESS = "busca"

    // M4A (AAC) não precisa de conversão; se não houver, pega o melhor áudio disponível
    private const val AUDIO_FORMAT = "bestaudio[ext=m4a]/bestaudio"

    // Clientes do YouTube que entregam o áudio sem o desafio JavaScript, e player_skip=js para o yt-dlp
    // nem baixar o JavaScript do player. Sem isso, como a biblioteca sempre liga o QuickJS, o yt-dlp
    // resolve o desafio "por garantia" e gasta ~11 s (emulador) a ~25 s (celular) por música.
    // skip=hls,translated_subs: não busca a lista HLS nem as legendas traduzidas, que não são usadas.
    private const val FAST_EXTRACTOR_ARGS = "youtube:player_client=visionos;player_skip=js;skip=hls,translated_subs"
    private val AUDIO_EXTENSIONS = setOf("m4a", "mp3", "opus", "ogg", "webm", "aac", "flac")

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
        val t0 = SystemClock.elapsedRealtime()
        // Texto comum: busca direta (rápida). Link ou falha da busca direta: yt-dlp.
        val isLink = query.startsWith("http://") || query.startsWith("https://")
        if (!isLink) {
            onStatus("Buscando…")
            val direct = withContext(Dispatchers.IO) { YouTubeSearch.search(query, limit) }
            if (direct != null) {
                Log.d(TAG, "Busca \"$query\" (direta): ${SystemClock.elapsedRealtime() - t0} ms (${direct.size} resultados)")
                return direct
            }
            Log.d(TAG, "Busca direta falhou; usando o yt-dlp")
        }
        ensureReady(context, onStatus)
        val t1 = SystemClock.elapsedRealtime()
        onStatus("Buscando…")
        return withContext(Dispatchers.IO) {
            YoutubeDL.getInstance().destroyProcessById(SEARCH_PROCESS)
            // Um link é aberto direto; qualquer outro texto vira uma busca no YouTube
            val request = YoutubeDLRequest(if (isLink) query else "ytsearch$limit:$query")
                .addOption("--flat-playlist")
                .addOption("--dump-single-json")
                .addOption("--no-warnings")
                .withCache(context)
            parseSearch(YoutubeDL.getInstance().execute(request, SEARCH_PROCESS).out).also {
                val t2 = SystemClock.elapsedRealtime()
                Log.d(TAG, "Busca \"$query\" (yt-dlp): preparar ${t1 - t0} ms + yt-dlp ${t2 - t1} ms = ${t2 - t0} ms (${it.size} resultados)")
            }
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
     * Baixa só o áudio, sem converter (normalmente M4A/AAC, que o YouTube já fornece e o Android toca),
     * com título, artista e capa quadrada embutidos. O arquivo fica no cache do app até ser copiado
     * para a pasta Música. [onProgress] recebe o progresso de 0 a 100 (ou -1 enquanto prepara)
     * e o tempo restante em segundos (ou -1 se desconhecido).
     *
     * São duas chamadas ao yt-dlp: a primeira só extrai as informações (a parte lenta, que resolve os
     * desafios JavaScript do YouTube); o app troca a capa pela que ele mesmo baixou em paralelo; a
     * segunda baixa a partir dessas informações, sem extrair de novo.
     */
    suspend fun download(
        context: Context,
        item: OnlineResult,
        isCanceled: () -> Boolean,
        onProgress: (progress: Float, etaSec: Long) -> Unit,
    ): File {
        ensureReady(context)
        return coroutineScope {
            val dir = File(context.cacheDir, "downloads/${item.id}").apply { deleteRecursively(); mkdirs() }
            val start = SystemClock.elapsedRealtime()
            val cover = async(Dispatchers.IO) { CoverArt.fetch(item.id, File(dir, "capa.jpg")) }

            // 1) Informações do vídeo, já com o formato de áudio escolhido.
            // Primeiro com clientes do YouTube que dispensam o desafio JavaScript (~2 s); só se o vídeo
            // não estiver disponível por eles, repete com o padrão (que resolve o desafio no QuickJS, ~20 s).
            val infoJson = withContext(Dispatchers.IO) {
                fun extract(fastClients: Boolean): String {
                    val request = YoutubeDLRequest(item.url)
                        .addOption("--no-playlist")
                        .addOption("-f", AUDIO_FORMAT)
                        .addOption("--dump-single-json")
                        .addOption("--no-warnings")
                        .withCache(context)
                    if (fastClients) request.addOption("--extractor-args", FAST_EXTRACTOR_ARGS)
                    return YoutubeDL.getInstance().execute(request, item.id).out
                }
                try {
                    extract(fastClients = true)
                } catch (e: YoutubeDL.CanceledException) {
                    throw e
                } catch (e: Exception) {
                    if (isCanceled()) throw YoutubeDL.CanceledException()
                    Log.d(TAG, "Clientes rápidos falharam para ${item.id} (${e.message?.lines()?.lastOrNull { it.isNotBlank() }}); usando o padrão")
                    extract(fastClients = false)
                }
            }
            Log.d(TAG, "Informações de ${item.id} em ${SystemClock.elapsedRealtime() - start} ms")
            if (isCanceled()) throw YoutubeDL.CanceledException()

            // Troca as capas do YouTube pela capa quadrada que o app baixou, e tira o que a etapa 2 não usa:
            // as legendas automáticas sozinhas são ~90% do JSON (~500 KB, 157 idiomas)
            val info = JSONObject(infoJson)
            info.remove("automatic_captions")
            info.remove("subtitles")
            info.remove("heatmap")
            val coverFile = cover.await()
            info.remove("thumbnail")
            if (coverFile != null) {
                info.put("thumbnails", JSONArray().put(JSONObject().put("id", "capa").put("url", "file://${coverFile.absolutePath}")))
            } else {
                info.remove("thumbnails")
            }
            val infoFile = File(dir, "info.json").apply { writeText(info.toString()) }

            // 2) Download + metadados + capa, sem converter o áudio
            withContext(Dispatchers.IO) {
                val request = YoutubeDLRequest(emptyList())
                    .addOption("--load-info-json", infoFile.absolutePath)
                    .addOption("--enable-file-urls")
                    .addOption("-f", AUDIO_FORMAT)
                    .addOption("-x")
                    .addOption("--audio-format", "m4a/best")
                    .addOption("--embed-metadata")
                    // Cada música vira seu próprio "álbum", para cada uma ter a própria capa na biblioteca
                    .addOption("--parse-metadata", "title:%(album)s")
                    .addOption("--no-mtime")
                    .addOption("-o", File(dir, "audio.%(ext)s").absolutePath)
                    .withCache(context)
                if (coverFile != null) request.addOption("--embed-thumbnail")

                var lastStep = ""
                YoutubeDL.getInstance().execute(request, item.id) { progress, eta, line ->
                    onProgress(progress, eta)
                    val step = line.substringBefore("]")
                    if (step != lastStep) {
                        Log.d(TAG, "+${SystemClock.elapsedRealtime() - start} ms  $line")
                        lastStep = step
                    }
                }
            }
            Log.d(TAG, "Download de ${item.id} levou ${SystemClock.elapsedRealtime() - start} ms")

            dir.listFiles()?.firstOrNull { it.name.startsWith("audio.") && it.extension in AUDIO_EXTENSIONS }
                ?: throw IOException("O yt-dlp terminou, mas o arquivo de áudio não foi gerado")
        }
    }

    /** Pasta de cache do yt-dlp: guarda o JavaScript do player do YouTube já processado entre downloads. */
    private fun YoutubeDLRequest.withCache(context: Context) =
        addOption("--cache-dir", File(context.cacheDir, "yt-dlp").absolutePath)

    fun cancel(id: String) = YoutubeDL.getInstance().destroyProcessById(id)
}
