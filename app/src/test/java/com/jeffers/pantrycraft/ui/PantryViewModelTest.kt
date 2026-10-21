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
        val itemA =
            PantryItem(
                id = 1,
                name = "Yogurt",
                category = "DAIRY",
                storageLocation = "FRIDGE",
                updatedAt = 100L,
            )
        val itemB =
            PantryItem(
                id = 2,
                name = "Apples",
                category = "PRODUCE",
                storageLocation = "COUNTER",
                updatedAt = 500L,
            )
        val itemC =
            PantryItem(
                id = 3,
                name = "Beans",
                category = "CANNED",
                storageLocation = "PANTRY",
                updatedAt = 200L,
            )

        val byUpdated = sortPantryItems(listOf(itemA, itemB, itemC), PantrySortOrder.RECENTLY_UPDATED)
        assertEquals(listOf(2L, 3L, 1L), byUpdated.map { it.id })

        val byCategory = sortPantryItems(listOf(itemA, itemB, itemC), PantrySortOrder.CATEGORY)
        assertEquals(listOf("CANNED", "DAIRY", "PRODUCE"), byCategory.map { it.category })

        val byZone = sortPantryItems(listOf(itemA, itemB, itemC), PantrySortOrder.STORAGE_LOCATION)
        assertEquals(listOf("COUNTER", "FRIDGE", "PANTRY"), byZone.map { it.storageLocation })
    }

    @Test
    fun sortPantryItems_sortsByNameCaseInsensitively() {
        val itemA = PantryItem(id = 1, name = "banana")
        val itemB = PantryItem(id = 2, name = "Apple")
        val itemC = PantryItem(id = 3, name = "carrot")

        val sorted = sortPantryItems(listOf(itemA, itemB, itemC), PantrySortOrder.NAME_ASC)
        assertEquals(listOf("Apple", "banana", "carrot"), sorted.map { it.name })
    }

    @Test
    fun sortPantryItems_breaksTiesByName() {
        val now = 1_000_000L
        val itemA = PantryItem(id = 1, name = "Zucchini", category = "PRODUCE", expirationTimestamp = now)
        val itemB = PantryItem(id = 2, name = "Artichoke", category = "PRODUCE", expirationTimestamp = now)

        val byExpiration = sortPantryItems(listOf(itemA, itemB), PantrySortOrder.EXPIRATION_SOONEST)
        assertEquals(listOf("Artichoke", "Zucchini"), byExpiration.map { it.name })

        val byCategory = sortPantryItems(listOf(itemA, itemB), PantrySortOrder.CATEGORY)
        assertEquals(listOf("Artichoke", "Zucchini"), byCategory.map { it.name })

        val byStorageZone = sortPantryItems(listOf(
            PantryItem(id = 1, name = "Zucchini", storageLocation = "FRIDGE"),
            PantryItem(id = 2, name = "Artichoke", storageLocation = "FRIDGE"),
        ), PantrySortOrder.STORAGE_LOCATION)
        assertEquals(listOf("Artichoke", "Zucchini"), byStorageZone.map { it.name })
    }

    @Test
    fun filterPantryItems_exactCutoffBoundaryCondition() {
        val now = 1_000_000_000L
        val fiveDaysMillis = 5L * 24L * 60L * 60L * 1000L
        val exactlyAtCutoff = PantryItem(id = 1, name = "Milk", expirationTimestamp = now + fiveDaysMillis)
        val pastCutoff = PantryItem(id = 2, name = "Canned Soup", expirationTimestamp = now + fiveDaysMillis + 1L)

        val filtered = filterPantryItems(
            listOf(exactlyAtCutoff, pastCutoff),
            PantryLocationFilter.EXPIRING_SOON,
            now = now,
        )

        assertEquals(1, filtered.size)
        assertEquals("Milk", filtered.first().name)
    }

    @Test
    fun filterPantryItems_caseInsensitiveLocationMatch() {
        val item = PantryItem(id = 1, name = "Butter", storageLocation = "fridge")
        val filtered = filterPantryItems(listOf(item), PantryLocationFilter.FRIDGE)

        assertEquals(1, filtered.size)
        assertEquals("Butter", filtered.first().name)
    }

    @Test
    fun pantryEnumsHaveExpectedLabelsAndLocations() {
        assertEquals("All", PantryLocationFilter.ALL.label)
        assertEquals("Pantry", PantryLocationFilter.PANTRY.label)
        assertEquals("Fridge", PantryLocationFilter.FRIDGE.label)
        assertEquals("Freezer", PantryLocationFilter.FREEZER.label)
        assertEquals("Counter", PantryLocationFilter.COUNTER.label)
        assertEquals("Expiring soon", PantryLocationFilter.EXPIRING_SOON.label)

        assertEquals(null, PantryLocationFilter.ALL.storageLocation)
        assertEquals("PANTRY", PantryLocationFilter.PANTRY.storageLocation)
        assertEquals("FRIDGE", PantryLocationFilter.FRIDGE.storageLocation)
        assertEquals("FREEZER", PantryLocationFilter.FREEZER.storageLocation)
        assertEquals("COUNTER", PantryLocationFilter.COUNTER.storageLocation)
        assertEquals(null, PantryLocationFilter.EXPIRING_SOON.storageLocation)

        assertEquals("Name (A-Z)", PantrySortOrder.NAME_ASC.label)
        assertEquals("Expiring soonest", PantrySortOrder.EXPIRATION_SOONEST.label)
        assertEquals("Recently updated", PantrySortOrder.RECENTLY_UPDATED.label)
        assertEquals("Category", PantrySortOrder.CATEGORY.label)
        assertEquals("Storage zone", PantrySortOrder.STORAGE_LOCATION.label)
    }

    private fun sampleItems() =
        listOf(
            PantryItem(id = 1, name = "Rice", storageLocation = "PANTRY"),
            PantryItem(id = 2, name = "Milk", storageLocation = "FRIDGE"),
            PantryItem(id = 3, name = "Peas", storageLocation = "FREEZER"),
        )
}
