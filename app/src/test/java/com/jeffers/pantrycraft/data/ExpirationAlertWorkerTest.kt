package com.jeffers.pantrycraft.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ExpirationAlertWorkerTest {
    @Test
    fun initialDelayBeforeNineAmTargetsSameDay() {
        val now = calendarAt(2026, Calendar.JANUARY, 10, 8, 30).timeInMillis
        val delay = ExpirationAlertWorker.calculateInitialDelayToNineAmMillis(now)

        assertEquals(30L * 60L * 1000L, delay)
    }

    @Test
    fun initialDelayAfterNineAmTargetsNextDay() {
        val now = calendarAt(2026, Calendar.JANUARY, 10, 10, 0).timeInMillis
        val delay = ExpirationAlertWorker.calculateInitialDelayToNineAmMillis(now)

        assertEquals(23L * 60L * 60L * 1000L, delay)
    }

    @Test
    fun initialDelayAtExactNineAmTargetsNextDay() {
        val now = calendarAt(2026, Calendar.JANUARY, 10, 9, 0).timeInMillis
        val delay = ExpirationAlertWorker.calculateInitialDelayToNineAmMillis(now)

        assertEquals(24L * 60L * 60L * 1000L, delay)
    }

    @Test
    fun alertWindowIsFortyEightHours() {
        assertEquals(2L * 24L * 60L * 60L * 1000L, ExpirationAlertWorker.ALERT_WINDOW_MILLIS)
        assertTrue(ExpirationAlertWorker.ALERT_WINDOW_MILLIS > 0L)
    }

    @Test
    fun calculateInitialDelayDefaultParameterUsesSystemTime() {
        val delay = ExpirationAlertWorker.calculateInitialDelayToNineAmMillis()
        assertTrue(delay >= 0L)
        assertTrue(delay <= 24L * 60L * 60L * 1000L)
    }

    private fun calendarAt(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }
}
