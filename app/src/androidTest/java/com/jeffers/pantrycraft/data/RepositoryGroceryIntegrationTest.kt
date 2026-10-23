package com.jeffers.pantrycraft.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryGroceryIntegrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repository: Repository
    private val prefix = "grocery-int-${System.nanoTime()}-"
    private var previouslyCheckedItems: List<GroceryItem> = emptyList()

    @Before
    fun setUp() {
        repository = Repository.getInstance(context)
        runBlocking {
            cleanupTestItems()
            previouslyCheckedItems = repository.observeGroceryItems().first().filter { it.checked }
            previouslyCheckedItems.forEach { repository.updateGrocery(it.copy(checked = false)) }
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            cleanupTestItems()
            previouslyCheckedItems.forEach { repository.updateGrocery(it) }
        }
    }

    private suspend fun cleanupTestItems() {
        val db = AppDatabase.getDatabase(context)
        db.pantryDao().deleteByNamePrefix("grocery-int-")
        db.groceryDao().deleteByNamePrefix("grocery-int-")
    }

    @Test
    fun insertAndObserveGroceryItems() = runBlocking {
        val groceryItem = GroceryItem(
            name = "${prefix}almond milk",
            quantity = 2f,
            unit = "BOTTLE",
            checked = false,
        )

        repository.insertGrocery(groceryItem)

        val items = repository.observeGroceryItems().first().filter { it.name.startsWith(prefix) }
        assertEquals(1, items.size)
        assertEquals("${prefix}almond milk", items.first().name)
        assertEquals(2f, items.first().quantity)
        assertEquals("BOTTLE", items.first().unit)
    }

    @Test
    fun updateGroceryItemCheckedState() = runBlocking {
        val groceryItem = GroceryItem(
            name = "${prefix}dark chocolate",
            quantity = 1f,
            unit = "PACKAGE",
            checked = false,
        )
        repository.insertGrocery(groceryItem)

        val inserted = repository.observeGroceryItems().first().first { it.name == "${prefix}dark chocolate" }
        repository.updateGrocery(inserted.copy(checked = true))

        val updated = repository.observeGroceryItems().first().first { it.name == "${prefix}dark chocolate" }
        assertTrue(updated.checked)
    }

    @Test
    fun restockCheckedGroceryWithLinkedPantryItemUpdatesQuantity() = runBlocking {
        // 1. Insert existing pantry item
        val pantryItem = PantryItem(
            name = "${prefix}flour",
            quantity = 2f,
            unit = "KG",
            storageLocation = "PANTRY",
        )
        val pantryId = repository.insertPantry(pantryItem)

        // 2. Insert checked grocery item linked to this pantry item
        val groceryItem = GroceryItem(
            name = "${prefix}flour",
            quantity = 3f,
            unit = "KG",
            checked = true,
            fromPantryId = pantryId,
        )
        repository.insertGrocery(groceryItem)

        // 3. Restock checked items to pantry
        val restockedCount = repository.restockCheckedGroceryToPantry()
        assertEquals(1, restockedCount)

        // 4. Verify pantry item quantity updated from 2f to 5f
        val updatedPantry = repository.observePantryItems().first().first { it.id == pantryId }
        assertEquals(5f, updatedPantry.quantity)

        // 5. Verify checked grocery item is deleted
        val remainingGrocery = repository.observeGroceryItems().first().filter { it.name == "${prefix}flour" }
        assertTrue("Checked grocery item must be deleted after restocking", remainingGrocery.isEmpty())
    }

    @Test
    fun restockCheckedGroceryWithoutLinkedPantryItemCreatesNewPantryItem() = runBlocking {
        // 1. Insert checked grocery item without pantry link
        val groceryItem = GroceryItem(
            name = "${prefix}organic vanilla",
            quantity = 1f,
            unit = "BOTTLE",
            checked = true,
            fromPantryId = null,
        )
        repository.insertGrocery(groceryItem)

        // 2. Restock checked items
        val restockedCount = repository.restockCheckedGroceryToPantry()
        assertEquals(1, restockedCount)

        // 3. Verify newly created pantry item
        val createdPantry =
            repository.observePantryItems().first().firstOrNull { it.name == "${prefix}organic vanilla" }
        assertNotNull(createdPantry)
        assertEquals(1f, createdPantry!!.quantity)
        assertEquals("BOTTLE", createdPantry.unit)
    }

    @Test
    fun restockLeavesUncheckedGroceryItemsUntouched() = runBlocking {
        val uncheckedItem = GroceryItem(
            name = "${prefix}cinnamon sticks",
            quantity = 1f,
            unit = "PACKAGE",
            checked = false,
        )
        repository.insertGrocery(uncheckedItem)

        val restockedCount = repository.restockCheckedGroceryToPantry()
        assertEquals(0, restockedCount)

        val remaining = repository.observeGroceryItems().first().filter { it.name == "${prefix}cinnamon sticks" }
        assertEquals(1, remaining.size)
    }

    @Test
    fun clearCheckedGroceryDeletesCheckedItemsWhileLeavingUncheckedIntact() = runBlocking {
        val checkedItem = GroceryItem(
            name = "${prefix}sea salt",
            quantity = 1f,
            unit = "CANISTER",
            checked = true,
        )
        val uncheckedItem = GroceryItem(
            name = "${prefix}black pepper",
            quantity = 2f,
            unit = "JAR",
            checked = false,
        )
        repository.insertGrocery(checkedItem)
        repository.insertGrocery(uncheckedItem)

        repository.clearCheckedGrocery()

        val items = repository.observeGroceryItems().first().filter { it.name.startsWith(prefix) }
        assertEquals(1, items.size)
        assertEquals("${prefix}black pepper", items.first().name)
        assertEquals(false, items.first().checked)
    }

    @Test
    fun deleteGroceryRemovesSingleItem() = runBlocking {
        val item = GroceryItem(
            name = "${prefix}olive oil",
            quantity = 1f,
            unit = "BOTTLE",
            checked = false,
        )
        repository.insertGrocery(item)

        val inserted = repository.observeGroceryItems().first().first { it.name == "${prefix}olive oil" }
        repository.deleteGrocery(inserted)

        val remaining = repository.observeGroceryItems().first().filter { it.name == "${prefix}olive oil" }
        assertTrue("Deleted grocery item must be removed from repository", remaining.isEmpty())
    }
}
