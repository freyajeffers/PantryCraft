package com.jeffers.pantrycraft.feature.camera.barcode

import android.content.Context
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
    ): File =
        withContext(Dispatchers.IO) {
            val destination = File(context.noBackupFilesDir, CATALOG_FILE_NAME)
            if (destination.isFile && CatalogDownloadVerifier.sha256(destination) == expectedSha256) {
                return@withContext destination
            }

            val temporary = File(context.noBackupFilesDir, "$CATALOG_FILE_NAME.download")
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 60_000
                connection.instanceFollowRedirects = true
                connection.requestMethod = "GET"
                connection.connect()
                if (connection.responseCode !in 200..299) {
                    throw CatalogDownloadException("Catalog download failed: HTTP ${connection.responseCode}")
                }
                val contentLength = connection.contentLengthLong
                if (contentLength > MAX_BYTES) throw CatalogDownloadException("Catalog exceeds size limit")
                connection.inputStream.use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAX_BYTES) throw CatalogDownloadException("Catalog exceeds size limit")
                            output.write(buffer, 0, count)
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
    }
}

object CatalogDownloadVerifier {
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

class CatalogDownloadException(
    message: String,
) : Exception(message)
