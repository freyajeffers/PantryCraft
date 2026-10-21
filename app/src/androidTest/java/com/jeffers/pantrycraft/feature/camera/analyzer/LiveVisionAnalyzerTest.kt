package com.jeffers.pantrycraft.feature.camera.analyzer

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveVisionAnalyzerTest {
    @Test
    fun rectToNormalizedWithinBounds() {
        val rect = Rect(100, 200, 400, 800)
        val normalized = rect.toNormalized(
            width = 1000,
            height = 1000,
            label = "Apple",
            confidence = 0.92f,
        )

        assertEquals(0.2f, normalized.ymin, 0.001f)
        assertEquals(0.1f, normalized.xmin, 0.001f)
        assertEquals(0.8f, normalized.ymax, 0.001f)
        assertEquals(0.4f, normalized.xmax, 0.001f)
        assertEquals("Apple", normalized.label)
        assertEquals(0.92f, normalized.confidence ?: 0f, 0.001f)
    }

    @Test
    fun rectToNormalizedClampsOutOfBoundsCoordinates() {
        val rect = Rect(-50, -100, 1500, 2000)
        val normalized = rect.toNormalized(
            width = 1000,
            height = 1000,
        )

        assertEquals(0f, normalized.ymin, 0.001f)
        assertEquals(0f, normalized.xmin, 0.001f)
        assertEquals(1f, normalized.ymax, 0.001f)
        assertEquals(1f, normalized.xmax, 0.001f)
        assertNull(normalized.label)
        assertNull(normalized.confidence)
    }

    @Test
    fun frameTimingStatsRecordsDurationsAndRetainsMax() {
        val stats = FrameTimingStats()
        assertEquals(0, stats.sampleCount)
        assertEquals(0L, stats.maxDurationMillis)

        stats.record(12L)
        stats.record(45L)
        stats.record(22L)

        assertEquals(3, stats.sampleCount)
        assertEquals(45L, stats.maxDurationMillis)
    }
}
