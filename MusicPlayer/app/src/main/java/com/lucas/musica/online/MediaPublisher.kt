package com.lucas.musica.online

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/** Copia um MP3 baixado para a pasta Music/Música Online e avisa o sistema, para ele aparecer na biblioteca. */
object MediaPublisher {
    private const val FOLDER = "Música Online"

    suspend fun publish(context: Context, file: File, title: String) {
        val name = title.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().take(120).ifEmpty { "musica" } + ".mp3"
        val path = if (Build.VERSION.SDK_INT >= 29) insertIntoMediaStore(context, file, name) else copyToMusicFolder(file, name)
        file.delete()
        // Garante que título, artista e duração já estejam indexados antes de recarregar a lista
        if (path != null) scan(context, path)
    }

    private fun insertIntoMediaStore(context: Context, file: File, name: String): String? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$FOLDER")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: error("Não foi possível criar o arquivo na pasta Música")
        resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)

        @Suppress("DEPRECATION")
        return resolver.query(uri, arrayOf(MediaStore.Audio.Media.DATA), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }

    @Suppress("DEPRECATION")
    private fun copyToMusicFolder(file: File, name: String): String {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), FOLDER).apply { mkdirs() }
        var dest = File(dir, name)
        var n = 1
        while (dest.exists()) dest = File(dir, name.removeSuffix(".mp3") + " ($n).mp3").also { n++ }
        file.copyTo(dest)
        return dest.absolutePath
    }

    private suspend fun scan(context: Context, path: String) = suspendCancellableCoroutine { cont ->
        MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf("audio/mpeg")) { _, _ ->
            if (cont.isActive) cont.resume(Unit)
        }
    }
}
