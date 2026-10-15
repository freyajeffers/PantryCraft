package com.jeffers.pantrycraft.feature.camera.analyzer

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameTimingStatsTest {
    @Test
    fun tracksFrameCountAndMaximumDuration() {
        val stats = FrameTimingStats()

        stats.record(31)
        stats.record(18)
        stats.record(44)

        assertEquals(3, stats.sampleCount)
        assertEquals(44, stats.maxDurationMillis)
    }
}
