package com.jeffers.pantrycraft.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryJsonParserTest {
    @Test
    fun parseFullPantryItemWithAllFieldsPopulated() {
        val json = JSONObject().apply {
            put("name", "Whole Milk")
            put("quantity", 2.5)
            put("unit", "L")
            put("customUnitName", "Jug")
            put("threshold", 1.0)
            put("barcode", "123456789012")
            put("storageLocation", "FRIDGE")
            put("category", "DAIRY")
            put("expirationTimestamp", 1700000000000L)
            put("isOpened", true)
            put("openedTimestamp", 1699900000000L)
            put("isAromaticOrSeasoning", false)
            put("notes", "Organic whole milk")
        }

        val item = json.toPantryItem()
        assertNotNull(item)
        assertEquals("Whole Milk", item!!.name)
        assertEquals(2.5f, item.quantity, 0.001f)
        assertEquals("L", item.unit)
        assertEquals("Jug", item.customUnitName)
        assertEquals(1.0f, item.threshold, 0.001f)
        assertEquals("123456789012", item.barcode)
        assertEquals("FRIDGE", item.storageLocation)
        assertEquals("DAIRY", item.category)
        assertEquals(1700000000000L, item.expirationTimestamp)
        assertTrue(item.isOpened)
        assertEquals(1699900000000L, item.openedTimestamp)
        assertFalse(item.isAromaticOrSeasoning)
        assertEquals("Organic whole milk", item.notes)
    }

    @Test
    fun parseMinimalItemAppliesDefaults() {
        val json = JSONObject().apply {
            put("name", "Basmati Rice")
        }

        val item = json.toPantryItem()
        assertNotNull(item)
        assertEquals("Basmati Rice", item!!.name)
        assertEquals(1.0f, item.quantity, 0.001f)
        assertEquals("COUNT", item.unit)
        assertEquals(0.0f, item.threshold, 0.001f)
        assertEquals("PANTRY", item.storageLocation)
        assertEquals("OTHER", item.category)
        assertNull(item.expirationTimestamp)
        assertFalse(item.isOpened)
        assertFalse(item.isAromaticOrSeasoning)
        assertNull(item.notes)
    }

    @Test
    fun blankOrMissingNameReturnsNull() {
        val missingName = JSONObject().apply { put("quantity", 5.0) }
        val emptyName = JSONObject().apply { put("name", "   ") }

        assertNull(missingName.toPantryItem())
        assertNull(emptyName.toPantryItem())
    }

    @Test
    fun negativeQuantitiesAreClampedToZero() {
        val json = JSONObject().apply {
            put("name", "Sugar")
            put("quantity", -10.0)
            put("threshold", -5.0)
        }

        val item = json.toPantryItem()
        assertNotNull(item)
        assertEquals(0f, item!!.quantity, 0.001f)
        assertEquals(0f, item.threshold, 0.001f)
    }

    @Test
    fun parseInventoryJsonExtractsListAndIgnoresMalformedRecords() {
        val payload = """
            {
                "version": 1,
                "exportedAt": 1700000000000,
                "pantry": [
                    { "name": "Apples", "quantity": 4.0, "category": "PRODUCE" },
                    { "quantity": 10.0 },
                    { "name": "Black Pepper", "isAromaticOrSeasoning": true, "category": "SPICES" }
                ]
            }
        """.trimIndent()

        val items = parsePantryItemsFromJson(payload)
        assertEquals(2, items.size)
        assertEquals("Apples", items[0].name)
        assertEquals(4.0f, items[0].quantity, 0.001f)
        assertEquals("Black Pepper", items[1].name)
        assertTrue(items[1].isAromaticOrSeasoning)
    }

    @Test
    fun invalidOrEmptyJsonReturnsEmptyList() {
        assertEquals(emptyList<PantryItem>(), parsePantryItemsFromJson(""))
        assertEquals(emptyList<PantryItem>(), parsePantryItemsFromJson("{ invalid json }"))
        assertEquals(emptyList<PantryItem>(), parsePantryItemsFromJson("{}"))
    }
}
