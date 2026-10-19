package com.jeffers.pantrycraft.feature.camera.barcode

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class CatalogDownloadService(
    private val context: Context,
) {
    suspend fun downloadIfNeeded(
        url: String = DEFAULT_URL,
        expectedSha256: String = DEFAULT_SHA256,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): File =
        withContext(Dispatchers.IO) {
            val destination = File(context.noBackupFilesDir, CATALOG_FILE_NAME)
            if (destination.isFile && CatalogDownloadVerifier.sha256(destination) == expectedSha256) {
                return@withContext destination
            }

            val temporary = File(context.noBackupFilesDir, "$CATALOG_FILE_NAME.download")
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
                connection.readTimeout = READ_TIMEOUT_MILLIS
                connection.instanceFollowRedirects = true
                connection.requestMethod = "GET"
                connection.connect()
                if (connection.responseCode !in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX) {
                    throw CatalogDownloadException("Catalog download failed: HTTP ${connection.responseCode}")
                }
                val contentLength =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        connection.contentLengthLong
                    } else {
                        connection.contentLength.toLong()
                    }
                if (contentLength > MAX_BYTES) throw CatalogDownloadException("Catalog exceeds size limit")
                connection.inputStream.use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(TRANSFER_BUFFER_BYTES)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAX_BYTES) throw CatalogDownloadException("Catalog exceeds size limit")
                            output.write(buffer, 0, count)
                            onProgress(total, contentLength)
                        }
                    }
                }
                connection.disconnect()
                if (CatalogDownloadVerifier.sha256(temporary) != expectedSha256) {
                    throw CatalogDownloadException("Catalog checksum mismatch")
                }
                if (!temporary.renameTo(destination)) {
                    throw CatalogDownloadException("Catalog replacement failed")
                }
                destination
            } finally {
                temporary.delete()
            }
        }

    companion object {
        const val DEFAULT_URL =
            "https://storage.googleapis.com/pantrycraft-1792015179-6259-foodfacts/foodfacts.db"
        const val DEFAULT_SHA256 =
            "d2fa1beec99ec5e78516cd02aec73b588c13e2e4906954ae135fa04d3ffea210"
        const val CATALOG_FILE_NAME = "foodfacts.db"
        private const val MAX_BYTES = 32L * 1024L * 1024L
        private const val CONNECT_TIMEOUT_MILLIS = 15_000
        private const val READ_TIMEOUT_MILLIS = 60_000
        private const val TRANSFER_BUFFER_BYTES = 8 * 1024
        private const val HTTP_SUCCESS_MIN = 200
        private const val HTTP_SUCCESS_MAX = 299
    }
}

object CatalogDownloadVerifier {
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(TRANSFER_BUFFER_BYTES)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

private const val TRANSFER_BUFFER_BYTES = 8 * 1024

class CatalogDownloadException(
    message: String,
) : Exception(message)
