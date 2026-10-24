package com.jeffers.pantrycraft.ui

import com.jeffers.pantrycraft.data.GroceryItem
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.PantryRepository
import com.jeffers.pantrycraft.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelSecondaryConstructorTest {
    private val testDispatcher = StandardTestDispatcher()
    private val fakeRepo = MinimalPantryRepo()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun secondaryConstructorsInstantiateCleanlyWithRepository() {
        val repo = createStubRepository()

        val pantryVm = PantryViewModel(repo)
        assertNotNull(pantryVm)

        val groceryVm = GroceryViewModel(repo)
        assertNotNull(groceryVm)
    }

    @Test
    fun viewModelFactoriesInstantiateViewModelsAndRejectUnknownClass() {
        val repo = createStubRepository()

        val pantryFactory = PantryViewModelFactory(repo)
        val pantryVm = pantryFactory.create(PantryViewModel::class.java)
        assertNotNull(pantryVm)

        val groceryFactory = GroceryViewModelFactory(repo)
        val groceryVm = groceryFactory.create(GroceryViewModel::class.java)
        assertNotNull(groceryVm)

        val unkPantry = org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            pantryFactory.create(UnregisteredViewModel::class.java)
        }
        assertEquals("Unknown ViewModel class", unkPantry.message)

        val unkGrocery = org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            groceryFactory.create(UnregisteredViewModel::class.java)
        }
        assertEquals("Unknown ViewModel class", unkGrocery.message)
    }

    private class UnregisteredViewModel : androidx.lifecycle.ViewModel()

    private fun createStubRepository(): Repository {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe").apply {
            isAccessible = true
        }
        val unsafe = theUnsafeField.get(null)
        val allocateMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
        val repo = allocateMethod.invoke(unsafe, Repository::class.java) as Repository

        for (field in Repository::class.java.declaredFields) {
            if (!java.lang.reflect.Modifier.isStatic(field.modifiers) &&
                PantryRepository::class.java.isAssignableFrom(field.type)
            ) {
                field.isAccessible = true
                field.set(repo, fakeRepo)
            }
        }
        return repo
    }

    @Test
    fun filterPantryItemsWithPantryStorageLocation() {
        val pantryItem = PantryItem(name = "Flour", quantity = 2f, storageLocation = "PANTRY")
        val fridgeItem = PantryItem(name = "Milk", quantity = 1f, storageLocation = "FRIDGE")
        val items = listOf(pantryItem, fridgeItem)

        val filtered = filterPantryItems(items, PantryLocationFilter.PANTRY)
        assertEquals(listOf(pantryItem), filtered)
    }

    private class MinimalPantryRepo : PantryRepository {
        private val pantryFlow = MutableStateFlow<List<PantryItem>>(emptyList())
        private val groceryFlow = MutableStateFlow<List<GroceryItem>>(emptyList())

        override fun observePantryItems(): Flow<List<PantryItem>> = pantryFlow
        override fun searchPantryItems(query: String): Flow<List<PantryItem>> = pantryFlow
        override suspend fun insertPantry(item: PantryItem): Long = 1L
        override suspend fun updatePantry(item: PantryItem) {
            // No-op for constructor test
        }
        override suspend fun decrementQuantity(item: PantryItem, delta: Float) {
            // No-op for constructor test
        }
        override suspend fun deletePantry(item: PantryItem) {
            // No-op for constructor test
        }
        override suspend fun restorePantry(item: PantryItem) {
            // No-op for constructor test
        }
        override fun observeGroceryItems(): Flow<List<GroceryItem>> = groceryFlow
        override suspend fun insertGrocery(item: GroceryItem): Long = 1L
        override suspend fun updateGrocery(item: GroceryItem) {
            // No-op for constructor test
        }
        override suspend fun deleteGrocery(item: GroceryItem) {
            // No-op for constructor test
        }
        override suspend fun clearCheckedGrocery() {
            // No-op for constructor test
        }
        override suspend fun restockCheckedGroceryToPantry(): Int = 0
        override fun reclaimTemporaryMedia(): Int = 0
    }
}
