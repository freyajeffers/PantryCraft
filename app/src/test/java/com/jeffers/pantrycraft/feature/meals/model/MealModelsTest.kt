package com.jeffers.pantrycraft.feature.meals.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MealModelsTest {
    @Test
    fun mealMatchModeFromIdResolvesCorrectly() {
        assertEquals(MealMatchMode.STRICT, MealMatchMode.fromId("STRICT"))
        assertEquals(MealMatchMode.STRICT, MealMatchMode.fromId("strict"))
        assertEquals(MealMatchMode.MOSTLY, MealMatchMode.fromId("mostly"))
        assertEquals(MealMatchMode.SHOPPING, MealMatchMode.fromId("shopping"))
        assertEquals(MealMatchMode.EMERGENCY_SALVAGE, MealMatchMode.fromId("EMERGENCY_SALVAGE"))
        assertEquals(MealMatchMode.STRICT, MealMatchMode.fromId(null))
        assertEquals(MealMatchMode.STRICT, MealMatchMode.fromId("unknown"))
    }

    @Test
    fun mealRecipeComputedPropertiesAreAccurate() {
        val recipe =
            MealRecipe(
                recipeId = "rec_1",
                title = "Test Soup",
                prepTimeMinutes = 15,
                cookTimeMinutes = 25,
                itemsUsedFromStock =
                    listOf(
                        RecipePantryUsage(name = "Carrot", quantityUsed = 2f, preventsWaste = true),
                        RecipePantryUsage(name = "Onion", quantityUsed = 1f, preventsWaste = false),
                        RecipePantryUsage(name = "Celery", quantityUsed = 2f, preventsWaste = true),
                    ),
            )

        assertEquals(40, recipe.totalTimeMinutes)
        assertEquals(2, recipe.wastePreventionCount)
    }

    @Test
    fun scaleToPortionsScalesBulkLinearlyAndSeasoningsSubLinearly() {
        val recipe =
            MealRecipe(
                recipeId = "rec_curry",
                title = "Chickpea Curry",
                targetPortions = 2,
                itemsUsedFromStock =
                    listOf(
                        RecipePantryUsage(
                            name = "Chickpeas",
                            quantityUsed = 200f,
                            unit = "g",
                            isAromaticOrSeasoning = false,
                        ),
                        RecipePantryUsage(
                            name = "Garam Masala",
                            quantityUsed = 10f,
                            unit = "g",
                            isAromaticOrSeasoning = true,
                        ),
                    ),
                missingItemsForGrocery =
                    listOf(
                        RecipeMissingItem(name = "Coconut Milk", quantityNeeded = 1f, unit = "can"),
                    ),
            )

        val scaled = recipe.scaleToPortions(4)
        assertEquals(4, scaled.targetPortions)

        // Bulk: 200g * (4/2) = 400g
        assertEquals(400f, scaled.itemsUsedFromStock[0].quantityUsed, 0.01f)

        // Seasoning: 10g * (4/2)^0.75 = 10 * 1.68179 = 16.82g
        assertEquals(16.82f, scaled.itemsUsedFromStock[1].quantityUsed, 0.05f)

        // Missing item: 1 can * 2 = 2 cans
        assertEquals(2f, scaled.missingItemsForGrocery[0].quantityNeeded, 0.01f)
    }

    @Test
    fun scaleToSamePortionsReturnsSameInstance() {
        val recipe = MealRecipe(recipeId = "rec_1", title = "Pantry Hash", targetPortions = 4)
        assertEquals(recipe, recipe.scaleToPortions(4))
    }

    @Test
    fun scaleToPortionsThrowsWhenOutOfRange() {
        val recipe = MealRecipe(recipeId = "rec_1", title = "Pantry Hash", targetPortions = 2)
        assertThrows(IllegalArgumentException::class.java) { recipe.scaleToPortions(0) }
        assertThrows(IllegalArgumentException::class.java) { recipe.scaleToPortions(13) }
    }
}
