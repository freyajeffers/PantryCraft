package com.jeffers.pantrycraft.feature.meals.ui

import android.app.KeyguardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import com.jeffers.pantrycraft.feature.meals.model.NutritionalEstimates
import com.jeffers.pantrycraft.feature.meals.model.RecipeMissingItem
import com.jeffers.pantrycraft.feature.meals.model.RecipePantryUsage
import com.jeffers.pantrycraft.feature.meals.swap.SmartSwapSuggestion
import com.jeffers.pantrycraft.ui.PantryCraftTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MealSynthesisScreenComposeTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        Assume.assumeTrue(
            "Screen is locked; skipping Compose UI test",
            km?.isKeyguardLocked == false,
        )
    }

    @After
    fun tearDown() {
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    @Test
    fun initialStateDisplaysDefaultControlsAndPlaceholder() {
        val viewModel = createTestViewModel()

        composeTestRule.setContent {
            PantryCraftTheme {
                MealSynthesisScreen(viewModel = viewModel)
            }
        }

        composeTestRule.onNodeWithText("Portion-Aware Meal Synthesis").assertIsDisplayed()
        composeTestRule.onNodeWithTag("panic_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("15-Min Pantry Panic Button (Rescue Expiring Food)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Servings / Portions:").assertIsDisplayed()
        composeTestRule.onNodeWithTag("portion_value_text").assertIsDisplayed()
        composeTestRule.onNodeWithText("2").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mode_chip_STRICT").assertIsDisplayed()
        composeTestRule.onNodeWithText("Zero-Waste Strict").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mode_chip_MOSTLY").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pantry First").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mode_chip_SHOPPING").assertIsDisplayed()
        composeTestRule.onNodeWithText("Inspirational Chef").assertIsDisplayed()
        composeTestRule.onNode(hasScrollAction()).performScrollToIndex(3)
        composeTestRule.onNodeWithTag("mode_chip_EMERGENCY_SALVAGE").assertIsDisplayed()
        composeTestRule.onNodeWithText("15-Min Rescue").assertIsDisplayed()
        composeTestRule.onNode(hasScrollAction()).performScrollToIndex(0)
        composeTestRule.onNodeWithTag("synthesize_meals_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("Synthesize Meals").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                "Select a mode, dial your portions, and tap 'Synthesize Meals' to generate recipes.",
            ).assertIsDisplayed()
    }

    @Test
    fun modeChipsUpdateSelectedModeOnChipClick() {
        val viewModel = createTestViewModel()

        composeTestRule.setContent {
            PantryCraftTheme {
                MealSynthesisScreen(viewModel = viewModel)
            }
        }

        composeTestRule.onNodeWithTag("mode_chip_MOSTLY").performClick()
        assertEquals(MealMatchMode.MOSTLY, viewModel.selectedMode.value)

        composeTestRule.onNodeWithTag("mode_chip_SHOPPING").performClick()
        assertEquals(MealMatchMode.SHOPPING, viewModel.selectedMode.value)

        composeTestRule.onNode(hasScrollAction()).performScrollToIndex(3)
        composeTestRule.onNodeWithTag("mode_chip_EMERGENCY_SALVAGE").performClick()
        assertEquals(MealMatchMode.EMERGENCY_SALVAGE, viewModel.selectedMode.value)

        composeTestRule.onNode(hasScrollAction()).performScrollToIndex(0)
        composeTestRule.onNodeWithTag("mode_chip_STRICT").performClick()
        assertEquals(MealMatchMode.STRICT, viewModel.selectedMode.value)
    }

    @Test
    fun panicButtonBannerTriggersEmergencySalvage() {
        val viewModel = createTestViewModel()

        composeTestRule.setContent {
            PantryCraftTheme {
                MealSynthesisScreen(viewModel = viewModel)
            }
        }

        composeTestRule.onNodeWithTag("panic_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(MealMatchMode.EMERGENCY_SALVAGE, viewModel.selectedMode.value)
    }

    @Test
    fun portionSelectorEnforcesMinMaxPortionBounds() {
        val viewModel = createTestViewModel()

        composeTestRule.setContent {
            PantryCraftTheme {
                MealSynthesisScreen(viewModel = viewModel)
            }
        }

        // Default portions = 2. Decrement to MIN_PORTIONS (1).
        composeTestRule.onNodeWithTag("portion_decrease_button").performClick()
        composeTestRule.onNodeWithText("$MIN_PORTIONS").assertIsDisplayed()
        composeTestRule.onNodeWithTag("portion_decrease_button").assertIsNotEnabled()

        // Increment back to 2
        composeTestRule.onNodeWithTag("portion_increase_button").performClick()
        composeTestRule.onNodeWithText("2").assertIsDisplayed()
        composeTestRule.onNodeWithTag("portion_decrease_button").assertIsEnabled()

        // Step up to MAX_PORTIONS (12)
        repeat(MAX_PORTIONS - 2) {
            composeTestRule.onNodeWithTag("portion_increase_button").performClick()
        }
        composeTestRule.onNodeWithText("$MAX_PORTIONS").assertIsDisplayed()
        composeTestRule.onNodeWithTag("portion_increase_button").assertIsNotEnabled()
    }

    @Test
    fun portionScalingUpdatesLinearAndNonLinearSeasoningQuantities() {
        val baseRecipe =
            MealRecipe(
                recipeId = "scaling_test",
                title = "Pilaf Delight",
                targetPortions = 2,
                itemsUsedFromStock =
                    listOf(
                        RecipePantryUsage(
                            name = "Jasmine Rice",
                            quantityUsed = 2.0f,
                            unit = "CUP",
                            isAromaticOrSeasoning = false,
                        ),
                        RecipePantryUsage(
                            name = "Cumin Powder",
                            quantityUsed = 2.0f,
                            unit = "TSP",
                            isAromaticOrSeasoning = true,
                        ),
                    ),
            )

        val recipeState = mutableStateOf(baseRecipe)

        // At base 2 portions:
        // Rice = 2.0 CUP, Cumin = 2.0 TSP
        composeTestRule.setContent {
            PantryCraftTheme {
                MealRecipeCard(recipe = recipeState.value, swaps = emptyMap(), onCook = {})
            }
        }
        composeTestRule.onNodeWithTag("recipe_card_scaling_test").assertIsDisplayed()
        composeTestRule.onNodeWithText("• Jasmine Rice: 2.0 CUP").assertIsDisplayed()
        composeTestRule.onNodeWithText("• Cumin Powder: 2.0 TSP").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cook This Meal (2 Servings)").assertIsDisplayed()

        // Scaled to 4 portions:
        // Linear: 2.0 * (4 / 2) = 4.0
        // Power curve: 2.0 * (4 / 2)^0.75 = 2.0 * 1.68179 = 3.36
        recipeState.value = baseRecipe.scaleToPortions(4)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("• Jasmine Rice: 4.0 CUP").assertIsDisplayed()
        composeTestRule.onNodeWithText("• Cumin Powder: 3.36 TSP").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cook This Meal (4 Servings)").assertIsDisplayed()

        // Scaled to 1 portion:
        // Linear: 2.0 * (1 / 2) = 1.0
        // Power curve: 2.0 * (1 / 2)^0.75 = 2.0 * 0.59460 = 1.19
        recipeState.value = baseRecipe.scaleToPortions(1)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("• Jasmine Rice: 1.0 CUP").assertIsDisplayed()
        composeTestRule.onNodeWithText("• Cumin Powder: 1.19 TSP").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cook This Meal (1 Servings)").assertIsDisplayed()
    }

    @Test
    fun recipeCardDisplaysHeaderNutritionAndRescuedWasteTag() {
        val recipe =
            MealRecipe(
                recipeId = "card_test",
                title = "Zero-Waste Farmhouse Frittata",
                matchMode = MealMatchMode.STRICT,
                targetPortions = 2,
                prepTimeMinutes = 10,
                cookTimeMinutes = 15,
                itemsUsedFromStock =
                    listOf(
                        RecipePantryUsage(name = "Fresh Eggs", quantityUsed = 4f, unit = null, preventsWaste = false),
                        RecipePantryUsage(name = "Baby Spinach", quantityUsed = 1f, unit = "BAG", preventsWaste = true),
                    ),
                stepByStepInstructions = listOf("1. Whisk eggs in bowl", "2. Sauté spinach and bake"),
                nutritionalEstimates =
                    NutritionalEstimates(
                        calories = 320,
                        proteinGrams = 22,
                        carbsGrams = 8,
                        fatGrams = 14,
                    ),
            )

        composeTestRule.setContent {
            PantryCraftTheme {
                MealRecipeCard(recipe = recipe, swaps = emptyMap(), onCook = {})
            }
        }

        composeTestRule.onNodeWithTag("recipe_card_card_test").assertIsDisplayed()
        composeTestRule.onNodeWithText("Zero-Waste Farmhouse Frittata").assertIsDisplayed()
        composeTestRule.onNodeWithText("25m").assertIsDisplayed()
        composeTestRule.onNodeWithText("Zero-Waste Strict • 2 Servings").assertIsDisplayed()
        composeTestRule.onNodeWithText("From Your Pantry:").assertIsDisplayed()
        composeTestRule.onNodeWithText("• Fresh Eggs: 4.0").assertIsDisplayed()
        composeTestRule.onNodeWithText("• Baby Spinach: 1.0 BAG (Rescued!)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Instructions:").assertIsDisplayed()
        composeTestRule.onNodeWithText("1. Whisk eggs in bowl").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                "Per Portion: 320 kcal • 22g Protein • 8g Carbs • 14g Fat",
            ).assertIsDisplayed()
    }

    @Test
    fun recipeCardDisplaysMissingItemsAndSmartSwapChips() {
        val recipe =
            MealRecipe(
                recipeId = "swap_test",
                title = "Pantry Pasta",
                matchMode = MealMatchMode.SHOPPING,
                targetPortions = 2,
                missingItemsForGrocery =
                    listOf(
                        RecipeMissingItem(name = "Olive Oil", quantityNeeded = 1f, unit = "TBSP"),
                    ),
            )
        val swaps =
            mapOf(
                "Olive Oil" to
                    listOf(
                        SmartSwapSuggestion(
                            missingItem = "Olive Oil",
                            substituteName = "Avocado Oil",
                            ratioExplanation = "1:1 direct smoke point substitute",
                            requiredStockItems = listOf("Avocado Oil"),
                            isFullyAvailableOnHand = true,
                        ),
                    ),
            )

        composeTestRule.setContent {
            PantryCraftTheme {
                MealRecipeCard(recipe = recipe, swaps = swaps, onCook = {})
            }
        }

        composeTestRule.onNodeWithText("Needed / Smart Swaps:").assertIsDisplayed()
        composeTestRule.onNodeWithText("• Olive Oil (1.0 TBSP)").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                "Swap: Avocado Oil (1:1 direct smoke point substitute)",
            ).assertIsDisplayed()
    }

    @Test
    fun cookMealButtonTriggersDeductionAndDisplaysSnackbar() {
        val rice = PantryItem(id = 10L, name = "Basmati Rice", quantity = 500f, unit = "g")
        val fakePantry = FakeComposeTestPantryRepo(listOf(rice))
        val fakeGrocery = FakeComposeTestGroceryRepo()
        val dummyModel =
            object : TextGenerationModel {
                override suspend fun generate(
                    systemPrompt: String,
                    userPrompt: String,
                ): String = "{}"
            }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = MealSynthesisEngine(context, dummyModel)
        val viewModel = MealSynthesisViewModel(fakePantry, fakeGrocery, engine)

        val recipe =
            MealRecipe(
                recipeId = "cook_1",
                title = "Steamed Rice",
                targetPortions = 2,
                itemsUsedFromStock =
                    listOf(
                        RecipePantryUsage(name = "Basmati Rice", pantryItemId = 10L, quantityUsed = 150f, unit = "g"),
                    ),
            )

        composeTestRule.setContent {
            PantryCraftTheme {
                MealSynthesisScreen(viewModel = viewModel)
            }
        }

        viewModel.cookMeal(recipe)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Cooked 'Steamed Rice'! Deducted 1 items.").assertIsDisplayed()
        assertEquals(350f, fakePantry.getItemQuantity(10L) ?: 0f, 0.01f)
    }

    private fun createTestViewModel(initialPantry: List<PantryItem> = emptyList()): MealSynthesisViewModel {
        val fakePantry = FakeComposeTestPantryRepo(initialPantry)
        val fakeGrocery = FakeComposeTestGroceryRepo()
        val dummyModel =
            object : TextGenerationModel {
                override suspend fun generate(
                    systemPrompt: String,
                    userPrompt: String,
                ): String = "{}"
            }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = MealSynthesisEngine(context, dummyModel)
        return MealSynthesisViewModel(fakePantry, fakeGrocery, engine)
    }

    private class FakeComposeTestPantryRepo(
        initialItems: List<PantryItem> = emptyList(),
    ) : PantryRepository {
        private val items = initialItems.map { it.copy() }.toMutableList()
        val flow = MutableStateFlow<List<PantryItem>>(items.toList())

        fun getItemQuantity(id: Long): Float? = items.firstOrNull { it.id == id }?.quantity

        override fun observePantryItems(): Flow<List<PantryItem>> = flow

        override fun searchPantryItems(query: String): Flow<List<PantryItem>> = flow

        override suspend fun insertPantry(item: PantryItem): Long = 1L

        override suspend fun updatePantry(item: PantryItem) { /* no-op */ }

        override suspend fun decrementQuantity(
            item: PantryItem,
            delta: Float,
        ) {
            val idx = items.indexOfFirst { it.id == item.id }
            if (idx >= 0) {
                val cur = items[idx]
                items[idx] = cur.copy(quantity = maxOf(0f, cur.quantity - delta))
                flow.value = items.toList()
            }
        }

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

    private class FakeComposeTestGroceryRepo : GroceryRepository {
        val inserted = mutableListOf<GroceryItem>()

        override fun observeGroceryItems(): Flow<List<GroceryItem>> = MutableStateFlow(inserted.toList())

        override suspend fun insertGrocery(item: GroceryItem): Long {
            inserted.add(item)
            return inserted.size.toLong()
        }

        override suspend fun updateGrocery(item: GroceryItem) { /* no-op */ }

        override suspend fun deleteGrocery(item: GroceryItem) { /* no-op */ }

        override suspend fun clearCheckedGrocery() { /* no-op */ }

        override suspend fun restockCheckedGroceryToPantry(): Int = 0

        override fun reclaimTemporaryMedia(): Int = 0
    }
}
