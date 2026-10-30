package com.jeffers.pantrycraft.feature.meals.prompt

import com.jeffers.pantrycraft.feature.meals.model.MealConstraints
import com.jeffers.pantrycraft.feature.meals.model.MealMatchMode
import org.junit.Assert.assertTrue
import org.junit.Test

class MealPromptBuilderTest {
    @Test
    fun systemPromptEnforcesPureJsonAndRecipeSchema() {
        val sys = MealPromptBuilder.buildSystemPrompt()
        assertTrue(sys.contains("PantryCraft's Senior Executive Chef"))
        assertTrue(sys.contains("exclusively as a valid JSON object"))
        assertTrue(sys.contains("\"recipes\""))
        assertTrue(sys.contains("\"items_used_from_stock\""))
        assertTrue(sys.contains("\"missing_items_for_grocery\""))
    }

    @Test
    fun userPromptIncludesConstraintsAndSeasoningCurve() {
        val prompt =
            MealPromptBuilder.buildUserPrompt(
                mode = MealMatchMode.STRICT,
                targetPortions = 4,
                inventoryContext = "[INVENTORY_CONTEXT]\nPantry: rice(1kg)",
                constraints =
                    MealConstraints(
                        dietaryPreferences = listOf("Gluten-Free", "Vegetarian"),
                        availableAppliances = listOf("Air Fryer", "Stovetop"),
                        maxCookTimeMinutes = 30,
                    ),
            )

        assertTrue(prompt.contains("Match Mode: STRICT"))
        assertTrue(prompt.contains("Target Portions: 4 servings"))
        assertTrue(prompt.contains("Gluten-Free, Vegetarian"))
        assertTrue(prompt.contains("Air Fryer, Stovetop"))
        assertTrue(prompt.contains("Maximum Total Time: 30 minutes"))
        assertTrue(prompt.contains("Zero outside purchases allowed"))
        assertTrue(prompt.contains("portions^0.75"))
    }

    @Test
    fun userPromptReflectsEmergencySalvageMode() {
        val prompt =
            MealPromptBuilder.buildUserPrompt(
                mode = MealMatchMode.EMERGENCY_SALVAGE,
                targetPortions = 2,
                inventoryContext = "[INVENTORY_CONTEXT]\nFridge: eggs(2count)",
            )

        assertTrue(prompt.contains("Match Mode: EMERGENCY_SALVAGE"))
        assertTrue(prompt.contains("15-Minute Emergency Rescue"))
        assertTrue(prompt.contains("Total cook + prep time MUST be <= 20 minutes"))
    }

    @Test
    fun userPromptReflectsMostlyAndShoppingModes() {
        val mostlyPrompt =
            MealPromptBuilder.buildUserPrompt(
                mode = MealMatchMode.MOSTLY,
                targetPortions = 3,
                inventoryContext = "[INVENTORY_CONTEXT]",
            )
        assertTrue(mostlyPrompt.contains("85-95% of ingredients must be from active inventory"))

        val shoppingPrompt =
            MealPromptBuilder.buildUserPrompt(
                mode = MealMatchMode.SHOPPING,
                targetPortions = 6,
                inventoryContext = "[INVENTORY_CONTEXT]",
            )
        assertTrue(shoppingPrompt.contains("Center the recipe around on-hand star ingredients"))
    }
}
