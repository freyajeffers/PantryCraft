package com.jeffers.pantrycraft.feature.camera.analyzer

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameTimingStatsTest {
    @Test
    fun initialStatsAreZero() {
        val stats = FrameTimingStats()
        assertEquals(0, stats.sampleCount)
        assertEquals(0L, stats.maxDurationMillis)
    }

    @Test
    fun tracksFrameCountAndMaximumDuration() {
        val stats = FrameTimingStats()

        stats.record(31)
        stats.record(18)
        stats.record(44)

        assertEquals(3, stats.sampleCount)
        assertEquals(44, stats.maxDurationMillis)
    }

    @Test
    fun recordZeroDurationIncrementsSampleCountWithoutIncreasingMax() {
        val stats = FrameTimingStats()
        stats.record(0L)
        assertEquals(1, stats.sampleCount)
        assertEquals(0L, stats.maxDurationMillis)
    }
}
