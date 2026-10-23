package com.musicplayer.app.online

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/** Copia um áudio baixado para a pasta Music/Música Online e avisa o sistema, para ele aparecer na biblioteca. */
object MediaPublisher {
    private const val FOLDER = "Música Online"

    /** Retorna o ID da música no MediaStore (o mesmo usado pela biblioteca), ou null se não souber. */
    suspend fun publish(context: Context, file: File, title: String): Long? {
        val ext = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: if (ext == "m4a") "audio/mp4" else "audio/*"
        val name = title.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().take(120).ifEmpty { "musica" } + ".$ext"
        val (path, insertedUri) =
            if (Build.VERSION.SDK_INT >= 29) insertIntoMediaStore(context, file, name, mime) else copyToMusicFolder(file, name) to null
        file.parentFile?.deleteRecursively()
        // Garante que título, artista e duração já estejam indexados antes de recarregar a lista
        val scannedUri = path?.let { scan(context, it, mime) }
        return (insertedUri ?: scannedUri)?.let { runCatching { ContentUris.parseId(it) }.getOrNull() }
    }

    private fun insertIntoMediaStore(context: Context, file: File, name: String, mime: String): Pair<String?, Uri> {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$FOLDER")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: error("Não foi possível criar o arquivo na pasta Música")
        resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)

        @Suppress("DEPRECATION")
        val path = resolver.query(uri, arrayOf(MediaStore.Audio.Media.DATA), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
        return path to uri
    }

    @Suppress("DEPRECATION")
    private fun copyToMusicFolder(file: File, name: String): String {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), FOLDER).apply { mkdirs() }
        var dest = File(dir, name)
        var n = 1
        while (dest.exists()) dest = File(dir, "${name.substringBeforeLast('.')} ($n).${name.substringAfterLast('.')}").also { n++ }
        file.copyTo(dest)
        return dest.absolutePath
    }

    private suspend fun scan(context: Context, path: String, mime: String): Uri? = suspendCancellableCoroutine { cont ->
        MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf(mime)) { _, uri ->
            if (cont.isActive) cont.resume(uri)
        }
    }
}
