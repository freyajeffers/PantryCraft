package com.jeffers.pantrycraft.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryExportTest {
    @Test
    fun exportInventoryToJsonSerializesPantryAndGrocery() {
        val now = 1_700_000_000_000L
        val pantry = listOf(
            PantryItem(
                id = 1L,
                name = "Rosemary",
                quantity = 2.5f,
                unit = "CUSTOM",
                customUnitName = "sprigs",
                threshold = 1f,
                barcode = "123456789012",
                storageLocation = "PANTRY",
                category = "SPICES",
                expirationTimestamp = now + 86_400_000L,
                isOpened = true,
                openedTimestamp = now,
                isAromaticOrSeasoning = true,
                notes = "Fresh from garden",
            ),
        )
        val grocery = listOf(
            GroceryItem(
                id = 10L,
                name = "Milk",
                quantity = 1f,
                unit = "GALLON",
                checked = true,
            ),
        )

        val jsonString = exportInventoryToJson(pantry, grocery, now)
        val root = JSONObject(jsonString)

        assertEquals(1, root.getInt("version"))
        assertEquals(now, root.getLong("exportedAt"))

        val pantryJson = root.getJSONArray("pantry")
        assertEquals(1, pantryJson.length())
        val pItem = pantryJson.getJSONObject(0)
        assertEquals("Rosemary", pItem.getString("name"))
        assertEquals(2.5, pItem.getDouble("quantity"), 0.001)
        assertEquals("CUSTOM", pItem.getString("unit"))
        assertEquals("sprigs", pItem.getString("customUnitName"))
        assertEquals(1.0, pItem.getDouble("threshold"), 0.001)
        assertEquals("123456789012", pItem.getString("barcode"))
        assertEquals("PANTRY", pItem.getString("storageLocation"))
        assertEquals("SPICES", pItem.getString("category"))
        assertEquals(now + 86_400_000L, pItem.getLong("expirationTimestamp"))
        assertTrue(pItem.getBoolean("isOpened"))
        assertEquals(now, pItem.getLong("openedTimestamp"))
        assertTrue(pItem.getBoolean("isAromaticOrSeasoning"))
        assertEquals("Fresh from garden", pItem.getString("notes"))

        val groceryJson = root.getJSONArray("grocery")
        assertEquals(1, groceryJson.length())
        val gItem = groceryJson.getJSONObject(0)
        assertEquals("Milk", gItem.getString("name"))
        assertEquals(1.0, gItem.getDouble("quantity"), 0.001)
        assertEquals("GALLON", gItem.getString("unit"))
        assertTrue(gItem.getBoolean("checked"))
    }

    @Test
    fun exportInventoryToJsonHandlesEmptyLists() {
        val jsonString = exportInventoryToJson(emptyList(), emptyList(), 1_000L)
        val root = JSONObject(jsonString)
        assertEquals(1, root.getInt("version"))
        assertEquals(0, root.getJSONArray("pantry").length())
        assertEquals(0, root.getJSONArray("grocery").length())
    }

    @Test
    fun exportPantryToCsvFormatsHeadersAndEscapesValues() {
        val items = listOf(
            PantryItem(
                name = "Dijon \"French\" Mustard",
                category = "CONDIMENTS",
                storageLocation = "FRIDGE",
                quantity = 1f,
                unit = "JAR",
                customUnitName = null,
                threshold = 0.5f,
                expirationTimestamp = 1_700_000_000_000L,
                isOpened = false,
                notes = "Contains seeds, whole grain",
            ),
        )

        val csv = exportPantryToCsv(items)
        val lines = csv.lines().filter { it.isNotBlank() }
        assertEquals(2, lines.size)

        assertEquals(
            "name,category,storageLocation,quantity,unit,customUnitName,threshold,expirationDate,isOpened,notes",
            lines[0],
        )

        assertTrue(lines[1].contains("\"Dijon \"\"French\"\" Mustard\""))
        assertTrue(lines[1].contains("\"CONDIMENTS\""))
        assertTrue(lines[1].contains("\"FRIDGE\""))
        assertTrue(lines[1].contains("\"Contains seeds, whole grain\""))
    }

    @Test
    fun exportPantryToCsvWithEmptyListOutputsOnlyHeader() {
        val csv = exportPantryToCsv(emptyList())
        val lines = csv.lines().filter { it.isNotBlank() }
        assertEquals(1, lines.size)
        assertTrue(lines[0].startsWith("name,category,storageLocation"))
    }
}
