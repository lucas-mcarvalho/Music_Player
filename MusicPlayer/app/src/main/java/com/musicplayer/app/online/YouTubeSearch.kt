package com.musicplayer.app.online

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Busca direto na API interna do site do YouTube (a mesma que o navegador usa), sem passar pelo
 * yt-dlp. Evita iniciar o Python a cada busca. Se o YouTube mudar o formato da resposta, devolve
 * null e o app usa a busca do yt-dlp.
 */
object YouTubeSearch {
    private const val URL_SEARCH = "https://www.youtube.com/youtubei/v1/search?prettyPrint=false"
    private const val CLIENT_VERSION = "2.20251020.01.00"
    private const val ONLY_VIDEOS = "EgIQAQ%3D%3D" // filtro "Tipo: Vídeo"

    fun search(query: String, limit: Int): List<OnlineResult>? = runCatching {
        val body = JSONObject()
            .put("context", JSONObject().put("client", JSONObject()
                .put("clientName", "WEB")
                .put("clientVersion", CLIENT_VERSION)
                .put("hl", "pt")
                .put("gl", "BR")))
            .put("query", query)
            .put("params", ONLY_VIDEOS)

        val conn = URL(URL_SEARCH).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            if (conn.responseCode != 200) return@runCatching null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })

            val renderers = mutableListOf<JSONObject>()
            collect(json, "videoRenderer", renderers)
            renderers.mapNotNull(::toResult).distinctBy { it.id }.take(limit).ifEmpty { null }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /** Procura todos os objetos [key] em qualquer profundidade, para não depender do layout exato da página. */
    private fun collect(node: Any?, key: String, out: MutableList<JSONObject>) {
        when (node) {
            is JSONObject -> node.keys().forEach { k ->
                val child = node.opt(k)
                if (k == key && child is JSONObject) out += child else collect(child, key, out)
            }
            is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i), key, out)
        }
    }

    private fun toResult(v: JSONObject): OnlineResult? {
        val id = v.optString("videoId").ifEmpty { return null }
        // Sem duração = transmissão ao vivo ou estreia: não dá para baixar
        val length = v.optJSONObject("lengthText")?.optString("simpleText").orEmpty().ifEmpty { return null }
        return OnlineResult(
            id = id,
            title = text(v.optJSONObject("title")) ?: return null,
            channel = text(v.optJSONObject("ownerText")) ?: text(v.optJSONObject("longBylineText")).orEmpty(),
            durationSec = parseDuration(length),
        )
    }

    private fun text(o: JSONObject?): String? =
        o?.optString("simpleText")?.ifEmpty { null }
            ?: o?.optJSONArray("runs")?.let { runs -> (0 until runs.length()).joinToString("") { runs.getJSONObject(it).optString("text") } }?.ifEmpty { null }

    /** "1:02:03" ou "2:23" → segundos. */
    private fun parseDuration(s: String): Long =
        s.split(":").fold(0L) { acc, part -> acc * 60 + (part.trim().toLongOrNull() ?: 0) }
}
