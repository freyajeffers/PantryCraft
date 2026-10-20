package com.jeffers.pantrycraft.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomBarcodeCacheTest {
    @Test
    fun defaultValuesMatchSpecification() {
        val before = System.currentTimeMillis()
        val item = CustomBarcodeItem(
            barcode = "012345678901",
            name = "Organic Black Tea",
        )
        val after = System.currentTimeMillis()

        assertEquals("012345678901", item.barcode)
        assertEquals("Organic Black Tea", item.name)
        assertEquals("OTHER", item.category)
        assertEquals("COUNT", item.defaultUnit)
        assertEquals(0, item.estimatedShelfLifeDays)
        assertTrue(item.lastScanned in before..after)
    }

    @Test
    fun customValuesPreservedAndCopyMaintainsIntegrity() {
        val item = CustomBarcodeItem(
            barcode = "987654321098",
            name = "Olive Oil Extra Virgin",
            category = "OILS",
            defaultUnit = "BOTTLE",
            estimatedShelfLifeDays = 365,
            lastScanned = 1_700_000_000_000L,
        )

        assertEquals("OILS", item.category)
        assertEquals("BOTTLE", item.defaultUnit)
        assertEquals(365, item.estimatedShelfLifeDays)
        assertEquals(1_700_000_000_000L, item.lastScanned)

        val updated = item.copy(
            estimatedShelfLifeDays = 180,
            lastScanned = 1_700_050_000_000L,
        )
        assertEquals("987654321098", updated.barcode)
        assertEquals("Olive Oil Extra Virgin", updated.name)
        assertEquals(180, updated.estimatedShelfLifeDays)
        assertEquals(1_700_050_000_000L, updated.lastScanned)
    }
}
