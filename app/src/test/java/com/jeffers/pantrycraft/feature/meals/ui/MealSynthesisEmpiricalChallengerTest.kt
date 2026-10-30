package com.jeffers.pantrycraft.feature.meals.ui

import android.content.ContextWrapper
import com.jeffers.pantrycraft.data.CulinaryScaling
import com.jeffers.pantrycraft.data.GroceryItem
import com.jeffers.pantrycraft.data.GroceryRepository
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.PantryRepository
import com.jeffers.pantrycraft.feature.meals.engine.MealSynthesisEngine
import com.jeffers.pantrycraft.feature.meals.engine.TextGenerationModel
import com.jeffers.pantrycraft.feature.meals.model.MAX_PORTIONS
import com.jeffers.pantrycraft.feature.meals.model.MIN_PORTIONS
import com.jeffers.pantrycraft.feature.meals.model.MealMatchMode
import com.jeffers.pantrycraft.feature.meals.model.MealRecipe
import com.jeffers.pantrycraft.feature.meals.model.RecipeMissingItem
import com.jeffers.pantrycraft.feature.meals.model.RecipePantryUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
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
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

/**
 * Empirical challenger test suite stress-testing Milestone 3 UI logic and culinary scaling.
 * Adversarially challenges:
 * 1. Extreme portion bounds (<1, >12, rapid clicks, cumulative round-trip drift).
 * 2. Rapid mode switching under concurrent state updates.
 * 3. Seasoning scaling accuracy (sub-linear exponent 0.75 vs linear 1.0).
 * 4. Concurrent meal synthesis requests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MealSynthesisEmpiricalChallengerTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var pantryRepo: ChallengerPantryRepo
    private lateinit var groceryRepo: ChallengerGroceryRepo
    private lateinit var engine: MealSynthesisEngine
    private lateinit var viewModel: MealSynthesisViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        pantryRepo = ChallengerPantryRepo()
        groceryRepo = ChallengerGroceryRepo()
        val dummyModel =
            object : TextGenerationModel {
                override suspend fun generate(
                    systemPrompt: String,
                    userPrompt: String,
                ): String = "{}"
            }
        engine = MealSynthesisEngine(ContextWrapper(null), dummyModel, dispatcher = testDispatcher)
        viewModel = MealSynthesisViewModel(pantryRepo, groceryRepo, engine)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // =========================================================================
    // 1. Extreme Portion Selector Bounds
    // =========================================================================

    @Test
    fun challengePortionSelectorBoundsClamping() {
        // Test lower bounds below MIN_PORTIONS (1)
        viewModel.updatePortions(0)
        assertEquals(MIN_PORTIONS, viewModel.portionCount.value)

        viewModel.updatePortions(-1)
        assertEquals(MIN_PORTIONS, viewModel.portionCount.value)

        viewModel.updatePortions(-9999)
        assertEquals(MIN_PORTIONS, viewModel.portionCount.value)

        viewModel.updatePortions(Int.MIN_VALUE)
        assertEquals(MIN_PORTIONS, viewModel.portionCount.value)

        // Test upper bounds above MAX_PORTIONS (12)
        viewModel.updatePortions(13)
        assertEquals(MAX_PORTIONS, viewModel.portionCount.value)

        viewModel.updatePortions(50)
        assertEquals(MAX_PORTIONS, viewModel.portionCount.value)

        viewModel.updatePortions(1_000_000)
        assertEquals(MAX_PORTIONS, viewModel.portionCount.value)

        viewModel.updatePortions(Int.MAX_VALUE)
        assertEquals(MAX_PORTIONS, viewModel.portionCount.value)

        // Verify valid internal bounds
        for (p in MIN_PORTIONS..MAX_PORTIONS) {
            viewModel.updatePortions(p)
            assertEquals(p, viewModel.portionCount.value)
        }
    }

    @Test
    fun challengeRapidPortionClickAlternations() {
        // Simulate 200 rapid alternating button clicks (+1 / -1)
        viewModel.updatePortions(2)
        var current = 2
        for (i in 1..200) {
            current = if (i % 2 == 1) current + 1 else current - 1
            viewModel.updatePortions(current)
            assertEquals(current, viewModel.portionCount.value)
        }
        assertEquals(2, viewModel.portionCount.value)
    }

    @Test
    fun challengeStepByStepRoundTripDriftMeasurement() {
        // Base recipe with 2 portions: 2.0g seasoning (Cumin), 200g bulk (Rice)
        val initialRecipe =
            MealRecipe(
                recipeId = "drift_test",
                title = "Pilaf",
                targetPortions = 2,
                itemsUsedFromStock =
                    listOf(
                        RecipePantryUsage(name = "Rice", quantityUsed = 200f, isAromaticOrSeasoning = false),
                        RecipePantryUsage(name = "Cumin", quantityUsed = 2f, isAromaticOrSeasoning = true),
                    ),
            )

        // Ramp portions up: 2 -> 3 -> 4 -> ... -> 12
        var currentRecipe = initialRecipe
        for (portions in 3..MAX_PORTIONS) {
            currentRecipe = currentRecipe.scaleToPortions(portions)
            assertEquals(portions, currentRecipe.targetPortions)
        }

        // Check values at 12 portions
        val riceAt12 = currentRecipe.itemsUsedFromStock.first { it.name == "Rice" }.quantityUsed
        val cuminAt12 = currentRecipe.itemsUsedFromStock.first { it.name == "Cumin" }.quantityUsed

        // Direct scale from 2 to 12
        val directRice12 = CulinaryScaling.scaleQuantity(200f, 2, 12, false)
        val directCumin12 = CulinaryScaling.scaleQuantity(2f, 2, 12, true)

        // Step-by-step vs direct scaling comparison
        assertEquals(directRice12, riceAt12, 0.5f)
        assertEquals(directCumin12, cuminAt12, 0.5f)

        // Ramp portions down: 12 -> 11 -> 10 -> ... -> 2
        for (portions in 11 downTo 2) {
            currentRecipe = currentRecipe.scaleToPortions(portions)
            assertEquals(portions, currentRecipe.targetPortions)
        }

        val roundTripRice = currentRecipe.itemsUsedFromStock.first { it.name == "Rice" }.quantityUsed
        val roundTripCumin = currentRecipe.itemsUsedFromStock.first { it.name == "Cumin" }.quantityUsed

        // Quantities should return very close to 200f and 2f within acceptable rounding tolerance
        assertTrue("Rice drifted too far: $roundTripRice", abs(roundTripRice - 200f) < 2.0f)
        assertTrue("Cumin drifted too far: $roundTripCumin", abs(roundTripCumin - 2f) < 0.2f)
    }

    // =========================================================================
    // 2. Seasoning Scaling Accuracy (Power Curve vs Linear)
    // =========================================================================

    @Test
    fun challengeSeasoningSubLinearExponentAccuracy() {
        val basePortions = 2
        val baseQuantity = 10f

        // For all target portions from 1 to 12
        for (targetPortions in MIN_PORTIONS..MAX_PORTIONS) {
            val seasoningQty =
                CulinaryScaling.scaleQuantity(
                    baseQuantity = baseQuantity,
                    basePortions = basePortions,
                    targetPortions = targetPortions,
                    isAromaticOrSeasoning = true,
                )
            val linearQty =
                CulinaryScaling.scaleQuantity(
                    baseQuantity = baseQuantity,
                    basePortions = basePortions,
                    targetPortions = targetPortions,
                    isAromaticOrSeasoning = false,
                )

            // Exact mathematical expected values
            val ratio = targetPortions.toDouble() / basePortions.toDouble()
            val expectedSeasoning = (round(baseQuantity.toDouble() * ratio.pow(0.75) * 100.0) / 100.0).toFloat()
            val expectedLinear = (round(baseQuantity.toDouble() * ratio * 100.0) / 100.0).toFloat()

            assertEquals(
                "Seasoning scaling mismatch at portions=$targetPortions",
                expectedSeasoning,
                seasoningQty,
                0.01f,
            )
            assertEquals("Linear scaling mismatch at portions=$targetPortions", expectedLinear, linearQty, 0.01f)

            when {
                targetPortions > basePortions -> {
                    // When scaling up, seasonings scale SUB-LINEARLY (less than bulk)
                    assertTrue("Seasoning ($seasoningQty) must be < linear ($linearQty)", seasoningQty < linearQty)
                }
                targetPortions < basePortions -> {
                    // When scaling down, seasonings scale SUB-LINEARLY (greater than bulk)
                    assertTrue("Seasoning ($seasoningQty) must be > linear ($linearQty)", seasoningQty > linearQty)
                }
                else -> {
                    assertEquals(baseQuantity, seasoningQty, 0.001f)
                    assertEquals(baseQuantity, linearQty, 0.001f)
                }
            }
        }
    }

    @Test
    fun challengePalateExponentCalibrationClamping() {
        val baseQty = 10f
        // Exponent below min (0.65) must be clamped to 0.65
        val belowMin = CulinaryScaling.scaleQuantity(baseQty, 2, 6, true, exponent = 0.10)
        val exactMin = CulinaryScaling.scaleQuantity(baseQty, 2, 6, true, exponent = 0.65)
        assertEquals(exactMin, belowMin, 0.001f)

        // Exponent above max (0.85) must be clamped to 0.85
        val aboveMax = CulinaryScaling.scaleQuantity(baseQty, 2, 6, true, exponent = 1.50)
        val exactMax = CulinaryScaling.scaleQuantity(baseQty, 2, 6, true, exponent = 0.85)
        assertEquals(exactMax, aboveMax, 0.001f)
    }

    @Test
    fun challengeScaleQuantityRejectsInvalidInputs() {
        assertThrows(IllegalArgumentException::class.java) {
            CulinaryScaling.scaleQuantity(-5f, 2, 4, false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CulinaryScaling.scaleQuantity(10f, 0, 4, false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CulinaryScaling.scaleQuantity(10f, -1, 4, false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CulinaryScaling.scaleQuantity(10f, 2, 0, false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CulinaryScaling.scaleQuantity(10f, 2, -2, false)
        }
    }

    @Test
    fun challengeMissingItemsScaleLinearlyInRecipe() {
        val recipe =
            MealRecipe(
                recipeId = "rec_missing",
                title = "Curry",
                targetPortions = 2,
                missingItemsForGrocery =
                    listOf(
                        RecipeMissingItem(name = "Garam Masala", quantityNeeded = 2f, unit = "TSP"),
                    ),
            )
        // Scaled to 4 portions: Garam Masala is a missing item, scales linearly: 2 * (4/2) = 4.0f
        val scaled = recipe.scaleToPortions(4)
        val missing = scaled.missingItemsForGrocery.first()
        assertEquals(4f, missing.quantityNeeded, 0.01f)
    }

    // =========================================================================
    // 3. Mode Switching Under Rapid State Changes
    // =========================================================================

    @Test
    fun challengeRapidModeSwitching() {
        val modes =
            listOf(
                MealMatchMode.STRICT,
                MealMatchMode.MOSTLY,
                MealMatchMode.SHOPPING,
                MealMatchMode.EMERGENCY_SALVAGE,
            )

        // Cycle through modes rapidly 50 times
        for (i in 0 until 50) {
            val targetMode = modes[i % modes.size]
            viewModel.selectMode(targetMode)
            assertEquals(targetMode, viewModel.selectedMode.value)
        }
    }

    @Test
    fun challengePanicButtonBannerTriggersEmergencySalvage() =
        testScope.runTest {
            viewModel.selectMode(MealMatchMode.STRICT)
            assertEquals(MealMatchMode.STRICT, viewModel.selectedMode.value)

            viewModel.triggerEmergencySalvage()
            assertEquals(MealMatchMode.EMERGENCY_SALVAGE, viewModel.selectedMode.value)

            advanceUntilIdle()
            assertFalse(viewModel.isLoading.value)
        }

    @Test
    fun challengeConcurrentGenerateMealsExecution() =
        testScope.runTest {
            // Populate pantry with 3 items
            pantryRepo.flow.value =
                listOf(
                    PantryItem(id = 1L, name = "Pasta", quantity = 300f, unit = "g"),
                    PantryItem(id = 2L, name = "Tomato", quantity = 2f, unit = "count"),
                    PantryItem(id = 3L, name = "Cheese", quantity = 100f, unit = "g"),
                )

            // Launch 25 concurrent generation requests
            val jobs =
                List(25) {
                    launch {
                        viewModel.generateMeals()
                    }
                }
            jobs.joinAll()
            advanceUntilIdle()

            // UI state must settle cleanly
            assertFalse(viewModel.isLoading.value)
        }

    @Test
    fun challengeEmptyInventoryEmitsStatusMessage() =
        testScope.runTest {
            pantryRepo.flow.value = emptyList()

            var emittedMessage: String? = null
            val collectJob =
                launch {
                    emittedMessage = viewModel.statusMessage.first()
                }

            viewModel.generateMeals()
            advanceUntilIdle()

            assertEquals("No recipes found with current inventory.", emittedMessage)
            assertFalse(viewModel.isLoading.value)
            collectJob.cancel()
        }

    // =========================================================================
    // Test Doubles
    // =========================================================================

    private class ChallengerPantryRepo(
        initial: List<PantryItem> = emptyList(),
    ) : PantryRepository {
        val flow = MutableStateFlow(initial)

        override fun observePantryItems(): Flow<List<PantryItem>> = flow

        override fun searchPantryItems(query: String): Flow<List<PantryItem>> = flow

        override suspend fun insertPantry(item: PantryItem): Long = 1L

        override suspend fun updatePantry(item: PantryItem) { /* no-op */ }

        override suspend fun decrementQuantity(
            item: PantryItem,
            delta: Float,
        ) { /* no-op */ }

        override suspend fun deletePantry(item: PantryItem) { /* no-op */ }

        override suspend fun restorePantry(item: PantryItem) { /* no-op */ }

        override fun observeGroceryItems(): Flow<List<GroceryItem>> = MutableStateFlow(emptyList())

        override suspend fun insertGrocery(item: GroceryItem): Long = 1L

        override suspend fun updateGrocery(item: GroceryItem) { /* no-op */ }

        override suspend fun deleteGrocery(item: GroceryItem) { /* no-op */ }

        override suspend fun clearCheckedGrocery() { /* no-op */ }

        override suspend fun restockCheckedGroceryToPantry(): Int = 0

        override fun reclaimTemporaryMedia(): Int = 0
    }

    private class ChallengerGroceryRepo : GroceryRepository {
        override fun observeGroceryItems(): Flow<List<GroceryItem>> = MutableStateFlow(emptyList())

        override suspend fun insertGrocery(item: GroceryItem): Long = 1L

        override suspend fun updateGrocery(item: GroceryItem) { /* no-op */ }

        override suspend fun deleteGrocery(item: GroceryItem) { /* no-op */ }

        override suspend fun clearCheckedGrocery() { /* no-op */ }

        override suspend fun restockCheckedGroceryToPantry(): Int = 0

        override fun reclaimTemporaryMedia(): Int = 0
    }
}
