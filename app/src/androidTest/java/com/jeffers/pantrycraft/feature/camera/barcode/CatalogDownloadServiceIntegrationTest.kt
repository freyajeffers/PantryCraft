package com.jeffers.pantrycraft.feature.camera.barcode

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

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

    @Test
    fun downloadIfNeededReturnsExistingFileWhenChecksumMatchesWithoutNetwork() = runBlocking {
        val testContent = "pantrycraft-sqlite-catalog-test-payload-bytes"
        catalogFile.writeText(testContent)

        val expectedSha = CatalogDownloadVerifier.sha256(catalogFile)
        val service = CatalogDownloadService(context)

        // Invalid URL proves no network request is executed when local file checksum matches
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
}
