package com.jeffers.pantrycraft.data

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
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

    @Test
    fun exportInventoryToJsonAndCsvWithNullFieldsUsesFallbacks() {
        val pantry = listOf(
            PantryItem(
                name = "Apple",
                unit = null,
                customUnitName = null,
                barcode = null,
                expirationTimestamp = null,
                openedTimestamp = null,
                notes = null,
            ),
        )
        val grocery = listOf(
            GroceryItem(
                name = "Bread",
                unit = null,
            ),
        )

        val json = exportInventoryToJson(pantry, grocery)
        val root = JSONObject(json)
        val p = root.getJSONArray("pantry").getJSONObject(0)
        assertEquals("COUNT", p.getString("unit"))
        assertTrue(p.isNull("customUnitName"))
        assertEquals("", p.getString("barcode"))
        assertTrue(p.isNull("expirationTimestamp"))
        assertTrue(p.isNull("openedTimestamp"))
        assertEquals("", p.getString("notes"))

        val g = root.getJSONArray("grocery").getJSONObject(0)
        assertEquals("COUNT", g.getString("unit"))

        val csv = exportPantryToCsv(pantry)
        val lines = csv.lines().filter { it.isNotBlank() }
        assertEquals(2, lines.size)
        assertTrue(lines[1].contains("\"COUNT\""))
    }

    @Test
    fun repositoryExportExtensionsReturnValidJsonAndCsv() = runBlocking {
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
                isOpened = true,
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
        val repo = createTestRepository(pantry, grocery)
        val json = repo.exportInventoryToJson()
        assertTrue(json.contains("\"name\": \"Rosemary\""))
        assertTrue(json.contains("\"name\": \"Milk\""))

        val csv = repo.exportPantryToCsv()
        assertTrue(csv.contains("name,category,storageLocation,quantity"))
        assertTrue(csv.contains("\"Rosemary\""))
    }

    private fun createTestRepository(
        pantryItems: List<PantryItem> = emptyList(),
        groceryItems: List<GroceryItem> = emptyList(),
    ): Repository {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        val allocateInstance = unsafeClass.getMethod("allocateInstance", Class::class.java)
        val repo = allocateInstance.invoke(unsafe, Repository::class.java) as Repository
        val delegateField = Repository::class.java.getDeclaredField("\$\$delegate_0").apply { isAccessible = true }
        val fakeRepo = object : PantryRepository {
            override fun observePantryItems() = flowOf(pantryItems)
            override fun observeGroceryItems() = flowOf(groceryItems)
            override fun searchPantryItems(query: String) = flowOf(pantryItems)
            override suspend fun insertPantry(item: PantryItem): Long = 1L
            override suspend fun updatePantry(item: PantryItem) {
                // Mock no-op
            }
            override suspend fun decrementQuantity(item: PantryItem, delta: Float) {
                // Mock no-op
            }
            override suspend fun deletePantry(item: PantryItem) {
                // Mock no-op
            }
            override suspend fun restorePantry(item: PantryItem) {
                // Mock no-op
            }
            override suspend fun insertGrocery(item: GroceryItem): Long = 1L
            override suspend fun updateGrocery(item: GroceryItem) {
                // Mock no-op
            }
            override suspend fun deleteGrocery(item: GroceryItem) {
                // Mock no-op
            }
            override suspend fun clearCheckedGrocery() {
                // Mock no-op
            }
            override suspend fun restockCheckedGroceryToPantry(): Int = 0
            override fun reclaimTemporaryMedia(): Int = 0
        }
        delegateField.set(repo, fakeRepo)
        return repo
    }
}
