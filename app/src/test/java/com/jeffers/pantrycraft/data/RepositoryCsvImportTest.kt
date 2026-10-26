package com.jeffers.pantrycraft.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryCsvImportTest {
    @Test
    fun emptyOrHeaderOnlyCsvReturnsEmptyList() {
        assertTrue(parsePantryItemsFromCsv("").isEmpty())
        assertTrue(parsePantryItemsFromCsv("   \n\n  ").isEmpty())
        assertTrue(parsePantryItemsFromCsv("name,category,storageLocation,quantity,unit\n").isEmpty())
    }

    @Test
    fun parsesExtendedCsvColumnsAccurately() {
        val now = 1_700_000_000_000L
        val csv = """
            name,category,storageLocation,quantity,unit,customUnitName,threshold,expirationDate,isOpened,notes
            "Organic Olive Oil",OILS,PANTRY,2.5,BOTTLE,500ml,1.0,2026-12-31,true,"Extra virgin, cold-pressed"
        """.trimIndent()

        val items = parsePantryItemsFromCsv(csv, now)
        assertEquals(1, items.size)
        val item = items.single()

        assertEquals("Organic Olive Oil", item.name)
        assertEquals("OILS", item.category)
        assertEquals("PANTRY", item.storageLocation)
        assertEquals(2.5f, item.quantity, 0.001f)
        assertEquals("BOTTLE", item.unit)
        assertEquals("500ml", item.customUnitName)
        assertEquals(1.0f, item.threshold, 0.001f)
        assertNotNull(item.expirationTimestamp)
        assertTrue(item.isOpened)
        assertEquals(now, item.openedTimestamp)
        assertEquals("Extra virgin, cold-pressed", item.notes)
    }

    @Test
    fun parsesLegacyEightColumnFormatWithDefaults() {
        val now = 1_700_000_000_000L
        val csv = """
            name,category,storageLocation,quantity,unit,expirationDate,isOpened,notes
            "Rolled Oats",GRAINS,PANTRY,3,KG,2027-01-15,false,"Quick oats"
        """.trimIndent()

        val items = parsePantryItemsFromCsv(csv, now)
        assertEquals(1, items.size)
        val item = items.single()

        assertEquals("Rolled Oats", item.name)
        assertEquals("GRAINS", item.category)
        assertEquals("PANTRY", item.storageLocation)
        assertEquals(3f, item.quantity, 0.001f)
        assertEquals("KG", item.unit)
        assertNull(item.customUnitName)
        assertEquals(0f, item.threshold, 0.001f)
        assertNotNull(item.expirationTimestamp)
        assertEquals(false, item.isOpened)
        assertNull(item.openedTimestamp)
        assertEquals("Quick oats", item.notes)
    }

    @Test
    fun invalidValuesFallBackToSafeDefaults() {
        val csv = """
            name,category,storageLocation,quantity,unit,customUnitName,threshold,expirationDate,isOpened,notes
            "Test Item",UNKNOWN_CAT,INVALID_LOC,-5.0,,   ,non_number,invalid-date,not-a-bool,
            "   ",DAIRY,FRIDGE,1,COUNT,,0,,,
        """.trimIndent()

        val items = parsePantryItemsFromCsv(csv)
        // Second row has blank name, so it must be skipped
        assertEquals(1, items.size)
        val item = items.single()

        assertEquals("Test Item", item.name)
        assertEquals("UNKNOWN_CAT", item.category)
        assertEquals("PANTRY", item.storageLocation)
        assertEquals(0f, item.quantity, 0.001f)
        assertEquals("COUNT", item.unit)
        assertNull(item.customUnitName)
        assertEquals(0f, item.threshold, 0.001f)
        assertNull(item.expirationTimestamp)
        assertEquals(false, item.isOpened)
        assertNull(item.notes)
    }

    @Test
    fun legacyFormatWithInvalidDateFallsBackToNull() {
        val csv = """
            name,category,storageLocation,quantity,unit,expirationDate,isOpened,notes
            "Legacy Unparseable",GRAINS,PANTRY,1,KG,not-a-date,false,""
        """.trimIndent()

        val items = parsePantryItemsFromCsv(csv)
        assertEquals(1, items.size)
        assertNull(items.single().expirationTimestamp)
    }

    @Test
    fun minimalCsvRowsWithMissingColumnsUseFallbacks() {
        val csv = """
            name
            "Solo Item"
            "Legacy Minimal",,,,,,
        """.trimIndent()

        val items = parsePantryItemsFromCsv(csv)
        assertEquals(2, items.size)

        val item1 = items[0]
        assertEquals("Solo Item", item1.name)
        assertEquals("OTHER", item1.category)
        assertEquals("PANTRY", item1.storageLocation)
        assertEquals(1f, item1.quantity, 0.001f)
        assertEquals("COUNT", item1.unit)
        assertEquals(0f, item1.threshold, 0.001f)
        assertNull(item1.expirationTimestamp)
        assertEquals(false, item1.isOpened)
        assertNull(item1.notes)

        val item2 = items[1]
        assertEquals("Legacy Minimal", item2.name)
        assertEquals("OTHER", item2.category)
        assertEquals("PANTRY", item2.storageLocation)
        assertEquals(1f, item2.quantity, 0.001f)
    }

    @Test
    fun parsePantryCsvItemWithEmptyColumnsReturnsNull() {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        assertNull(parsePantryCsvItem(emptyList(), dateFormat, 1_000L))
    }

    @Test
    fun parseThresholdWithExtendedAndEmptyColsReturnsZero() {
        assertEquals(0f, parseThreshold(emptyList(), extended = true), 0.001f)
        assertEquals(0f, parseThreshold(emptyList(), extended = false), 0.001f)
        val colsWithNegative = listOf("A", "B", "C", "D", "E", "F", "-5.5")
        assertEquals(0f, parseThreshold(colsWithNegative, extended = true), 0.001f)
    }
}
