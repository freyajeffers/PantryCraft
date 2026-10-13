package com.jeffers.pantrycraft.ui

import com.jeffers.pantrycraft.data.PantryItem
import org.junit.Assert.assertEquals
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

    private fun sampleItems() =
        listOf(
            PantryItem(id = 1, name = "Rice", storageLocation = "PANTRY"),
            PantryItem(id = 2, name = "Milk", storageLocation = "FRIDGE"),
            PantryItem(id = 3, name = "Peas", storageLocation = "FREEZER"),
        )
}
