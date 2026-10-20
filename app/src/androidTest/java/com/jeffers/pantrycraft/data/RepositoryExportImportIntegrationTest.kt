package com.jeffers.pantrycraft.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryExportImportIntegrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repository: Repository
    private val prefix = "export-import-int-${System.nanoTime()}-"

    @Before
    fun setUp() {
        repository = Repository.getInstance(context)
        cleanupTestItems()
    }

    @After
    fun tearDown() {
        cleanupTestItems()
    }

    private fun cleanupTestItems() = runBlocking {
        val db = AppDatabase.getDatabase(context)
        db.pantryDao().deleteByNamePrefix("export-import-int-")
        db.groceryDao().deleteByNamePrefix("export-import-int-")
    }

    @Test
    fun exportInventoryToJsonContainsValidSchemaAndItems() = runBlocking {
        val pantryItem = PantryItem(
            name = "${prefix}tea",
            quantity = 5f,
            storageLocation = "PANTRY",
            category = "BEVERAGES",
            notes = "Earl Grey, loose leaf",
            isAromaticOrSeasoning = true,
        )
        val groceryItem = GroceryItem(
            name = "${prefix}sugar",
            quantity = 2f,
            unit = "KG",
            checked = false,
        )

        repository.insertPantry(pantryItem)
        repository.insertGrocery(groceryItem)

        val exportedJson = repository.exportInventoryToJson()
        assertNotNull(exportedJson)

        val root = JSONObject(exportedJson)
        assertEquals(1, root.getInt("version"))
        assertTrue(root.getLong("exportedAt") > 0L)

        val pantryArray = root.getJSONArray("pantry")
        var foundPantry = false
        for (i in 0 until pantryArray.length()) {
            val obj = pantryArray.getJSONObject(i)
            if (obj.getString("name") == "${prefix}tea") {
                foundPantry = true
                assertEquals("BEVERAGES", obj.getString("category"))
                assertEquals("PANTRY", obj.getString("storageLocation"))
                assertEquals(5.0, obj.getDouble("quantity"), 0.01)
                assertEquals("Earl Grey, loose leaf", obj.getString("notes"))
                assertTrue(obj.getBoolean("isAromaticOrSeasoning"))
            }
        }
        assertTrue("Exported JSON must contain inserted pantry item", foundPantry)

        val groceryArray = root.getJSONArray("grocery")
        var foundGrocery = false
        for (i in 0 until groceryArray.length()) {
            val obj = groceryArray.getJSONObject(i)
            if (obj.getString("name") == "${prefix}sugar") {
                foundGrocery = true
                assertEquals(2.0, obj.getDouble("quantity"), 0.01)
                assertEquals("KG", obj.getString("unit"))
            }
        }
        assertTrue("Exported JSON must contain inserted grocery item", foundGrocery)
    }

    @Test
    fun exportPantryToCsvContainsHeaderAndEscapedValues() = runBlocking {
        val pantryItem = PantryItem(
            name = "${prefix}vanilla extract",
            quantity = 1.5f,
            storageLocation = "PANTRY",
            category = "BAKING",
            notes = "Pure Madagascar, \"Bourbon\" variety",
            isAromaticOrSeasoning = true,
        )
        repository.insertPantry(pantryItem)

        val csvString = repository.exportPantryToCsv()
        assertTrue(
            csvString.startsWith(
                "name,category,storageLocation,quantity,unit,customUnitName,threshold,expirationDate,isOpened,notes",
            ),
        )
        assertTrue(csvString.contains("${prefix}vanilla extract"))
        // Check that quotes in notes are escaped with double quotes
        assertTrue(csvString.contains("\"\"Bourbon\"\""))
    }

    @Test
    fun importPantryFromCsvImportsRowsCorrectly() = runBlocking {
        val csvData = """
            name,category,storageLocation,quantity,unit,customUnitName,threshold,expirationDate,isOpened,notes
            "${prefix}matcha powder",SPICES,PANTRY,100,G,,20,2027-01-01,false,"Ceremonial grade"
            "${prefix}quinoa",GRAINS,PANTRY,2,KG,,1,2026-12-31,false,"Organic white"
        """.trimIndent()

        val importedCount = repository.importPantryFromCsv(csvData)
        assertEquals(2, importedCount)

        val items = repository.observePantryItems().first().filter { it.name.startsWith(prefix) }
        assertEquals(2, items.size)
        val matcha = items.first { it.name == "${prefix}matcha powder" }
        assertEquals("SPICES", matcha.category)
        assertEquals(100f, matcha.quantity)
        assertEquals("G", matcha.unit)
        assertEquals("Ceremonial grade", matcha.notes)
        assertTrue(matcha.isAromaticOrSeasoning)
    }

    @Test
    fun importInventoryFromJsonImportsRowsCorrectly() = runBlocking {
        val jsonData = """
            {
              "version": 1,
              "exportedAt": 1700000000000,
              "pantry": [
                {
                  "name": "${prefix}chia seeds",
                  "quantity": 500.0,
                  "unit": "G",
                  "category": "GRAINS",
                  "storageLocation": "PANTRY",
                  "notes": "Rich in omega-3"
                }
              ]
            }
        """.trimIndent()

        val importedCount = repository.importInventoryFromJson(jsonData)
        assertEquals(1, importedCount)

        val items = repository.observePantryItems().first().filter { it.name == "${prefix}chia seeds" }
        assertEquals(1, items.size)
        assertEquals(500f, items.first().quantity)
        assertEquals("GRAINS", items.first().category)
    }

    @Test
    fun importLegacyPantryFromCsvSupportsEightColumnFormat() = runBlocking {
        val legacyCsv = """
            name,category,storageLocation,quantity,unit,expirationDate,isOpened,notes
            "${prefix}rolled oats",GRAINS,PANTRY,1.5,KG,2026-11-15,true,"Old fashioned"
        """.trimIndent()

        val importedCount = repository.importPantryFromCsv(legacyCsv)
        assertEquals(1, importedCount)

        val items = repository.observePantryItems().first().filter { it.name.startsWith(prefix) }
        assertEquals(1, items.size)
        val oats = items.first { it.name == "${prefix}rolled oats" }
        assertEquals("GRAINS", oats.category)
        assertEquals("PANTRY", oats.storageLocation)
        assertEquals(1.5f, oats.quantity)
        assertEquals("KG", oats.unit)
        assertEquals(null, oats.customUnitName)
        assertEquals(0f, oats.threshold)
        assertTrue(oats.isOpened)
        assertNotNull(oats.openedTimestamp)
        assertEquals("Old fashioned", oats.notes)
    }

    @Test
    fun importEmptyCsvAndJsonReturnsZeroCount() = runBlocking {
        assertEquals(0, repository.importPantryFromCsv(""))
        assertEquals(0, repository.importPantryFromCsv("   \n\n"))
        assertEquals(0, repository.importPantryFromCsv("name,category,storageLocation,quantity,unit\n"))

        assertEquals(0, repository.importInventoryFromJson(""))
        assertEquals(0, repository.importInventoryFromJson("{}"))
        assertEquals(0, repository.importInventoryFromJson("{\"pantry\": []}"))
    }
}
