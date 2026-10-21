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
import com.jeffers.pantrycraft.data.observeGroceryItems
import com.jeffers.pantrycraft.data.observePantryItems
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

        var inserted: GroceryItem? = null
        for (attempt in 1..RETRY_ATTEMPTS) {
            val current = repository.observeGroceryItems().first().firstOrNull { it.name == itemName }
            if (current != null) {
                inserted = current
                break
            }
            delay(RETRY_DELAY_MILLIS)
        }
        assertNotNull(inserted)
        assertEquals(2f, inserted!!.quantity)

        val updatedItem = inserted.copy(quantity = 5f, checked = true)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            groceryVm.update(updatedItem)
        }

        var updated: GroceryItem? = null
        for (attempt in 1..RETRY_ATTEMPTS) {
            val current = repository.observeGroceryItems().first().firstOrNull { it.id == inserted.id }
            if (current != null && current.quantity == 5f && current.checked) {
                updated = current
                break
            }
            delay(RETRY_DELAY_MILLIS)
        }
        assertNotNull(updated)
        assertEquals(5f, updated!!.quantity)
        assertTrue(updated.checked)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            groceryVm.delete(updated)
        }

        var deleted = false
        for (attempt in 1..RETRY_ATTEMPTS) {
            val current = repository.observeGroceryItems().first().firstOrNull { it.id == inserted.id }
            if (current == null) {
                deleted = true
                break
            }
            delay(RETRY_DELAY_MILLIS)
        }
        assertTrue(deleted)
    }

    @Test
    fun pantryViewModelSelectionAndCrudLifecycle() = runBlocking {
        val pantryVm = PantryViewModel(repository)
        val itemName = "${prefix}greek-yogurt"
        val pantryItem = PantryItem(
            name = itemName,
            quantity = 3f,
            category = "DAIRY",
            storageLocation = "FRIDGE",
        )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            pantryVm.selectLocation(PantryLocationFilter.FRIDGE)
            pantryVm.selectSortOrder(PantrySortOrder.EXPIRATION_SOONEST)
            pantryVm.setSearchQuery("yogurt")
        }
        assertEquals(PantryLocationFilter.FRIDGE, pantryVm.selectedLocation.value)
        assertEquals(PantrySortOrder.EXPIRATION_SOONEST, pantryVm.selectedSortOrder.value)
        assertEquals("yogurt", pantryVm.searchQuery.value)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            pantryVm.add(pantryItem)
        }

        var inserted: PantryItem? = null
        for (attempt in 1..RETRY_ATTEMPTS) {
            val current = repository.observePantryItems().first().firstOrNull { it.name == itemName }
            if (current != null) {
                inserted = current
                break
            }
            delay(RETRY_DELAY_MILLIS)
        }
        assertNotNull(inserted)
        assertEquals("DAIRY", inserted!!.category)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            pantryVm.decrement(inserted, 1f)
        }

        var decremented: PantryItem? = null
        for (attempt in 1..RETRY_ATTEMPTS) {
            val current = repository.observePantryItems().first().firstOrNull { it.id == inserted.id }
            if (current != null && current.quantity == 2f) {
                decremented = current
                break
            }
            delay(RETRY_DELAY_MILLIS)
        }
        assertNotNull(decremented)
        assertEquals(2f, decremented!!.quantity)

        val groceryName = "${prefix}grocery-reorder"
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            pantryVm.addGroceryItem(name = groceryName, quantity = 1f, unit = "TUB", fromPantryId = inserted.id)
        }

        var groceryAdded: GroceryItem? = null
        for (attempt in 1..RETRY_ATTEMPTS) {
            val current = repository.observeGroceryItems().first().firstOrNull { it.name == groceryName }
            if (current != null) {
                groceryAdded = current
                break
            }
            delay(RETRY_DELAY_MILLIS)
        }
        assertNotNull(groceryAdded)
        assertEquals(inserted.id, groceryAdded!!.fromPantryId)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            pantryVm.delete(decremented)
        }

        var softDeleted = false
        for (attempt in 1..RETRY_ATTEMPTS) {
            val current = repository.observePantryItems().first().firstOrNull { it.id == inserted.id }
            if (current == null) {
                softDeleted = true
                break
            }
            delay(RETRY_DELAY_MILLIS)
        }
        assertTrue(softDeleted)

        // Test restoring the soft-deleted item
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            pantryVm.restore(decremented)
        }

        var restored = false
        for (attempt in 1..RETRY_ATTEMPTS) {
            val current = repository.observePantryItems().first().firstOrNull { it.id == inserted.id }
            if (current != null) {
                restored = true
                break
            }
            delay(RETRY_DELAY_MILLIS)
        }
        assertTrue(restored)
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

        // Wait until both items are observed
        for (attempt in 1..RETRY_ATTEMPTS) {
            val items = repository.observeGroceryItems().first().filter { it.name.startsWith(prefix) }
            if (items.size == 2) break
            delay(RETRY_DELAY_MILLIS)
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            groceryVm.clearChecked()
        }

        var cleared = false
        for (attempt in 1..RETRY_ATTEMPTS) {
            val items = repository.observeGroceryItems().first().filter { it.name.startsWith(prefix) }
            if (items.size == 1 && items.first().name == uncheckedName) {
                cleared = true
                break
            }
            delay(RETRY_DELAY_MILLIS)
        }
        assertTrue(cleared)
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

        // Wait until item is in grocery list
        for (attempt in 1..RETRY_ATTEMPTS) {
            val items = repository.observeGroceryItems().first().filter { it.name == restockName }
            if (items.isNotEmpty()) break
            delay(RETRY_DELAY_MILLIS)
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            groceryVm.restockChecked()
        }

        // Verify item is transferred to pantry and removed from grocery
        var restockedToPantry = false
        for (attempt in 1..RETRY_ATTEMPTS) {
            val pantryItems = repository.observePantryItems().first().filter { it.name == restockName }
            val groceryItems = repository.observeGroceryItems().first().filter { it.name == restockName }
            if (pantryItems.isNotEmpty() && groceryItems.isEmpty()) {
                val pantry = pantryItems.first()
                if (pantry.quantity == 2f && pantry.unit == "BOTTLE") {
                    restockedToPantry = true
                    break
                }
            }
            delay(RETRY_DELAY_MILLIS)
        }
        assertTrue("Checked item must be restocked to pantry and removed from grocery", restockedToPantry)
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

        var inserted: PantryItem? = null
        for (attempt in 1..RETRY_ATTEMPTS) {
            val current = repository.observePantryItems().first().firstOrNull { it.name == itemName }
            if (current != null) {
                inserted = current
                break
            }
            delay(RETRY_DELAY_MILLIS)
        }
        assertNotNull(inserted)

        // Increment quantity
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            pantryVm.update(inserted!!.copy(quantity = inserted!!.quantity + 1f))
        }

        var incremented: PantryItem? = null
        for (attempt in 1..RETRY_ATTEMPTS) {
            val current = repository.observePantryItems().first().firstOrNull { it.id == inserted!!.id }
            if (current != null && current.quantity == 2f) {
                incremented = current
                break
            }
            delay(RETRY_DELAY_MILLIS)
        }
        assertNotNull(incremented)
        assertEquals(2f, incremented!!.quantity)

        // Mark as opened
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            pantryVm.update(incremented!!.markOpened())
        }

        var opened: PantryItem? = null
        for (attempt in 1..RETRY_ATTEMPTS) {
            val current = repository.observePantryItems().first().firstOrNull { it.id == inserted!!.id }
            if (current != null && current.isOpened) {
                opened = current
                break
            }
            delay(RETRY_DELAY_MILLIS)
        }
        assertNotNull(opened)
        assertTrue(opened!!.isOpened)
        assertNotNull(opened!!.openedTimestamp)
    }
}
