package com.jeffers.pantrycraft.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var context: Context
    private lateinit var repository: Repository
    private var preexistingCheckedItems: List<GroceryItem> = emptyList()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = Repository.getInstance(context)
        runBlocking {
            cleanup(context)
            preexistingCheckedItems = repository.observeGroceryItems().first().filter { it.checked }
            preexistingCheckedItems.forEach { repository.updateGrocery(it.copy(checked = false)) }
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            cleanup(context)
            preexistingCheckedItems.forEach { repository.updateGrocery(it) }
        }
    }

    private suspend fun cleanup(context: Context) {
        val db = AppDatabase.getDatabase(context)
        db.pantryDao().deleteByNamePrefix("repository-")
        db.pantryDao().deleteByBarcodePrefix("barcode-repository-")
        db.groceryDao().deleteByNamePrefix("repository-")
    }

    @Test
    fun repositoryPantryCrudAndSearchContracts() = runBlocking {
        val marker = "repository-${System.nanoTime()}"
        val item = PantryItem(
            name = "  $marker  ",
            quantity = 2f,
            threshold = 2f,
            barcode = "barcode-$marker",
            notes = "searchable note",
        )

        val id = repository.insertPantry(item)
        val observed = repository.observePantryItems().first().single { it.id == id }
        assertEquals(marker, observed.normalizedName)
        assertEquals("$marker", observed.name.trim())
        assertEquals(observed, repository.findPantryItemByBarcode(item.barcode!!))
        assertTrue(repository.observeLowStock().first().any { it.id == id })
        assertTrue(repository.getAvailableIngredientsForMeals().any { it.id == id })
        assertEquals(1, repository.searchPantryItems(marker).first().count { it.id == id })
        assertEquals(1, repository.searchPantryItems("$marker searchable").first().count { it.id == id })
        assertEquals(1, repository.searchPantryItems("\"$marker\"").first().count { it.id == id })
        assertEquals(1, repository.searchPantryItems("searchable note").first().count { it.id == id })
        assertEquals(1, repository.searchPantryItems("  ").first().count { it.id == id })

        // FTS5 search query sanitization (symbols, wildcards, operators do not crash)
        assertEquals(1, repository.searchPantryItems("***").first().count { it.id == id })
        assertEquals(1, repository.searchPantryItems("---").first().count { it.id == id })
        assertEquals(1, repository.searchPantryItems("OR").first().count { it.id == id })
        assertEquals(1, repository.searchPantryItems("\"\"").first().count { it.id == id })
        assertEquals(1, repository.searchPantryItems("(()").first().count { it.id == id })

        repository.decrementQuantity(observed, 5f)
        val depleted = repository.observePantryItems().first().single { it.id == id }
        assertEquals(0f, depleted.quantity)
        repository.updatePantry(observed.copy(quantity = 4f))
        val updated = repository.observePantryItems().first().single { it.id == id }
        assertEquals(4f, updated.quantity)
        repository.deletePantry(observed)
        assertFalse(repository.observePantryItems().first().any { it.id == id })
        assertEquals(null, repository.findPantryItemByBarcode(item.barcode!!))
        assertNotNull(repository.findAnyPantryItemByBarcode(item.barcode!!))
    }

    @Test
    fun repositoryBarcodeLifecycleAndBatchContracts() = runBlocking {
        val marker = "repository-bar-${System.nanoTime()}"
        val item = PantryItem(
            name = marker,
            quantity = 2f,
            barcode = "barcode-$marker",
        )

        val id = repository.insertPantry(item)
        val observed = repository.observePantryItems().first().single { it.id == id }
        repository.deletePantry(observed)

        // Re-inserting with the same barcode restores the soft-deleted item
        val restoredId = repository.insertPantry(item.copy(quantity = 3f))
        assertEquals(id, restoredId)
        val restored = repository.observePantryItems().first().single { it.id == id }
        assertEquals(3f, restored.quantity)
        assertFalse(restored.isDeleted)

        // Scanning duplicate barcode increments quantity instead of overwriting
        val incrementedId = repository.insertPantry(item.copy(quantity = 2f))
        assertEquals(id, incrementedId)
        val incremented = repository.observePantryItems().first().single { it.id == id }
        assertEquals(5f, incremented.quantity)

        // Batch insert
        val batchItem1 = PantryItem(name = "$marker-batch1", quantity = 1f)
        val batchItem2 = PantryItem(name = "$marker-batch2", quantity = 2f)
        val batchIds = repository.insertPantryBatch(listOf(batchItem1, batchItem2))
        assertEquals(2, batchIds.size)
    }

    @Test
    fun repositoryExportImportGroceryAndValidationContracts() = runBlocking {
        val marker = "repository-exp-${System.nanoTime()}"
        val item = PantryItem(name = marker, quantity = 2f)
        val id = repository.insertPantry(item)
        val observed = repository.observePantryItems().first().single { it.id == id }

        // Data Sovereignty: JSON and CSV export/import
        val exportedJson = repository.exportInventoryToJson()
        assertTrue(exportedJson.contains(marker))
        val exportedCsv = repository.exportPantryToCsv()
        assertTrue(exportedCsv.contains(marker))

        val importedCount = repository.importInventoryFromJson(exportedJson)
        assertTrue(importedCount > 0)
        val importedCsvCount = repository.importPantryFromCsv(exportedCsv)
        assertTrue(importedCsvCount > 0)

        val grocery = GroceryItem(name = marker)
        val groceryId = repository.insertGrocery(grocery)
        assertTrue(repository.observeGroceryItems().first().any { it.id == groceryId })
        repository.updateGrocery(grocery.copy(id = groceryId, checked = true))
        val deletedGroceryId = repository.insertGrocery(GroceryItem(name = "$marker-delete"))
        repository.deleteGrocery(grocery.copy(id = deletedGroceryId))

        // Restock checked grocery to pantry
        val restockedCount = repository.restockCheckedGroceryToPantry()
        assertEquals(1, restockedCount)
        assertFalse(repository.observeGroceryItems().first().any { it.id == groceryId })

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.insertPantry(item.copy(name = "   ")) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.insertPantry(item.copy(quantity = -1f)) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.insertPantry(item.copy(threshold = -1f)) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.insertPantry(item.copy(storageLocation = "INVALID")) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.decrementQuantity(observed, 0f) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.decrementQuantity(observed, Float.NaN) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.decrementQuantity(observed, Float.POSITIVE_INFINITY) }
        }

        val scratch = context.cacheDir.resolve("vision_scratch")
        scratch.deleteRecursively()
        assertEquals(0, repository.reclaimTemporaryMedia())
        scratch.mkdirs()
        scratch.resolve("temporary-frame").writeText("ephemeral")
        assertEquals(1, repository.reclaimTemporaryMedia())
        assertEquals(0, repository.reclaimTemporaryMedia())
    }

    @Test
    fun repositoryOfflineBarcodeResolverInitialization() {
        val resolver = repository.offlineBarcodeResolver()
        assertNotNull(resolver)
        resolver.close()
    }
}
