package com.jeffers.pantrycraft.ui

import android.database.sqlite.SQLiteFullException
import com.jeffers.pantrycraft.data.GroceryItem
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.PantryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PantryViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakePantryRepository
    private lateinit var viewModel: PantryViewModel

    private val sampleItem =
        PantryItem(
            id = 10L,
            name = "Organic Oats",
            quantity = 2f,
            storageLocation = "PANTRY",
            category = "GRAINS",
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakePantryRepository()
        viewModel = PantryViewModel(fakeRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun viewModel_initialStateDefaults() {
        org.junit.Assert.assertNotNull(viewModel.items)
        assertEquals(PantryLocationFilter.ALL, viewModel.selectedLocation.value)
        assertEquals(PantrySortOrder.NAME_ASC, viewModel.selectedSortOrder.value)
        assertEquals("", viewModel.searchQuery.value)
    }

    @Test
    fun viewModel_stateMutatorsUpdateStateFlows() {
        viewModel.selectLocation(PantryLocationFilter.FRIDGE)
        assertEquals(PantryLocationFilter.FRIDGE, viewModel.selectedLocation.value)

        viewModel.selectSortOrder(PantrySortOrder.EXPIRATION_SOONEST)
        assertEquals(PantrySortOrder.EXPIRATION_SOONEST, viewModel.selectedSortOrder.value)

        viewModel.setSearchQuery("Apple")
        assertEquals("Apple", viewModel.searchQuery.value)
    }

    @Test
    fun viewModel_visibleItemsReactsToSearchAndFiltering() =
        runTest {
            val apple = PantryItem(id = 1L, name = "Apple", storageLocation = "COUNTER")
            val avocado = PantryItem(id = 2L, name = "Avocado", storageLocation = "FRIDGE")
            fakeRepo.searchItemsFlow.value = listOf(apple, avocado)

            val visible = mutableListOf<List<PantryItem>>()
            val collectJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.visibleItems.collect { visible.add(it) }
                }

            viewModel.setSearchQuery("A")
            advanceTimeBy(60L)
            advanceUntilIdle()

            viewModel.selectLocation(PantryLocationFilter.FRIDGE)
            advanceUntilIdle()

            val last = visible.last()
            assertEquals(1, last.size)
            assertEquals("Avocado", last.first().name)

            collectJob.cancel()
        }

    @Test
    fun viewModel_add_successAndFailure() =
        runTest {
            val errors = mutableListOf<String>()
            val collectJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.operationErrors.collect { errors.add(it) }
                }

            viewModel.add(sampleItem)
            advanceUntilIdle()
            assertEquals(listOf(sampleItem), fakeRepo.insertedPantry)
            assertTrue(errors.isEmpty())

            fakeRepo.insertException = RuntimeException("DB write error")
            viewModel.add(sampleItem)
            advanceUntilIdle()
            assertEquals(listOf("Unable to add pantry item"), errors)

            collectJob.cancel()
        }

    @Test
    fun viewModel_update_successAndFailure() =
        runTest {
            val errors = mutableListOf<String>()
            val collectJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.operationErrors.collect { errors.add(it) }
                }

            viewModel.update(sampleItem)
            advanceUntilIdle()
            assertEquals(listOf(sampleItem), fakeRepo.updatedPantry)
            assertTrue(errors.isEmpty())

            fakeRepo.updateException = RuntimeException("Update failed")
            viewModel.update(sampleItem)
            advanceUntilIdle()
            assertEquals(listOf("Unable to update pantry item"), errors)

            collectJob.cancel()
        }

    @Test
    fun viewModel_decrement_successAndFailure() =
        runTest {
            val errors = mutableListOf<String>()
            val collectJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.operationErrors.collect { errors.add(it) }
                }

            viewModel.decrement(sampleItem, 1f)
            advanceUntilIdle()
            assertEquals(1, fakeRepo.decrementedItems.size)
            assertEquals(sampleItem to 1f, fakeRepo.decrementedItems.first())

            fakeRepo.decrementException = RuntimeException("Decrement failed")
            viewModel.decrement(sampleItem, 1f)
            advanceUntilIdle()
            assertEquals(listOf("Unable to update pantry quantity"), errors)

            collectJob.cancel()
        }

    @Test
    fun viewModel_deleteAndRestore_successAndFailure() =
        runTest {
            val errors = mutableListOf<String>()
            val collectJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.operationErrors.collect { errors.add(it) }
                }

            viewModel.delete(sampleItem)
            advanceUntilIdle()
            assertEquals(listOf(sampleItem), fakeRepo.deletedPantry)

            viewModel.restore(sampleItem)
            advanceUntilIdle()
            assertEquals(listOf(sampleItem), fakeRepo.restoredPantry)
            assertTrue(errors.isEmpty())

            fakeRepo.deleteException = RuntimeException("Delete failed")
            viewModel.delete(sampleItem)
            advanceUntilIdle()
            assertEquals(listOf("Unable to delete pantry item"), errors)

            fakeRepo.restoreException = RuntimeException("Restore failed")
            viewModel.restore(sampleItem)
            advanceUntilIdle()
            assertEquals(
                listOf("Unable to delete pantry item", "Unable to restore pantry item"),
                errors,
            )

            collectJob.cancel()
        }

    @Test
    fun viewModel_addGroceryItem_emitsSuccessNotificationAndHandlesError() =
        runTest {
            val errors = mutableListOf<String>()
            val collectJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.operationErrors.collect { errors.add(it) }
                }

            viewModel.addGroceryItem("Olive Oil", 2f, "BOTTLE", fromPantryId = 10L)
            advanceUntilIdle()
            assertEquals(1, fakeRepo.insertedGrocery.size)
            assertEquals("Olive Oil", fakeRepo.insertedGrocery.first().name)
            assertEquals(listOf("Added Olive Oil to grocery list"), errors)

            fakeRepo.groceryException = RuntimeException("Failed to add grocery")
            viewModel.addGroceryItem("Salt")
            advanceUntilIdle()
            assertEquals(
                listOf("Added Olive Oil to grocery list", "Unable to add grocery item"),
                errors,
            )

            collectJob.cancel()
        }

    @Test
    fun viewModel_handlesSQLiteFullExceptionAndReclaimsMedia() =
        runTest {
            val errors = mutableListOf<String>()
            val collectJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.operationErrors.collect { errors.add(it) }
                }

            fakeRepo.insertException = SQLiteFullException("Disk full")
            viewModel.add(sampleItem)
            advanceUntilIdle()

            assertEquals(1, fakeRepo.reclaimMediaCallCount)
            assertEquals(
                listOf("Storage is full; temporary media cleared; free space and try again"),
                errors,
            )

            collectJob.cancel()
        }

    @Test
    fun viewModel_rethrowsCancellationExceptionWithoutEmittingError() =
        runTest {
            val errors = mutableListOf<String>()
            val collectJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.operationErrors.collect { errors.add(it) }
                }

            fakeRepo.insertException = CancellationException("Job cancelled")
            val job = viewModel.add(sampleItem)
            advanceUntilIdle()

            assertTrue(job.isCancelled)
            assertTrue(errors.isEmpty())

            collectJob.cancel()
        }

    private class FakePantryRepository : PantryRepository {
        val pantryItemsFlow = MutableStateFlow<List<PantryItem>>(emptyList())
        val searchItemsFlow = MutableStateFlow<List<PantryItem>>(emptyList())
        val groceryItemsFlow = MutableStateFlow<List<GroceryItem>>(emptyList())

        val insertedPantry = mutableListOf<PantryItem>()
        val updatedPantry = mutableListOf<PantryItem>()
        val deletedPantry = mutableListOf<PantryItem>()
        val restoredPantry = mutableListOf<PantryItem>()
        val decrementedItems = mutableListOf<Pair<PantryItem, Float>>()
        val insertedGrocery = mutableListOf<GroceryItem>()

        var reclaimMediaCallCount = 0
        var insertException: Throwable? = null
        var updateException: Throwable? = null
        var decrementException: Throwable? = null
        var deleteException: Throwable? = null
        var restoreException: Throwable? = null
        var groceryException: Throwable? = null

        override fun observePantryItems(): Flow<List<PantryItem>> = pantryItemsFlow

        override fun searchPantryItems(query: String): Flow<List<PantryItem>> = searchItemsFlow

        override suspend fun insertPantry(item: PantryItem): Long {
            insertException?.let { throw it }
            insertedPantry.add(item)
            return item.id
        }

        override suspend fun updatePantry(item: PantryItem) {
            updateException?.let { throw it }
            updatedPantry.add(item)
        }

        override suspend fun decrementQuantity(
            item: PantryItem,
            delta: Float,
        ) {
            decrementException?.let { throw it }
            decrementedItems.add(item to delta)
        }

        override suspend fun deletePantry(item: PantryItem) {
            deleteException?.let { throw it }
            deletedPantry.add(item)
        }

        override suspend fun restorePantry(item: PantryItem) {
            restoreException?.let { throw it }
            restoredPantry.add(item)
        }

        override fun observeGroceryItems(): Flow<List<GroceryItem>> = groceryItemsFlow

        override suspend fun insertGrocery(item: GroceryItem): Long {
            groceryException?.let { throw it }
            insertedGrocery.add(item)
            return item.id
        }

        override suspend fun updateGrocery(item: GroceryItem) {
            // No-op for test stub
        }

        override suspend fun deleteGrocery(item: GroceryItem) {
            // No-op for test stub
        }

        override suspend fun clearCheckedGrocery() {
            // No-op for test stub
        }

        override suspend fun restockCheckedGroceryToPantry(): Int = 0

        override fun reclaimTemporaryMedia(): Int {
            reclaimMediaCallCount++
            return 1
        }
    }

    @Test
    fun filterPantryItems_allLocationsReturnsEveryItem() {
        val items = sampleItems()

        assertEquals(items, filterPantryItems(items, PantryLocationFilter.ALL))
    }

    @Test
    fun filterPantryItems_filtersByStorageLocation() {
        val items = sampleItems()

        assertEquals(
            listOf(items[1]),
            filterPantryItems(items, PantryLocationFilter.FRIDGE),
        )
        assertEquals(
            listOf(items[2]),
            filterPantryItems(items, PantryLocationFilter.FREEZER),
        )
    }

    @Test
    fun filterPantryItems_filtersCounterItems() {
        val items = sampleItems() + PantryItem(id = 4, name = "Bread", storageLocation = "COUNTER")

        assertEquals(
            listOf(items[3]),
            filterPantryItems(items, PantryLocationFilter.COUNTER),
        )
    }

    @Test
    fun filterPantryItems_expiringSoonIncludesExpiredAndUrgentItems() {
        val now = 1_000_000_000L
        val expired = PantryItem(id = 1, name = "Expired Yogurt", expirationTimestamp = now - 50_000L)
        val expiringSoon = PantryItem(id = 2, name = "Fresh Milk", expirationTimestamp = now + 86_400_000L * 2)
        val fresh = PantryItem(id = 3, name = "Dry Rice", expirationTimestamp = now + 86_400_000L * 30)
        val noExpiration = PantryItem(id = 4, name = "Salt", expirationTimestamp = null)

        val filtered =
            filterPantryItems(
                listOf(expired, expiringSoon, fresh, noExpiration),
                PantryLocationFilter.EXPIRING_SOON,
                now,
            )

        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.id == 1L })
        assertTrue(filtered.any { it.id == 2L })
    }

    @Test
    fun sortPantryItems_sortsByExpirationUrgency() {
        val now = 1_000_000_000L
        val itemA = PantryItem(id = 1, name = "Apples", expirationTimestamp = now + 100_000L)
        val itemB = PantryItem(id = 2, name = "Bananas", expirationTimestamp = now + 20_000L)
        val itemC = PantryItem(id = 3, name = "Canned Corn", expirationTimestamp = null)

        val sorted = sortPantryItems(listOf(itemA, itemB, itemC), PantrySortOrder.EXPIRATION_SOONEST)

        assertEquals("Bananas", sorted[0].name)
        assertEquals("Apples", sorted[1].name)
        assertEquals("Canned Corn", sorted[2].name)
    }

    @Test
    fun sortPantryItems_sortsByRecentlyUpdatedAndCategoryAndStorageZone() {
        val itemA =
            PantryItem(
                id = 1,
                name = "Yogurt",
                category = "DAIRY",
                storageLocation = "FRIDGE",
                updatedAt = 100L,
            )
        val itemB =
            PantryItem(
                id = 2,
                name = "Apples",
                category = "PRODUCE",
                storageLocation = "COUNTER",
                updatedAt = 500L,
            )
        val itemC =
            PantryItem(
                id = 3,
                name = "Beans",
                category = "CANNED",
                storageLocation = "PANTRY",
                updatedAt = 200L,
            )

        val byUpdated = sortPantryItems(listOf(itemA, itemB, itemC), PantrySortOrder.RECENTLY_UPDATED)
        assertEquals(listOf(2L, 3L, 1L), byUpdated.map { it.id })

        val byCategory = sortPantryItems(listOf(itemA, itemB, itemC), PantrySortOrder.CATEGORY)
        assertEquals(listOf("CANNED", "DAIRY", "PRODUCE"), byCategory.map { it.category })

        val byZone = sortPantryItems(listOf(itemA, itemB, itemC), PantrySortOrder.STORAGE_LOCATION)
        assertEquals(listOf("COUNTER", "FRIDGE", "PANTRY"), byZone.map { it.storageLocation })
    }

    @Test
    fun sortPantryItems_sortsByNameCaseInsensitively() {
        val itemA = PantryItem(id = 1, name = "banana")
        val itemB = PantryItem(id = 2, name = "Apple")
        val itemC = PantryItem(id = 3, name = "carrot")

        val sorted = sortPantryItems(listOf(itemA, itemB, itemC), PantrySortOrder.NAME_ASC)
        assertEquals(listOf("Apple", "banana", "carrot"), sorted.map { it.name })
    }

    @Test
    fun sortPantryItems_breaksTiesByName() {
        val now = 1_000_000L
        val itemA = PantryItem(id = 1, name = "Zucchini", category = "PRODUCE", expirationTimestamp = now)
        val itemB = PantryItem(id = 2, name = "Artichoke", category = "PRODUCE", expirationTimestamp = now)

        val byExpiration = sortPantryItems(listOf(itemA, itemB), PantrySortOrder.EXPIRATION_SOONEST)
        assertEquals(listOf("Artichoke", "Zucchini"), byExpiration.map { it.name })

        val byCategory = sortPantryItems(listOf(itemA, itemB), PantrySortOrder.CATEGORY)
        assertEquals(listOf("Artichoke", "Zucchini"), byCategory.map { it.name })

        val byStorageZone =
            sortPantryItems(
                listOf(
                    PantryItem(id = 1, name = "Zucchini", storageLocation = "FRIDGE"),
                    PantryItem(id = 2, name = "Artichoke", storageLocation = "FRIDGE"),
                ),
                PantrySortOrder.STORAGE_LOCATION,
            )
        assertEquals(listOf("Artichoke", "Zucchini"), byStorageZone.map { it.name })
    }

    @Test
    fun filterPantryItems_exactCutoffBoundaryCondition() {
        val now = 1_000_000_000L
        val fiveDaysMillis = 5L * 24L * 60L * 60L * 1000L
        val exactlyAtCutoff = PantryItem(id = 1, name = "Milk", expirationTimestamp = now + fiveDaysMillis)
        val pastCutoff = PantryItem(id = 2, name = "Canned Soup", expirationTimestamp = now + fiveDaysMillis + 1L)

        val filtered =
            filterPantryItems(
                listOf(exactlyAtCutoff, pastCutoff),
                PantryLocationFilter.EXPIRING_SOON,
                now = now,
            )

        assertEquals(1, filtered.size)
        assertEquals("Milk", filtered.first().name)

        val defaultNowFiltered =
            filterPantryItems(
                listOf(PantryItem(id = 10, name = "Immediate", expirationTimestamp = System.currentTimeMillis() + 1000L)),
                PantryLocationFilter.EXPIRING_SOON,
            )
        assertEquals(1, defaultNowFiltered.size)
    }

    @Test
    fun filterPantryItems_caseInsensitiveLocationMatch() {
        val item = PantryItem(id = 1, name = "Butter", storageLocation = "fridge")
        val filtered = filterPantryItems(listOf(item), PantryLocationFilter.FRIDGE)

        assertEquals(1, filtered.size)
        assertEquals("Butter", filtered.first().name)
    }

    @Test
    fun pantryEnumsHaveExpectedLabelsAndLocations() {
        assertEquals("All", PantryLocationFilter.ALL.label)
        assertEquals("Pantry", PantryLocationFilter.PANTRY.label)
        assertEquals("Fridge", PantryLocationFilter.FRIDGE.label)
        assertEquals("Freezer", PantryLocationFilter.FREEZER.label)
        assertEquals("Counter", PantryLocationFilter.COUNTER.label)
        assertEquals("Expiring soon", PantryLocationFilter.EXPIRING_SOON.label)

        assertEquals(null, PantryLocationFilter.ALL.storageLocation)
        assertEquals("PANTRY", PantryLocationFilter.PANTRY.storageLocation)
        assertEquals("FRIDGE", PantryLocationFilter.FRIDGE.storageLocation)
        assertEquals("FREEZER", PantryLocationFilter.FREEZER.storageLocation)
        assertEquals("COUNTER", PantryLocationFilter.COUNTER.storageLocation)
        assertEquals(null, PantryLocationFilter.EXPIRING_SOON.storageLocation)

        assertEquals("Name (A-Z)", PantrySortOrder.NAME_ASC.label)
        assertEquals("Expiring soonest", PantrySortOrder.EXPIRATION_SOONEST.label)
        assertEquals("Recently updated", PantrySortOrder.RECENTLY_UPDATED.label)
        assertEquals("Category", PantrySortOrder.CATEGORY.label)
        assertEquals("Storage zone", PantrySortOrder.STORAGE_LOCATION.label)
    }

    private fun sampleItems() =
        listOf(
            PantryItem(id = 1, name = "Rice", storageLocation = "PANTRY"),
            PantryItem(id = 2, name = "Milk", storageLocation = "FRIDGE"),
            PantryItem(id = 3, name = "Peas", storageLocation = "FREEZER"),
        )
}
