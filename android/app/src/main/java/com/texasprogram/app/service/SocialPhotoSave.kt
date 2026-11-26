package com.texasprogram.app.service

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Copies the photo served by STRAIN at its original resolution to the device gallery. */
internal object SocialPhotoSave {
    // The social service currently accepts uploads up to 3 MiB.
    private const val MAX_BYTES = 4 * 1024 * 1024
    private val extensions = mapOf(
        "image/jpeg" to "jpg",
        "image/png" to "png",
        "image/heic" to "heic",
        "image/webp" to "webp"
    )

    suspend fun save(context: Context, url: String) = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 20_000
        val (mime, extension, data) = try {
            check(connection.responseCode in 200..299)
            val mime = connection.contentType?.substringBefore(';')?.trim()?.lowercase()
                ?: throw IOException("Missing image type")
            val extension = extensions[mime] ?: throw IOException("Unsupported image type")
            val data = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > MAX_BYTES) throw IOException("Image too large")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            if (data.isEmpty()) throw IOException("Empty image")
            Triple(mime, extension, data)
        } finally {
            connection.disconnect()
        }

        val name = "STRAIN-${UUID.randomUUID()}.$extension"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/STRAIN")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Cannot create gallery entry")
            try {
                val output = resolver.openOutputStream(uri) ?: throw IOException("Cannot open gallery entry")
                output.use { it.write(data) }
                val published = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                if (resolver.update(uri, published, null, null) != 1) throw IOException("Cannot publish gallery entry")
            } catch (failure: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw failure
            }
        } else {
            @Suppress("DEPRECATION")
            val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "STRAIN")
            if (!folder.isDirectory && !folder.mkdirs()) throw IOException("Cannot create gallery folder")
            val file = File(folder, name)
            try {
                file.writeBytes(data)
            } catch (failure: IOException) {
                file.delete()
                throw failure
            }
            MediaScannerConnection.scanFile(context, arrayOf(file.path), arrayOf(mime), null)
        }
    }
}
