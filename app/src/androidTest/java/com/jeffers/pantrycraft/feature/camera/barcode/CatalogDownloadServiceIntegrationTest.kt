package com.jeffers.pantrycraft.feature.camera.barcode

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class CatalogDownloadServiceIntegrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val catalogFile = File(context.noBackupFilesDir, CatalogDownloadService.CATALOG_FILE_NAME)
    private val backupFile = File(context.noBackupFilesDir, "${CatalogDownloadService.CATALOG_FILE_NAME}.test_backup")
    private var hadExistingCatalog = false

    @Before
    fun setUp() {
        if (catalogFile.exists()) {
            hadExistingCatalog = true
            catalogFile.copyTo(backupFile, overwrite = true)
            catalogFile.delete()
        }
    }

    @After
    fun tearDown() {
        if (catalogFile.exists()) {
            catalogFile.delete()
        }
        if (hadExistingCatalog && backupFile.exists()) {
            backupFile.copyTo(catalogFile, overwrite = true)
            backupFile.delete()
        }
    }

    private fun runMockServer(
        statusLine: String = "HTTP/1.1 200 OK",
        contentLength: Long = 0L,
        body: ByteArray = ByteArray(0),
        block: (url: String) -> Unit,
    ) {
        val server = ServerSocket(0)
        val port = server.localPort
        val worker = thread {
            try {
                val socket = server.accept()
                val reader = socket.getInputStream().bufferedReader()
                var line = reader.readLine()
                while (!line.isNullOrEmpty()) {
                    line = reader.readLine()
                }
                val out = socket.getOutputStream()
                val header = "$statusLine\r\nContent-Length: $contentLength\r\nConnection: close\r\n\r\n"
                out.write(header.toByteArray(StandardCharsets.UTF_8))
                if (body.isNotEmpty()) {
                    out.write(body)
                }
                out.flush()
                socket.close()
            } catch (_: Exception) {
                // Socket closed or stream finished
            } finally {
                runCatching { server.close() }
            }
        }
        try {
            block("http://127.0.0.1:$port/foodfacts.db")
        } finally {
            runCatching { server.close() }
            worker.join(2000)
        }
    }

    @Test
    fun downloadIfNeededReturnsExistingFileWhenChecksumMatchesWithoutNetwork() = runBlocking {
        val testContent = "pantrycraft-sqlite-catalog-test-payload-bytes"
        catalogFile.writeText(testContent)

        val expectedSha = CatalogDownloadVerifier.sha256(catalogFile)
        val service = CatalogDownloadService(context)

        var progressCalled = false
        val result = service.downloadIfNeeded(
            url = "https://127.0.0.1:9/invalid-url-should-never-be-reached",
            expectedSha256 = expectedSha,
            onProgress = { _, _ -> progressCalled = true },
        )

        assertEquals(catalogFile.absolutePath, result.absolutePath)
        assertTrue(result.isFile)
        assertEquals(testContent, result.readText())
        assertEquals(false, progressCalled)
    }

    @Test
    fun downloadIfNeededSuccessDownloadsAndVerifiesPayload() = runBlocking {
        val payload = "mock-sqlite-database-contents-for-offline-catalog".toByteArray(StandardCharsets.UTF_8)
        val tempFile = File(context.cacheDir, "expected.tmp")
        tempFile.writeBytes(payload)
        val expectedSha = CatalogDownloadVerifier.sha256(tempFile)
        tempFile.delete()

        var progressReported = false
        runMockServer(
            statusLine = "HTTP/1.1 200 OK",
            contentLength = payload.size.toLong(),
            body = payload,
        ) { url ->
            val service = CatalogDownloadService(context)
            val result = runBlocking {
                service.downloadIfNeeded(
                    url = url,
                    expectedSha256 = expectedSha,
                    onProgress = { current, total ->
                        if (current > 0 && total > 0) progressReported = true
                    },
                )
            }
            assertEquals(catalogFile.absolutePath, result.absolutePath)
            assertTrue(result.isFile)
            assertEquals(payload.size.toLong(), result.length())
        }
        assertTrue(progressReported)
    }

    @Test
    fun downloadIfNeededFailsOnHttpError() = runBlocking {
        runMockServer(statusLine = "HTTP/1.1 404 Not Found") { url ->
            val service = CatalogDownloadService(context)
            val exception = assertThrows(CatalogDownloadException::class.java) {
                runBlocking { service.downloadIfNeeded(url = url, expectedSha256 = "dummy") }
            }
            assertTrue(exception.message?.contains("HTTP 404") == true)
        }
    }

    @Test
    fun downloadIfNeededFailsOnChecksumMismatch() = runBlocking {
        val payload = "payload-with-mismatched-sha".toByteArray(StandardCharsets.UTF_8)
        runMockServer(
            statusLine = "HTTP/1.1 200 OK",
            contentLength = payload.size.toLong(),
            body = payload,
        ) { url ->
            val service = CatalogDownloadService(context)
            val exception = assertThrows(CatalogDownloadException::class.java) {
                runBlocking {
                    service.downloadIfNeeded(
                        url = url,
                        expectedSha256 = "0000000000000000000000000000000000000000000000000000000000000000",
                    )
                }
            }
            assertTrue(exception.message?.contains("Catalog checksum mismatch") == true)
        }
    }

    @Test
    fun downloadIfNeededFailsOnExcessiveSizeLimit() = runBlocking {
        val hugeSize = 35L * 1024L * 1024L
        runMockServer(
            statusLine = "HTTP/1.1 200 OK",
            contentLength = hugeSize,
        ) { url ->
            val service = CatalogDownloadService(context)
            val exception = assertThrows(CatalogDownloadException::class.java) {
                runBlocking { service.downloadIfNeeded(url = url, expectedSha256 = "dummy") }
            }
            assertTrue(exception.message?.contains("Catalog exceeds size limit") == true)
        }
    }
}
