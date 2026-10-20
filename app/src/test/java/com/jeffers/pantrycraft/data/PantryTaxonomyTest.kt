package com.jeffers.pantrycraft.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PantryTaxonomyTest {
    @Test
    fun categoriesListContainsRequiredTaxonomy() {
        val expected = listOf(
            "OTHER", "PRODUCE", "DAIRY", "MEAT", "SEAFOOD",
            "BAKERY", "GRAINS", "CANNED", "PANTRY_STAPLE", "FROZEN",
            "SNACKS", "BEVERAGES", "CONDIMENTS", "SPICES"
        )
        assertEquals(expected, PantryTaxonomy.categories)
    }

    @Test
    fun storageLocationsContainsCoreHouseholdZones() {
        assertEquals(listOf("PANTRY", "FRIDGE", "FREEZER", "COUNTER"), PantryTaxonomy.storageLocations)
    }

    @Test
    fun integerQuantityUnitsAreCorrectlyFlagged() {
        assertTrue(PantryTaxonomy.integerQuantityUnits.contains("COUNT"))
        assertTrue(PantryTaxonomy.integerQuantityUnits.contains("EACH"))
        assertTrue(PantryTaxonomy.integerQuantityUnits.contains("PACKAGE"))
        assertTrue(PantryTaxonomy.integerQuantityUnits.contains("CAN"))
        assertTrue(PantryTaxonomy.integerQuantityUnits.contains("BOTTLE"))
        assertFalse(PantryTaxonomy.integerQuantityUnits.contains("G"))
        assertFalse(PantryTaxonomy.integerQuantityUnits.contains("ML"))
        assertFalse(PantryTaxonomy.integerQuantityUnits.contains("KG"))
    }

    @Test
    fun isAromaticOrSeasoningCategoryIdentifiesSpicesAndCondiments() {
        assertTrue(PantryTaxonomy.isAromaticOrSeasoningCategory("SPICES"))
        assertTrue(PantryTaxonomy.isAromaticOrSeasoningCategory("spices"))
        assertTrue(PantryTaxonomy.isAromaticOrSeasoningCategory("CONDIMENTS"))
        assertTrue(PantryTaxonomy.isAromaticOrSeasoningCategory("condiments"))

        assertFalse(PantryTaxonomy.isAromaticOrSeasoningCategory("PRODUCE"))
        assertFalse(PantryTaxonomy.isAromaticOrSeasoningCategory("DAIRY"))
        assertFalse(PantryTaxonomy.isAromaticOrSeasoningCategory("MEAT"))
        assertFalse(PantryTaxonomy.isAromaticOrSeasoningCategory("GRAINS"))
        assertFalse(PantryTaxonomy.isAromaticOrSeasoningCategory("OTHER"))
    }

    @Test
    fun openedShelfLifeDaysFollowsFoodSafetyGuidelines() {
        // Highly perishable (3 days)
        assertEquals(3, PantryTaxonomy.openedShelfLifeDays("MEAT"))
        assertEquals(3, PantryTaxonomy.openedShelfLifeDays("SEAFOOD"))

        // Fresh perishables (7 days)
        assertEquals(7, PantryTaxonomy.openedShelfLifeDays("DAIRY"))
        assertEquals(7, PantryTaxonomy.openedShelfLifeDays("PRODUCE"))
        assertEquals(7, PantryTaxonomy.openedShelfLifeDays("BAKERY"))
        assertEquals(7, PantryTaxonomy.openedShelfLifeDays("BEVERAGES"))
        assertEquals(7, PantryTaxonomy.openedShelfLifeDays("CANNED"))

        // Stable items (14 days)
        assertEquals(14, PantryTaxonomy.openedShelfLifeDays("CONDIMENTS"))
        assertEquals(14, PantryTaxonomy.openedShelfLifeDays("SNACKS"))

        // Frozen (30 days)
        assertEquals(30, PantryTaxonomy.openedShelfLifeDays("FROZEN"))

        // Dry staples & spices (90 days)
        assertEquals(90, PantryTaxonomy.openedShelfLifeDays("SPICES"))
        assertEquals(90, PantryTaxonomy.openedShelfLifeDays("GRAINS"))
        assertEquals(90, PantryTaxonomy.openedShelfLifeDays("PANTRY_STAPLE"))

        // Default fallback (14 days)
        assertEquals(14, PantryTaxonomy.openedShelfLifeDays("UNKNOWN_CUSTOM"))
    }

    @Test
    fun unitsListContainsSupportedMeasurementUnits() {
        val expectedUnits = listOf(
            "COUNT", "EACH", "G", "KG", "ML", "L", "OZ", "LB",
            "PACKAGE", "CAN", "BOTTLE", "CUSTOM"
        )
        assertEquals(expectedUnits, PantryTaxonomy.units)
    }

    @Test
    fun shelfLifePresetsAreMonotonicallyIncreasing() {
        val presets = PantryTaxonomy.shelfLifePresetDays
        assertTrue(presets.isNotEmpty())
        assertEquals(0, presets.first())
        assertEquals(365, presets.last())
        for (i in 0 until presets.size - 1) {
            assertTrue(presets[i] < presets[i + 1])
        }
    }
}
