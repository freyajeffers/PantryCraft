package com.jeffers.pantrycraft.feature.camera.barcode

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

class CatalogDownloadServiceTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createTestContext(): Context =
        object : ContextWrapper(null) {
            override fun getNoBackupFilesDir(): File = tempFolder.root
        }

    private fun runMockServer(
        statusLine: String = "HTTP/1.1 200 OK",
        contentLength: Long = 0L,
        body: ByteArray = ByteArray(0),
        block: (url: String) -> Unit,
    ) {
        val server = ServerSocket(0)
        val port = server.localPort
        val worker =
            thread {
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
                    // Expected when stream closes
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
    fun downloadIfNeededReturnsExistingFileWhenChecksumMatchesWithoutNetwork() =
        runBlocking {
            val context = createTestContext()
            val catalogFile = File(context.noBackupFilesDir, CatalogDownloadService.CATALOG_FILE_NAME)
            val testContent = "pantrycraft-sqlite-catalog-test-payload-bytes"
            catalogFile.writeText(testContent)

            val expectedSha = CatalogDownloadVerifier.sha256(catalogFile)
            val service = CatalogDownloadService(context)

            var progressCalled = false
            val result =
                service.downloadIfNeeded(
                    url = "http://127.0.0.1:1/invalid-unreachable-url",
                    expectedSha256 = expectedSha,
                    onProgress = { _, _ -> progressCalled = true },
                )

            assertEquals(catalogFile.absolutePath, result.absolutePath)
            assertTrue(result.isFile)
            assertEquals(testContent, result.readText())
            assertFalse(progressCalled)
        }

    @Test
    fun downloadIfNeededSuccessDownloadsAndVerifiesPayload() =
        runBlocking {
            val context = createTestContext()
            val catalogFile = File(context.noBackupFilesDir, CatalogDownloadService.CATALOG_FILE_NAME)
            val payload = "mock-sqlite-database-contents-for-offline-catalog".toByteArray(StandardCharsets.UTF_8)
            val tempCheck = tempFolder.newFile("check.tmp")
            tempCheck.writeBytes(payload)
            val expectedSha = CatalogDownloadVerifier.sha256(tempCheck)
            tempCheck.delete()

            var progressReported = false
            runMockServer(
                statusLine = "HTTP/1.1 200 OK",
                contentLength = payload.size.toLong(),
                body = payload,
            ) { url ->
                val service = CatalogDownloadService(context)
                val result =
                    runBlocking {
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
    fun downloadIfNeededFailsOnHttpError() =
        runBlocking {
            val context = createTestContext()
            runMockServer(statusLine = "HTTP/1.1 404 Not Found") { url ->
                val service = CatalogDownloadService(context)
                val exception =
                    assertThrows(CatalogDownloadException::class.java) {
                        runBlocking { service.downloadIfNeeded(url = url, expectedSha256 = "dummy") }
                    }
                assertTrue(exception.message?.contains("HTTP 404") == true)
            }
            runMockServer(statusLine = "HTTP/1.1 199 Early Error") { url ->
                val service = CatalogDownloadService(context)
                val exception =
                    assertThrows(CatalogDownloadException::class.java) {
                        runBlocking { service.downloadIfNeeded(url = url, expectedSha256 = "dummy") }
                    }
                assertTrue(exception.message?.contains("HTTP 199") == true)
            }
        }

    @Test
    fun downloadIfNeededFailsOnChecksumMismatch() =
        runBlocking {
            val context = createTestContext()
            val payload = "payload-with-mismatched-sha".toByteArray(StandardCharsets.UTF_8)
            runMockServer(
                statusLine = "HTTP/1.1 200 OK",
                contentLength = payload.size.toLong(),
                body = payload,
            ) { url ->
                val service = CatalogDownloadService(context)
                val exception =
                    assertThrows(CatalogDownloadException::class.java) {
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
    fun downloadIfNeededFailsOnExcessiveContentLength() =
        runBlocking {
            val context = createTestContext()
            val hugeSize = 35L * 1024L * 1024L
            runMockServer(
                statusLine = "HTTP/1.1 200 OK",
                contentLength = hugeSize,
            ) { url ->
                val service = CatalogDownloadService(context)
                val exception =
                    assertThrows(CatalogDownloadException::class.java) {
                        runBlocking { service.downloadIfNeeded(url = url, expectedSha256 = "dummy") }
                    }
                assertTrue(exception.message?.contains("Catalog exceeds size limit") == true)
            }
        }

    @Test
    fun catalogDownloadServiceConstantsVerification() {
        assertEquals("foodfacts.db", CatalogDownloadService.CATALOG_FILE_NAME)
        assertTrue(CatalogDownloadService.DEFAULT_URL.startsWith("https://"))
        assertEquals(64, CatalogDownloadService.DEFAULT_SHA256.length)
    }

    @Test
    fun downloadIfNeededFailsWhenReplacementFails() =
        runBlocking {
            val context = createTestContext()
            val catalogDir = File(context.noBackupFilesDir, CatalogDownloadService.CATALOG_FILE_NAME)
            catalogDir.mkdirs()

            val payload = "replacement-failure-payload".toByteArray(StandardCharsets.UTF_8)
            val tempCheck = tempFolder.newFile("replacement_check.tmp")
            tempCheck.writeBytes(payload)
            val expectedSha = CatalogDownloadVerifier.sha256(tempCheck)
            tempCheck.delete()

            runMockServer(
                statusLine = "HTTP/1.1 200 OK",
                contentLength = payload.size.toLong(),
                body = payload,
            ) { url ->
                val service = CatalogDownloadService(context)
                val exception =
                    assertThrows(CatalogDownloadException::class.java) {
                        runBlocking {
                            service.downloadIfNeeded(
                                url = url,
                                expectedSha256 = expectedSha,
                            )
                        }
                    }
                assertTrue(exception.message?.contains("Catalog replacement failed") == true)
            }
        }

    @Test
    fun downloadIfNeededInvokesDefaultArgumentsAndApiN() =
        runBlocking {
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
            val putInt =
                unsafeClass.getMethod(
                    "putInt",
                    Any::class.java,
                    Long::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                )
            val staticFieldBase = unsafeClass.getMethod("staticFieldBase", java.lang.reflect.Field::class.java)
            val staticFieldOffset = unsafeClass.getMethod("staticFieldOffset", java.lang.reflect.Field::class.java)
            val field = Build.VERSION::class.java.getField("SDK_INT")
            val orig = Build.VERSION.SDK_INT
            val base = staticFieldBase.invoke(unsafe, field)
            val offset = staticFieldOffset.invoke(unsafe, field) as Long
            putInt.invoke(unsafe, base, offset, 26)
            try {
                val context = createTestContext()
                val payload = "defaults-test".toByteArray(StandardCharsets.UTF_8)
                runMockServer(
                    statusLine = "HTTP/1.1 200 OK",
                    contentLength = payload.size.toLong(),
                    body = payload,
                ) { url ->
                    val service = CatalogDownloadService(context)
                    val exception =
                        assertThrows(CatalogDownloadException::class.java) {
                            runBlocking { service.downloadIfNeeded(url = url) }
                        }
                    assertTrue(exception.message?.contains("Catalog checksum mismatch") == true)
                    try {
                        runBlocking { service.downloadIfNeeded() }
                    } catch (_: Exception) {
                    }
                }
            } finally {
                putInt.invoke(unsafe, base, offset, orig)
            }
        }

    @Test
    fun downloadIfNeededFailsOnStreamExceedingLimit() =
        runBlocking {
            val context = createTestContext()
            val server = ServerSocket(0)
            val port = server.localPort
            val worker =
                thread {
                    try {
                        val socket = server.accept()
                        val reader = socket.getInputStream().bufferedReader()
                        var line = reader.readLine()
                        while (!line.isNullOrEmpty()) line = reader.readLine()
                        val out = socket.getOutputStream()
                        out.write("HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
                        val chunk = ByteArray(64 * 1024)
                        repeat(520) {
                            try {
                                out.write(chunk)
                                out.flush()
                            } catch (_: Exception) {
                                return@repeat
                            }
                        }
                        socket.close()
                    } catch (_: Exception) {
                    }
                }
            val service = CatalogDownloadService(context)
            val exception =
                assertThrows(CatalogDownloadException::class.java) {
                    runBlocking { service.downloadIfNeeded(url = "http://127.0.0.1:$port") }
                }
            assertTrue(exception.message?.contains("Catalog exceeds size limit") == true)
            worker.join()
            server.close()
        }
}
