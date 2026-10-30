package com.jeffers.pantrycraft.ui

import android.database.sqlite.SQLiteFullException
import com.jeffers.pantrycraft.data.GroceryItem
import com.jeffers.pantrycraft.data.GroceryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GroceryViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakeGroceryRepository
    private lateinit var viewModel: GroceryViewModel

    private val sampleItem =
        GroceryItem(
            id = 1L,
            name = "Oat Milk",
            quantity = 2f,
            unit = "CARTON",
            checked = false,
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeGroceryRepository()
        viewModel = GroceryViewModel(fakeRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun items_observesRepositoryGroceryFlow() =
        runTest {
            val observed = mutableListOf<List<GroceryItem>>()
            val collectJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.items.collect { observed.add(it) }
                }

            fakeRepo.itemsFlow.value = listOf(sampleItem)
            advanceUntilIdle()

            assertEquals(listOf(sampleItem), observed.last())
            collectJob.cancel()
        }

    @Test
    fun add_successfulInsertDoesNotEmitErrors() =
        runTest {
            val emittedErrors = mutableListOf<String>()
            val collectJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.operationErrors.collect { emittedErrors.add(it) }
                }

            viewModel.add(sampleItem)
            advanceUntilIdle()

            assertEquals(listOf(sampleItem), fakeRepo.insertedItems)
            assertTrue(emittedErrors.isEmpty())
            collectJob.cancel()
        }

    @Test
    fun update_successfulUpdateCallsRepository() =
        runTest {
            viewModel.update(sampleItem)
            advanceUntilIdle()

            assertEquals(listOf(sampleItem), fakeRepo.updatedItems)
        }

    @Test
    fun delete_successfulDeleteCallsRepository() =
        runTest {
            viewModel.delete(sampleItem)
            advanceUntilIdle()

            assertEquals(listOf(sampleItem), fakeRepo.deletedItems)
        }

    @Test
    fun clearChecked_successfulClearCallsRepository() =
        runTest {
            viewModel.clearChecked()
            advanceUntilIdle()

            assertTrue(fakeRepo.clearCheckedCalled)
        }

    @Test
    fun add_emitsExpectedErrorMessageOnFailure() =
        runTest {
            val emittedErrors = mutableListOf<String>()
            val collectJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.operationErrors.collect { emittedErrors.add(it) }
                }

            fakeRepo.insertException = RuntimeException("Database write failure")
            viewModel.add(sampleItem)
            advanceUntilIdle()

            assertEquals(listOf("Unable to add grocery item"), emittedErrors)
            assertEquals(0, fakeRepo.reclaimTemporaryMediaCalled)
            collectJob.cancel()
        }

    @Test
    fun update_emitsExpectedErrorMessageOnFailure() =
        runTest {
            val emittedErrors = mutableListOf<String>()
            val collectJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.operationErrors.collect { emittedErrors.add(it) }
                }

            fakeRepo.updateException = IllegalStateException("Row not found")
            viewModel.update(sampleItem)
            advanceUntilIdle()

            assertEquals(listOf("Unable to update grocery item"), emittedErrors)
            collectJob.cancel()
        }

    @Test
    fun delete_emitsExpectedErrorMessageOnFailure() =
        runTest {
            val emittedErrors = mutableListOf<String>()
            val collectJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.operationErrors.collect { emittedErrors.add(it) }
                }

            fakeRepo.deleteException = RuntimeException("Foreign key error")
            viewModel.delete(sampleItem)
            advanceUntilIdle()

            assertEquals(listOf("Unable to delete grocery item"), emittedErrors)
            collectJob.cancel()
        }

    @Test
    fun clearChecked_emitsExpectedErrorMessageOnFailure() =
        runTest {
            val emittedErrors = mutableListOf<String>()
            val collectJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.operationErrors.collect { emittedErrors.add(it) }
                }

            fakeRepo.clearCheckedException = RuntimeException("Transaction rolled back")
            viewModel.clearChecked()
            advanceUntilIdle()

            assertEquals(listOf("Unable to clear checked grocery items"), emittedErrors)
            collectJob.cancel()
        }

    @Test
    fun restockChecked_emitsExpectedErrorMessageOnFailure() =
        runTest {
            val emittedErrors = mutableListOf<String>()
            val collectJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.operationErrors.collect { emittedErrors.add(it) }
                }

            fakeRepo.restockException = RuntimeException("Pantry sync failure")
            viewModel.restockChecked()
            advanceUntilIdle()

            assertEquals(listOf("Unable to restock checked grocery items"), emittedErrors)
            collectJob.cancel()
        }

    @Test
    fun restockChecked_emitsSuccessCountMessageOnSuccess() =
        runTest {
            val emittedErrors = mutableListOf<String>()
            val collectJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.operationErrors.collect { emittedErrors.add(it) }
                }

            fakeRepo.restockCheckedResult = 4
            viewModel.restockChecked()
            advanceUntilIdle()

            assertEquals(listOf("Restocked 4 items to pantry"), emittedErrors)
            collectJob.cancel()
        }

    @Test
    fun add_handlesSQLiteFullExceptionAndReclaimsMedia() =
        runTest {
            val emittedErrors = mutableListOf<String>()
            val collectJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.operationErrors.collect { emittedErrors.add(it) }
                }

            fakeRepo.insertException = SQLiteFullException("Disk space exhausted")
            viewModel.add(sampleItem)
            advanceUntilIdle()

            assertEquals(1, fakeRepo.reclaimTemporaryMediaCalled)
            assertEquals(
                listOf("Storage is full; temporary media cleared; free space and try again"),
                emittedErrors,
            )
            collectJob.cancel()
        }

    @Test
    fun update_handlesSQLiteFullExceptionAndReclaimsMedia() =
        runTest {
            val emittedErrors = mutableListOf<String>()
            val collectJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.operationErrors.collect { emittedErrors.add(it) }
                }

            fakeRepo.updateException = SQLiteFullException("Disk space exhausted")
            viewModel.update(sampleItem)
            advanceUntilIdle()

            assertEquals(1, fakeRepo.reclaimTemporaryMediaCalled)
            assertEquals(
                listOf("Storage is full; temporary media cleared; free space and try again"),
                emittedErrors,
            )
            collectJob.cancel()
        }

    @Test
    fun clearChecked_handlesSQLiteFullExceptionAndReclaimsMedia() =
        runTest {
            val emittedErrors = mutableListOf<String>()
            val collectJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.operationErrors.collect { emittedErrors.add(it) }
                }

            fakeRepo.clearCheckedException = SQLiteFullException("Disk space exhausted")
            viewModel.clearChecked()
            advanceUntilIdle()

            assertEquals(1, fakeRepo.reclaimTemporaryMediaCalled)
            assertEquals(
                listOf("Storage is full; temporary media cleared; free space and try again"),
                emittedErrors,
            )
            collectJob.cancel()
        }

    @Test
    fun add_rethrowsCancellationExceptionWithoutEmittingError() =
        runTest {
            val emittedErrors = mutableListOf<String>()
            val collectJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.operationErrors.collect { emittedErrors.add(it) }
                }

            fakeRepo.insertException = CancellationException("Operation cancelled")
            val job = viewModel.add(sampleItem)
            advanceUntilIdle()

            assertTrue(job.isCancelled)
            assertTrue(emittedErrors.isEmpty())
            collectJob.cancel()
        }

    @Test
    fun update_rethrowsCancellationExceptionWithoutEmittingError() =
        runTest {
            val emittedErrors = mutableListOf<String>()
            val collectJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.operationErrors.collect { emittedErrors.add(it) }
                }

            fakeRepo.updateException = CancellationException("Operation cancelled")
            val job = viewModel.update(sampleItem)
            advanceUntilIdle()

            assertTrue(job.isCancelled)
            assertTrue(emittedErrors.isEmpty())
            collectJob.cancel()
        }

    private class FakeGroceryRepository : GroceryRepository {
        val itemsFlow = MutableStateFlow<List<GroceryItem>>(emptyList())
        val insertedItems = mutableListOf<GroceryItem>()
        val updatedItems = mutableListOf<GroceryItem>()
        val deletedItems = mutableListOf<GroceryItem>()
        var clearCheckedCalled = false
        var restockCheckedResult = 0
        var reclaimTemporaryMediaCalled = 0

        var insertException: Throwable? = null
        var updateException: Throwable? = null
        var deleteException: Throwable? = null
        var clearCheckedException: Throwable? = null
        var restockException: Throwable? = null

        override fun observeGroceryItems(): Flow<List<GroceryItem>> = itemsFlow

        override suspend fun insertGrocery(item: GroceryItem): Long {
            insertException?.let { throw it }
            insertedItems.add(item)
            return item.id
        }

        override suspend fun updateGrocery(item: GroceryItem) {
            updateException?.let { throw it }
            updatedItems.add(item)
        }

        override suspend fun deleteGrocery(item: GroceryItem) {
            deleteException?.let { throw it }
            deletedItems.add(item)
        }

        override suspend fun clearCheckedGrocery() {
            clearCheckedException?.let { throw it }
            clearCheckedCalled = true
        }

        override suspend fun restockCheckedGroceryToPantry(): Int {
            restockException?.let { throw it }
            return restockCheckedResult
        }

        override fun reclaimTemporaryMedia(): Int {
            reclaimTemporaryMediaCalled++
            return 1
        }
    }
}
