package com.jeffers.pantrycraft.feature.meals.ui

import android.content.Context
import android.content.ContextWrapper
import com.jeffers.pantrycraft.data.GroceryItem
import com.jeffers.pantrycraft.data.GroceryRepository
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.PantryRepository
import com.jeffers.pantrycraft.feature.meals.engine.MealSynthesisEngine
import com.jeffers.pantrycraft.feature.meals.engine.TextGenerationModel
import com.jeffers.pantrycraft.feature.meals.model.MealMatchMode
import com.jeffers.pantrycraft.feature.meals.model.MealRecipe
import com.jeffers.pantrycraft.feature.meals.model.RecipePantryUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
class MealSynthesisViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var fakePantry: TestPantryRepo
    private lateinit var fakeGrocery: TestGroceryRepo
    private lateinit var engine: MealSynthesisEngine
    private lateinit var viewModel: MealSynthesisViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakePantry = TestPantryRepo(
            listOf(PantryItem(id = 1L, name = "Eggs", quantity = 4f, unit = "count")),
        )
        fakeGrocery = TestGroceryRepo()
        val dummyModel = object : TextGenerationModel {
            override suspend fun generate(systemPrompt: String, userPrompt: String): String = "{}"
        }
        engine = MealSynthesisEngine(ContextWrapper(null), dummyModel)
        viewModel = MealSynthesisViewModel(fakePantry, fakeGrocery, engine)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialViewModelStateHasSensibleDefaults() {
        assertEquals(MealMatchMode.STRICT, viewModel.selectedMode.value)
        assertEquals(2, viewModel.portionCount.value)
        assertFalse(viewModel.isLoading.value)
        assertTrue(viewModel.recipes.value.isEmpty())
    }

    @Test
    fun selectModeUpdatesSelectedMode() {
        viewModel.selectMode(MealMatchMode.SHOPPING)
        assertEquals(MealMatchMode.SHOPPING, viewModel.selectedMode.value)
    }

    @Test
    fun updatePortionsClampsAndUpdatesPortionCount() {
        viewModel.updatePortions(6)
        assertEquals(6, viewModel.portionCount.value)
        viewModel.updatePortions(20)
        assertEquals(12, viewModel.portionCount.value)
        viewModel.updatePortions(0)
        assertEquals(1, viewModel.portionCount.value)
    }

    @Test
    fun cookMealDeductsAndEmitsStatus() = testScope.runTest {
        val recipe = MealRecipe(
            recipeId = "rec_test",
            title = "Scrambled Eggs",
            itemsUsedFromStock = listOf(
                RecipePantryUsage(name = "Eggs", pantryItemId = 1L, quantityUsed = 2f),
            ),
        )

        var statusMessage: String? = null
        val job = launch {
            statusMessage = viewModel.statusMessage.first()
        }

        viewModel.cookMeal(recipe)
        advanceUntilIdle()

        assertTrue(statusMessage?.contains("Cooked 'Scrambled Eggs'") == true)
        job.cancel()
    }

    private class TestPantryRepo(initial: List<PantryItem>) : PantryRepository {
        val flow = MutableStateFlow(initial)
        override fun observePantryItems(): Flow<List<PantryItem>> = flow
        override fun searchPantryItems(query: String): Flow<List<PantryItem>> = flow
        override suspend fun insertPantry(item: PantryItem): Long = 1L
        override suspend fun updatePantry(item: PantryItem) { /* no-op for test */ }
        override suspend fun decrementQuantity(item: PantryItem, delta: Float) { /* no-op for test */ }
        override suspend fun deletePantry(item: PantryItem) { /* no-op for test */ }
        override suspend fun restorePantry(item: PantryItem) { /* no-op for test */ }
        override fun observeGroceryItems(): Flow<List<GroceryItem>> = MutableStateFlow(emptyList())
        override suspend fun insertGrocery(item: GroceryItem): Long = 1L
        override suspend fun updateGrocery(item: GroceryItem) { /* no-op for test */ }
        override suspend fun deleteGrocery(item: GroceryItem) { /* no-op for test */ }
        override suspend fun clearCheckedGrocery() { /* no-op for test */ }
        override suspend fun restockCheckedGroceryToPantry(): Int = 0
        override fun reclaimTemporaryMedia(): Int = 0
    }

    private class TestGroceryRepo : GroceryRepository {
        override fun observeGroceryItems(): Flow<List<GroceryItem>> = MutableStateFlow(emptyList())
        override suspend fun insertGrocery(item: GroceryItem): Long = 1L
        override suspend fun updateGrocery(item: GroceryItem) { /* no-op for test */ }
        override suspend fun deleteGrocery(item: GroceryItem) { /* no-op for test */ }
        override suspend fun clearCheckedGrocery() { /* no-op for test */ }
        override suspend fun restockCheckedGroceryToPantry(): Int = 0
        override fun reclaimTemporaryMedia(): Int = 0
    }
}
