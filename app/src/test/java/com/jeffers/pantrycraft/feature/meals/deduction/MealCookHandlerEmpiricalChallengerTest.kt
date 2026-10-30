package com.jeffers.pantrycraft.feature.meals.deduction

import com.jeffers.pantrycraft.data.GroceryItem
import com.jeffers.pantrycraft.data.GroceryRepository
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.PantryRepository
import com.jeffers.pantrycraft.feature.meals.model.MealRecipe
import com.jeffers.pantrycraft.feature.meals.model.RecipeMissingItem
import com.jeffers.pantrycraft.feature.meals.model.RecipePantryUsage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Empirical challenger test suite for Milestone 2 transactional deduction.
 * Verifies transaction rollback, zero-floor clamping, and auto-grocery list insertion.
 */
class MealCookHandlerEmpiricalChallengerTest {
    // =========================================================================
    // 1. Transaction Rollback & Atomicity Stress Tests
    // =========================================================================

    @Test
    fun challengeTransactionRollbackWhenSecondItemDecrementFails() =
        runBlocking {
            val rice = PantryItem(id = 1L, name = "Basmati Rice", quantity = 500f, unit = "g")
            val chicken = PantryItem(id = 2L, name = "Chicken Breast", quantity = 400f, unit = "g")
            val butter = PantryItem(id = 3L, name = "Butter", quantity = 250f, unit = "g")

            val coordinator = TransactionalHarness(listOf(rice, chicken, butter))
            coordinator.pantryRepo.failOnDecrementId = 2L // Chicken decrement fails

            val recipe =
                createRecipe(
                    itemsUsed =
                        listOf(
                            RecipePantryUsage(name = "Basmati Rice", pantryItemId = 1L, quantityUsed = 150f),
                            RecipePantryUsage(name = "Chicken Breast", pantryItemId = 2L, quantityUsed = 200f),
                            RecipePantryUsage(name = "Butter", pantryItemId = 3L, quantityUsed = 50f),
                        ),
                    missingItems =
                        listOf(
                            RecipeMissingItem(name = "Curry Powder", quantityNeeded = 1f, unit = "JAR"),
                        ),
                )

            assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    coordinator.runInTransaction { pantry, grocery ->
                        MealCookHandler.executeCookMeal(
                            recipe = recipe,
                            pantryRepository = pantry,
                            groceryRepository = grocery,
                            addMissingToGrocery = true,
                        )
                    }
                }
            }

            // Verify that failure on 2nd item completely rolled back 1st item decrement
            assertEquals(500f, coordinator.pantryRepo.getItemById(1L)?.quantity ?: 0f, 0.001f)
            assertEquals(400f, coordinator.pantryRepo.getItemById(2L)?.quantity ?: 0f, 0.001f)
            assertEquals(250f, coordinator.pantryRepo.getItemById(3L)?.quantity ?: 0f, 0.001f)
            // Verify no partial grocery items were committed
            assertEquals(0, coordinator.groceryRepo.committedItems.size)
        }

    @Test
    fun challengeTransactionRollbackWhenGroceryInsertionFails() =
        runBlocking {
            val pasta = PantryItem(id = 10L, name = "Penne Pasta", quantity = 1000f, unit = "g")
            val sauce = PantryItem(id = 20L, name = "Tomato Sauce", quantity = 500f, unit = "ML")

            val coordinator = TransactionalHarness(listOf(pasta, sauce))
            coordinator.groceryRepo.failOnInsertItemName = "Parmesan"

            val recipe =
                createRecipe(
                    itemsUsed =
                        listOf(
                            RecipePantryUsage(name = "Penne Pasta", pantryItemId = 10L, quantityUsed = 300f),
                            RecipePantryUsage(name = "Tomato Sauce", pantryItemId = 20L, quantityUsed = 250f),
                        ),
                    missingItems =
                        listOf(
                            RecipeMissingItem(name = "Oregano", quantityNeeded = 1f, unit = "JAR"),
                            RecipeMissingItem(name = "Parmesan", quantityNeeded = 100f, unit = "g"),
                        ),
                )

            assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    coordinator.runInTransaction { pantry, grocery ->
                        MealCookHandler.executeCookMeal(
                            recipe = recipe,
                            pantryRepository = pantry,
                            groceryRepository = grocery,
                            addMissingToGrocery = true,
                        )
                    }
                }
            }

            // Verify all stock reductions are rolled back
            assertEquals(1000f, coordinator.pantryRepo.getItemById(10L)?.quantity ?: 0f, 0.001f)
            assertEquals(500f, coordinator.pantryRepo.getItemById(20L)?.quantity ?: 0f, 0.001f)
            // Verify grocery committed list remains empty (Oregano was not left orphaned)
            assertEquals(0, coordinator.groceryRepo.committedItems.size)
        }

    @Test
    fun challengeTransactionCommitPersistsWhenAllOperationsSucceed() =
        runBlocking {
            val oats = PantryItem(id = 1L, name = "Rolled Oats", quantity = 500f, unit = "g")
            val milk = PantryItem(id = 2L, name = "Whole Milk", quantity = 1000f, unit = "ML")

            val coordinator = TransactionalHarness(listOf(oats, milk))

            val recipe =
                createRecipe(
                    itemsUsed =
                        listOf(
                            RecipePantryUsage(name = "Rolled Oats", pantryItemId = 1L, quantityUsed = 80f),
                            RecipePantryUsage(name = "Whole Milk", pantryItemId = 2L, quantityUsed = 200f),
                        ),
                    missingItems =
                        listOf(
                            RecipeMissingItem(name = "Honey", quantityNeeded = 1f, unit = "BOTTLE"),
                        ),
                )

            val result =
                coordinator.runInTransaction { pantry, grocery ->
                    MealCookHandler.executeCookMeal(
                        recipe = recipe,
                        pantryRepository = pantry,
                        groceryRepository = grocery,
                        addMissingToGrocery = true,
                    )
                }

            assertEquals(2, result.itemsDeductedCount)
            assertEquals(1, result.groceryItemsAddedCount)
            assertEquals(420f, coordinator.pantryRepo.getItemById(1L)?.quantity ?: 0f, 0.001f)
            assertEquals(800f, coordinator.pantryRepo.getItemById(2L)?.quantity ?: 0f, 0.001f)
            assertEquals(1, coordinator.groceryRepo.committedItems.size)
            assertEquals(
                "Honey",
                coordinator.groceryRepo.committedItems
                    .first()
                    .name,
            )
        }

    // =========================================================================
    // 2. Zero-Floor Enforcement Stress Tests
    // =========================================================================

    @Test
    fun challengeZeroFloorDeductExactQuantityReducesToZero() =
        runBlocking {
            val sugar = PantryItem(id = 1L, name = "Sugar", quantity = 250f, unit = "g")
            val harness = TransactionalHarness(listOf(sugar))
            val recipe =
                createRecipe(
                    itemsUsed = listOf(RecipePantryUsage(name = "Sugar", pantryItemId = 1L, quantityUsed = 250f)),
                )

            harness.runInTransaction { pantry, grocery ->
                MealCookHandler.executeCookMeal(recipe, pantry, grocery)
            }

            assertEquals(0f, harness.pantryRepo.getItemById(1L)?.quantity ?: -1f, 0.001f)
        }

    @Test
    fun challengeZeroFloorDeductExcessQuantityClampsCleanlyAtZero() =
        runBlocking {
            val salt = PantryItem(id = 1L, name = "Salt", quantity = 30f, unit = "g")
            val harness = TransactionalHarness(listOf(salt))
            val recipe =
                createRecipe(
                    itemsUsed = listOf(RecipePantryUsage(name = "Salt", pantryItemId = 1L, quantityUsed = 100f)),
                )

            harness.runInTransaction { pantry, grocery ->
                MealCookHandler.executeCookMeal(recipe, pantry, grocery)
            }

            val remaining = harness.pantryRepo.getItemById(1L)?.quantity ?: -1f
            assertEquals(0f, remaining, 0.001f)
            assertTrue("Stock must never drop below 0.0", remaining >= 0f)
        }

    @Test
    fun challengeZeroFloorDeductHugeExcessClampsCleanlyAtZero() =
        runBlocking {
            val pepper = PantryItem(id = 1L, name = "Black Pepper", quantity = 1.5f, unit = "g")
            val harness = TransactionalHarness(listOf(pepper))
            val recipe =
                createRecipe(
                    itemsUsed = listOf(RecipePantryUsage(name = "Black Pepper", pantryItemId = 1L, quantityUsed = 1_000_000f)),
                )

            harness.runInTransaction { pantry, grocery ->
                MealCookHandler.executeCookMeal(recipe, pantry, grocery)
            }

            val remaining = harness.pantryRepo.getItemById(1L)?.quantity ?: -1f
            assertEquals(0f, remaining, 0.001f)
        }

    @Test
    fun challengeZeroFloorDeductWhenAlreadyZeroRemainsZero() =
        runBlocking {
            val cocoa = PantryItem(id = 1L, name = "Cocoa Powder", quantity = 0f, unit = "g")
            val harness = TransactionalHarness(listOf(cocoa))
            val recipe =
                createRecipe(
                    itemsUsed = listOf(RecipePantryUsage(name = "Cocoa Powder", pantryItemId = 1L, quantityUsed = 25f)),
                )

            harness.runInTransaction { pantry, grocery ->
                MealCookHandler.executeCookMeal(recipe, pantry, grocery)
            }

            assertEquals(0f, harness.pantryRepo.getItemById(1L)?.quantity ?: -1f, 0.001f)
        }

    @Test
    fun challengeZeroFloorMultipleItemsVaryingDepletion() =
        runBlocking {
            val itemA = PantryItem(id = 1L, name = "A", quantity = 50f, unit = "g")
            val itemB = PantryItem(id = 2L, name = "B", quantity = 20f, unit = "g")
            val itemC = PantryItem(id = 3L, name = "C", quantity = 0f, unit = "g")
            val harness = TransactionalHarness(listOf(itemA, itemB, itemC))

            val recipe =
                createRecipe(
                    itemsUsed =
                        listOf(
                            RecipePantryUsage(name = "A", pantryItemId = 1L, quantityUsed = 30f),
                            RecipePantryUsage(name = "B", pantryItemId = 2L, quantityUsed = 80f),
                            RecipePantryUsage(name = "C", pantryItemId = 3L, quantityUsed = 10f),
                        ),
                )

            harness.runInTransaction { pantry, grocery ->
                MealCookHandler.executeCookMeal(recipe, pantry, grocery)
            }

            assertEquals(20f, harness.pantryRepo.getItemById(1L)?.quantity ?: -1f, 0.001f)
            assertEquals(0f, harness.pantryRepo.getItemById(2L)?.quantity ?: -1f, 0.001f)
            assertEquals(0f, harness.pantryRepo.getItemById(3L)?.quantity ?: -1f, 0.001f)
        }

    @Test
    fun challengeZeroFloorInvalidDeltasDoNotModifyStock() =
        runBlocking {
            val oliveOil = PantryItem(id = 1L, name = "Olive Oil", quantity = 500f, unit = "ML")
            val harness = TransactionalHarness(listOf(oliveOil))

            val recipe =
                createRecipe(
                    itemsUsed =
                        listOf(
                            RecipePantryUsage(name = "Olive Oil", pantryItemId = 1L, quantityUsed = 0f),
                            RecipePantryUsage(name = "Olive Oil", pantryItemId = 1L, quantityUsed = -15f),
                            RecipePantryUsage(name = "Olive Oil", pantryItemId = 1L, quantityUsed = Float.NaN),
                            RecipePantryUsage(name = "Olive Oil", pantryItemId = 1L, quantityUsed = Float.POSITIVE_INFINITY),
                        ),
                )

            val result =
                harness.runInTransaction { pantry, grocery ->
                    MealCookHandler.executeCookMeal(recipe, pantry, grocery)
                }

            assertEquals(0, result.itemsDeductedCount)
            assertEquals(500f, harness.pantryRepo.getItemById(1L)?.quantity ?: -1f, 0.001f)
        }

    // =========================================================================
    // 3. Auto-Grocery List Insertion Tests
    // =========================================================================

    @Test
    fun challengeAutoGroceryInsertedWhenAddMissingIsTrue() =
        runBlocking {
            val harness = TransactionalHarness()
            val recipe =
                createRecipe(
                    missingItems =
                        listOf(
                            RecipeMissingItem(name = "Dijon Mustard", quantityNeeded = 1f, unit = "JAR"),
                            RecipeMissingItem(name = "Garlic Cloves", quantityNeeded = 4f, unit = "PIECE"),
                        ),
                )

            val result =
                harness.runInTransaction { pantry, grocery ->
                    MealCookHandler.executeCookMeal(
                        recipe = recipe,
                        pantryRepository = pantry,
                        groceryRepository = grocery,
                        addMissingToGrocery = true,
                    )
                }

            assertEquals(2, result.groceryItemsAddedCount)
            val inserted = harness.groceryRepo.committedItems
            assertEquals(2, inserted.size)
            assertEquals("Dijon Mustard", inserted[0].name)
            assertEquals(1f, inserted[0].quantity, 0.001f)
            assertEquals("JAR", inserted[0].unit)
            assertFalse("Auto-inserted grocery must be unchecked", inserted[0].checked)

            assertEquals("Garlic Cloves", inserted[1].name)
            assertEquals(4f, inserted[1].quantity, 0.001f)
            assertFalse(inserted[1].checked)
        }

    @Test
    fun challengeAutoGrocerySkippedWhenAddMissingIsFalse() =
        runBlocking {
            val harness = TransactionalHarness()
            val recipe =
                createRecipe(
                    missingItems =
                        listOf(
                            RecipeMissingItem(name = "Soy Sauce", quantityNeeded = 1f, unit = "BOTTLE"),
                            RecipeMissingItem(name = "Ginger Root", quantityNeeded = 1f, unit = "PIECE"),
                        ),
                )

            val result =
                harness.runInTransaction { pantry, grocery ->
                    MealCookHandler.executeCookMeal(
                        recipe = recipe,
                        pantryRepository = pantry,
                        groceryRepository = grocery,
                        addMissingToGrocery = false,
                    )
                }

            assertEquals(0, result.groceryItemsAddedCount)
            assertEquals(0, harness.groceryRepo.committedItems.size)
        }

    @Test
    fun challengeAutoGroceryFiltersBlankItemNames() =
        runBlocking {
            val harness = TransactionalHarness()
            val recipe =
                createRecipe(
                    missingItems =
                        listOf(
                            RecipeMissingItem(name = "   ", quantityNeeded = 2f, unit = "KG"),
                            RecipeMissingItem(name = "", quantityNeeded = 1f, unit = "BOX"),
                            RecipeMissingItem(name = "Coriander", quantityNeeded = 1f, unit = "BUNCH"),
                        ),
                )

            val result =
                harness.runInTransaction { pantry, grocery ->
                    MealCookHandler.executeCookMeal(
                        recipe = recipe,
                        pantryRepository = pantry,
                        groceryRepository = grocery,
                        addMissingToGrocery = true,
                    )
                }

            assertEquals(1, result.groceryItemsAddedCount)
            assertEquals(1, harness.groceryRepo.committedItems.size)
            assertEquals("Coriander", harness.groceryRepo.committedItems[0].name)
        }

    @Test
    fun challengeAutoGroceryEmptyListYieldsZeroInserts() =
        runBlocking {
            val harness = TransactionalHarness()
            val recipe = createRecipe(missingItems = emptyList())

            val result =
                harness.runInTransaction { pantry, grocery ->
                    MealCookHandler.executeCookMeal(
                        recipe = recipe,
                        pantryRepository = pantry,
                        groceryRepository = grocery,
                        addMissingToGrocery = true,
                    )
                }

            assertEquals(0, result.groceryItemsAddedCount)
            assertEquals(0, harness.groceryRepo.committedItems.size)
        }

    // =========================================================================
    // 4. Volume Conversion & Edge Case Deductions
    // =========================================================================

    @Test
    fun challengeVolumeConversionsAcrossAllStandardUnits() =
        runBlocking {
            verifyDeduction(initial = 200f, stockUnit = "ML", used = 4f, recipeUnit = "FL_OZ", expectedRemaining = 80f)
            verifyDeduction(initial = 16f, stockUnit = "FL_OZ", used = 1f, recipeUnit = "CUP", expectedRemaining = 8f)
            verifyDeduction(initial = 10f, stockUnit = "TSP", used = 2f, recipeUnit = "TBSP", expectedRemaining = 4f)
            verifyDeduction(initial = 5f, stockUnit = "CUP", used = 480f, recipeUnit = "ML", expectedRemaining = 3f)
            verifyDeduction(initial = 500f, stockUnit = "ML", used = 0.25f, recipeUnit = "L", expectedRemaining = 250f)
        }

    @Test
    fun challengeSoftDeletedItemNeverDeducted() =
        runBlocking {
            val activeEggs = PantryItem(id = 1L, name = "Eggs", quantity = 12f, unit = "COUNT", isDeleted = false)
            val deletedEggs = PantryItem(id = 2L, name = "Eggs", quantity = 6f, unit = "COUNT", isDeleted = true)
            val harness = TransactionalHarness(listOf(activeEggs, deletedEggs))

            val recipe =
                createRecipe(
                    itemsUsed = listOf(RecipePantryUsage(name = "Eggs", pantryItemId = 2L, quantityUsed = 2f)),
                )

            val result =
                harness.runInTransaction { pantry, grocery ->
                    MealCookHandler.executeCookMeal(recipe, pantry, grocery)
                }

            assertEquals(0, result.itemsDeductedCount)
            assertEquals(6f, harness.pantryRepo.getItemById(2L)?.quantity ?: 0f, 0.001f)
            assertEquals(12f, harness.pantryRepo.getItemById(1L)?.quantity ?: 0f, 0.001f)
        }

    private suspend fun verifyDeduction(
        initial: Float,
        stockUnit: String,
        used: Float,
        recipeUnit: String,
        expectedRemaining: Float,
    ) {
        val item = PantryItem(id = 100L, name = "Liquid Ingredient", quantity = initial, unit = stockUnit)
        val harness = TransactionalHarness(listOf(item))
        val recipe =
            createRecipe(
                itemsUsed =
                    listOf(
                        RecipePantryUsage(
                            name = "Liquid Ingredient",
                            pantryItemId = 100L,
                            quantityUsed = used,
                            unit = recipeUnit,
                        ),
                    ),
            )

        harness.runInTransaction { pantry, grocery ->
            MealCookHandler.executeCookMeal(recipe, pantry, grocery)
        }

        assertEquals(
            expectedRemaining,
            harness.pantryRepo.getItemById(100L)?.quantity ?: -1f,
            0.01f,
        )
    }

    private fun createRecipe(
        itemsUsed: List<RecipePantryUsage> = emptyList(),
        missingItems: List<RecipeMissingItem> = emptyList(),
    ): MealRecipe =
        MealRecipe(
            recipeId = "rec_challenge",
            title = "Challenger Recipe",
            targetPortions = 2,
            itemsUsedFromStock = itemsUsed,
            missingItemsForGrocery = missingItems,
        )
}

/**
 * Transactional test harness that coordinates atomic operations between
 * pantry and grocery repositories, validating rollback on exception.
 */
private class TransactionalHarness(
    initialPantry: List<PantryItem> = emptyList(),
) {
    val pantryRepo = ChallengerPantryRepository(initialPantry)
    val groceryRepo = ChallengerGroceryRepository()

    suspend fun <T> runInTransaction(block: suspend (PantryRepository, GroceryRepository) -> T): T {
        pantryRepo.beginTransaction()
        groceryRepo.beginTransaction()
        return try {
            val result = block(pantryRepo, groceryRepo)
            pantryRepo.commitTransaction()
            groceryRepo.commitTransaction()
            result
        } catch (e: Throwable) {
            pantryRepo.rollbackTransaction()
            groceryRepo.rollbackTransaction()
            throw e
        }
    }
}

private class ChallengerPantryRepository(
    initial: List<PantryItem>,
) : PantryRepository {
    private val persistentItems = initial.map { it.copy() }.toMutableList()
    private val stagingItems = mutableListOf<PantryItem>()
    private var inTx = false

    var failOnDecrementId: Long? = null

    fun beginTransaction() {
        inTx = true
        stagingItems.clear()
        stagingItems.addAll(persistentItems.map { it.copy() })
    }

    fun commitTransaction() {
        persistentItems.clear()
        persistentItems.addAll(stagingItems.map { it.copy() })
        stagingItems.clear()
        inTx = false
    }

    fun rollbackTransaction() {
        stagingItems.clear()
        inTx = false
    }

    private fun currentList(): MutableList<PantryItem> = if (inTx) stagingItems else persistentItems

    fun getItemById(id: Long): PantryItem? = currentList().firstOrNull { it.id == id }

    override fun observePantryItems(): Flow<List<PantryItem>> = MutableStateFlow(currentList().toList())

    override fun searchPantryItems(query: String): Flow<List<PantryItem>> = MutableStateFlow(currentList().toList())

    override suspend fun insertPantry(item: PantryItem): Long = 1L

    override suspend fun updatePantry(item: PantryItem) { /* no-op */ }

    override suspend fun decrementQuantity(
        item: PantryItem,
        delta: Float,
    ) {
        require(delta > 0f && delta.isFinite()) { "Delta must be finite and positive" }
        if (failOnDecrementId != null && item.id == failOnDecrementId) {
            error("Simulated SQLite failure during decrement of item id=${item.id}")
        }
        val list = currentList()
        val index = list.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            val existing = list[index]
            val clamped = (existing.quantity - delta).coerceAtLeast(0f)
            list[index] = existing.copy(quantity = clamped)
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

private class ChallengerGroceryRepository : GroceryRepository {
    private val persistentGrocery = mutableListOf<GroceryItem>()
    private val stagingGrocery = mutableListOf<GroceryItem>()
    private var inTx = false

    var failOnInsertItemName: String? = null

    val committedItems: List<GroceryItem> get() = persistentGrocery.toList()

    fun beginTransaction() {
        inTx = true
        stagingGrocery.clear()
        stagingGrocery.addAll(persistentGrocery.map { it.copy() })
    }

    fun commitTransaction() {
        persistentGrocery.clear()
        persistentGrocery.addAll(stagingGrocery.map { it.copy() })
        stagingGrocery.clear()
        inTx = false
    }

    fun rollbackTransaction() {
        stagingGrocery.clear()
        inTx = false
    }

    private fun currentList(): MutableList<GroceryItem> = if (inTx) stagingGrocery else persistentGrocery

    override fun observeGroceryItems(): Flow<List<GroceryItem>> = MutableStateFlow(currentList().toList())

    override suspend fun insertGrocery(item: GroceryItem): Long {
        if (failOnInsertItemName != null && item.name.equals(failOnInsertItemName, ignoreCase = true)) {
            error("Simulated SQLite insertion failure for grocery item '${item.name}'")
        }
        currentList().add(item)
        return currentList().size.toLong()
    }

    override suspend fun updateGrocery(item: GroceryItem) { /* no-op */ }

    override suspend fun deleteGrocery(item: GroceryItem) { /* no-op */ }

    override suspend fun clearCheckedGrocery() { /* no-op */ }

    override suspend fun restockCheckedGroceryToPantry(): Int = 0

    override fun reclaimTemporaryMedia(): Int = 0
}
