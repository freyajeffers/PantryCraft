package com.jeffers.pantrycraft.feature.camera.vision

import org.junit.Assert.assertEquals
import org.junit.Test

class ParsedVisionItemMappingTest {
    @Test
    fun mapsVisionItemToPantryItemWithExpirationAndMetadata() {
        val item =
            ParsedVisionItem(
                name = "Spinach",
                category = "PRODUCE",
                storageLocation = "FRIDGE",
                quantity = 2f,
                unit = "COUNT",
                confidence = 0.9f,
                estimatedShelfLifeDays = 3,
                isAromaticOrSeasoning = false,
                boundingBox = NormalizedBox(0f, 0f, 1f, 1f),
                conditionNotes = "Fresh",
            )
        val now = 1_000L

        val pantryItem = item.toPantryItem(now)

        assertEquals("Spinach", pantryItem.name)
        assertEquals("PRODUCE", pantryItem.category)
        assertEquals("FRIDGE", pantryItem.storageLocation)
        assertEquals(2f, pantryItem.quantity)
        assertEquals("COUNT", pantryItem.unit)
        assertEquals(now + 3 * 86_400_000L, pantryItem.expirationTimestamp)
        assertEquals("Fresh", pantryItem.notes)
    }

    @Test
    fun zeroShelfLifeDoesNotCreateAnExpirationDate() {
        val item =
            ParsedVisionItem(
                name = "Salt",
                category = "OTHER",
                storageLocation = "PANTRY",
                quantity = 1f,
                unit = "COUNT",
                confidence = 0.9f,
                estimatedShelfLifeDays = 0,
                isAromaticOrSeasoning = true,
                boundingBox = NormalizedBox(0f, 0f, 1f, 1f),
                conditionNotes = null,
            )

        assertEquals(null, item.toPantryItem(1_000L).expirationTimestamp)
    }
}
