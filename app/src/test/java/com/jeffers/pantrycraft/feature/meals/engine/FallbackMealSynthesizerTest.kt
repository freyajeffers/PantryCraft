package com.jeffers.pantrycraft.feature.meals.engine

import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.feature.meals.model.MealMatchMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackMealSynthesizerTest {

    private val now = 1700000000000L
    private val oneDayMillis = 86_400_000L

    @Test
    fun synthesizeEmptyInventoryReturnsEmptyList() {
        val recipes = FallbackMealSynthesizer.synthesize(
            items = emptyList(),
            mode = MealMatchMode.STRICT,
            targetPortions = 2,
            nowMillis = now,
        )
        assertTrue(recipes.isEmpty())
    }

    @Test
    fun synthesizePrioritizesUrgentIngredientsAndTagsWaste() {
        val items = listOf(
            PantryItem(
                id = 1L,
                name = "Fresh Spinach",
                quantity = 200f,
                unit = "g",
                expirationTimestamp = now + (1 * oneDayMillis),
            ),
            PantryItem(
                id = 2L,
                name = "Eggs",
                quantity = 6f,
                unit = "count",
            ),
        )

        val recipes = FallbackMealSynthesizer.synthesize(
            items = items,
            mode = MealMatchMode.EMERGENCY_SALVAGE,
            targetPortions = 2,
            nowMillis = now,
        )

        assertEquals(1, recipes.size)
        val recipe = recipes.first()
        assertTrue(recipe.title.contains("Spinach"))
        assertTrue(recipe.totalTimeMinutes <= 20)
        assertTrue(recipe.wastePreventionCount > 0)
        assertEquals(2, recipe.targetPortions)
    }

    @Test
    fun synthesizeScalesIngredientsToTargetPortions() {
        val items = listOf(
            PantryItem(
                id = 1L,
                name = "Chicken Breast",
                quantity = 500f,
                unit = "g",
            ),
        )

        val recipes = FallbackMealSynthesizer.synthesize(
            items = items,
            mode = MealMatchMode.STRICT,
            targetPortions = 4,
            nowMillis = now,
        )

        assertEquals(1, recipes.size)
        assertEquals(4, recipes.first().targetPortions)
    }
}
