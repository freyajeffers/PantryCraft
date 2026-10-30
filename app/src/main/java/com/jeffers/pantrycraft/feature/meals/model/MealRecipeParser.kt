package com.jeffers.pantrycraft.feature.meals.model

import org.json.JSONArray
import org.json.JSONObject

object MealRecipeParser {
    private const val DEFAULT_PORTIONS = 2
    private const val DEFAULT_PREP_TIME_MINUTES = 10
    private const val DEFAULT_COOK_TIME_MINUTES = 15
    private const val DEFAULT_URGENCY_SCORE = 0.5
    private const val RANDOM_ID_BOUND = 1000

    fun parseRecipes(jsonText: String): List<MealRecipe> {
        val sanitized = extractJsonPayload(jsonText)
        if (sanitized.isBlank()) return emptyList()

        return runCatching {
            if (sanitized.startsWith("[")) {
                parseRecipeArray(JSONArray(sanitized))
            } else {
                val root = JSONObject(sanitized)
                val array = root.optJSONArray("recipes") ?: JSONArray()
                parseRecipeArray(array)
            }
        }.getOrDefault(emptyList())
    }

    private fun extractJsonPayload(raw: String): String {
        val trimmed = raw.trim()
        val codeFenceMatch = Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```").find(trimmed)
        if (codeFenceMatch != null) {
            return codeFenceMatch.groupValues[1].trim()
        }
        val firstBrace = trimmed.indexOf('{')
        val firstBracket = trimmed.indexOf('[')
        val start =
            when {
                firstBrace >= 0 && firstBracket >= 0 -> minOf(firstBrace, firstBracket)
                firstBrace >= 0 -> firstBrace
                else -> firstBracket
            }
        val end = maxOf(trimmed.lastIndexOf('}'), trimmed.lastIndexOf(']'))
        return if (start >= 0 && end > start) trimmed.substring(start, end + 1) else trimmed
    }

    private fun parseRecipeArray(array: JSONArray): List<MealRecipe> =
        (0 until array.length()).mapNotNull { i ->
            runCatching { parseRecipeObject(array.getJSONObject(i)) }.getOrNull()
        }

    private fun parseRecipeObject(obj: JSONObject): MealRecipe {
        val recipeId =
            obj.optString("recipe_id").takeIf { it.isNotBlank() }
                ?: "recipe_${System.currentTimeMillis()}_${(1..RANDOM_ID_BOUND).random()}"
        val title = obj.optString("title", "Untitled Recipe")
        val matchMode = MealMatchMode.fromId(obj.optString("match_mode"))
        val portions = obj.optInt("target_portions", DEFAULT_PORTIONS).coerceIn(MIN_PORTIONS, MAX_PORTIONS)
        val prepTime = obj.optInt("prep_time_minutes", DEFAULT_PREP_TIME_MINUTES)
        val cookTime = obj.optInt("cook_time_minutes", DEFAULT_COOK_TIME_MINUTES)
        val urgency = obj.optDouble("urgency_score", DEFAULT_URGENCY_SCORE).toFloat()

        val usedItems = parseUsedItems(obj.optJSONArray("items_used_from_stock"))
        val missingItems = parseMissingItems(obj.optJSONArray("missing_items_for_grocery"))
        val instructions = parseInstructions(obj.optJSONArray("step_by_step_instructions"))
        val nutrition = parseNutrition(obj.optJSONObject("nutritional_estimates_per_portion"))

        return MealRecipe(
            recipeId = recipeId,
            title = title,
            matchMode = matchMode,
            targetPortions = portions,
            prepTimeMinutes = prepTime,
            cookTimeMinutes = cookTime,
            urgencyScore = urgency,
            itemsUsedFromStock = usedItems,
            missingItemsForGrocery = missingItems,
            stepByStepInstructions = instructions,
            nutritionalEstimates = nutrition,
        )
    }

    private fun parseUsedItems(array: JSONArray?): List<RecipePantryUsage> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            RecipePantryUsage(
                name = item.optString("name", "Ingredient"),
                pantryItemId = if (item.has("pantry_item_id")) item.optLong("pantry_item_id") else null,
                quantityUsed = item.optDouble("quantity_used", 1.0).toFloat(),
                unit = item.optString("unit").takeIf { it.isNotBlank() },
                preventsWaste = item.optBoolean("prevents_waste", false),
                isAromaticOrSeasoning = item.optBoolean("is_aromatic_or_seasoning", false),
            )
        }
    }

    private fun parseMissingItems(array: JSONArray?): List<RecipeMissingItem> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            RecipeMissingItem(
                name = item.optString("name", "Missing Item"),
                quantityNeeded = item.optDouble("quantity_needed", 1.0).toFloat(),
                unit = item.optString("unit").takeIf { it.isNotBlank() },
            )
        }
    }

    private fun parseInstructions(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            array.optString(i).takeIf { it.isNotBlank() }
        }
    }

    private fun parseNutrition(obj: JSONObject?): NutritionalEstimates? {
        if (obj == null) return null
        return NutritionalEstimates(
            calories = obj.optInt("calories", 0),
            proteinGrams = obj.optInt("protein_grams", 0),
            carbsGrams = obj.optInt("carbs_grams", 0),
            fatGrams = obj.optInt("fat_grams", 0),
        )
    }
}
