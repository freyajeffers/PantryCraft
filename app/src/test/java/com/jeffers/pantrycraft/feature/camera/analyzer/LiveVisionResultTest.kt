package com.jeffers.pantrycraft.feature.camera.analyzer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveVisionResultTest {
    @Test
    fun normalizedVisionBoxDefaultsAndCopy() {
        val box =
            NormalizedVisionBox(
                ymin = 0.1f,
                xmin = 0.2f,
                ymax = 0.8f,
                xmax = 0.9f,
            )

        assertEquals(0.1f, box.ymin)
        assertEquals(0.2f, box.xmin)
        assertEquals(0.8f, box.ymax)
        assertEquals(0.9f, box.xmax)
        assertNull(box.label)
        assertNull(box.confidence)

        val labeled = box.copy(label = "Apple", confidence = 0.95f)
        assertEquals("Apple", labeled.label)
        assertEquals(0.95f, labeled.confidence)
    }

    @Test
    fun liveVisionResultDefaultsAndValues() {
        val barcodes = listOf("012345678901")
        val boxes =
            listOf(
                NormalizedVisionBox(ymin = 0f, xmin = 0f, ymax = 1f, xmax = 1f, label = "Food"),
            )
        val result =
            LiveVisionResult(
                barcodes = barcodes,
                objectBoxes = boxes,
            )

        assertEquals(barcodes, result.barcodes)
        assertEquals(boxes, result.objectBoxes)
        assertEquals(0, result.imageWidth)
        assertEquals(0, result.imageHeight)

        val sized = result.copy(imageWidth = 1920, imageHeight = 1080)
        assertEquals(1920, sized.imageWidth)
        assertEquals(1080, sized.imageHeight)
    }

    @Test
    fun frameTimingStatsRecordsDurationsAndKeepsMax() {
        val stats = FrameTimingStats()
        assertEquals(0, stats.sampleCount)
        assertEquals(0L, stats.maxDurationMillis)

        stats.record(15L)
        stats.record(48L)
        stats.record(25L)

        assertEquals(3, stats.sampleCount)
        assertEquals(48L, stats.maxDurationMillis)
    }

    @Test
    fun rectToNormalizedClampsAndCalculatesRatios() {
        val rect =
            android.graphics.Rect().apply {
                left = 100
                top = 200
                right = 400
                bottom = 800
            }
        val normalized =
            rect.toNormalized(
                width = 1000,
                height = 1000,
                label = "Apple",
                confidence = 0.95f,
            )

        assertEquals(0.2f, normalized.ymin, 0.001f)
        assertEquals(0.1f, normalized.xmin, 0.001f)
        assertEquals(0.8f, normalized.ymax, 0.001f)
        assertEquals(0.4f, normalized.xmax, 0.001f)
        assertEquals("Apple", normalized.label)
        assertEquals(0.95f, normalized.confidence ?: 0f, 0.001f)

        val defaultBox = rect.toNormalized(1000, 1000)
        assertNull(defaultBox.label)
        assertNull(defaultBox.confidence)

        val labelOnlyBox = rect.toNormalized(1000, 1000, label = "Orange")
        assertEquals("Orange", labelOnlyBox.label)
        assertNull(labelOnlyBox.confidence)
    }
}
