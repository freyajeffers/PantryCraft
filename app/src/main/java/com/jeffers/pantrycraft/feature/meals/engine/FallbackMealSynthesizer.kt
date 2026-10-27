package com.jeffers.pantrycraft.feature.meals.engine

import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.feature.meals.model.MAX_PORTIONS
import com.jeffers.pantrycraft.feature.meals.model.MIN_PORTIONS
import com.jeffers.pantrycraft.feature.meals.model.MealMatchMode
import com.jeffers.pantrycraft.feature.meals.model.MealRecipe
import com.jeffers.pantrycraft.feature.meals.model.NutritionalEstimates
import com.jeffers.pantrycraft.feature.meals.model.RecipeMissingItem
import com.jeffers.pantrycraft.feature.meals.model.RecipePantryUsage

object FallbackMealSynthesizer {

    private const val MILLIS_PER_DAY = 86_400_000L
    private const val URGENT_DAYS_THRESHOLD = 2L
    private const val MAIN_ITEM_PORTION_FRACTION = 0.5f
    private const val COMP_ITEM_PORTION_FRACTION = 0.25f
    private const val COMPLEMENTARY_ITEMS_LIMIT = 3
    private const val EMERGENCY_PREP_TIME = 5
    private const val DEFAULT_PREP_TIME = 10
    private const val EMERGENCY_COOK_TIME = 10
    private const val DEFAULT_COOK_TIME = 20
    private const val HIGH_URGENCY_SCORE = 0.95f
    private const val MEDIUM_URGENCY_SCORE = 0.5f
    private const val ESTIMATED_CALORIES = 380
    private const val ESTIMATED_PROTEIN = 24
    private const val ESTIMATED_CARBS = 32
    private const val ESTIMATED_FAT = 14

    fun synthesize(
        items: List<PantryItem>,
        mode: MealMatchMode,
        targetPortions: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<MealRecipe> {
        val active = items.filter { !it.isDeleted && it.quantity > 0f }
        if (active.isEmpty()) return emptyList()

        val urgent = active.filter { item ->
            item.expirationTimestamp?.let { exp ->
                (exp - nowMillis) <= (URGENT_DAYS_THRESHOLD * MILLIS_PER_DAY)
            } ?: false
        }

        val (mainItem, usedStock) = buildPantryUsage(active, urgent)
        val missing = buildMissingItems(mode)
        val title = buildRecipeTitle(mode, mainItem.name)

        val recipe = MealRecipe(
            recipeId = "local_${System.currentTimeMillis()}_${mainItem.id}",
            title = title,
            matchMode = mode,
            targetPortions = targetPortions.coerceIn(MIN_PORTIONS, MAX_PORTIONS),
            prepTimeMinutes = if (mode == MealMatchMode.EMERGENCY_SALVAGE) EMERGENCY_PREP_TIME else DEFAULT_PREP_TIME,
            cookTimeMinutes = if (mode == MealMatchMode.EMERGENCY_SALVAGE) EMERGENCY_COOK_TIME else DEFAULT_COOK_TIME,
            urgencyScore = if (urgent.isNotEmpty()) HIGH_URGENCY_SCORE else MEDIUM_URGENCY_SCORE,
            itemsUsedFromStock = usedStock,
            missingItemsForGrocery = missing,
            stepByStepInstructions = listOf(
                "1. Gather and prep ${usedStock.joinToString(", ") { it.name }}.",
                "2. Heat a pan over medium heat with cooking oil or butter.",
                "3. Sauté ingredients until cooked through and golden brown.",
                "4. Season with salt and spices to taste, then serve warm.",
            ),
            nutritionalEstimates = NutritionalEstimates(
                calories = ESTIMATED_CALORIES,
                proteinGrams = ESTIMATED_PROTEIN,
                carbsGrams = ESTIMATED_CARBS,
                fatGrams = ESTIMATED_FAT,
            ),
        )

        return listOf(recipe.scaleToPortions(targetPortions))
    }

    private fun buildPantryUsage(
        active: List<PantryItem>,
        urgent: List<PantryItem>,
    ): Pair<PantryItem, List<RecipePantryUsage>> {
        val primaryList = if (urgent.isNotEmpty()) urgent else active
        val mainItem = primaryList.maxByOrNull { it.quantity } ?: active.first()
        val complementary = active.filter { it.id != mainItem.id }.take(COMPLEMENTARY_ITEMS_LIMIT)

        val usedStock = mutableListOf<RecipePantryUsage>()
        usedStock.add(
            RecipePantryUsage(
                name = mainItem.name,
                pantryItemId = mainItem.id,
                quantityUsed = (mainItem.quantity * MAIN_ITEM_PORTION_FRACTION).coerceAtLeast(1f),
                unit = mainItem.unit,
                preventsWaste = urgent.any { it.id == mainItem.id },
                isAromaticOrSeasoning = mainItem.isAromaticOrSeasoning,
            ),
        )

        for (comp in complementary) {
            usedStock.add(
                RecipePantryUsage(
                    name = comp.name,
                    pantryItemId = comp.id,
                    quantityUsed = (comp.quantity * COMP_ITEM_PORTION_FRACTION).coerceAtLeast(1f),
                    unit = comp.unit,
                    preventsWaste = urgent.any { it.id == comp.id },
                    isAromaticOrSeasoning = comp.isAromaticOrSeasoning,
                ),
            )
        }
        return Pair(mainItem, usedStock)
    }

    private fun buildMissingItems(mode: MealMatchMode): List<RecipeMissingItem> =
        if (mode == MealMatchMode.SHOPPING) {
            listOf(RecipeMissingItem(name = "Fresh Herbs or Garnish", quantityNeeded = 1f, unit = "BUNCH"))
        } else {
            emptyList()
        }

    private fun buildRecipeTitle(mode: MealMatchMode, mainName: String): String =
        when (mode) {
            MealMatchMode.EMERGENCY_SALVAGE -> "15-Min $mainName Emergency Skillet"
            MealMatchMode.STRICT -> "Zero-Waste Farmhouse $mainName Medley"
            MealMatchMode.MOSTLY -> "Hearty $mainName Pantry Bowl"
            MealMatchMode.SHOPPING -> "Chef's Signature $mainName Creation"
        }
}
