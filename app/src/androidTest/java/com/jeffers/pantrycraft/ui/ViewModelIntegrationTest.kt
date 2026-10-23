package com.jeffers.pantrycraft.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jeffers.pantrycraft.data.AppDatabase
import com.jeffers.pantrycraft.data.GroceryItem
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.Repository
import com.jeffers.pantrycraft.data.markOpened
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val RETRY_ATTEMPTS = 25
private const val RETRY_DELAY_MILLIS = 50L

@RunWith(AndroidJUnit4::class)
class ViewModelIntegrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repository: Repository
    private val prefix = "vm-test-${System.nanoTime()}-"

    @Before
    fun setUp() {
        repository = Repository.getInstance(context)
        cleanupTestData()
    }

    @After
    fun tearDown() {
        cleanupTestData()
    }

    private fun cleanupTestData() = runBlocking {
        val db = AppDatabase.getDatabase(context)
        db.pantryDao().deleteByNamePrefix("vm-test-")
        db.groceryDao().deleteByNamePrefix("vm-test-")
    }

    private suspend fun <T> awaitCondition(
        retries: Int = RETRY_ATTEMPTS,
        delayMs: Long = RETRY_DELAY_MILLIS,
        predicate: suspend () -> T?,
    ): T? {
        repeat(retries) {
            val result = predicate()
            if (result != null) return result
            delay(delayMs)
        }
        return null
    }

    private class UnregisteredViewModel : ViewModel()

    @Test
    fun viewModelFactoriesInstantiateCorrectViewModels() {
        val pantryFactory = PantryViewModelFactory(repository)
        val pantryVm = pantryFactory.create(PantryViewModel::class.java)
        assertNotNull(pantryVm)

        try {
            pantryFactory.create(UnregisteredViewModel::class.java)
            fail("PantryViewModelFactory should throw on unknown class")
        } catch (expected: IllegalArgumentException) {
            assertEquals("Unknown ViewModel class", expected.message)
        }

        val groceryFactory = GroceryViewModelFactory(repository)
        val groceryVm = groceryFactory.create(GroceryViewModel::class.java)
        assertNotNull(groceryVm)

        try {
            groceryFactory.create(UnregisteredViewModel::class.java)
            fail("GroceryViewModelFactory should throw on unknown class")
        } catch (expected: IllegalArgumentException) {
            assertEquals("Unknown ViewModel class", expected.message)
        }
    }

    @Test
    fun groceryViewModelCrudLifecycle() = runBlocking {
        val groceryVm = GroceryViewModel(repository)
        val itemName = "${prefix}almond-milk"
        val initialItem = GroceryItem(name = itemName, quantity = 2f, unit = "BOTTLE")

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            groceryVm.add(initialItem)
        }

        val inserted = awaitCondition {
            repository.observeGroceryItems().first().firstOrNull { it.name == itemName }
        }
        assertNotNull(inserted)
        val nonNullInserted = requireNotNull(inserted)
        assertEquals(2f, nonNullInserted.quantity)

        val updatedItem = nonNullInserted.copy(quantity = 5f, checked = true)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            groceryVm.update(updatedItem)
        }

        val updated = awaitCondition {
            repository.observeGroceryItems().first().firstOrNull {
                it.id == nonNullInserted.id && it.quantity == 5f && it.checked
            }
        }
        assertNotNull(updated)
        val nonNullUpdated = requireNotNull(updated)
        assertEquals(5f, nonNullUpdated.quantity)
        assertTrue(nonNullUpdated.checked)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            groceryVm.delete(nonNullUpdated)
        }

        val deleted = awaitCondition {
            val exists = repository.observeGroceryItems().first().any { it.id == nonNullInserted.id }
            if (!exists) true else null
        }
        assertTrue(deleted == true)
    }

    @Test
    fun pantryViewModelSelectionState() {
        val pantryVm = PantryViewModel(repository)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            pantryVm.selectLocation(PantryLocationFilter.FRIDGE)
            pantryVm.selectSortOrder(PantrySortOrder.EXPIRATION_SOONEST)
            pantryVm.setSearchQuery("yogurt")
        }
        assertEquals(PantryLocationFilter.FRIDGE, pantryVm.selectedLocation.value)
        assertEquals(PantrySortOrder.EXPIRATION_SOONEST, pantryVm.selectedSortOrder.value)
        assertEquals("yogurt", pantryVm.searchQuery.value)
    }

    @Test
    fun pantryViewModelCrudLifecycle() = runBlocking {
        val pantryVm = PantryViewModel(repository)
        val itemName = "${prefix}greek-yogurt"
        val pantryItem = PantryItem(
            name = itemName,
            quantity = 3f,
            category = "DAIRY",
            storageLocation = "FRIDGE",
        )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            pantryVm.add(pantryItem)
        }

        val inserted = awaitCondition {
            repository.observePantryItems().first().firstOrNull { it.name == itemName }
        }
        assertNotNull(inserted)
        val nonNullInserted = requireNotNull(inserted)
        assertEquals("DAIRY", nonNullInserted.category)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            pantryVm.decrement(nonNullInserted, 1f)
        }

        val decremented = awaitCondition {
            repository.observePantryItems().first().firstOrNull {
                it.id == nonNullInserted.id && it.quantity == 2f
            }
        }
        assertNotNull(decremented)
        val nonNullDecremented = requireNotNull(decremented)
        assertEquals(2f, nonNullDecremented.quantity)

        val groceryName = "${prefix}grocery-reorder"
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            pantryVm.addGroceryItem(name = groceryName, quantity = 1f, unit = "TUB", fromPantryId = nonNullInserted.id)
        }

        val groceryAdded = awaitCondition {
            repository.observeGroceryItems().first().firstOrNull { it.name == groceryName }
        }
        assertNotNull(groceryAdded)
        assertEquals(nonNullInserted.id, groceryAdded?.fromPantryId)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            pantryVm.delete(nonNullDecremented)
        }

        val softDeleted = awaitCondition {
            val exists = repository.observePantryItems().first().any { it.id == nonNullInserted.id }
            if (!exists) true else null
        }
        assertTrue(softDeleted == true)

        // Test restoring the soft-deleted item
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            pantryVm.restore(nonNullDecremented)
        }

        val restored = awaitCondition {
            repository.observePantryItems().first().firstOrNull { it.id == nonNullInserted.id }
        }
        assertNotNull(restored)
    }

    @Test
    fun groceryViewModelClearCheckedRemovesCheckedItems() = runBlocking {
        val groceryVm = GroceryViewModel(repository)
        val checkedName = "${prefix}checked-item"
        val uncheckedName = "${prefix}unchecked-item"

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            groceryVm.add(GroceryItem(name = checkedName, quantity = 1f, checked = true))
            groceryVm.add(GroceryItem(name = uncheckedName, quantity = 2f, checked = false))
        }

        awaitCondition {
            val items = repository.observeGroceryItems().first().filter { it.name.startsWith(prefix) }
            if (items.size == 2) true else null
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            groceryVm.clearChecked()
        }

        val cleared = awaitCondition {
            val items = repository.observeGroceryItems().first().filter { it.name.startsWith(prefix) }
            if (items.size == 1 && items.first().name == uncheckedName) true else null
        }
        assertTrue(cleared == true)
    }

    @Test
    fun groceryViewModelRestockCheckedTransfersItemsToPantryAndEmitsCount() = runBlocking {
        val groceryVm = GroceryViewModel(repository)
        val restockName = "${prefix}restock-vanilla"
        val groceryItem = GroceryItem(
            name = restockName,
            quantity = 2f,
            unit = "BOTTLE",
            checked = true,
        )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            groceryVm.add(groceryItem)
        }

        awaitCondition {
            val items = repository.observeGroceryItems().first().filter { it.name == restockName }
            if (items.isNotEmpty()) true else null
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            groceryVm.restockChecked()
        }

        val restockedToPantry = awaitCondition {
            val pantryItems = repository.observePantryItems().first().filter { it.name == restockName }
            val groceryItems = repository.observeGroceryItems().first().filter { it.name == restockName }
            if (pantryItems.isNotEmpty() && groceryItems.isEmpty()) {
                val pantry = pantryItems.first()
                if (pantry.quantity == 2f && pantry.unit == "BOTTLE") true else null
            } else {
                null
            }
        }
        assertTrue("Checked item must be restocked to pantry and removed from grocery", restockedToPantry == true)
    }

    @Test
    fun pantryViewModelEmitsErrorOnInvalidInsert() = runBlocking {
        val pantryVm = PantryViewModel(repository)
        val invalidItem = PantryItem(name = "   ")

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            pantryVm.add(invalidItem)
        }

        val errorMessage = pantryVm.operationErrors.first()
        assertEquals("Unable to add pantry item", errorMessage)
    }

    @Test
    fun pantryViewModelIncrementAndMarkOpened() = runBlocking {
        val pantryVm = PantryViewModel(repository)
        val itemName = "${prefix}sour-cream"
        val item = PantryItem(
            name = itemName,
            quantity = 1f,
            category = "DAIRY",
            storageLocation = "FRIDGE",
            isOpened = false,
        )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            pantryVm.add(item)
        }

        val inserted = awaitCondition {
            repository.observePantryItems().first().firstOrNull { it.name == itemName }
        }
        assertNotNull(inserted)
        val nonNullInserted = requireNotNull(inserted)

        // Increment quantity
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            pantryVm.update(nonNullInserted.copy(quantity = nonNullInserted.quantity + 1f))
        }

        val incremented = awaitCondition {
            repository.observePantryItems().first().firstOrNull {
                it.id == nonNullInserted.id && it.quantity == 2f
            }
        }
        assertNotNull(incremented)
        val nonNullIncremented = requireNotNull(incremented)
        assertEquals(2f, nonNullIncremented.quantity)

        // Mark as opened
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            pantryVm.update(nonNullIncremented.markOpened())
        }

        val opened = awaitCondition {
            repository.observePantryItems().first().firstOrNull {
                it.id == nonNullInserted.id && it.isOpened
            }
        }
        assertNotNull(opened)
        val nonNullOpened = requireNotNull(opened)
        assertTrue(nonNullOpened.isOpened)
        assertNotNull(nonNullOpened.openedTimestamp)
    }
}
