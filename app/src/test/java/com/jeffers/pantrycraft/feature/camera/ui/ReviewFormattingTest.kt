package com.jeffers.pantrycraft.feature.camera.ui

import com.jeffers.pantrycraft.ui.formatFileSize
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewFormattingTest {
    @Test
    fun formatQuantityStripsTrailingZeros() {
        assertEquals("1", formatQuantity(1.0f))
        assertEquals("2.5", formatQuantity(2.50f))
        assertEquals("0", formatQuantity(0.0f))
        assertEquals("100.125", formatQuantity(100.125f))
        assertEquals("3.75", formatQuantity(3.750f))
    }

    @Test
    fun formatFileSizeFormatsBytesKilobytesAndMegabytes() {
        assertEquals("0 B", formatFileSize(0L))
        assertEquals("512 B", formatFileSize(512L))
        assertEquals("1023 B", formatFileSize(1023L))
        assertEquals("1.0 KB", formatFileSize(1024L))
        assertEquals("1.5 KB", formatFileSize(1536L))
        assertEquals("1.0 MB", formatFileSize(1024L * 1024L))
        assertEquals("45.2 MB", formatFileSize((45.2 * 1024.0 * 1024.0).toLong()))
    }
}
