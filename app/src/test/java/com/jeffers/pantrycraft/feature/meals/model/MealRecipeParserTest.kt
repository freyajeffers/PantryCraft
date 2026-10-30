package com.jeffers.pantrycraft.feature.meals.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MealRecipeParserTest {
    @Test
    fun parseRecipesExtractsFromRootObject() {
        val json =
            """
            {
              "recipes": [
                {
                  "recipe_id": "omelet_1",
                  "title": "Spinach Omelet",
                  "match_mode": "STRICT",
                  "target_portions": 2,
                  "prep_time_minutes": 5,
                  "cook_time_minutes": 10,
                  "urgency_score": 0.9,
                  "items_used_from_stock": [
                    { "name": "Eggs", "pantry_item_id": 10, "quantity_used": 3.0, "unit": "count", "prevents_waste": true }
                  ],
                  "missing_items_for_grocery": [],
                  "step_by_step_instructions": [
                    "1. Whisk eggs.",
                    "2. Cook in pan."
                  ],
                  "nutritional_estimates_per_portion": {
                    "calories": 250,
                    "protein_grams": 18,
                    "carbs_grams": 2,
                    "fat_grams": 14
                  }
                }
              ]
            }
            """.trimIndent()

        val parsed = MealRecipeParser.parseRecipes(json)
        assertEquals(1, parsed.size)
        val recipe = parsed.first()
        assertEquals("omelet_1", recipe.recipeId)
        assertEquals("Spinach Omelet", recipe.title)
        assertEquals(MealMatchMode.STRICT, recipe.matchMode)
        assertEquals(2, recipe.targetPortions)
        assertEquals(15, recipe.totalTimeMinutes)
        assertEquals(1, recipe.itemsUsedFromStock.size)
        assertEquals(10L, recipe.itemsUsedFromStock.first().pantryItemId)
        assertTrue(recipe.itemsUsedFromStock.first().preventsWaste)
        assertEquals(2, recipe.stepByStepInstructions.size)
        assertNotNull(recipe.nutritionalEstimates)
        assertEquals(250, recipe.nutritionalEstimates?.calories)
    }

    @Test
    fun parseRecipesExtractsFromDirectJsonArray() {
        val json =
            """
            [
              {
                "recipe_id": "salad_1",
                "title": "Garden Salad",
                "match_mode": "MOSTLY"
              }
            ]
            """.trimIndent()

        val parsed = MealRecipeParser.parseRecipes(json)
        assertEquals(1, parsed.size)
        assertEquals("salad_1", parsed.first().recipeId)
        assertEquals(MealMatchMode.MOSTLY, parsed.first().matchMode)
    }

    @Test
    fun parseRecipesHandlesMarkdownCodeFences() {
        val markdown =
            """
            Here is your recipe:
            ```json
            {
              "recipes": [
                {
                  "recipe_id": "stir_fry_1",
                  "title": "Tofu Stir Fry"
                }
              ]
            }
            ```
            Enjoy cooking!
            """.trimIndent()

        val parsed = MealRecipeParser.parseRecipes(markdown)
        assertEquals(1, parsed.size)
        assertEquals("stir_fry_1", parsed.first().recipeId)
        assertEquals("Tofu Stir Fry", parsed.first().title)
    }

    @Test
    fun parseRecipesReturnsEmptyOnInvalidJson() {
        assertTrue(MealRecipeParser.parseRecipes("").isEmpty())
        assertTrue(MealRecipeParser.parseRecipes("Not JSON").isEmpty())
        assertTrue(MealRecipeParser.parseRecipes("{ invalid json").isEmpty())
    }
}
