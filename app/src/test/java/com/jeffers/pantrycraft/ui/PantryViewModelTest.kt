package com.jeffers.pantrycraft.ui

import com.jeffers.pantrycraft.data.PantryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PantryViewModelTest {
    @Test
    fun filterPantryItems_allLocationsReturnsEveryItem() {
        val items = sampleItems()

        assertEquals(items, filterPantryItems(items, PantryLocationFilter.ALL))
    }

    @Test
    fun filterPantryItems_filtersByStorageLocation() {
        val items = sampleItems()

        assertEquals(
            listOf(items[1]),
            filterPantryItems(items, PantryLocationFilter.FRIDGE),
        )
        assertEquals(
            listOf(items[2]),
            filterPantryItems(items, PantryLocationFilter.FREEZER),
        )
    }

    @Test
    fun filterPantryItems_filtersCounterItems() {
        val items = sampleItems() + PantryItem(id = 4, name = "Bread", storageLocation = "COUNTER")

        assertEquals(
            listOf(items[3]),
            filterPantryItems(items, PantryLocationFilter.COUNTER),
        )
    }

    @Test
    fun filterPantryItems_expiringSoonIncludesExpiredAndUrgentItems() {
        val now = 1_000_000_000L
        val expired = PantryItem(id = 1, name = "Expired Yogurt", expirationTimestamp = now - 50_000L)
        val expiringSoon = PantryItem(id = 2, name = "Fresh Milk", expirationTimestamp = now + 86_400_000L * 2)
        val fresh = PantryItem(id = 3, name = "Dry Rice", expirationTimestamp = now + 86_400_000L * 30)
        val noExpiration = PantryItem(id = 4, name = "Salt", expirationTimestamp = null)

        val filtered =
            filterPantryItems(
                listOf(expired, expiringSoon, fresh, noExpiration),
                PantryLocationFilter.EXPIRING_SOON,
                now,
            )

        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.id == 1L })
        assertTrue(filtered.any { it.id == 2L })
    }

    @Test
    fun sortPantryItems_sortsByExpirationUrgency() {
        val now = 1_000_000_000L
        val itemA = PantryItem(id = 1, name = "Apples", expirationTimestamp = now + 100_000L)
        val itemB = PantryItem(id = 2, name = "Bananas", expirationTimestamp = now + 20_000L)
        val itemC = PantryItem(id = 3, name = "Canned Corn", expirationTimestamp = null)

        val sorted = sortPantryItems(listOf(itemA, itemB, itemC), PantrySortOrder.EXPIRATION_SOONEST)

        assertEquals("Bananas", sorted[0].name)
        assertEquals("Apples", sorted[1].name)
        assertEquals("Canned Corn", sorted[2].name)
    }

    @Test
    fun sortPantryItems_sortsByRecentlyUpdatedAndCategoryAndStorageZone() {
        val itemA = PantryItem(id = 1, name = "Yogurt", category = "DAIRY", storageLocation = "FRIDGE", updatedAt = 100L)
        val itemB = PantryItem(id = 2, name = "Apples", category = "PRODUCE", storageLocation = "COUNTER", updatedAt = 500L)
        val itemC = PantryItem(id = 3, name = "Beans", category = "CANNED", storageLocation = "PANTRY", updatedAt = 200L)

        val byUpdated = sortPantryItems(listOf(itemA, itemB, itemC), PantrySortOrder.RECENTLY_UPDATED)
        assertEquals(listOf(2L, 3L, 1L), byUpdated.map { it.id })

        val byCategory = sortPantryItems(listOf(itemA, itemB, itemC), PantrySortOrder.CATEGORY)
        assertEquals(listOf("CANNED", "DAIRY", "PRODUCE"), byCategory.map { it.category })

        val byZone = sortPantryItems(listOf(itemA, itemB, itemC), PantrySortOrder.STORAGE_LOCATION)
        assertEquals(listOf("COUNTER", "FRIDGE", "PANTRY"), byZone.map { it.storageLocation })
    }

    private fun sampleItems() =
        listOf(
            PantryItem(id = 1, name = "Rice", storageLocation = "PANTRY"),
            PantryItem(id = 2, name = "Milk", storageLocation = "FRIDGE"),
            PantryItem(id = 3, name = "Peas", storageLocation = "FREEZER"),
        )
}
