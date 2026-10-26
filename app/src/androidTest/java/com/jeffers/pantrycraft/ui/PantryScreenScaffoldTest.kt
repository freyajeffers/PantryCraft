package com.jeffers.pantrycraft.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.pantrycraft.data.AppDatabase
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PantryScreenScaffoldTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repository: Repository
    private lateinit var viewModel: PantryViewModel
    private val prefix = "scaffold-test-${System.nanoTime()}-"

    @Before
    fun setUp() {
        repository = Repository.getInstance(context)
        viewModel = PantryViewModel(repository)
        cleanupTestData()
    }

    @After
    fun tearDown() {
        cleanupTestData()
    }

    private fun cleanupTestData() = runBlocking {
        val db = AppDatabase.getDatabase(context)
        db.pantryDao().deleteByNamePrefix(prefix)
        db.groceryDao().deleteByNamePrefix(prefix)
    }

    @Test
    fun buildPantryScaffoldPropsPassesPropertiesAndTriggersEditorCallbacks() {
        val sampleItem = PantryItem(
            id = 42L,
            name = "${prefix}Granola",
            quantity = 3f,
            unit = "BOX",
        )
        val itemsList = listOf(sampleItem)
        var openedItem: PantryItem? = sampleItem
        var editorCallCount = 0
        var usedItem: PantryItem? = null

        val state = PantryScreenScaffoldState(
            viewModel = viewModel,
            items = itemsList,
            selectedLocation = PantryLocationFilter.FRIDGE,
            selectedSortOrder = PantrySortOrder.CATEGORY,
            searchQuery = "Granola",
            onOpenEditor = { item ->
                editorCallCount++
                openedItem = item
            },
            onUseItem = { item ->
                usedItem = item
            },
            snackbarHostState = SnackbarHostState(),
            scope = CoroutineScope(Dispatchers.Main.immediate),
        )

        val props = buildPantryScaffoldProps(state)

        assertEquals(itemsList, props.items)
        assertEquals(PantryLocationFilter.FRIDGE, props.selectedLocation)
        assertEquals(PantrySortOrder.CATEGORY, props.selectedSortOrder)
        assertEquals("Granola", props.searchQuery)

        // onAddClick passes null to onOpenEditor
        props.onAddClick()
        assertEquals(1, editorCallCount)
        assertNull(openedItem)

        // onEdit passes the target item to onOpenEditor
        props.onEdit(sampleItem)
        assertEquals(2, editorCallCount)
        assertEquals(sampleItem, openedItem)

        // onUse passes the item to onUseItem
        props.onUse(sampleItem)
        assertEquals(sampleItem, usedItem)
    }

    @Test
    fun scaffoldPropsFilterAndSortMutationsPropagateToViewModel() {
        val state = PantryScreenScaffoldState(
            viewModel = viewModel,
            items = emptyList(),
            selectedLocation = PantryLocationFilter.ALL,
            selectedSortOrder = PantrySortOrder.NAME_ASC,
            searchQuery = "",
            onOpenEditor = {},
            onUseItem = {},
            snackbarHostState = SnackbarHostState(),
            scope = CoroutineScope(Dispatchers.Main.immediate),
        )

        val props = buildPantryScaffoldProps(state)

        props.onSearchQueryChange("Olive Oil")
        assertEquals("Olive Oil", viewModel.searchQuery.value)

        props.onLocationSelected(PantryLocationFilter.PANTRY)
        assertEquals(PantryLocationFilter.PANTRY, viewModel.selectedLocation.value)

        props.onSortSelected(PantrySortOrder.RECENTLY_UPDATED)
        assertEquals(PantrySortOrder.RECENTLY_UPDATED, viewModel.selectedSortOrder.value)
    }

    @Test
    fun scaffoldPropsAddToGroceryInsertsGroceryItem() = runBlocking {
        val pantryItem = PantryItem(
            name = "${prefix}Almonds",
            quantity = 1f,
            unit = "BAG",
        )
        val pantryId = repository.insertPantry(pantryItem)
        val persistedItem = pantryItem.copy(id = pantryId)

        val state = PantryScreenScaffoldState(
            viewModel = viewModel,
            items = listOf(persistedItem),
            selectedLocation = PantryLocationFilter.ALL,
            selectedSortOrder = PantrySortOrder.NAME_ASC,
            searchQuery = "",
            onOpenEditor = {},
            onUseItem = {},
            snackbarHostState = SnackbarHostState(),
            scope = CoroutineScope(Dispatchers.Main.immediate),
        )

        val props = buildPantryScaffoldProps(state)
        props.onAddToGrocery(persistedItem)

        // Allow coroutine execution
        var attempts = 0
        var foundGrocery = false
        while (attempts < 20 && !foundGrocery) {
            val groceries = repository.observeGroceryItems().first().filter { it.name.startsWith(prefix) }
            if (groceries.isNotEmpty()) {
                foundGrocery = true
                val grocery = groceries.first()
                assertEquals("${prefix}Almonds", grocery.name)
                assertEquals(1f, grocery.quantity)
                assertEquals("BAG", grocery.unit)
                assertEquals(pantryId, grocery.fromPantryId)
            } else {
                kotlinx.coroutines.delay(50L)
                attempts++
            }
        }
        assertTrue("Grocery item must be inserted from pantry item via scaffold prop", foundGrocery)
    }

    @Test
    fun pantryScreenScaffoldRendersContent() {
        val state = PantryScreenScaffoldState(
            viewModel = viewModel,
            items = emptyList(),
            selectedLocation = PantryLocationFilter.ALL,
            selectedSortOrder = PantrySortOrder.NAME_ASC,
            searchQuery = "",
            onOpenEditor = {},
            onUseItem = {},
            snackbarHostState = SnackbarHostState(),
            scope = CoroutineScope(Dispatchers.Main.immediate),
        )
        composeTestRule.setContent {
            PantryCraftTheme {
                PantryScreenScaffold(state)
            }
        }
        composeTestRule.waitForIdle()
    }
}
