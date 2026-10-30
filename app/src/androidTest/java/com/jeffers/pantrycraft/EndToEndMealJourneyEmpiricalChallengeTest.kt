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
import com.jeffers.pantrycraft.feature.meals.model.MealMatchMode
import com.jeffers.pantrycraft.feature.meals.model.MealRecipe
import com.jeffers.pantrycraft.feature.meals.model.RecipeMissingItem
import com.jeffers.pantrycraft.feature.meals.model.RecipePantryUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
 * Empirical challenger test suite for Milestone 3 End-to-End Room Integration.
 * Stress-tests full intake-to-cook journey, transaction rollback atomicity,
 * zero-floor clamping under concurrent access and extreme overdraw,
 * and automatic grocery checklist insertion and filtering.
 */
@RunWith(AndroidJUnit4::class)
class EndToEndMealJourneyEmpiricalChallengeTest {
    private val marker = "challenger-m3-2-${System.nanoTime()}-"
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
    fun challengeCompleteJourneyMultiLocationWithPerishabilityAndScaling() =
        runBlocking {
            val now = System.currentTimeMillis()
            val (salmonId, garlicId) = setupMultiLocationItems(now)

            val onHand = repository.observePantryItems().first().filter { it.name.startsWith(marker) }
            assertEquals(4, onHand.size)

            // Context compression stress-test
            val compressed = InventoryContextCompressor.compress(onHand, now)
            assertTrue(compressed.contains("[INVENTORY_CONTEXT]"))
            assertTrue(compressed.contains("Fridge:"))
            assertTrue(compressed.contains("Pantry:"))
            assertTrue(compressed.contains("Freezer:"))
            assertTrue(compressed.contains("Counter:"))
            assertTrue(compressed.contains("[EXPIRY_URGENT]"))

            // Meal synthesis with scaling
            val baseRecipe =
                FallbackMealSynthesizer
                    .synthesize(
                        items = onHand,
                        mode = MealMatchMode.EMERGENCY_SALVAGE,
                        targetPortions = 2,
                        nowMillis = now,
                    ).first()
            assertTrue(baseRecipe.itemsUsedFromStock.any { it.preventsWaste })

            val scaledRecipe = baseRecipe.scaleToPortions(4)
            verifyScaledAromatics(baseRecipe, scaledRecipe, garlicId)

            // "Cook This Meal" execution & Room DB verification
            val result =
                MealCookHandler.executeCookMealTransactional(
                    database = db,
                    recipe = scaledRecipe,
                    pantryRepository = repository,
                    groceryRepository = repository,
                    addMissingToGrocery = true,
                )
            assertTrue(result.itemsDeductedCount > 0)
            assertTrue(result.foodWastePreventedCount >= 1)

            val updated = repository.observePantryItems().first().filter { it.name.startsWith(marker) }
            val updatedSalmon = updated.first { it.id == salmonId }
            val usedSalmon = scaledRecipe.itemsUsedFromStock.first { it.pantryItemId == salmonId }.quantityUsed
            assertEquals(400f - usedSalmon, updatedSalmon.quantity, 0.01f)
        }

    private suspend fun setupMultiLocationItems(now: Long): Pair<Long, Long> {
        val twoHoursMillis = 7_200_000L
        val salmonId =
            repository.insertPantry(
                PantryItem(
                    name = "${marker}Wild Salmon",
                    quantity = 400f,
                    unit = "g",
                    storageLocation = "FRIDGE",
                    expirationTimestamp = now + twoHoursMillis,
                ),
            )
        val garlicId =
            repository.insertPantry(
                PantryItem(
                    name = "${marker}Garlic Powder",
                    quantity = 20f,
                    unit = "g",
                    isAromaticOrSeasoning = true,
                    storageLocation = "PANTRY",
                ),
            )
        repository.insertPantry(
            PantryItem(
                name = "${marker}Frozen Spinach",
                quantity = 500f,
                unit = "g",
                storageLocation = "FREEZER",
            ),
        )
        repository.insertPantry(
            PantryItem(
                name = "${marker}Olive Oil",
                quantity = 250f,
                unit = "ml",
                storageLocation = "COUNTER",
            ),
        )
        return Pair(salmonId, garlicId)
    }

    private fun verifyScaledAromatics(
        baseRecipe: MealRecipe,
        scaledRecipe: MealRecipe,
        garlicId: Long,
    ) {
        val garlicUsage = scaledRecipe.itemsUsedFromStock.firstOrNull { it.pantryItemId == garlicId }
        if (garlicUsage != null) {
            val baseGarlic = baseRecipe.itemsUsedFromStock.first { it.pantryItemId == garlicId }.quantityUsed
            val expectedGarlic = CulinaryScaling.scaleQuantity(baseGarlic, 2, 4, true)
            assertEquals(expectedGarlic, garlicUsage.quantityUsed, 0.01f)
            assertTrue(garlicUsage.quantityUsed < baseGarlic * 2f)
        }
    }

    @Test
    fun challengeCompleteJourneyEmptyInventoryReturnsEmptyWithoutCrash() =
        runBlocking {
            val now = System.currentTimeMillis()
            val emptyItems = emptyList<PantryItem>()

            val compressed = InventoryContextCompressor.compress(emptyItems, now)
            assertTrue(compressed.contains("[INVENTORY_CONTEXT]"))
            assertEquals(0, InventoryContextCompressor.estimateTokenCount(""))

            val recipes =
                FallbackMealSynthesizer.synthesize(
                    items = emptyItems,
                    mode = MealMatchMode.STRICT,
                    targetPortions = 2,
                    nowMillis = now,
                )
            assertTrue(recipes.isEmpty())

            val emptyRecipe =
                MealRecipe(
                    recipeId = "${marker}empty",
                    title = "Empty Recipe",
                    itemsUsedFromStock = emptyList(),
                    missingItemsForGrocery = emptyList(),
                )
            val result =
                MealCookHandler.executeCookMealTransactional(
                    database = db,
                    recipe = emptyRecipe,
                    pantryRepository = repository,
                    groceryRepository = repository,
                )
            assertEquals(0, result.itemsDeductedCount)
            assertEquals(0, result.foodWastePreventedCount)
            assertEquals(0, result.groceryItemsAddedCount)
        }

    @Test
    fun challengeTransactionRollbackWhenDatabaseCrashSimulatedMidTransaction() =
        runBlocking {
            val broth =
                PantryItem(
                    name = "${marker}Beef Broth",
                    quantity = 1000f,
                    unit = "ml",
                    storageLocation = "PANTRY",
                )
            val carrot =
                PantryItem(
                    name = "${marker}Carrots",
                    quantity = 500f,
                    unit = "g",
                    storageLocation = "FRIDGE",
                )
            val brothId = repository.insertPantry(broth)
            val carrotId = repository.insertPantry(carrot)

            val recipe =
                MealRecipe(
                    recipeId = "${marker}recipe_crash",
                    title = "Crash Recipe",
                    itemsUsedFromStock =
                        listOf(
                            RecipePantryUsage(
                                name = "${marker}Beef Broth",
                                pantryItemId = brothId,
                                quantityUsed = 200f,
                                unit = "ml",
                            ),
                            RecipePantryUsage(
                                name = "${marker}Carrots",
                                pantryItemId = carrotId,
                                quantityUsed = 100f,
                                unit = "g",
                            ),
                        ),
                    missingItemsForGrocery =
                        listOf(
                            RecipeMissingItem(name = "${marker}Fresh Thyme", quantityNeeded = 1f, unit = "BUNCH"),
                        ),
                )

            var caughtException = false
            try {
                db.withTransaction {
                    MealCookHandler.executeCookMealTransactional(
                        database = db,
                        recipe = recipe,
                        pantryRepository = repository,
                        groceryRepository = repository,
                        addMissingToGrocery = true,
                    )
                    error("Simulated catastrophic crash mid-transaction")
                }
            } catch (_: IllegalStateException) {
                caughtException = true
            }
            assertTrue(caughtException)
            verifyCrashRollbackState(brothId, carrotId)
        }

    private suspend fun verifyCrashRollbackState(
        brothId: Long,
        carrotId: Long,
    ) {
        val itemsAfter = repository.observePantryItems().first().filter { it.name.startsWith(marker) }
        val brothAfter = itemsAfter.first { it.id == brothId }
        val carrotAfter = itemsAfter.first { it.id == carrotId }
        assertEquals(1000f, brothAfter.quantity, 0.01f)
        assertEquals(500f, carrotAfter.quantity, 0.01f)

        val groceriesAfter = repository.observeGroceryItems().first().filter { it.name.startsWith(marker) }
        assertTrue(groceriesAfter.isEmpty())
    }

    @Test
    fun challengeZeroFloorSingleLargeOverdraw() =
        runBlocking {
            val spice =
                PantryItem(
                    name = "${marker}Paprika",
                    quantity = 5f,
                    unit = "TBSP",
                    storageLocation = "PANTRY",
                )
            val spiceId = repository.insertPantry(spice)

            val recipe =
                MealRecipe(
                    recipeId = "${marker}overdraw",
                    title = "Massive Overdraw Recipe",
                    itemsUsedFromStock =
                        listOf(
                            RecipePantryUsage(
                                name = "${marker}Paprika",
                                pantryItemId = spiceId,
                                quantityUsed = 100_000f,
                                unit = "TBSP",
                            ),
                        ),
                )

            val result =
                MealCookHandler.executeCookMealTransactional(
                    database = db,
                    recipe = recipe,
                    pantryRepository = repository,
                    groceryRepository = repository,
                )
            assertEquals(1, result.itemsDeductedCount)

            val itemAfter = repository.observePantryItems().first().first { it.id == spiceId }
            assertEquals(0f, itemAfter.quantity, 0.0001f)
        }

    @Test
    fun challengeZeroFloorAlreadyZeroStockDeduction() =
        runBlocking {
            val flour =
                PantryItem(
                    name = "${marker}Almond Flour",
                    quantity = 0f,
                    unit = "g",
                    storageLocation = "PANTRY",
                )
            val flourId = repository.insertPantry(flour)

            val recipe =
                MealRecipe(
                    recipeId = "${marker}already_zero",
                    title = "Zero Stock Recipe",
                    itemsUsedFromStock =
                        listOf(
                            RecipePantryUsage(
                                name = "${marker}Almond Flour",
                                pantryItemId = flourId,
                                quantityUsed = 50f,
                                unit = "g",
                            ),
                        ),
                )

            val result =
                MealCookHandler.executeCookMealTransactional(
                    database = db,
                    recipe = recipe,
                    pantryRepository = repository,
                    groceryRepository = repository,
                )
            assertEquals(1, result.itemsDeductedCount)

            val itemAfter = repository.observePantryItems().first().first { it.id == flourId }
            assertEquals(0f, itemAfter.quantity, 0.0001f)
        }

    @Test
    fun challengeZeroFloorConcurrentMultiThreadedHammer() =
        runBlocking {
            val sugar =
                PantryItem(
                    name = "${marker}Cane Sugar",
                    quantity = 100f,
                    unit = "g",
                    storageLocation = "PANTRY",
                )
            val sugarId = repository.insertPantry(sugar)

            // 20 concurrent tasks: 10 deduct 10g, 10 deduct 15g (Total attempted = 250g > 100g)
            val jobs =
                (1..20).map { index ->
                    async(Dispatchers.IO) {
                        val delta = if (index % 2 == 0) 10f else 15f
                        val recipe =
                            MealRecipe(
                                recipeId = "${marker}concurrent_$index",
                                title = "Concurrent Hammer $index",
                                itemsUsedFromStock =
                                    listOf(
                                        RecipePantryUsage(
                                            name = "${marker}Cane Sugar",
                                            pantryItemId = sugarId,
                                            quantityUsed = delta,
                                            unit = "g",
                                        ),
                                    ),
                            )
                        MealCookHandler.executeCookMealTransactional(
                            database = db,
                            recipe = recipe,
                            pantryRepository = repository,
                            groceryRepository = repository,
                        )
                    }
                }
            val results = jobs.awaitAll()
            assertEquals(20, results.size)

            // Verify zero floor strictly maintained under concurrent SQLite writes
            val finalSugar = repository.observePantryItems().first().first { it.id == sugarId }
            assertEquals(0f, finalSugar.quantity, 0.0001f)
        }

    @Test
    fun challengeConcurrentDeductionsExactQuantityAccounting() =
        runBlocking {
            val oats =
                PantryItem(
                    name = "${marker}Rolled Oats",
                    quantity = 200f,
                    unit = "g",
                    storageLocation = "PANTRY",
                )
            val oatsId = repository.insertPantry(oats)

            // 20 concurrent coroutines deducting 5g each (Total deduction = 100g)
            val jobs =
                (1..20).map { index ->
                    async(Dispatchers.IO) {
                        val recipe =
                            MealRecipe(
                                recipeId = "${marker}exact_$index",
                                title = "Exact Accounting $index",
                                itemsUsedFromStock =
                                    listOf(
                                        RecipePantryUsage(
                                            name = "${marker}Rolled Oats",
                                            pantryItemId = oatsId,
                                            quantityUsed = 5f,
                                            unit = "g",
                                        ),
                                    ),
                            )
                        MealCookHandler.executeCookMealTransactional(
                            database = db,
                            recipe = recipe,
                            pantryRepository = repository,
                            groceryRepository = repository,
                        )
                    }
                }
            jobs.awaitAll()

            // 200g - (20 * 5g) = exactly 100g. No lost updates under WAL concurrency!
            val finalOats = repository.observePantryItems().first().first { it.id == oatsId }
            assertEquals(100f, finalOats.quantity, 0.01f)
        }

    @Test
    fun challengeAutoGroceryInsertionMultipleItemsAndFiltering() =
        runBlocking {
            val recipe =
                MealRecipe(
                    recipeId = "${marker}recipe_grocery_filter",
                    title = "Curry Night",
                    itemsUsedFromStock = emptyList(),
                    missingItemsForGrocery =
                        listOf(
                            RecipeMissingItem(name = "${marker}Coconut Milk", quantityNeeded = 2f, unit = "CAN"),
                            RecipeMissingItem(name = "${marker}Curry Powder", quantityNeeded = 1.5f, unit = "TBSP"),
                            RecipeMissingItem(name = "   ", quantityNeeded = 5f, unit = "PIECE"),
                            RecipeMissingItem(name = "${marker}Cilantro", quantityNeeded = 1f, unit = null),
                        ),
                )

            val result =
                MealCookHandler.executeCookMealTransactional(
                    database = db,
                    recipe = recipe,
                    pantryRepository = repository,
                    groceryRepository = repository,
                    addMissingToGrocery = true,
                )
            assertEquals(3, result.groceryItemsAddedCount)

            val groceries = repository.observeGroceryItems().first().filter { it.name.startsWith(marker) }
            assertEquals(3, groceries.size)

            val coconut = groceries.first { it.name == "${marker}Coconut Milk" }
            assertEquals(2f, coconut.quantity, 0.01f)
            assertEquals("CAN", coconut.unit)
            assertFalse(coconut.checked)

            val cilantro = groceries.first { it.name == "${marker}Cilantro" }
            assertEquals(1f, cilantro.quantity, 0.01f)
            assertEquals(null, cilantro.unit)
            assertFalse(cilantro.checked)
        }

    @Test
    fun challengeAutoGroceryInsertionDisabledWhenFlagIsFalse() =
        runBlocking {
            val recipe =
                MealRecipe(
                    recipeId = "${marker}recipe_no_grocery",
                    title = "No Grocery Meal",
                    itemsUsedFromStock = emptyList(),
                    missingItemsForGrocery =
                        listOf(
                            RecipeMissingItem(name = "${marker}Shallots", quantityNeeded = 3f, unit = "PIECE"),
                        ),
                )

            val result =
                MealCookHandler.executeCookMealTransactional(
                    database = db,
                    recipe = recipe,
                    pantryRepository = repository,
                    groceryRepository = repository,
                    addMissingToGrocery = false,
                )
            assertEquals(0, result.groceryItemsAddedCount)

            val groceries = repository.observeGroceryItems().first().filter { it.name.startsWith(marker) }
            assertTrue(groceries.isEmpty())
        }

    @Test
    fun challengeSequentialCookDeductionsUntilZeroFloor() =
        runBlocking {
            val butter =
                PantryItem(
                    name = "${marker}Salted Butter",
                    quantity = 70f,
                    unit = "g",
                    storageLocation = "FRIDGE",
                )
            val butterId = repository.insertPantry(butter)

            fun createRecipe(step: Int) =
                MealRecipe(
                    recipeId = "${marker}seq_$step",
                    title = "Step $step",
                    itemsUsedFromStock =
                        listOf(
                            RecipePantryUsage(
                                name = "${marker}Salted Butter",
                                pantryItemId = butterId,
                                quantityUsed = 30f,
                                unit = "g",
                            ),
                        ),
                )

            // 1: 70 - 30 = 40
            MealCookHandler.executeCookMealTransactional(db, createRecipe(1), repository, repository)
            val step1 = repository.observePantryItems().first().first { it.id == butterId }
            assertEquals(40f, step1.quantity, 0.01f)

            // 2: 40 - 30 = 10
            MealCookHandler.executeCookMealTransactional(db, createRecipe(2), repository, repository)
            val step2 = repository.observePantryItems().first().first { it.id == butterId }
            assertEquals(10f, step2.quantity, 0.01f)

            // 3: 10 - 30 = 0 (clamped)
            MealCookHandler.executeCookMealTransactional(db, createRecipe(3), repository, repository)
            val step3 = repository.observePantryItems().first().first { it.id == butterId }
            assertEquals(0f, step3.quantity, 0.0001f)

            // 4: 0 - 30 = 0 (remains 0)
            MealCookHandler.executeCookMealTransactional(db, createRecipe(4), repository, repository)
            val step4 = repository.observePantryItems().first().first { it.id == butterId }
            assertEquals(0f, step4.quantity, 0.0001f)
        }

    @Test
    fun challengeVolumeConversionAcrossTspTbspCup() =
        runBlocking {
            val oil =
                PantryItem(
                    name = "${marker}Canola Oil",
                    quantity = 2f,
                    unit = "CUP",
                    storageLocation = "PANTRY",
                )
            val oilId = repository.insertPantry(oil)

            // 16 TBSP = 1 CUP. Stock becomes 1 CUP.
            val recipe1 =
                MealRecipe(
                    recipeId = "${marker}conv_1",
                    title = "Convert TBSP to CUP",
                    itemsUsedFromStock =
                        listOf(
                            RecipePantryUsage(
                                name = "${marker}Canola Oil",
                                pantryItemId = oilId,
                                quantityUsed = 16f,
                                unit = "TBSP",
                            ),
                        ),
                )
            MealCookHandler.executeCookMealTransactional(db, recipe1, repository, repository)
            val afterRecipe1 = repository.observePantryItems().first().first { it.id == oilId }
            assertEquals(1f, afterRecipe1.quantity, 0.01f)

            // 48 TSP = 1 CUP. Stock becomes 0 CUP.
            val recipe2 =
                MealRecipe(
                    recipeId = "${marker}conv_2",
                    title = "Convert TSP to CUP",
                    itemsUsedFromStock =
                        listOf(
                            RecipePantryUsage(
                                name = "${marker}Canola Oil",
                                pantryItemId = oilId,
                                quantityUsed = 48f,
                                unit = "TSP",
                            ),
                        ),
                )
            MealCookHandler.executeCookMealTransactional(db, recipe2, repository, repository)
            val afterRecipe2 = repository.observePantryItems().first().first { it.id == oilId }
            assertEquals(0f, afterRecipe2.quantity, 0.0001f)
        }
}
