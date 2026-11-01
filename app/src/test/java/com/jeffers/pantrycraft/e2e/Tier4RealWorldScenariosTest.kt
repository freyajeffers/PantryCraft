package com.jeffers.pantrycraft.e2e

import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.feature.meals.deduction.MealCookHandler
import com.jeffers.pantrycraft.feature.meals.engine.FallbackMealSynthesizer
import com.jeffers.pantrycraft.feature.meals.model.MealMatchMode
import com.jeffers.pantrycraft.feature.meals.model.MealRecipe
import com.jeffers.pantrycraft.feature.meals.model.RecipeMissingItem
import com.jeffers.pantrycraft.feature.meals.model.RecipePantryUsage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier 4: Real-World Scenarios Test Suite.
 * Validates practical user journeys and complex real-life culinary operations:
 * 1. Emergency 15-Minute Salvage (perishables rescue under 48h)
 * 2. Holiday Feast Portion Scaling (2 -> 10 portions with sub-linear seasoning curve)
 * 3. Offline Zero-Network Pantry Synthesis (local deterministic reasoning)
 * 4. Weekly Meal Planning & Replenishment (multi-day cooking and restocking cycles)
 */
class Tier4RealWorldScenariosTest {
    @Test
    fun testScenarioEmergencyFifteenMinuteSalvage() =
        runBlocking {
            val now = 1_700_000_000_000L
            val hoursRemaining = 18L
            val expiringTimestamp = now + (hoursRemaining * 3_600_000L) // 18 hours until expiration (<48h)

            val items = createPerishableItems(expiringTimestamp)
            val pantryRepo = InMemoryPantryRepository(items)
            val groceryRepo = InMemoryPantryRepository()

            // 1. Synthesize emergency 15-min rescue
            val inventory = pantryRepo.observePantryItems().first()
            val recipes =
                FallbackMealSynthesizer.synthesize(
                    items = inventory,
                    mode = MealMatchMode.EMERGENCY_SALVAGE,
                    targetPortions = 2,
                    nowMillis = now,
                )

            assertEquals(1, recipes.size)
            val rescueRecipe = recipes.first()

            // Verify SLA: Emergency salvage recipe total time is <= 15 minutes (5 min prep + 10 min cook)
            assertEquals(5, rescueRecipe.prepTimeMinutes)
            assertEquals(10, rescueRecipe.cookTimeMinutes)
            assertEquals(15, rescueRecipe.totalTimeMinutes)

            // Verify high urgency score indicating waste prevention
            assertEquals(0.95f, rescueRecipe.urgencyScore, 0.01f)
            assertTrue("Urgent items must be prioritized", rescueRecipe.title.contains("Chicken"))

            // 2. Cook the emergency meal
            val cookResult =
                MealCookHandler.executeCookMeal(
                    recipe = rescueRecipe,
                    pantryRepository = pantryRepo,
                    groceryRepository = groceryRepo,
                )

            assertTrue(cookResult.itemsDeductedCount >= 2)
            assertTrue(
                "Waste prevented counter must increment for items expiring < 48h",
                cookResult.foodWastePreventedCount >= 1,
            )

            val remainingPantry = pantryRepo.observePantryItems().first()
            val remainingChicken = remainingPantry.first { it.id == 101L }
            assertTrue(remainingChicken.quantity < 400f)
        }

    @Test
    fun testScenarioHolidayFeastPortionScaling() {
        val baseRecipe = createHolidayBaseRecipe()
        val holidayFeast = baseRecipe.scaleToPortions(10)
        assertEquals(10, holidayFeast.targetPortions)

        val turkey = holidayFeast.itemsUsedFromStock.first { it.name == "Turkey Roast" }
        val potatoes = holidayFeast.itemsUsedFromStock.first { it.name == "Potatoes" }
        val rosemary = holidayFeast.itemsUsedFromStock.first { it.name == "Rosemary" }
        val garlic = holidayFeast.itemsUsedFromStock.first { it.name == "Minced Garlic" }
        val salt = holidayFeast.itemsUsedFromStock.first { it.name == "Sea Salt" }

        // Bulk ingredients must scale strictly linearly: 5x
        assertEquals(3000f, turkey.quantityUsed, 0.01f) // 600 * 5 = 3000g
        assertEquals(2500f, potatoes.quantityUsed, 0.01f) // 500 * 5 = 2500g

        // Seasonings must scale non-linearly using 5^0.75 ≈ 3.3437
        // Rosemary: 2 * 3.3437 = 6.69 tsp (instead of 10 tsp linear)
        assertEquals(6.69f, rosemary.quantityUsed, 0.02f)
        assertTrue("Sub-linear scaling protects dish from being overpowered", rosemary.quantityUsed < 10f)

        // Garlic: 1 * 3.3437 = 3.34 tbsp (instead of 5 tbsp linear)
        assertEquals(3.34f, garlic.quantityUsed, 0.02f)
        assertTrue(garlic.quantityUsed < 5f)

        // Salt: 1 * 3.3437 = 3.34 tsp (instead of 5 tsp linear)
        assertEquals(3.34f, salt.quantityUsed, 0.02f)
        assertTrue(salt.quantityUsed < 5f)

        // Missing grocery item scales linearly
        val cranberry = holidayFeast.missingItemsForGrocery.first { it.name == "Cranberry Sauce" }
        assertEquals(5f, cranberry.quantityNeeded, 0.01f)
    }

    @Test
    fun testScenarioOfflineZeroNetworkPantrySynthesis() {
        // In a cabin with zero cell signal and no BYOKey configured
        val staples =
            listOf(
                PantryItem(id = 1L, name = "Black Beans", quantity = 3f, unit = "CAN", category = "CANNED"),
                PantryItem(id = 2L, name = "White Rice", quantity = 1000f, unit = "G", category = "GRAINS"),
                PantryItem(id = 3L, name = "Cumin", quantity = 50f, unit = "G", category = "SPICES"),
            )

        val recipes =
            FallbackMealSynthesizer.synthesize(
                items = staples,
                mode = MealMatchMode.STRICT,
                targetPortions = 4,
            )

        assertEquals(1, recipes.size)
        val recipe = recipes.first()

        assertEquals(MealMatchMode.STRICT, recipe.matchMode)
        assertEquals(4, recipe.targetPortions)
        assertTrue(recipe.missingItemsForGrocery.isEmpty()) // Strict mode = zero purchases needed
        assertTrue(recipe.itemsUsedFromStock.isNotEmpty())
        assertTrue(recipe.stepByStepInstructions.size >= 4)

        // Nutrition estimates present
        val nutrition = recipe.nutritionalEstimates
        assertNotNull(nutrition)
        assertTrue((nutrition?.calories ?: 0) > 0)
        assertTrue((nutrition?.proteinGrams ?: 0) > 0)
    }

    @Test
    fun testScenarioWeeklyMealPlanningMultiMealDeduction() =
        runBlocking {
            // Initial Monday stock
            val pasta = PantryItem(id = 1L, name = "Penne Pasta", quantity = 1000f, unit = "G")
            val beef = PantryItem(id = 2L, name = "Ground Beef", quantity = 800f, unit = "G")
            val soySauce = PantryItem(id = 3L, name = "Soy Sauce", quantity = 100f, unit = "ML", category = "CONDIMENTS")
            val tofu = PantryItem(id = 4L, name = "Firm Tofu", quantity = 300f, unit = "G")

            val repo = InMemoryPantryRepository(listOf(pasta, beef, soySauce, tofu))

            // DAY 1 (Tuesday): Cook Pasta Bolognese
            val meal1 =
                MealRecipe(
                    recipeId = "meal_day1",
                    title = "Penne Bolognese",
                    targetPortions = 4,
                    itemsUsedFromStock =
                        listOf(
                            RecipePantryUsage(name = "Penne Pasta", pantryItemId = 1L, quantityUsed = 400f, unit = "G"),
                            RecipePantryUsage(name = "Ground Beef", pantryItemId = 2L, quantityUsed = 400f, unit = "G"),
                        ),
                )
            val result1 = MealCookHandler.executeCookMeal(recipe = meal1, pantryRepository = repo, groceryRepository = repo)
            assertEquals(2, result1.itemsDeductedCount)

            var currentPantry = repo.observePantryItems().first()
            assertEquals(600f, currentPantry.first { it.id == 1L }.quantity, 0.01f)
            assertEquals(400f, currentPantry.first { it.id == 2L }.quantity, 0.01f)

            // DAY 2 (Wednesday): Cook Tofu Stir Fry (Depletes Soy Sauce and Tofu)
            val meal2 =
                MealRecipe(
                    recipeId = "meal_day2",
                    title = "Tofu Stir Fry",
                    targetPortions = 2,
                    itemsUsedFromStock =
                        listOf(
                            RecipePantryUsage(name = "Firm Tofu", pantryItemId = 4L, quantityUsed = 300f, unit = "G"),
                            RecipePantryUsage(name = "Soy Sauce", pantryItemId = 3L, quantityUsed = 100f, unit = "ML"),
                        ),
                    missingItemsForGrocery =
                        listOf(
                            RecipeMissingItem(name = "Soy Sauce", quantityNeeded = 250f, unit = "ML"),
                            RecipeMissingItem(name = "Firm Tofu", quantityNeeded = 300f, unit = "G"),
                        ),
                )
            val result2 = MealCookHandler.executeCookMeal(recipe = meal2, pantryRepository = repo, groceryRepository = repo)
            assertEquals(2, result2.itemsDeductedCount)
            assertEquals(2, result2.groceryItemsAddedCount)

            currentPantry = repo.observePantryItems().first()
            assertEquals(0f, currentPantry.first { it.id == 3L }.quantity, 0.01f)
            assertEquals(0f, currentPantry.first { it.id == 4L }.quantity, 0.01f)

            // DAY 3 (Thursday): Grocery Restock
            val groceries = repo.observeGroceryItems().first()
            assertEquals(2, groceries.size)
            // Mark both as bought
            groceries.forEach { repo.updateGrocery(it.copy(checked = true)) }

            val restocked = repo.restockCheckedGroceryToPantry()
            assertEquals(2, restocked)

            // DAY 4 (Friday): Verify restocked items are replenished
            val weekendPantry = repo.observePantryItems().first()
            assertTrue(weekendPantry.any { it.name == "Soy Sauce" && it.quantity > 0f })
            assertTrue(weekendPantry.any { it.name == "Firm Tofu" && it.quantity > 0f })
        }

    private fun createPerishableItems(expiringTimestamp: Long): List<PantryItem> =
        listOf(
            PantryItem(
                id = 101L,
                name = "Chicken Tenders",
                quantity = 400f,
                unit = "G",
                category = "MEAT",
                storageLocation = "FRIDGE",
                expirationTimestamp = expiringTimestamp,
            ),
            PantryItem(
                id = 102L,
                name = "Baby Spinach",
                quantity = 150f,
                unit = "G",
                category = "PRODUCE",
                storageLocation = "FRIDGE",
                expirationTimestamp = expiringTimestamp,
            ),
            PantryItem(
                id = 103L,
                name = "Cooking Oil",
                quantity = 500f,
                unit = "ML",
                category = "PANTRY_STAPLE",
                storageLocation = "PANTRY",
            ),
        )

    private fun createHolidayBaseRecipe(): MealRecipe =
        MealRecipe(
            recipeId = "rec_holiday_roast",
            title = "Herb-Crusted Roast Dinner",
            targetPortions = 2,
            prepTimeMinutes = 20,
            cookTimeMinutes = 45,
            itemsUsedFromStock =
                listOf(
                    RecipePantryUsage(
                        name = "Turkey Roast",
                        quantityUsed = 600f,
                        unit = "G",
                        isAromaticOrSeasoning = false,
                    ),
                    RecipePantryUsage(
                        name = "Potatoes",
                        quantityUsed = 500f,
                        unit = "G",
                        isAromaticOrSeasoning = false,
                    ),
                    RecipePantryUsage(
                        name = "Rosemary",
                        quantityUsed = 2f,
                        unit = "TSP",
                        isAromaticOrSeasoning = true,
                    ),
                    RecipePantryUsage(
                        name = "Minced Garlic",
                        quantityUsed = 1f,
                        unit = "TBSP",
                        isAromaticOrSeasoning = true,
                    ),
                    RecipePantryUsage(
                        name = "Sea Salt",
                        quantityUsed = 1f,
                        unit = "TSP",
                        isAromaticOrSeasoning = true,
                    ),
                ),
            missingItemsForGrocery =
                listOf(
                    RecipeMissingItem(name = "Cranberry Sauce", quantityNeeded = 1f, unit = "JAR"),
                ),
        )
}
