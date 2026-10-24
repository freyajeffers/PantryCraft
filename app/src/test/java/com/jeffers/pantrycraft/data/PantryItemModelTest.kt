package com.jeffers.pantrycraft.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PantryItemModelTest {
    private val now = 1_700_000_000_000L
    private val millisPerDay = 86_400_000L

    @Test
    fun markOpenedCalculatesShelfLifeForDairy() {
        val item = PantryItem(
            name = "Organic Milk",
            category = "DAIRY",
            quantity = 1f,
            expirationTimestamp = null,
        )

        val opened = item.markOpened(now)

        assertTrue(opened.isOpened)
        assertEquals(now, opened.openedTimestamp)
        assertEquals(now + 7 * millisPerDay, opened.expirationTimestamp)
        assertEquals(now, opened.lastUpdated)
        assertEquals(now, opened.updatedAt)
    }

    @Test
    fun markOpenedShortensFarFutureExpirationForPerishables() {
        // Original expiration is 100 days away
        val originalExpiration = now + 100 * millisPerDay
        val item = PantryItem(
            name = "Fresh Salmon",
            category = "SEAFOOD",
            quantity = 1f,
            expirationTimestamp = originalExpiration,
        )

        val opened = item.markOpened(now)

        assertTrue(opened.isOpened)
        // Seafood opened shelf life is 3 days
        val expectedOpenedExpiration = now + 3 * millisPerDay
        assertEquals(expectedOpenedExpiration, opened.expirationTimestamp)
    }

    @Test
    fun markOpenedPreservesSoonerExpirationIfOriginalExpiresEarlier() {
        // Original expiration is tomorrow (1 day away)
        val originalExpiration = now + 1 * millisPerDay
        val item = PantryItem(
            name = "Ground Beef",
            category = "MEAT",
            quantity = 1f,
            expirationTimestamp = originalExpiration,
        )

        val opened = item.markOpened(now)

        assertTrue(opened.isOpened)
        // Opened expiration for meat is 3 days, but original is 1 day, so minOf preserves 1 day
        assertEquals(originalExpiration, opened.expirationTimestamp)
    }

    @Test
    fun markOpenedIsIdempotentWhenAlreadyOpened() {
        val openedItem = PantryItem(
            name = "Mustard",
            category = "CONDIMENTS",
            quantity = 1f,
            isOpened = true,
            openedTimestamp = now,
            expirationTimestamp = now + 14 * millisPerDay,
        )

        val result = openedItem.markOpened(now + 10_000L)

        assertSame(openedItem, result)
        assertEquals(now, result.openedTimestamp)
    }

    @Test
    fun defaultNormalizedNameTrimsAndLowercases() {
        val item = PantryItem(
            name = "  Organic Whole Milk  ",
            quantity = 1f,
        )

        assertEquals("organic whole milk", item.normalizedName)
        assertFalse(item.isDeleted)
        assertFalse(item.isOpened)
    }

    @Test
    fun groceryItemDefaultsAndCopySemantics() {
        val item = GroceryItem(name = "Brown Sugar")
        assertEquals("Brown Sugar", item.name)
        assertEquals(1f, item.quantity)
        assertEquals(null, item.unit)
        assertFalse(item.checked)
        assertEquals(null, item.fromPantryId)
        assertTrue(item.createdAt > 0L)

        val updated = item.copy(quantity = 2.5f, checked = true, unit = "KG", fromPantryId = 101L)
        assertEquals(2.5f, updated.quantity)
        assertTrue(updated.checked)
        assertEquals("KG", updated.unit)
        assertEquals(101L, updated.fromPantryId)
    }

    @Test
    fun markOpenedWithSpicesCalculatesNinetyDayShelfLife() {
        val spice = PantryItem(
            name = "Paprika",
            category = "SPICES",
            quantity = 1f,
        )
        val opened = spice.markOpened(now)
        assertEquals(now + 90 * millisPerDay, opened.expirationTimestamp)
    }

    @Test
    fun markOpenedWithFrozenCalculatesThirtyDayShelfLife() {
        val frozenItem = PantryItem(
            name = "Frozen Berries",
            category = "FROZEN",
            quantity = 1f,
        )
        val opened = frozenItem.markOpened(now)
        assertEquals(now + 30 * millisPerDay, opened.expirationTimestamp)
    }

    @Test
    fun markOpenedWithDefaultTimestampUsesCurrentTime() {
        val before = System.currentTimeMillis()
        val item = PantryItem(
            name = "Default Milk",
            category = "DAIRY",
            quantity = 1f,
        )
        val opened = item.markOpened()
        val after = System.currentTimeMillis()

        assertTrue(opened.isOpened)
        assertTrue(opened.openedTimestamp!! in before..after)
    }
}
