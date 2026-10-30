package com.jeffers.pantrycraft

import android.content.Context
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.pantrycraft.data.AppDatabase
import com.jeffers.pantrycraft.data.CulinaryScaling
import com.jeffers.pantrycraft.data.GroceryDao
import com.jeffers.pantrycraft.data.PantryDao
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.Repository
import com.jeffers.pantrycraft.feature.meals.context.InventoryContextCompressor
import com.jeffers.pantrycraft.feature.meals.deduction.MealCookHandler
import com.jeffers.pantrycraft.feature.meals.engine.FallbackMealSynthesizer
import com.jeffers.pantrycraft.feature.meals.engine.MealSynthesisEngine
import com.jeffers.pantrycraft.feature.meals.model.MealMatchMode
import com.jeffers.pantrycraft.feature.meals.model.MealRecipe
import com.jeffers.pantrycraft.feature.meals.model.RecipeMissingItem
import com.jeffers.pantrycraft.feature.meals.model.RecipePantryUsage
import com.jeffers.pantrycraft.feature.meals.ui.MealSynthesisViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end integration test verifying the complete meal journey:
 * 1. Intake: Inserting ingredients into SQLCipher KeyStore-encrypted Room DB.
 * 2. Context Compression: Compressing on-hand items into token-efficient prompt format.
 * 3. Meal Synthesis: Synthesizing recipes with non-linear portion scaling ($portions^{0.75}$).
 * 4. "Cook This Meal" Execution: Room transactional stock deduction with zero floor.
 * 5. Missing items automatically populated into grocery_items checklist.
 */
@RunWith(AndroidJUnit4::class)
class EndToEndMealJourneyTest {

    private val marker = "e2e-meal-${System.nanoTime()}-"
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: Repository
    private lateinit var pantryDao: PantryDao
    private lateinit var groceryDao: GroceryDao

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = Repository.getInstance(context)
        db = repository.db
        pantryDao = db.pantryDao()
        groceryDao = db.groceryDao()
        runBlocking {
            cleanupTestData()
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            cleanupTestData()
        }
    }

    private suspend fun cleanupTestData() {
        pantryDao.deleteByNamePrefix(marker)
        groceryDao.deleteByNamePrefix(marker)
    }

    @Test
    fun testCompleteEndToEndMealJourneyFromIntakeToCook() = runBlocking {
        val now = System.currentTimeMillis()
        val oneDayMillis = 86_400_000L

        // 1. Intake: Ingest ingredients into Room database
        val salmon = PantryItem(
            name = "${marker}Atlantic Salmon",
            quantity = 300f,
            unit = "g",
            storageLocation = "FRIDGE",
            expirationTimestamp = now + oneDayMillis,
        )
        val rice = PantryItem(
            name = "${marker}Jasmine Rice",
            quantity = 500f,
            unit = "g",
            storageLocation = "PANTRY",
        )
        val salmonId = repository.insertPantry(salmon)
        val riceId = repository.insertPantry(rice)

        // Verify intake persisted in Room
        val onHand = repository.observePantryItems().first().filter { it.name.startsWith(marker) }
        assertEquals(2, onHand.size)

        // 2. Context Compression
        val compressed = InventoryContextCompressor.compress(onHand, now)
        assertTrue(compressed.contains("[INVENTORY_CONTEXT]"))
        assertTrue(compressed.contains("Fridge:"))
        assertTrue(compressed.contains("Pantry:"))
        assertTrue(compressed.contains("[EXPIRY_URGENT]"))
        val tokenEstimate = InventoryContextCompressor.estimateTokenCount(compressed)
        assertTrue(tokenEstimate in 1..200)

        // 3. Meal Synthesis
        val recipes = FallbackMealSynthesizer.synthesize(
            items = onHand,
            mode = MealMatchMode.EMERGENCY_SALVAGE,
            targetPortions = 2,
            nowMillis = now,
        )
        assertFalse(recipes.isEmpty())
        val recipe = recipes.first()
        assertTrue(recipe.urgencyScore >= 0.9f)
        assertTrue(recipe.itemsUsedFromStock.any { it.preventsWaste })

        // 4. "Cook This Meal" Execution
        val cookResult = MealCookHandler.executeCookMealTransactional(
            database = db,
            recipe = recipe,
            pantryRepository = repository,
            groceryRepository = repository,
            addMissingToGrocery = true,
        )
        assertTrue(cookResult.itemsDeductedCount > 0)
        assertTrue(cookResult.foodWastePreventedCount >= 1)

        // 5. Verification of Room DB state
        val updatedItems = repository.observePantryItems().first().filter { it.name.startsWith(marker) }
        val updatedSalmon = updatedItems.first { it.id == salmonId }
        val usedSalmon = recipe.itemsUsedFromStock.first { it.pantryItemId == salmonId }.quantityUsed
        assertEquals(300f - usedSalmon, updatedSalmon.quantity, 0.01f)
    }

    @Test
    fun testAtomicDeductionWithZeroFloorEnforcement() = runBlocking {
        // Ingest low-stock item
        val butter = PantryItem(
            name = "${marker}Butter",
            quantity = 2f,
            unit = "TBSP",
            storageLocation = "FRIDGE",
        )
        val butterId = repository.insertPantry(butter)
        val savedButter = repository.observePantryItems().first().first { it.id == butterId }
        assertEquals(2f, savedButter.quantity, 0.01f)

        // Recipe uses 10 TBSP (delta exceeds available stock of 2 TBSP)
        val recipe = MealRecipe(
            recipeId = "${marker}recipe_zero_floor",
            title = "Butter Overdraw Recipe",
            targetPortions = 2,
            itemsUsedFromStock = listOf(
                RecipePantryUsage(
                    name = "${marker}Butter",
                    pantryItemId = butterId,
                    quantityUsed = 10f,
                    unit = "TBSP",
                ),
            ),
        )

        // Execute cook deduction
        val result = MealCookHandler.executeCookMealTransactional(
            database = db,
            recipe = recipe,
            pantryRepository = repository,
            groceryRepository = repository,
            addMissingToGrocery = true,
        )
        assertEquals(1, result.itemsDeductedCount)

        // Verify zero floor in Room SQLite: quantity must be 0f, NOT negative
        val postCookButter = repository.observePantryItems().first().first { it.id == butterId }
        assertEquals(0f, postCookButter.quantity, 0.001f)
    }

    @Test
    fun testMissingItemsPopulatedToGroceryChecklist() = runBlocking {
        val pasta = PantryItem(
            name = "${marker}Penne Pasta",
            quantity = 400f,
            unit = "g",
            storageLocation = "PANTRY",
        )
        val pastaId = repository.insertPantry(pasta)

        val missingItemName = "${marker}Fresh Basil"
        val recipe = MealRecipe(
            recipeId = "${marker}recipe_shopping",
            title = "Pesto Pasta",
            targetPortions = 2,
            itemsUsedFromStock = listOf(
                RecipePantryUsage(
                    name = "${marker}Penne Pasta",
                    pantryItemId = pastaId,
                    quantityUsed = 200f,
                    unit = "g",
                ),
            ),
            missingItemsForGrocery = listOf(
                RecipeMissingItem(
                    name = missingItemName,
                    quantityNeeded = 1f,
                    unit = "BUNCH",
                ),
            ),
        )

        val result = MealCookHandler.executeCookMealTransactional(
            database = db,
            recipe = recipe,
            pantryRepository = repository,
            groceryRepository = repository,
            addMissingToGrocery = true,
        )
        assertEquals(1, result.itemsDeductedCount)
        assertEquals(1, result.groceryItemsAddedCount)

        // Verify grocery item in Room
        val groceries = repository.observeGroceryItems().first().filter { it.name.startsWith(marker) }
        assertEquals(1, groceries.size)
        val grocery = groceries.first()
        assertEquals(missingItemName, grocery.name)
        assertEquals(1f, grocery.quantity, 0.01f)
        assertEquals("BUNCH", grocery.unit)
        assertFalse(grocery.checked)
    }

    @Test
    fun testTransactionAtomicRollbackOnCookFailure() = runBlocking {
        val oliveOil = PantryItem(
            name = "${marker}Olive Oil",
            quantity = 500f,
            unit = "ml",
            storageLocation = "PANTRY",
        )
        val oilId = repository.insertPantry(oliveOil)

        val recipe = MealRecipe(
            recipeId = "${marker}recipe_fail",
            title = "Failed Recipe",
            itemsUsedFromStock = listOf(
                RecipePantryUsage(
                    name = "${marker}Olive Oil",
                    pantryItemId = oilId,
                    quantityUsed = 50f,
                    unit = "ml",
                ),
            ),
            missingItemsForGrocery = listOf(
                RecipeMissingItem(
                    name = "${marker}Black Truffle",
                    quantityNeeded = 1f,
                    unit = "PIECE",
                ),
            ),
        )

        // Simulate transactional failure
        try {
            db.withTransaction {
                MealCookHandler.executeCookMealTransactional(
                    database = db,
                    recipe = recipe,
                    pantryRepository = repository,
                    groceryRepository = repository,
                    addMissingToGrocery = true,
                )
                error("Simulated crash mid-transaction")
            }
        } catch (_: IllegalStateException) {
            // Expected rollback
        }

        // Verify Room DB: stock unchanged, no grocery inserted
        val oilAfter = repository.observePantryItems().first().first { it.id == oilId }
        assertEquals(500f, oilAfter.quantity, 0.01f)
        val groceries = repository.observeGroceryItems().first().filter { it.name.startsWith(marker) }
        assertTrue(groceries.isEmpty())
    }

    @Test
    fun testCulinaryVolumeConversionDuringStockDeduction() = runBlocking {
        // Stock stored in TBSP (4 TBSP)
        val stockItem = PantryItem(
            name = "${marker}Soy Sauce",
            quantity = 4f,
            unit = "TBSP",
            storageLocation = "PANTRY",
        )
        val itemId = repository.insertPantry(stockItem)

        // Recipe calls for 3 TSP (3 TSP = 1 TBSP)
        val recipe = MealRecipe(
            recipeId = "${marker}recipe_volume_convert",
            title = "Asian Stir Fry",
            itemsUsedFromStock = listOf(
                RecipePantryUsage(
                    name = "${marker}Soy Sauce",
                    pantryItemId = itemId,
                    quantityUsed = 3f,
                    unit = "TSP",
                ),
            ),
        )

        val result = MealCookHandler.executeCookMealTransactional(
            database = db,
            recipe = recipe,
            pantryRepository = repository,
            groceryRepository = repository,
        )
        assertEquals(1, result.itemsDeductedCount)

        // 4 TBSP - 1 TBSP (converted from 3 TSP) = 3 TBSP remaining
        val updatedStock = repository.observePantryItems().first().first { it.id == itemId }
        assertEquals(3f, updatedStock.quantity, 0.01f)
    }

    @Test
    fun testSeasoningPowerCurveNonLinearDeduction() = runBlocking {
        val garlicPowder = PantryItem(
            name = "${marker}Garlic Powder",
            quantity = 10f,
            unit = "g",
            isAromaticOrSeasoning = true,
            storageLocation = "PANTRY",
        )
        val flour = PantryItem(
            name = "${marker}Flour",
            quantity = 1000f,
            unit = "g",
            isAromaticOrSeasoning = false,
            storageLocation = "PANTRY",
        )
        val garlicId = repository.insertPantry(garlicPowder)
        val flourId = repository.insertPantry(flour)

        // Base recipe for 2 portions
        val baseRecipe = MealRecipe(
            recipeId = "${marker}recipe_scaling",
            title = "Seasoned Bread",
            targetPortions = 2,
            itemsUsedFromStock = listOf(
                RecipePantryUsage(
                    name = "${marker}Garlic Powder",
                    pantryItemId = garlicId,
                    quantityUsed = 2f,
                    unit = "g",
                    isAromaticOrSeasoning = true,
                ),
                RecipePantryUsage(
                    name = "${marker}Flour",
                    pantryItemId = flourId,
                    quantityUsed = 200f,
                    unit = "g",
                    isAromaticOrSeasoning = false,
                ),
            ),
        )

        // Scale to 4 portions (ratio = 2.0)
        val scaledRecipe = baseRecipe.scaleToPortions(4)
        val scaledFlourUsage = scaledRecipe.itemsUsedFromStock.first { it.pantryItemId == flourId }
        val scaledGarlicUsage = scaledRecipe.itemsUsedFromStock.first { it.pantryItemId == garlicId }

        // Flour scales linearly: 200 * 2 = 400g
        assertEquals(400f, scaledFlourUsage.quantityUsed, 0.01f)
        // Garlic scales non-linearly: 2 * 2^0.75 ~= 3.36g (< 4.0g)
        val expectedGarlic = CulinaryScaling.scaleQuantity(2f, 2, 4, true)
        assertEquals(expectedGarlic, scaledGarlicUsage.quantityUsed, 0.01f)
        assertTrue(scaledGarlicUsage.quantityUsed < 4.0f)

        // Cook the scaled meal
        MealCookHandler.executeCookMealTransactional(
            database = db,
            recipe = scaledRecipe,
            pantryRepository = repository,
            groceryRepository = repository,
        )

        val flourAfter = repository.observePantryItems().first().first { it.id == flourId }
        val garlicAfter = repository.observePantryItems().first().first { it.id == garlicId }
        assertEquals(600f, flourAfter.quantity, 0.01f)
        assertEquals(10f - expectedGarlic, garlicAfter.quantity, 0.01f)
    }

    @Test
    fun testViewModelEndToEndMealJourney() = runBlocking {
        val now = System.currentTimeMillis()
        val chicken = PantryItem(
            name = "${marker}Chicken Breast",
            quantity = 500f,
            unit = "g",
            storageLocation = "FRIDGE",
            expirationTimestamp = now + 43_200_000L, // 12 hours
        )
        val itemId = repository.insertPantry(chicken)

        val engine = MealSynthesisEngine(context)
        val viewModel = MealSynthesisViewModel(repository, repository, engine)

        // Select emergency mode
        viewModel.selectMode(MealMatchMode.EMERGENCY_SALVAGE)
        viewModel.updatePortions(2)
        viewModel.generateMeals()

        // Verify generated recipes
        val recipes = viewModel.recipes.value
        assertFalse(recipes.isEmpty())
        val recipe = recipes.first()

        // Cook meal via ViewModel
        viewModel.cookMeal(recipe)

        // Verify deduction applied to Room DB
        val updatedChicken = repository.observePantryItems().first().first { it.id == itemId }
        assertTrue(updatedChicken.quantity < 500f)
    }
}
