package com.jeffers.pantrycraft.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FoodFactsUnitTest {
    @Test
    fun formatFileSizeFormatsBytesKilobytesAndMegabytes() {
        assertEquals("0 B", formatFileSize(0L))
        assertEquals("512 B", formatFileSize(512L))
        assertEquals("1023 B", formatFileSize(1023L))

        assertEquals("1.0 KB", formatFileSize(1024L))
        assertEquals("2.0 KB", formatFileSize(2048L))
        assertEquals("500.0 KB", formatFileSize(512_000L))

        assertEquals("1.0 MB", formatFileSize(1024L * 1024L))
        assertEquals("4.8 MB", formatFileSize(5_000_000L))
    }

    @Test
    fun foodFactsPropsInstantiatesAndInvokesCallbacks() {
        var startCalled = false
        var deleteCalled = false
        var progressDownloaded = 0L
        var progressTotal = 0L
        var downloadResult: Result<File>? = null

        val props =
            FoodFactsProps(
                catalogReady = false,
                catalogFileSize = 1024L,
                catalogDownloading = false,
                catalogDownloadedBytes = 0L,
                catalogTotalBytes = 0L,
                onStartDownload = { startCalled = true },
                onDownloadResult = { downloadResult = it },
                onProgress = { cur, tot ->
                    progressDownloaded = cur
                    progressTotal = tot
                },
                onDelete = { deleteCalled = true },
            )

        assertFalse(props.catalogReady)
        assertFalse(props.catalogDownloading)
        assertEquals(1024L, props.catalogFileSize)
        assertEquals(0L, props.catalogDownloadedBytes)
        assertEquals(0L, props.catalogTotalBytes)

        props.onStartDownload()
        assertTrue(startCalled)

        props.onDelete()
        assertTrue(deleteCalled)

        props.onProgress(500L, 1000L)
        assertEquals(500L, progressDownloaded)
        assertEquals(1000L, progressTotal)

        val file = File("/tmp/catalog.db")
        props.onDownloadResult(Result.success(file))
        assertTrue(downloadResult?.isSuccess == true)
        assertEquals(file, downloadResult?.getOrNull())
    }
}
