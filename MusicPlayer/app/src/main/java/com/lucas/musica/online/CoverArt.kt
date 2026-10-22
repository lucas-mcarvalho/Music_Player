package com.lucas.musica.online

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.min

/**
 * Baixa a capa de um vídeo do YouTube e recorta em quadrado.
 * Feito aqui em vez de no yt-dlp porque ele testa uma imagem de cada vez (várias não existem),
 * o que custava ~10 s por música.
 */
object CoverArt {
    // Da maior para a menor. hqdefault fica por último porque tem faixas pretas em vídeos 16:9.
    private val CANDIDATES = listOf("maxresdefault", "hq720", "mqdefault", "hqdefault")
    private const val MAX_SIZE = 720

    fun fetch(videoId: String, dest: File): File? {
        for (name in CANDIDATES) {
            val bitmap = download("https://i.ytimg.com/vi/$videoId/$name.jpg") ?: continue
            val side = min(bitmap.width, bitmap.height)
            var square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
            if (side > MAX_SIZE) square = Bitmap.createScaledBitmap(square, MAX_SIZE, MAX_SIZE, true)
            dest.outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            return dest
        }
        return null
    }

    private fun download(url: String): Bitmap? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        try {
            // Imagens inexistentes respondem 404 (com uma imagem cinza de "não encontrado")
            if (conn.responseCode != 200) null else conn.inputStream.use { BitmapFactory.decodeStream(it) }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
