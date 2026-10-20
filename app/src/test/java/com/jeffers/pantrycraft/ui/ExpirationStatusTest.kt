package com.jeffers.pantrycraft.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpirationStatusTest {
    private val now = 1_700_000_000_000L

    @Test
    fun expirationStatus_marksFreshItemsGreen() {
        assertTrue(expirationStatus(now + 6.daysInMillis(), now) == ExpirationStatus.FRESH)
    }

    @Test
    fun expirationStatus_marksItemsWithinFourDaysAmber() {
        assertTrue(expirationStatus(now + 3.daysInMillis(), now) == ExpirationStatus.EXPIRING_SOON)
    }

    @Test
    fun expirationStatus_marksItemsWithinFortyEightHoursAmber() {
        assertTrue(expirationStatus(now + 47.hoursInMillis(), now) == ExpirationStatus.EXPIRING_SOON)
    }

    @Test
    fun expirationStatus_marksExpiredItemsRed() {
        assertFalse(expirationStatus(now - 1, now) == ExpirationStatus.FRESH)
        assertTrue(expirationStatus(now - 1, now) == ExpirationStatus.EXPIRED)
    }

    @Test
    fun expirationStatus_returnsNoneForNullTimestamp() {
        assertTrue(expirationStatus(null, now) == ExpirationStatus.NONE)
    }

    @Test
    fun expirationStatus_marksZeroRemainingAsExpiringSoon() {
        assertTrue(expirationStatus(now, now) == ExpirationStatus.EXPIRING_SOON)
    }

    @Test
    fun expirationStatus_marksFiveDaysBorderlineCorrectly() {
        // Exactly 5 days is <= 5 days -> EXPIRING_SOON
        assertTrue(expirationStatus(now + 5.daysInMillis(), now) == ExpirationStatus.EXPIRING_SOON)
        // 5 days + 1ms is > 5 days -> FRESH
        assertTrue(expirationStatus(now + 5.daysInMillis() + 1, now) == ExpirationStatus.FRESH)
    }

    private fun Int.daysInMillis() = this.toLong() * 24 * 60 * 60 * 1_000

    private fun Int.hoursInMillis() = this.toLong() * 60 * 60 * 1_000
}
