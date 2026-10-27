package com.jeffers.pantrycraft.feature.meals.context

import com.jeffers.pantrycraft.data.PantryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryContextCompressorTest {

    private val now = 1700000000000L
    private val oneDayMillis = 86_400_000L

    @Test
    fun compressEmptyInventoryReturnsNotice() {
        val result = InventoryContextCompressor.compress(emptyList(), now)
        assertEquals("[INVENTORY_CONTEXT]\nNo ingredients in stock.", result)
    }

    @Test
    fun compressFiltersDeletedAndZeroQuantityItems() {
        val items = listOf(
            PantryItem(name = "Old Milk", quantity = 0f, storageLocation = "FRIDGE"),
            PantryItem(name = "Trash Rice", quantity = 1f, isDeleted = true, storageLocation = "PANTRY"),
            PantryItem(name = "Fresh Eggs", quantity = 6f, unit = "COUNT", storageLocation = "FRIDGE"),
        )
        val result = InventoryContextCompressor.compress(items, now)
        assertTrue(result.contains("Fridge: fresh_eggs(6 count)"))
        assertTrue(!result.contains("old_milk"))
        assertTrue(!result.contains("trash_rice"))
    }

    @Test
    fun compressFormatsStorageZonesAndUrgentExpirations() {
        val items = listOf(
            PantryItem(
                name = "Baby Spinach",
                quantity = 200f,
                unit = "G",
                storageLocation = "FRIDGE",
                expirationTimestamp = now + (1 * oneDayMillis),
            ),
            PantryItem(
                name = "Jasmine Rice",
                quantity = 2f,
                unit = "KG",
                storageLocation = "PANTRY",
            ),
            PantryItem(
                name = "Chicken Thighs",
                quantity = 1f,
                unit = "KG",
                storageLocation = "FREEZER",
                expirationTimestamp = now + (30 * oneDayMillis),
            ),
        )

        val compressed = InventoryContextCompressor.compress(items, now)
        assertTrue(compressed.startsWith("[INVENTORY_CONTEXT]"))
        assertTrue(compressed.contains("Pantry: jasmine_rice(2 kg)"))
        assertTrue(compressed.contains("Fridge: baby_spinach(200 g, EXP:1d)"))
        assertTrue(compressed.contains("Freezer: chicken_thighs(1 kg, EXP:30d)"))
        assertTrue(compressed.contains("[EXPIRY_URGENT]: baby_spinach (1 days left!)"))
    }

    @Test
    fun compressTwoHundredItemsMeetsTokenBudget() {
        val items = (1..200).map { i ->
            PantryItem(
                id = i.toLong(),
                name = "Item$i",
                quantity = (i % 5 + 1).toFloat(),
                unit = "G",
                storageLocation = if (i % 2 == 0) "PANTRY" else "FRIDGE",
            )
        }
        val compressed = InventoryContextCompressor.compress(items, now)
        val tokens = InventoryContextCompressor.estimateTokenCount(compressed)
        assertTrue("Estimated tokens ($tokens) must be under 800", tokens < 800)
    }
}
