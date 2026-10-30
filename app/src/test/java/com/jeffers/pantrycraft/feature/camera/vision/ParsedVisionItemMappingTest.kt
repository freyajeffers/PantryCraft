package com.jeffers.pantrycraft.feature.camera.vision

import com.jeffers.pantrycraft.data.PantryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun mapsCustomUnitNameToPantryItem() {
        val item =
            ParsedVisionItem(
                name = "Rosemary",
                category = "SPICES",
                storageLocation = "PANTRY",
                quantity = 3f,
                unit = "CUSTOM",
                confidence = 1f,
                estimatedShelfLifeDays = 30,
                isAromaticOrSeasoning = true,
                boundingBox = NormalizedBox(0f, 0f, 1f, 1f),
                conditionNotes = null,
                customUnitName = "sprigs",
            )
        val pantryItem = item.toPantryItem(1_000L)
        assertEquals("CUSTOM", pantryItem.unit)
        assertEquals("sprigs", pantryItem.customUnitName)

        val mappedBack = pantryItem.toParsedVisionItem(1_000L)
        assertEquals("sprigs", mappedBack.customUnitName)
    }

    @Test
    fun mapsVisionItemWithOpenedStatusAppliesOpenedShelfLifeClamping() {
        val now = 1_000_000L
        val item =
            ParsedVisionItem(
                name = "Greek Yogurt",
                category = "DAIRY",
                storageLocation = "FRIDGE",
                quantity = 1f,
                unit = "TUB",
                confidence = 0.95f,
                estimatedShelfLifeDays = 30, // Unopened would be 30 days
                isAromaticOrSeasoning = false,
                boundingBox = NormalizedBox(0f, 0f, 1f, 1f),
                conditionNotes = null,
                isOpened = true,
            )

        val pantryItem = item.toPantryItem(now)

        assertTrue(pantryItem.isOpened)
        assertEquals(now, pantryItem.openedTimestamp)
        // DAIRY opened shelf life is 7 days, which is less than 30 days
        assertEquals(now + 7 * 86_400_000L, pantryItem.expirationTimestamp)
    }

    @Test
    fun toParsedVisionItemCalculatesRemainingDaysAndClampsNegative() {
        val now = 1_000_000_000L
        val itemFuture =
            PantryItem(
                name = "Cheddar",
                category = "DAIRY",
                quantity = 2f,
                expirationTimestamp = now + 10 * 86_400_000L,
            )
        val visionFuture = itemFuture.toParsedVisionItem(now)
        assertEquals(10, visionFuture.estimatedShelfLifeDays)

        val itemPast =
            PantryItem(
                name = "Expired Cream",
                category = "DAIRY",
                quantity = 1f,
                expirationTimestamp = now - 5 * 86_400_000L,
            )
        val visionPast = itemPast.toParsedVisionItem(now)
        assertEquals(0, visionPast.estimatedShelfLifeDays)
    }

    @Test
    fun toUpdatedPantryItemPreservesExistingFieldsAndMergesBarcode() {
        val now = 1_000_000L
        val existing =
            PantryItem(
                id = 42L,
                name = "Dijon Mustard",
                category = "CONDIMENTS",
                storageLocation = "FRIDGE",
                quantity = 1f,
                barcode = "012345678901",
                purchaseTimestamp = 500_000L,
                createdAt = 400_000L,
                isDeleted = false,
                isOpened = true,
                openedTimestamp = 600_000L,
            )

        // Vision item with null barcode should preserve existing barcode and existing openedTimestamp
        val visionItem =
            ParsedVisionItem(
                name = "Dijon Mustard (Updated)",
                category = "CONDIMENTS",
                storageLocation = "FRIDGE",
                quantity = 2f,
                unit = "BOTTLE",
                confidence = 0.99f,
                estimatedShelfLifeDays = 14,
                isAromaticOrSeasoning = false,
                boundingBox = NormalizedBox(0f, 0f, 1f, 1f),
                conditionNotes = "Nearly full",
                barcode = null,
                isOpened = true,
            )

        val updated = visionItem.toUpdatedPantryItem(existing, now)

        assertEquals(42L, updated.id)
        assertEquals("012345678901", updated.barcode)
        assertEquals(500_000L, updated.purchaseTimestamp)
        assertEquals(400_000L, updated.createdAt)
        assertEquals(false, updated.isDeleted)
        assertTrue(updated.isOpened)
        // Existing openedTimestamp preserved because existing was already opened
        assertEquals(600_000L, updated.openedTimestamp)
    }

    @Test
    fun toUpdatedPantryItemHandlesOpeningUnopenedItem() {
        val now = 1_000_000L
        val existing =
            PantryItem(
                id = 10L,
                name = "Salsa",
                category = "CONDIMENTS",
                storageLocation = "PANTRY",
                quantity = 1f,
                isOpened = false,
                openedTimestamp = null,
            )

        val visionItem =
            ParsedVisionItem(
                name = "Salsa",
                category = "CONDIMENTS",
                storageLocation = "FRIDGE",
                quantity = 1f,
                unit = "JAR",
                confidence = 0.9f,
                estimatedShelfLifeDays = 7,
                isAromaticOrSeasoning = false,
                boundingBox = NormalizedBox(0f, 0f, 1f, 1f),
                conditionNotes = null,
                isOpened = true,
            )

        val updated = visionItem.toUpdatedPantryItem(existing, now)
        assertTrue(updated.isOpened)
        assertEquals(now, updated.openedTimestamp)
    }

    @Test
    fun toUpdatedPantryItemWithUnopenedStatusClearsOpenedTimestampAndPreservesDeletedFlag() {
        val now = 2_000_000L
        val existing =
            PantryItem(
                id = 55L,
                name = "Marinara",
                category = "CANNED",
                isOpened = true,
                openedTimestamp = 1_000_000L,
                isDeleted = true,
            )

        val visionItem =
            ParsedVisionItem(
                name = "Marinara",
                category = "CANNED",
                storageLocation = "PANTRY",
                quantity = 2f,
                unit = "CAN",
                confidence = 0.95f,
                estimatedShelfLifeDays = 60,
                isAromaticOrSeasoning = false,
                boundingBox = NormalizedBox(0f, 0f, 1f, 1f),
                conditionNotes = null,
                isOpened = false,
            )

        val updated = visionItem.toUpdatedPantryItem(existing, now)
        assertEquals(false, updated.isOpened)
        org.junit.Assert.assertNull(updated.openedTimestamp)
        assertEquals(true, updated.isDeleted)
    }

    @Test
    fun mapsVisionItemWithOpenedStatusPreservesEarlierUnopenedExpiration() {
        val now = 1_000_000L
        val item =
            ParsedVisionItem(
                name = "Fresh Cream",
                category = "DAIRY",
                storageLocation = "FRIDGE",
                quantity = 1f,
                unit = "BOTTLE",
                confidence = 0.95f,
                estimatedShelfLifeDays = 2, // 2 days is shorter than DAIRY opened default of 7 days
                isAromaticOrSeasoning = false,
                boundingBox = NormalizedBox(0f, 0f, 1f, 1f),
                conditionNotes = null,
                isOpened = true,
            )

        val pantryItem = item.toPantryItem(now)

        assertTrue(pantryItem.isOpened)
        assertEquals(now, pantryItem.openedTimestamp)
        // Should retain earlier expiration (2 days), not extended to 7 days
        assertEquals(now + 2 * 86_400_000L, pantryItem.expirationTimestamp)
    }

    @Test
    fun negativeShelfLifeDaysDoesNotCreateAnExpirationDate() {
        val item =
            ParsedVisionItem(
                name = "Old Yeast",
                category = "BAKING",
                storageLocation = "PANTRY",
                quantity = 1f,
                unit = "PACKET",
                confidence = 0.9f,
                estimatedShelfLifeDays = -3,
                isAromaticOrSeasoning = false,
                boundingBox = NormalizedBox(0f, 0f, 1f, 1f),
                conditionNotes = null,
            )

        val pantryItem = item.toPantryItem(1_000L)
        org.junit.Assert.assertNull(pantryItem.expirationTimestamp)
    }

    @Test
    fun defaultTimestampParametersUseCurrentTime() {
        val before = System.currentTimeMillis()
        val item =
            ParsedVisionItem(
                name = "Apples",
                category = "PRODUCE",
                storageLocation = "PANTRY",
                quantity = 3f,
                unit = "COUNT",
                confidence = 0.9f,
                estimatedShelfLifeDays = 7,
                isAromaticOrSeasoning = false,
                boundingBox = NormalizedBox(0f, 0f, 1f, 1f),
                conditionNotes = null,
            )

        val pantryItem = item.toPantryItem()
        val after = System.currentTimeMillis()
        assertTrue(pantryItem.createdAt in before..after)

        val visionItem = pantryItem.toParsedVisionItem()
        assertEquals("Apples", visionItem.name)

        val expired = pantryItem.copy(expirationTimestamp = before - 86_400_000L)
        val expiredVision = expired.toParsedVisionItem(before)
        assertEquals(0, expiredVision.estimatedShelfLifeDays)

        val nullExpVision = pantryItem.copy(expirationTimestamp = null).toParsedVisionItem(before)
        assertEquals(0, nullExpVision.estimatedShelfLifeDays)

        val existing = PantryItem(name = "Apples", category = "PRODUCE")
        val updated = item.toUpdatedPantryItem(existing)
        assertTrue(updated.lastUpdated in before..System.currentTimeMillis())
    }

    @Test
    fun toUpdatedPantryItemDefaultsOpenedTimestampWhenExistingOpenedTimestampNull() {
        val now = 5_000_000L
        val existing =
            PantryItem(
                id = 77L,
                name = "Olive Oil",
                category = "OILS",
                isOpened = true,
                openedTimestamp = null,
            )

        val visionItem =
            ParsedVisionItem(
                name = "Olive Oil",
                category = "OILS",
                storageLocation = "PANTRY",
                quantity = 1f,
                unit = "BOTTLE",
                confidence = 0.9f,
                estimatedShelfLifeDays = 180,
                isAromaticOrSeasoning = false,
                boundingBox = NormalizedBox(0f, 0f, 1f, 1f),
                conditionNotes = null,
                isOpened = true,
            )

        val updated = visionItem.toUpdatedPantryItem(existing, now)
        assertTrue(updated.isOpened)
        // Since existing.openedTimestamp is null, it falls back to now
        assertEquals(now, updated.openedTimestamp)
    }
}
