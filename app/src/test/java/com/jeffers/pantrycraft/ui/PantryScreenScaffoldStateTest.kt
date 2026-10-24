package com.jeffers.pantrycraft.ui

import androidx.compose.material3.SnackbarHostState
import com.jeffers.pantrycraft.data.GroceryItem
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.PantryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PantryScreenScaffoldStateTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var fakeRepo: TestPantryRepo
    private lateinit var viewModel: PantryViewModel

    private val sampleItem = PantryItem(
        id = 101L,
        name = "Sourdough Bread",
        quantity = 2f,
        storageLocation = "PANTRY",
        category = "BAKERY",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = TestPantryRepo()
        viewModel = PantryViewModel(fakeRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun buildPantryScaffoldPropsMapsPropertiesDirectly() {
        val hostState = SnackbarHostState()
        val itemsList = listOf(sampleItem)
        val scaffoldState = PantryScreenScaffoldState(
            viewModel = viewModel,
            items = itemsList,
            selectedLocation = PantryLocationFilter.PANTRY,
            selectedSortOrder = PantrySortOrder.NAME_ASC,
            searchQuery = "bread",
            onOpenEditor = {},
            onUseItem = {},
            snackbarHostState = hostState,
            scope = testScope,
        )

        val props = buildPantryScaffoldProps(scaffoldState)

        assertEquals(PantrySortOrder.NAME_ASC, props.selectedSortOrder)
        assertEquals(PantryLocationFilter.PANTRY, props.selectedLocation)
        assertEquals("bread", props.searchQuery)
        assertEquals(itemsList, props.items)
        assertEquals(hostState, props.snackbarHostState)
        assertEquals(testScope, props.scope)
    }

    @Test
    fun buildPantryScaffoldPropsDelegatesSortSearchAndLocation() {
        val scaffoldState = PantryScreenScaffoldState(
            viewModel = viewModel,
            items = emptyList(),
            selectedLocation = PantryLocationFilter.ALL,
            selectedSortOrder = PantrySortOrder.NAME_ASC,
            searchQuery = "",
            onOpenEditor = {},
            onUseItem = {},
            snackbarHostState = SnackbarHostState(),
            scope = testScope,
        )

        val props = buildPantryScaffoldProps(scaffoldState)

        props.onSortSelected(PantrySortOrder.EXPIRATION_SOONEST)
        assertEquals(PantrySortOrder.EXPIRATION_SOONEST, viewModel.selectedSortOrder.value)

        props.onSearchQueryChange("rye flour")
        assertEquals("rye flour", viewModel.searchQuery.value)

        props.onLocationSelected(PantryLocationFilter.FRIDGE)
        assertEquals(PantryLocationFilter.FRIDGE, viewModel.selectedLocation.value)
    }

    @Test
    fun buildPantryScaffoldPropsDelegatesEditorAndUseActions() {
        var openedEditorItem: PantryItem? = sampleItem
        var editorCallCount = 0
        var usedItem: PantryItem? = null

        val scaffoldState = PantryScreenScaffoldState(
            viewModel = viewModel,
            items = emptyList(),
            selectedLocation = PantryLocationFilter.ALL,
            selectedSortOrder = PantrySortOrder.NAME_ASC,
            searchQuery = "",
            onOpenEditor = {
                openedEditorItem = it
                editorCallCount++
            },
            onUseItem = { usedItem = it },
            snackbarHostState = SnackbarHostState(),
            scope = testScope,
        )

        val props = buildPantryScaffoldProps(scaffoldState)

        props.onAddClick()
        assertNull(openedEditorItem)
        assertEquals(1, editorCallCount)

        props.onEdit(sampleItem)
        assertEquals(sampleItem, openedEditorItem)
        assertEquals(2, editorCallCount)

        props.onUse(sampleItem)
        assertEquals(sampleItem, usedItem)
    }

    @Test
    fun buildPantryScaffoldPropsDelegatesMutations() = runTest(testDispatcher) {
        val scaffoldState = PantryScreenScaffoldState(
            viewModel = viewModel,
            items = emptyList(),
            selectedLocation = PantryLocationFilter.ALL,
            selectedSortOrder = PantrySortOrder.NAME_ASC,
            searchQuery = "",
            onOpenEditor = {},
            onUseItem = {},
            snackbarHostState = SnackbarHostState(),
            scope = testScope,
        )

        val props = buildPantryScaffoldProps(scaffoldState)

        props.onDecrement(sampleItem)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1f, fakeRepo.decrementedDelta)

        props.onIncrement(sampleItem)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(3f, fakeRepo.updatedItem?.quantity)

        props.onMarkOpened(sampleItem)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(fakeRepo.updatedItem?.isOpened == true)

        props.onAddToGrocery(sampleItem)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("Sourdough Bread", fakeRepo.insertedGrocery?.name)
        assertEquals(101L, fakeRepo.insertedGrocery?.fromPantryId)

        props.onDelete(sampleItem)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(sampleItem.id, fakeRepo.deletedItem?.id)

        props.onRestore(sampleItem)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(sampleItem.id, fakeRepo.restoredItem?.id)
    }

    private class TestPantryRepo : PantryRepository {
        val itemsFlow = MutableStateFlow<List<PantryItem>>(emptyList())
        val groceryFlow = MutableStateFlow<List<GroceryItem>>(emptyList())
        var updatedItem: PantryItem? = null
        var decrementedDelta: Float? = null
        var deletedItem: PantryItem? = null
        var restoredItem: PantryItem? = null
        var insertedGrocery: GroceryItem? = null

        override fun observePantryItems(): Flow<List<PantryItem>> = itemsFlow
        override fun searchPantryItems(query: String): Flow<List<PantryItem>> = itemsFlow
        override suspend fun insertPantry(item: PantryItem): Long = 1L
        override suspend fun updatePantry(item: PantryItem) { updatedItem = item }
        override suspend fun decrementQuantity(item: PantryItem, delta: Float) { decrementedDelta = delta }
        override suspend fun deletePantry(item: PantryItem) { deletedItem = item }
        override suspend fun restorePantry(item: PantryItem) { restoredItem = item }
        override fun observeGroceryItems(): Flow<List<GroceryItem>> = groceryFlow
        override suspend fun insertGrocery(item: GroceryItem): Long {
            insertedGrocery = item
            return 1L
        }
        override suspend fun updateGrocery(item: GroceryItem) {
            // No-op for scaffold state test
        }
        override suspend fun deleteGrocery(item: GroceryItem) {
            // No-op for scaffold state test
        }
        override suspend fun clearCheckedGrocery() {
            // No-op for scaffold state test
        }
        override suspend fun restockCheckedGroceryToPantry(): Int = 0
        override fun reclaimTemporaryMedia(): Int = 0
    }
}
