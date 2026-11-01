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
import org.junit.Test

class MealCookHandlerTest {
    @Test
    fun executeCookMealDeductsPantryAndInsertsGrocery() =
        runBlocking {
            val pantryItem = PantryItem(id = 100L, name = "Rice", quantity = 500f, unit = "g")
            val fakePantry = FakePantryRepository(listOf(pantryItem))
            val fakeGrocery = FakeGroceryRepository()

            val recipe =
                createRecipe(
                    itemsUsed =
                        listOf(
                            RecipePantryUsage(
                                name = "Rice",
                                pantryItemId = 100L,
                                quantityUsed = 150f,
                                preventsWaste = true,
                            ),
                        ),
                    missingItems =
                        listOf(
                            RecipeMissingItem(name = "Sesame Seeds", quantityNeeded = 1f, unit = "JAR"),
                        ),
                )

            val result =
                MealCookHandler.executeCookMeal(
                    recipe = recipe,
                    pantryRepository = fakePantry,
                    groceryRepository = fakeGrocery,
                    addMissingToGrocery = true,
                )

            assertEquals(1, result.itemsDeductedCount)
            assertEquals(1, result.foodWastePreventedCount)
            assertEquals(1, result.groceryItemsAddedCount)
            assertEquals(150f, fakePantry.lastDecrementedDelta ?: 0f, 0.01f)
            assertEquals("Sesame Seeds", fakeGrocery.insertedItems.first().name)
        }

    @Test
    fun transactionCommitPersistsAllDeductionsAndGroceries() =
        runBlocking {
            val rice = PantryItem(id = 1L, name = "Rice", quantity = 500f, unit = "g")
            val fakePantry = FakePantryRepository(listOf(rice))
            val fakeGrocery = FakeGroceryRepository()

            val recipe =
                createRecipe(
                    itemsUsed =
                        listOf(
                            RecipePantryUsage(name = "Rice", pantryItemId = 1L, quantityUsed = 150f),
                        ),
                    missingItems =
                        listOf(
                            RecipeMissingItem(name = "Olive Oil", quantityNeeded = 1f, unit = "BOTTLE"),
                        ),
                )

            fakePantry.beginTransaction()
            fakeGrocery.beginTransaction()
            val result =
                MealCookHandler.executeCookMeal(
                    recipe = recipe,
                    pantryRepository = fakePantry,
                    groceryRepository = fakeGrocery,
                    addMissingToGrocery = true,
                )
            fakePantry.commitTransaction()
            fakeGrocery.commitTransaction()

            assertEquals(1, result.itemsDeductedCount)
            assertEquals(1, result.groceryItemsAddedCount)
            assertEquals(350f, fakePantry.getItem("Rice")?.quantity ?: 0f, 0.01f)
            assertEquals(1, fakeGrocery.insertedItems.size)
            assertEquals("Olive Oil", fakeGrocery.insertedItems.first().name)
        }

    @Test
    fun transactionRollbackRevertsDeductionWhenGroceryFails() =
        runBlocking {
            val rice = PantryItem(id = 1L, name = "Rice", quantity = 500f, unit = "g")
            val fakePantry = FakePantryRepository(listOf(rice))
            val fakeGrocery = FakeGroceryRepository().apply { failOnInsert = true }

            val recipe =
                createRecipe(
                    itemsUsed =
                        listOf(
                            RecipePantryUsage(name = "Rice", pantryItemId = 1L, quantityUsed = 150f),
                        ),
                    missingItems =
                        listOf(
                            RecipeMissingItem(name = "Salt", quantityNeeded = 1f, unit = "BOX"),
                        ),
                )

            fakePantry.beginTransaction()
            fakeGrocery.beginTransaction()
            assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    try {
                        MealCookHandler.executeCookMeal(
                            recipe = recipe,
                            pantryRepository = fakePantry,
                            groceryRepository = fakeGrocery,
                            addMissingToGrocery = true,
                        )
                        fakePantry.commitTransaction()
                        fakeGrocery.commitTransaction()
                    } catch (e: Exception) {
                        fakePantry.rollbackTransaction()
                        fakeGrocery.rollbackTransaction()
                        throw e
                    }
                }
            }

            assertEquals(500f, fakePantry.getItem("Rice")?.quantity ?: 0f, 0.01f)
            assertEquals(0, fakeGrocery.insertedItems.size)
        }

    @Test
    fun transactionRollbackRevertsDeductionsWhenSecondItemFails() =
        runBlocking {
            val rice = PantryItem(id = 1L, name = "Rice", quantity = 500f, unit = "g")
            val milk = PantryItem(id = 2L, name = "Milk", quantity = 1000f, unit = "ML")
            val fakePantry =
                FakePantryRepository(listOf(rice, milk)).apply {
                    failOnDecrementItemName = "Milk"
                }
            val fakeGrocery = FakeGroceryRepository()

            val recipe =
                createRecipe(
                    itemsUsed =
                        listOf(
                            RecipePantryUsage(name = "Rice", pantryItemId = 1L, quantityUsed = 150f),
                            RecipePantryUsage(name = "Milk", pantryItemId = 2L, quantityUsed = 500f),
                        ),
                    missingItems =
                        listOf(
                            RecipeMissingItem(name = "Sugar", quantityNeeded = 1f, unit = "BAG"),
                        ),
                )

            fakePantry.beginTransaction()
            fakeGrocery.beginTransaction()
            assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    try {
                        MealCookHandler.executeCookMeal(
                            recipe = recipe,
                            pantryRepository = fakePantry,
                            groceryRepository = fakeGrocery,
                            addMissingToGrocery = true,
                        )
                        fakePantry.commitTransaction()
                        fakeGrocery.commitTransaction()
                    } catch (e: Exception) {
                        fakePantry.rollbackTransaction()
                        fakeGrocery.rollbackTransaction()
                        throw e
                    }
                }
            }

            assertEquals(500f, fakePantry.getItem("Rice")?.quantity ?: 0f, 0.01f)
            assertEquals(1000f, fakePantry.getItem("Milk")?.quantity ?: 0f, 0.01f)
            assertEquals(0, fakeGrocery.insertedItems.size)
        }

    @Test
    fun decrementExactQuantityReducesStockToZero() =
        runBlocking {
            val flour = PantryItem(id = 1L, name = "Flour", quantity = 100f, unit = "g")
            val fakePantry = FakePantryRepository(listOf(flour))
            val recipe =
                createRecipe(
                    itemsUsed =
                        listOf(
                            RecipePantryUsage(name = "Flour", pantryItemId = 1L, quantityUsed = 100f),
                        ),
                )

            val result = MealCookHandler.executeCookMeal(recipe, fakePantry, FakeGroceryRepository())

            assertEquals(1, result.itemsDeductedCount)
            assertEquals(0f, fakePantry.getItem("Flour")?.quantity ?: -1f, 0.01f)
        }

    @Test
    fun decrementExcessQuantityClampsStockAtZero() =
        runBlocking {
            val sugar = PantryItem(id = 1L, name = "Sugar", quantity = 50f, unit = "g")
            val fakePantry = FakePantryRepository(listOf(sugar))
            val recipe =
                createRecipe(
                    itemsUsed =
                        listOf(
                            RecipePantryUsage(name = "Sugar", pantryItemId = 1L, quantityUsed = 150f),
                        ),
                )

            val result = MealCookHandler.executeCookMeal(recipe, fakePantry, FakeGroceryRepository())

            assertEquals(1, result.itemsDeductedCount)
            assertEquals(0f, fakePantry.getItem("Sugar")?.quantity ?: -1f, 0.01f)
        }

    @Test
    fun decrementWhenStockAlreadyZeroRemainsZero() =
        runBlocking {
            val salt = PantryItem(id = 1L, name = "Salt", quantity = 0f, unit = "g")
            val fakePantry = FakePantryRepository(listOf(salt))
            val recipe =
                createRecipe(
                    itemsUsed =
                        listOf(
                            RecipePantryUsage(name = "Salt", pantryItemId = 1L, quantityUsed = 10f),
                        ),
                )

            val result = MealCookHandler.executeCookMeal(recipe, fakePantry, FakeGroceryRepository())

            assertEquals(1, result.itemsDeductedCount)
            assertEquals(0f, fakePantry.getItem("Salt")?.quantity ?: -1f, 0.01f)
        }

    @Test
    fun decrementMultipleItemsWithPartialAndZeroFloorDepletion() =
        runBlocking {
            val itemA = PantryItem(id = 1L, name = "Item A", quantity = 100f, unit = "g")
            val itemB = PantryItem(id = 2L, name = "Item B", quantity = 20f, unit = "g")
            val fakePantry = FakePantryRepository(listOf(itemA, itemB))
            val recipe =
                createRecipe(
                    itemsUsed =
                        listOf(
                            RecipePantryUsage(name = "Item A", pantryItemId = 1L, quantityUsed = 40f),
                            RecipePantryUsage(name = "Item B", pantryItemId = 2L, quantityUsed = 50f),
                        ),
                )

            val result = MealCookHandler.executeCookMeal(recipe, fakePantry, FakeGroceryRepository())

            assertEquals(2, result.itemsDeductedCount)
            assertEquals(60f, fakePantry.getItem("Item A")?.quantity ?: 0f, 0.01f)
            assertEquals(0f, fakePantry.getItem("Item B")?.quantity ?: -1f, 0.01f)
        }

    @Test
    fun unitConversionRecipeTspToPantryMl() =
        runBlocking {
            verifyVolumeConversion(VolumeSpec(50f, "ML", 2f, "TSP", 10f))
        }

    @Test
    fun unitConversionRecipeTbspToPantryMl() =
        runBlocking {
            verifyVolumeConversion(VolumeSpec(100f, "ML", 3f, "TBSP", 45f))
        }

    @Test
    fun unitConversionRecipeCupToPantryMl() =
        runBlocking {
            verifyVolumeConversion(VolumeSpec(500f, "ML", 0.5f, "CUP", 120f))
        }

    @Test
    fun unitConversionRecipeLiterToPantryMl() =
        runBlocking {
            verifyVolumeConversion(VolumeSpec(1000f, "ML", 0.5f, "L", 500f))
        }

    @Test
    fun unitConversionRecipeMlToPantryCup() =
        runBlocking {
            verifyVolumeConversion(VolumeSpec(2f, "CUP", 240f, "ML", 1f))
        }

    @Test
    fun unitConversionRecipeMlToPantryTbsp() =
        runBlocking {
            verifyVolumeConversion(VolumeSpec(5f, "TBSP", 30f, "ML", 2f))
        }

    @Test
    fun unitConversionRecipeMlToPantryTsp() =
        runBlocking {
            verifyVolumeConversion(VolumeSpec(10f, "TSP", 15f, "ML", 3f))
        }

    @Test
    fun unitConversionRecipeMlToPantryLiter() =
        runBlocking {
            verifyVolumeConversion(VolumeSpec(1f, "L", 500f, "ML", 0.5f))
        }

    @Test
    fun unitConversionRecipeTbspToPantryTsp() =
        runBlocking {
            verifyVolumeConversion(VolumeSpec(10f, "TSP", 2f, "TBSP", 6f))
        }

    @Test
    fun unitConversionRecipeCupToPantryTbsp() =
        runBlocking {
            verifyVolumeConversion(VolumeSpec(8f, "TBSP", 0.25f, "CUP", 4f))
        }

    @Test
    fun unitConversionRecipeFlOzToPantryMl() =
        runBlocking {
            verifyVolumeConversion(VolumeSpec(100f, "ML", 2f, "FL_OZ", 60f))
        }

    @Test
    fun unitConversionRecipeMlToPantryFlOz() =
        runBlocking {
            verifyVolumeConversion(VolumeSpec(10f, "FL_OZ", 90f, "ML", 3f))
        }

    @Test
    fun unitConversionRecipeFlOzToPantryCup() =
        runBlocking {
            verifyVolumeConversion(VolumeSpec(2f, "CUP", 8f, "FL_OZ", 1f))
        }

    @Test
    fun unitConversionRecipeCupToPantryFlOz() =
        runBlocking {
            verifyVolumeConversion(VolumeSpec(16f, "FL_OZ", 1f, "CUP", 8f))
        }

    @Test
    fun unitConversionIdenticalUnitsDoesNotRescale() =
        runBlocking {
            verifyVolumeConversion(VolumeSpec(500f, "ML", 250f, "ML", 250f))
        }

    @Test
    fun unitConversionCaseInsensitiveMatching() =
        runBlocking {
            verifyVolumeConversion(VolumeSpec(500f, "ml", 1f, "CUP", 240f))
        }

    @Test
    fun incompatibleUnitConversionFallsBackToRawQuantity() =
        runBlocking {
            verifyVolumeConversion(VolumeSpec(500f, "ML", 100f, "g", 100f))
        }

    @Test
    fun nullUnitInRecipeFallsBackToRawQuantity() =
        runBlocking {
            val sugar = PantryItem(id = 1L, name = "Sugar", quantity = 500f, unit = "g")
            val fakePantry = FakePantryRepository(listOf(sugar))
            val recipe =
                createRecipe(
                    itemsUsed =
                        listOf(
                            RecipePantryUsage(name = "Sugar", pantryItemId = 1L, quantityUsed = 150f, unit = null),
                        ),
                )

            MealCookHandler.executeCookMeal(recipe, fakePantry, FakeGroceryRepository())

            assertEquals(350f, fakePantry.getItem("Sugar")?.quantity ?: 0f, 0.01f)
            assertEquals(150f, fakePantry.lastDecrementedDelta ?: 0f, 0.01f)
        }

    @Test
    fun nullUnitInPantryFallsBackToRawQuantity() =
        runBlocking {
            val bread = PantryItem(id = 1L, name = "Bread", quantity = 10f, unit = null)
            val fakePantry = FakePantryRepository(listOf(bread))
            val recipe =
                createRecipe(
                    itemsUsed =
                        listOf(
                            RecipePantryUsage(name = "Bread", pantryItemId = 1L, quantityUsed = 2f, unit = "SLICES"),
                        ),
                )

            MealCookHandler.executeCookMeal(recipe, fakePantry, FakeGroceryRepository())

            assertEquals(8f, fakePantry.getItem("Bread")?.quantity ?: 0f, 0.01f)
            assertEquals(2f, fakePantry.lastDecrementedDelta ?: 0f, 0.01f)
        }

    @Test
    fun missingItemsInsertedWhenAddMissingIsTrue() =
        runBlocking {
            val fakeGrocery = FakeGroceryRepository()
            val recipe =
                createRecipe(
                    missingItems =
                        listOf(
                            RecipeMissingItem(name = "Salt", quantityNeeded = 1f, unit = "BOX"),
                            RecipeMissingItem(name = "Pepper", quantityNeeded = 2f, unit = "JAR"),
                        ),
                )

            val result =
                MealCookHandler.executeCookMeal(
                    recipe = recipe,
                    pantryRepository = FakePantryRepository(),
                    groceryRepository = fakeGrocery,
                    addMissingToGrocery = true,
                )

            assertEquals(2, result.groceryItemsAddedCount)
            assertEquals(2, fakeGrocery.insertedItems.size)
            assertEquals("Salt", fakeGrocery.insertedItems[0].name)
            assertFalse(fakeGrocery.insertedItems[0].checked)
            assertEquals("Pepper", fakeGrocery.insertedItems[1].name)
            assertFalse(fakeGrocery.insertedItems[1].checked)
        }

    @Test
    fun missingItemsSkippedWhenAddMissingIsFalse() =
        runBlocking {
            val fakeGrocery = FakeGroceryRepository()
            val recipe =
                createRecipe(
                    missingItems =
                        listOf(
                            RecipeMissingItem(name = "Salt", quantityNeeded = 1f, unit = "BOX"),
                        ),
                )

            val result =
                MealCookHandler.executeCookMeal(
                    recipe = recipe,
                    pantryRepository = FakePantryRepository(),
                    groceryRepository = fakeGrocery,
                    addMissingToGrocery = false,
                )

            assertEquals(0, result.groceryItemsAddedCount)
            assertEquals(0, fakeGrocery.insertedItems.size)
        }

    @Test
    fun emptyMissingItemsResultsInZeroGroceryInserts() =
        runBlocking {
            val fakeGrocery = FakeGroceryRepository()
            val recipe = createRecipe(missingItems = emptyList())

            val result =
                MealCookHandler.executeCookMeal(
                    recipe = recipe,
                    pantryRepository = FakePantryRepository(),
                    groceryRepository = fakeGrocery,
                    addMissingToGrocery = true,
                )

            assertEquals(0, result.groceryItemsAddedCount)
            assertEquals(0, fakeGrocery.insertedItems.size)
        }

    @Test
    fun foodWastePreventionCountCorrectlyTracked() =
        runBlocking {
            val carrot = PantryItem(id = 1L, name = "Carrot", quantity = 5f, unit = "PIECE")
            val celery = PantryItem(id = 2L, name = "Celery", quantity = 4f, unit = "PIECE")
            val onion = PantryItem(id = 3L, name = "Onion", quantity = 3f, unit = "PIECE")
            val fakePantry = FakePantryRepository(listOf(carrot, celery, onion))

            val recipe =
                createRecipe(
                    itemsUsed =
                        listOf(
                            RecipePantryUsage(name = "Carrot", pantryItemId = 1L, quantityUsed = 2f, preventsWaste = true),
                            RecipePantryUsage(name = "Celery", pantryItemId = 2L, quantityUsed = 1f, preventsWaste = true),
                            RecipePantryUsage(name = "Onion", pantryItemId = 3L, quantityUsed = 1f, preventsWaste = false),
                        ),
                )

            val result = MealCookHandler.executeCookMeal(recipe, fakePantry, FakeGroceryRepository())

            assertEquals(3, result.itemsDeductedCount)
            assertEquals(2, result.foodWastePreventedCount)
        }

    @Test
    fun zeroOrNegativeUsageQuantityIgnored() =
        runBlocking {
            val apple = PantryItem(id = 1L, name = "Apple", quantity = 10f, unit = "COUNT")
            val fakePantry = FakePantryRepository(listOf(apple))

            val recipe =
                createRecipe(
                    itemsUsed =
                        listOf(
                            RecipePantryUsage(name = "Apple", pantryItemId = 1L, quantityUsed = 0f),
                            RecipePantryUsage(name = "Apple", pantryItemId = 1L, quantityUsed = -5f),
                        ),
                )

            val result = MealCookHandler.executeCookMeal(recipe, fakePantry, FakeGroceryRepository())

            assertEquals(0, result.itemsDeductedCount)
            assertEquals(10f, fakePantry.getItem("Apple")?.quantity ?: 0f, 0.01f)
        }

    @Test
    fun pantryEmptyCausesNoDeductionsWithoutCrashing() =
        runBlocking {
            val fakeGrocery = FakeGroceryRepository()
            val recipe =
                createRecipe(
                    itemsUsed = listOf(RecipePantryUsage(name = "Pasta", pantryItemId = 1L, quantityUsed = 100f)),
                    missingItems = listOf(RecipeMissingItem(name = "Pasta", quantityNeeded = 100f, unit = "g")),
                )

            val result =
                MealCookHandler.executeCookMeal(
                    recipe = recipe,
                    pantryRepository = FakePantryRepository(),
                    groceryRepository = fakeGrocery,
                    addMissingToGrocery = true,
                )

            assertEquals(0, result.itemsDeductedCount)
            assertEquals(1, result.groceryItemsAddedCount)
        }

    @Test
    fun multipleMatchingItemsDisambiguatedByPantryItemId() =
        runBlocking {
            val eggsA = PantryItem(id = 101L, name = "Eggs", quantity = 6f, unit = "COUNT")
            val eggsB = PantryItem(id = 102L, name = "Eggs", quantity = 12f, unit = "COUNT")
            val fakePantry = FakePantryRepository(listOf(eggsA, eggsB))

            val recipe =
                createRecipe(
                    itemsUsed = listOf(RecipePantryUsage(name = "Eggs", pantryItemId = 102L, quantityUsed = 2f)),
                )

            val result = MealCookHandler.executeCookMeal(recipe, fakePantry, FakeGroceryRepository())

            assertEquals(1, result.itemsDeductedCount)
            assertEquals(6f, fakePantry.getItemById(101L)?.quantity ?: 0f, 0.01f)
            assertEquals(10f, fakePantry.getItemById(102L)?.quantity ?: 0f, 0.01f)
        }

    @Test
    fun matchingByNameWhenPantryItemIdIsNull() =
        runBlocking {
            val cheese = PantryItem(id = 50L, name = "Cheddar Cheese", quantity = 200f, unit = "g")
            val fakePantry = FakePantryRepository(listOf(cheese))

            val recipe =
                createRecipe(
                    itemsUsed =
                        listOf(
                            RecipePantryUsage(name = "  cheddar cheese  ", pantryItemId = null, quantityUsed = 50f),
                        ),
                )

            val result = MealCookHandler.executeCookMeal(recipe, fakePantry, FakeGroceryRepository())

            assertEquals(1, result.itemsDeductedCount)
            assertEquals(150f, fakePantry.getItem("Cheddar Cheese")?.quantity ?: 0f, 0.01f)
        }

    @Test
    fun pantryItemIdNotFoundDoesNotDeduct() =
        runBlocking {
            val milk = PantryItem(id = 1L, name = "Milk", quantity = 1000f, unit = "ML")
            val fakePantry = FakePantryRepository(listOf(milk))

            val recipe =
                createRecipe(
                    itemsUsed = listOf(RecipePantryUsage(name = "Milk", pantryItemId = 9999L, quantityUsed = 200f)),
                )

            val result = MealCookHandler.executeCookMeal(recipe, fakePantry, FakeGroceryRepository())

            assertEquals(0, result.itemsDeductedCount)
            assertEquals(1000f, fakePantry.getItem("Milk")?.quantity ?: 0f, 0.01f)
        }

    @Test
    fun recipeScalePortionsEnforcesBounds() {
        val recipe = createRecipe()

        assertThrows(IllegalArgumentException::class.java) {
            recipe.scaleToPortions(0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            recipe.scaleToPortions(13)
        }

        val scaledMin = recipe.scaleToPortions(1)
        assertEquals(1, scaledMin.targetPortions)
        val scaledMax = recipe.scaleToPortions(12)
        assertEquals(12, scaledMax.targetPortions)
    }

    @Test
    fun recipeScaledPortionsCorrectlyDeductsMultipliedQuantities() =
        runBlocking {
            val rice = PantryItem(id = 1L, name = "Rice", quantity = 500f, unit = "g")
            val fakePantry = FakePantryRepository(listOf(rice))

            val baseRecipe =
                createRecipe(
                    targetPortions = 2,
                    itemsUsed = listOf(RecipePantryUsage(name = "Rice", pantryItemId = 1L, quantityUsed = 100f)),
                )
            val scaledRecipe = baseRecipe.scaleToPortions(4)

            val result = MealCookHandler.executeCookMeal(scaledRecipe, fakePantry, FakeGroceryRepository())

            assertEquals(1, result.itemsDeductedCount)
            assertEquals(300f, fakePantry.getItem("Rice")?.quantity ?: 0f, 0.01f)
        }

    private suspend fun verifyVolumeConversion(spec: VolumeSpec) {
        val item = PantryItem(id = 1L, name = "Liquid", quantity = spec.initialQty, unit = spec.stockUnit)
        val pantry = FakePantryRepository(listOf(item))
        val recipe =
            createRecipe(
                itemsUsed =
                    listOf(
                        RecipePantryUsage(
                            name = "Liquid",
                            pantryItemId = 1L,
                            quantityUsed = spec.usedQty,
                            unit = spec.recipeUnit,
                        ),
                    ),
            )
        MealCookHandler.executeCookMeal(recipe, pantry, FakeGroceryRepository())
        val expectedRemaining = spec.initialQty - spec.expectedDelta
        assertEquals(expectedRemaining, pantry.getItem("Liquid")?.quantity ?: 0f, 0.01f)
        assertEquals(spec.expectedDelta, pantry.lastDecrementedDelta ?: 0f, 0.01f)
    }

    private fun createRecipe(
        itemsUsed: List<RecipePantryUsage> = emptyList(),
        missingItems: List<RecipeMissingItem> = emptyList(),
        targetPortions: Int = 2,
    ): MealRecipe =
        MealRecipe(
            recipeId = "rec_test",
            title = "Test Recipe",
            targetPortions = targetPortions,
            itemsUsedFromStock = itemsUsed,
            missingItemsForGrocery = missingItems,
        )
}

private data class VolumeSpec(
    val initialQty: Float,
    val stockUnit: String,
    val usedQty: Float,
    val recipeUnit: String,
    val expectedDelta: Float,
)

private class FakePantryRepository(
    initial: List<PantryItem> = emptyList(),
) : PantryRepository {
    private val items = initial.map { it.copy() }.toMutableList()
    private val snapshotItems = mutableListOf<PantryItem>()
    val flow = MutableStateFlow<List<PantryItem>>(items.toList())
    var lastDecrementedDelta: Float? = null
    var failOnDecrementItemName: String? = null

    fun beginTransaction() {
        snapshotItems.clear()
        snapshotItems.addAll(items.map { it.copy() })
    }

    fun commitTransaction() {
        snapshotItems.clear()
    }

    fun rollbackTransaction() {
        items.clear()
        items.addAll(snapshotItems.map { it.copy() })
        flow.value = items.toList()
    }

    fun getItem(name: String): PantryItem? = items.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun getItemById(id: Long): PantryItem? = items.firstOrNull { it.id == id }

    override fun observePantryItems(): Flow<List<PantryItem>> = flow

    override fun searchPantryItems(query: String): Flow<List<PantryItem>> = flow

    override suspend fun insertPantry(item: PantryItem): Long = 1L

    override suspend fun updatePantry(item: PantryItem) { /* no-op */ }

    override suspend fun decrementQuantity(
        item: PantryItem,
        delta: Float,
    ) {
        require(delta > 0f && delta.isFinite()) {
            "Quantity decrement must be finite and positive"
        }
        val failName = failOnDecrementItemName
        if (failName != null && item.name.equals(failName, ignoreCase = true)) {
            error("Simulated database failure during decrement of ${item.name}")
        }
        lastDecrementedDelta = delta
        val index = items.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            val existing = items[index]
            val clamped = (existing.quantity - delta).coerceAtLeast(0f)
            items[index] = existing.copy(quantity = clamped)
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

private class FakeGroceryRepository : GroceryRepository {
    val insertedItems = mutableListOf<GroceryItem>()
    private val snapshotItems = mutableListOf<GroceryItem>()
    var failOnInsert: Boolean = false

    fun beginTransaction() {
        snapshotItems.clear()
        snapshotItems.addAll(insertedItems.map { it.copy() })
    }

    fun commitTransaction() {
        snapshotItems.clear()
    }

    fun rollbackTransaction() {
        insertedItems.clear()
        insertedItems.addAll(snapshotItems.map { it.copy() })
    }

    override fun observeGroceryItems(): Flow<List<GroceryItem>> = MutableStateFlow(insertedItems.toList())

    override suspend fun insertGrocery(item: GroceryItem): Long {
        if (failOnInsert) {
            error("Simulated disk error inserting grocery item: ${item.name}")
        }
        insertedItems.add(item)
        return insertedItems.size.toLong()
    }

    override suspend fun updateGrocery(item: GroceryItem) { /* no-op */ }

    override suspend fun deleteGrocery(item: GroceryItem) { /* no-op */ }

    override suspend fun clearCheckedGrocery() { /* no-op */ }

    override suspend fun restockCheckedGroceryToPantry(): Int = 0

    override fun reclaimTemporaryMedia(): Int = 0
}
