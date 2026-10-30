package com.jeffers.pantrycraft.feature.meals.prompt

import com.jeffers.pantrycraft.feature.meals.model.MAX_PORTIONS
import com.jeffers.pantrycraft.feature.meals.model.MIN_PORTIONS
import com.jeffers.pantrycraft.feature.meals.model.MealConstraints
import com.jeffers.pantrycraft.feature.meals.model.MealMatchMode

object MealPromptBuilder {
    fun buildSystemPrompt(): String =
        """
        You are PantryCraft's Senior Executive Chef & Culinary Reasoning Engine.
        Your mission is to synthesize delicious, chef-quality recipes optimized to prevent food waste.
        Always return your response exclusively as a valid JSON object matching this schema:
        {
          "recipes": [
            {
              "recipe_id": "unique_string",
              "title": "Recipe Title",
              "match_mode": "STRICT|MOSTLY|SHOPPING|EMERGENCY_SALVAGE",
              "target_portions": 2,
              "prep_time_minutes": 10,
              "cook_time_minutes": 15,
              "urgency_score": 0.9,
              "items_used_from_stock": [
                {
                  "name": "item_name",
                  "pantry_item_id": 1,
                  "quantity_used": 100.0,
                  "unit": "g",
                  "prevents_waste": true,
                  "is_aromatic_or_seasoning": false
                }
              ],
              "missing_items_for_grocery": [
                {
                  "name": "item_name",
                  "quantity_needed": 1.0,
                  "unit": "can"
                }
              ],
              "step_by_step_instructions": [
                "1. Prep step",
                "2. Cook step"
              ],
              "nutritional_estimates_per_portion": {
                "calories": 450,
                "protein_grams": 28,
                "carbs_grams": 35,
                "fat_grams": 16
              }
            }
          ]
        }
        Respond with pure JSON. Do not include markdown preamble or conversational commentary outside JSON.
        """.trimIndent()

    fun buildUserPrompt(
        mode: MealMatchMode,
        targetPortions: Int,
        inventoryContext: String,
        constraints: MealConstraints = MealConstraints(),
    ): String {
        val clampedPortions = targetPortions.coerceIn(MIN_PORTIONS, MAX_PORTIONS)
        val builder = StringBuilder()
        builder.append("### INVENTORY\n").append(inventoryContext).append("\n\n")
        builder.append("### CONSTRAINTS\n")
        builder
            .append("- Match Mode: ")
            .append(mode.id)
            .append(" (")
            .append(mode.description)
            .append(")\n")
        builder.append("- Target Portions: ").append(clampedPortions).append(" servings\n")

        if (constraints.dietaryPreferences.isNotEmpty()) {
            val dietary = constraints.dietaryPreferences.joinToString(", ")
            builder.append("- Dietary Constraints: ").append(dietary).append("\n")
        }
        if (constraints.availableAppliances.isNotEmpty()) {
            val appliances = constraints.availableAppliances.joinToString(", ")
            builder.append("- Available Appliances: ").append(appliances).append("\n")
        }
        if (constraints.maxCookTimeMinutes != null) {
            builder.append("- Maximum Total Time: ").append(constraints.maxCookTimeMinutes).append(" minutes\n")
        }

        builder.append("\n### CULINARY RULES\n")
        when (mode) {
            MealMatchMode.STRICT -> {
                builder.append("1. Zero outside purchases allowed (except tap water, salt, cooking oil).\n")
                builder.append("2. Must prioritize items marked in [EXPIRY_URGENT].\n")
                builder.append("3. 'missing_items_for_grocery' MUST be empty.\n")
            }

            MealMatchMode.MOSTLY -> {
                builder.append("1. 85-95% of ingredients must be from active inventory.\n")
                builder.append("2. Up to 2 missing staple ingredients allowed in 'missing_items_for_grocery'.\n")
            }

            MealMatchMode.SHOPPING -> {
                builder.append("1. Center the recipe around on-hand star ingredients.\n")
                builder.append("2. Output all missing complementary ingredients in 'missing_items_for_grocery'.\n")
            }

            MealMatchMode.EMERGENCY_SALVAGE -> {
                builder.append("1. 15-Minute Emergency Rescue: Total cook + prep time MUST be <= 20 minutes.\n")
                builder.append("2. MUST use as many [EXPIRY_URGENT] ingredients as possible.\n")
                builder.append("3. Focus on fast techniques: stir-fry, frittata, skillet hash, or salvage soup.\n")
            }
        }
        builder.append("4. Scale bulk ingredients linearly for $clampedPortions portions.\n")
        builder.append("5. Scale seasonings and aromatics sub-linearly with power curve (portions^0.75).\n")
        builder.append("6. Tag 'is_aromatic_or_seasoning': true for spices, herbs, garlic, ginger, and hot peppers.\n")

        return builder.toString()
    }
}
