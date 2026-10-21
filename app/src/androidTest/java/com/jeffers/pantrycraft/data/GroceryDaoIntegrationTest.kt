package com.jeffers.pantrycraft.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroceryDaoIntegrationTest {
    private lateinit var context: Context
    private lateinit var repository: Repository
    private val marker = "grocery-test-${System.nanoTime()}"
    private var preexistingCheckedItems: List<GroceryItem> = emptyList()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = Repository.getInstance(context)
        runBlocking {
            val db = AppDatabase.getDatabase(context)
            db.groceryDao().deleteByNamePrefix("grocery-test-")
            db.pantryDao().deleteByNamePrefix("grocery-test-")

            preexistingCheckedItems = repository.observeGroceryItems().first().filter { it.checked }
            preexistingCheckedItems.forEach { repository.updateGrocery(it.copy(checked = false)) }
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            val db = AppDatabase.getDatabase(context)
            db.groceryDao().deleteByNamePrefix("grocery-test-")
            db.pantryDao().deleteByNamePrefix("grocery-test-")

            preexistingCheckedItems.forEach { repository.updateGrocery(it) }
        }
    }




    @Test
    fun groceryDaoCrudLifecycle() = runBlocking {
        val itemA = GroceryItem(name = "$marker-ItemA", quantity = 2f, unit = "COUNT")
        val itemB = GroceryItem(name = "$marker-ItemB", quantity = 1.5f, unit = "KG")

        val idA = repository.insertGrocery(itemA)
        val idB = repository.insertGrocery(itemB)

        val items = repository.observeGroceryItems().first().filter { it.name.startsWith(marker) }
        assertEquals(2, items.size)

        val insertedA = items.single { it.id == idA }
        repository.updateGrocery(insertedA.copy(checked = true))

        val updatedItems = repository.observeGroceryItems().first().filter { it.name.startsWith(marker) }
        assertTrue(updatedItems.single { it.id == idA }.checked)
        assertFalse(updatedItems.single { it.id == idB }.checked)

        repository.clearCheckedGrocery()

        val remaining = repository.observeGroceryItems().first().filter { it.name.startsWith(marker) }
        assertEquals(1, remaining.size)
        assertEquals(idB, remaining.first().id)
    }

    @Test
    fun restockCheckedGroceryUpdatesExistingPantryItem() = runBlocking {
        val pantryId = repository.insertPantry(
            PantryItem(name = "$marker-Milk", quantity = 1f, unit = "L", storageLocation = "FRIDGE")
        )

        val groceryItem = GroceryItem(
            name = "$marker-Milk",
            quantity = 2f,
            unit = "L",
            checked = true,
            fromPantryId = pantryId,
        )
        val groceryId = repository.insertGrocery(groceryItem)

        val restockedCount = repository.restockCheckedGroceryToPantry()
        assertEquals(1, restockedCount)

        // Verifies grocery item was cleared
        assertFalse(repository.observeGroceryItems().first().any { it.id == groceryId })

        // Verifies pantry item quantity was incremented (1 + 2 = 3)
        val updatedPantry = repository.observePantryItems().first().single { it.id == pantryId }
        assertEquals(3f, updatedPantry.quantity)
    }

    @Test
    fun restockCheckedGroceryCreatesNewPantryItemWhenUnlinked() = runBlocking {
        val groceryItem = GroceryItem(
            name = "$marker-OliveOil",
            quantity = 500f,
            unit = "ML",
            checked = true,
            fromPantryId = null,
        )
        repository.insertGrocery(groceryItem)

        val count = repository.restockCheckedGroceryToPantry()
        assertEquals(1, count)

        val pantryItem = repository.observePantryItems().first().single { it.name == "$marker-OliveOil" }
        assertEquals(500f, pantryItem.quantity)
        assertEquals("ML", pantryItem.unit)
    }

    @Test
    fun groceryDaoDirectDeleteAndPrefixDelete() = runBlocking {
        val db = AppDatabase.getDatabase(context)
        val dao = db.groceryDao()

        val item1 = GroceryItem(name = "$marker-Direct1", quantity = 1f)
        val item2 = GroceryItem(name = "$marker-Direct2", quantity = 2f)
        val id1 = dao.insert(item1)
        val id2 = dao.insert(item2)

        dao.delete(item1.copy(id = id1))
        val afterDeleteOne = dao.observeAllGroceryItems().first().filter { it.name.startsWith(marker) }
        assertEquals(1, afterDeleteOne.size)
        assertEquals(id2, afterDeleteOne.first().id)

        val deletedCount = dao.deleteByNamePrefix(marker)
        assertEquals(1, deletedCount)
        val afterPrefixDelete = dao.observeAllGroceryItems().first().filter { it.name.startsWith(marker) }
        assertTrue(afterPrefixDelete.isEmpty())
    }
}
