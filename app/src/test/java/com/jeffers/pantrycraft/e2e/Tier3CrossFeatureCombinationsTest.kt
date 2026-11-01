package com.jeffers.pantrycraft.e2e

import com.jeffers.pantrycraft.data.CustomBarcodeItem
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.feature.camera.barcode.OfflineBarcodeResolver
import com.jeffers.pantrycraft.feature.meals.deduction.MealCookHandler
import com.jeffers.pantrycraft.feature.meals.engine.FallbackMealSynthesizer
import com.jeffers.pantrycraft.feature.meals.model.MealMatchMode
import com.jeffers.pantrycraft.feature.meals.model.MealRecipe
import com.jeffers.pantrycraft.feature.meals.model.RecipeMissingItem
import com.jeffers.pantrycraft.feature.meals.model.RecipePantryUsage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier 3: Cross-Feature Combinations Test Suite.
 * Pairwise integration tests validating end-to-end data pipelines:
 * Camera intake -> Ledger storage -> Meal synthesis -> Cooking deduction -> Grocery restock -> Replenishment.
 */
class Tier3CrossFeatureCombinationsTest {
    @Test
    fun testCameraIntakeToInventoryToSynthesisFlow() =
        runBlocking {
            val customDao = InMemoryCustomBarcodeDao()
            val resolver = OfflineBarcodeResolver(customBarcodeDao = customDao)
            val pantryRepo = InMemoryPantryRepository()

            // 1. Camera intake resolves 2 barcodes
            val pastaBarcode = "0123456789012"
            val sauceBarcode = "9876543210987"
            customDao.upsert(
                CustomBarcodeItem(barcode = pastaBarcode, name = "Fusilli Pasta", category = "GRAINS", defaultUnit = "G"),
            )
            customDao.upsert(
                CustomBarcodeItem(barcode = sauceBarcode, name = "Tomato Puree", category = "CANNED", defaultUnit = "G"),
            )

            val pastaMatch = resolver.resolveBarcode(pastaBarcode)
            val sauceMatch = resolver.resolveBarcode(sauceBarcode)
            assertNotNull(pastaMatch)
            assertNotNull(sauceMatch)

            // 2. Commit resolved items into inventory ledger
            val pastaId =
                pantryRepo.insertPantry(
                    PantryItem(
                        name = pastaMatch?.name.orEmpty(),
                        quantity = 500f,
                        unit = pastaMatch?.unit,
                        category = pastaMatch?.category ?: "OTHER",
                        barcode = pastaBarcode,
                    ),
                )
            val sauceId =
                pantryRepo.insertPantry(
                    PantryItem(
                        name = sauceMatch?.name.orEmpty(),
                        quantity = 400f,
                        unit = sauceMatch?.unit,
                        category = sauceMatch?.category ?: "OTHER",
                        barcode = sauceBarcode,
                    ),
                )

            val activeInventory = pantryRepo.observePantryItems().first()
            assertEquals(2, activeInventory.size)

            // 3. Synthesize meal from active inventory
            val recipes =
                FallbackMealSynthesizer.synthesize(
                    items = activeInventory,
                    mode = MealMatchMode.STRICT,
                    targetPortions = 2,
                )

            assertEquals(1, recipes.size)
            val recipe = recipes.first()
            val usedItemIds = recipe.itemsUsedFromStock.mapNotNull { it.pantryItemId }
            assertTrue(
                "Synthesized recipe must use scanned items from inventory",
                usedItemIds.contains(pastaId) || usedItemIds.contains(sauceId),
            )
        }

    @Test
    fun testSynthesisToCookingDeductionToZeroStock() =
        runBlocking {
            val rice = PantryItem(id = 1L, name = "Jasmine Rice", quantity = 200f, unit = "G")
            val chicken = PantryItem(id = 2L, name = "Chicken Thighs", quantity = 150f, unit = "G")
            val pantryRepo = InMemoryPantryRepository(listOf(rice, chicken))
            val groceryRepo = InMemoryPantryRepository()

            val activeItems = pantryRepo.observePantryItems().first()
            val recipes =
                FallbackMealSynthesizer.synthesize(
                    items = activeItems,
                    mode = MealMatchMode.STRICT,
                    targetPortions = 2,
                )
            assertEquals(1, recipes.size)

            // Force recipe usage to consume all available rice and chicken
            val fullConsumptionRecipe =
                recipes.first().copy(
                    itemsUsedFromStock =
                        listOf(
                            RecipePantryUsage(name = "Jasmine Rice", pantryItemId = 1L, quantityUsed = 200f),
                            RecipePantryUsage(name = "Chicken Thighs", pantryItemId = 2L, quantityUsed = 250f), // > stock
                        ),
                )

            val result =
                MealCookHandler.executeCookMeal(
                    recipe = fullConsumptionRecipe,
                    pantryRepository = pantryRepo,
                    groceryRepository = groceryRepo,
                )

            assertEquals(2, result.itemsDeductedCount)
            val updatedStock = pantryRepo.observePantryItems().first()
            val riceRemaining = updatedStock.first { it.id == 1L }
            val chickenRemaining = updatedStock.first { it.id == 2L }

            assertEquals(0f, riceRemaining.quantity, 0.001f)
            assertEquals("Zero floor protection prevents negative stock", 0f, chickenRemaining.quantity, 0.001f)

            // Subsequent synthesis ignores zero-quantity items
            val nextRecipes =
                FallbackMealSynthesizer.synthesize(
                    items = updatedStock,
                    mode = MealMatchMode.STRICT,
                    targetPortions = 2,
                )
            assertTrue("Depleted items must not produce new recipes", nextRecipes.isEmpty())
        }

    @Test
    fun testCookingMissingItemsToGroceryRestockFlow() =
        runBlocking {
            val pantryRepo = InMemoryPantryRepository()
            val groceryRepo = InMemoryPantryRepository()

            // Recipe requires ingredient not on hand
            val recipe =
                MealRecipe(
                    recipeId = "rec_risotto",
                    title = "Mushroom Risotto",
                    targetPortions = 2,
                    missingItemsForGrocery =
                        listOf(
                            RecipeMissingItem(name = "Arborio Rice", quantityNeeded = 300f, unit = "G"),
                        ),
                )

            // 1. Cook meal adds missing items to grocery checklist
            val cookResult =
                MealCookHandler.executeCookMeal(
                    recipe = recipe,
                    pantryRepository = pantryRepo,
                    groceryRepository = groceryRepo,
                    addMissingToGrocery = true,
                )
            assertEquals(1, cookResult.groceryItemsAddedCount)

            var groceryList = groceryRepo.observeGroceryItems().first()
            assertEquals(1, groceryList.size)
            val missingGrocery = groceryList.first()
            assertEquals("Arborio Rice", missingGrocery.name)
            assertFalse(missingGrocery.checked)

            // 2. User shops and checks off the item
            groceryRepo.updateGrocery(missingGrocery.copy(checked = true))
            groceryList = groceryRepo.observeGroceryItems().first()
            assertTrue(groceryList.first().checked)

            // 3. User taps 'Restock Checked' -> transferred into active pantry
            val restockedCount = groceryRepo.restockCheckedGroceryToPantry()
            assertEquals(1, restockedCount)

            val restockedGroceryList = groceryRepo.observeGroceryItems().first()
            assertTrue("Restocked checked items must be cleared", restockedGroceryList.isEmpty())
        }

    @Test
    fun testCameraBatchReviewCommitToMealSynthesis() =
        runBlocking {
            val repo = InMemoryPantryRepository()

            // Simulated batch review commit of 3 items
            val oil = PantryItem(name = "Olive Oil", quantity = 250f, unit = "ML", category = "PANTRY_STAPLE")
            val garlic = PantryItem(name = "Garlic", quantity = 10f, unit = "CLOVE", category = "SPICES")
            val basil = PantryItem(name = "Fresh Basil", quantity = 30f, unit = "G", category = "SPICES")

            repo.insertPantry(oil)
            repo.insertPantry(garlic)
            repo.insertPantry(basil)

            val active = repo.observePantryItems().first()
            assertEquals(3, active.size)

            // Verify aromatic taxonomy classification
            val activeGarlic = active.first { it.name == "Garlic" }
            val activeBasil = active.first { it.name == "Fresh Basil" }
            assertTrue(activeGarlic.isAromaticOrSeasoning)
            assertTrue(activeBasil.isAromaticOrSeasoning)

            // Synthesize meal with scaling
            val baseRecipe =
                FallbackMealSynthesizer
                    .synthesize(
                        items = active,
                        mode = MealMatchMode.STRICT,
                        targetPortions = 2,
                    ).first()

            val scaledRecipe = baseRecipe.scaleToPortions(6)
            assertEquals(6, scaledRecipe.targetPortions)

            // Cook meal deducting scaled amounts
            val cookResult =
                MealCookHandler.executeCookMeal(
                    recipe = scaledRecipe,
                    pantryRepository = repo,
                    groceryRepository = repo,
                )
            assertTrue(cookResult.itemsDeductedCount > 0)
        }

    @Test
    fun testFullEndToEndIntakeSynthesisCookReplenishCycle() =
        runBlocking {
            val customDao = InMemoryCustomBarcodeDao()
            val resolver = OfflineBarcodeResolver(customBarcodeDao = customDao)
            val repo = InMemoryPantryRepository()

            // Phase A: Barcode intake of Milk (1000 ML) and Flour (500 G)
            val (milkId, flourId) = seedPantryIntake(customDao, resolver, repo)

            // Phase B: Search verifies items in encrypted ledger
            val searchMilk = repo.searchPantryItems("milk").first()
            assertEquals(1, searchMilk.size)
            assertEquals(milkId, searchMilk.first().id)

            // Phase C: Meal Synthesis recipe requiring Milk, Flour, and missing Eggs
            val pancakeRecipe =
                MealRecipe(
                    recipeId = "rec_pancakes",
                    title = "Golden Pancakes",
                    targetPortions = 2,
                    itemsUsedFromStock =
                        listOf(
                            RecipePantryUsage(name = "Whole Milk", pantryItemId = milkId, quantityUsed = 250f, unit = "ML"),
                            RecipePantryUsage(name = "All Purpose Flour", pantryItemId = flourId, quantityUsed = 200f, unit = "G"),
                        ),
                    missingItemsForGrocery =
                        listOf(
                            RecipeMissingItem(name = "Large Eggs", quantityNeeded = 2f, unit = "COUNT"),
                        ),
                )

            // Phase D: Cooking deduction
            val cookResult =
                MealCookHandler.executeCookMeal(
                    recipe = pancakeRecipe,
                    pantryRepository = repo,
                    groceryRepository = repo,
                    addMissingToGrocery = true,
                )
            assertEquals(2, cookResult.itemsDeductedCount)
            assertEquals(1, cookResult.groceryItemsAddedCount)

            val activeAfterCook = repo.observePantryItems().first()
            val remainingMilk = activeAfterCook.first { it.id == milkId }
            val remainingFlour = activeAfterCook.first { it.id == flourId }
            assertEquals(750f, remainingMilk.quantity, 0.01f)
            assertEquals(300f, remainingFlour.quantity, 0.01f)

            // Phase E: Grocery Restock (User buys eggs and additional flour)
            val groceryList = repo.observeGroceryItems().first()
            assertEquals(1, groceryList.size)
            repo.updateGrocery(groceryList.first().copy(checked = true))

            val restockedCount = repo.restockCheckedGroceryToPantry()
            assertEquals(1, restockedCount)

            // Phase F: Verifies Eggs are now present in pantry
            val pantryAfterRestock = repo.observePantryItems().first()
            val restockedEggs = pantryAfterRestock.firstOrNull { it.name == "Large Eggs" }
            assertNotNull("Restocked item must now exist in pantry", restockedEggs)
            assertEquals(2f, restockedEggs?.quantity ?: 0f, 0.01f)
        }

    @Test
    fun testSoftDeleteExclusionAcrossSynthesisAndDeduction() =
        runBlocking {
            val butter = PantryItem(id = 10L, name = "Butter", quantity = 100f, unit = "G")
            val bread = PantryItem(id = 20L, name = "Bread Loaf", quantity = 1f, unit = "COUNT")
            val repo = InMemoryPantryRepository(listOf(butter, bread))

            // Soft delete bread
            repo.deletePantry(bread)

            val active = repo.observePantryItems().first()
            assertEquals(1, active.size)
            assertEquals("Butter", active.first().name)

            // Synthesis only uses Butter
            val recipes =
                FallbackMealSynthesizer.synthesize(
                    items = active,
                    mode = MealMatchMode.STRICT,
                    targetPortions = 2,
                )
            val recipe = recipes.first()
            assertFalse(recipe.itemsUsedFromStock.any { it.name == "Bread Loaf" })

            // Restore bread
            val allRaw = repo.getAllRawPantry()
            val deletedBread = allRaw.first { it.id == 20L }
            repo.restorePantry(deletedBread)

            val restoredActive = repo.observePantryItems().first()
            assertEquals(2, restoredActive.size)
        }

    private suspend fun seedPantryIntake(
        customDao: InMemoryCustomBarcodeDao,
        resolver: OfflineBarcodeResolver,
        repo: InMemoryPantryRepository,
    ): Pair<Long, Long> {
        val milkBarcode = "1111111111111"
        val flourBarcode = "2222222222222"
        customDao.upsert(
            CustomBarcodeItem(barcode = milkBarcode, name = "Whole Milk", category = "DAIRY", defaultUnit = "ML"),
        )
        customDao.upsert(
            CustomBarcodeItem(
                barcode = flourBarcode,
                name = "All Purpose Flour",
                category = "GRAINS",
                defaultUnit = "G",
            ),
        )

        val milkMatch = resolver.resolveBarcode(milkBarcode)
        val flourMatch = resolver.resolveBarcode(flourBarcode)

        val milkId =
            repo.insertPantry(
                PantryItem(
                    name = milkMatch?.name.orEmpty(),
                    quantity = 1000f,
                    unit = "ML",
                    storageLocation = "FRIDGE",
                    barcode = milkBarcode,
                ),
            )
        val flourId =
            repo.insertPantry(
                PantryItem(
                    name = flourMatch?.name.orEmpty(),
                    quantity = 500f,
                    unit = "G",
                    storageLocation = "PANTRY",
                    barcode = flourBarcode,
                ),
            )
        return Pair(milkId, flourId)
    }
}
