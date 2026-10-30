package com.jeffers.pantrycraft.feature.meals.model

import com.jeffers.pantrycraft.data.CulinaryScaling

const val MIN_PORTIONS = 1
const val MAX_PORTIONS = 12

data class MealConstraints(
    val dietaryPreferences: List<String> = emptyList(),
    val availableAppliances: List<String> = emptyList(),
    val maxCookTimeMinutes: Int? = null,
)

enum class MealMatchMode(
    val id: String,
    val title: String,
    val description: String,
) {
    STRICT(
        id = "STRICT",
        title = "Zero-Waste Strict",
        description = "Uses 100% on-hand inventory. Zero purchases required.",
    ),
    MOSTLY(
        id = "MOSTLY",
        title = "Pantry First",
        description = "Uses 85-95% on-hand items; allows up to 2 flexible staples.",
    ),
    SHOPPING(
        id = "SHOPPING",
        title = "Inspirational Chef",
        description = "Features on-hand star ingredients with a grocery delta list.",
    ),
    EMERGENCY_SALVAGE(
        id = "EMERGENCY_SALVAGE",
        title = "15-Min Rescue",
        description = "Rapid rescue meals for items expiring within 48 hours.",
    ),
    ;

    companion object {
        fun fromId(id: String?): MealMatchMode = entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: STRICT
    }
}

data class NutritionalEstimates(
    val calories: Int,
    val proteinGrams: Int,
    val carbsGrams: Int,
    val fatGrams: Int,
)

data class RecipePantryUsage(
    val name: String,
    val pantryItemId: Long? = null,
    val quantityUsed: Float,
    val unit: String? = null,
    val preventsWaste: Boolean = false,
    val isAromaticOrSeasoning: Boolean = false,
)

data class RecipeMissingItem(
    val name: String,
    val quantityNeeded: Float,
    val unit: String? = null,
    val estimatedCostCents: Int? = null,
)

data class MealRecipe(
    val recipeId: String,
    val title: String,
    val matchMode: MealMatchMode = MealMatchMode.STRICT,
    val targetPortions: Int = 2,
    val prepTimeMinutes: Int = 10,
    val cookTimeMinutes: Int = 15,
    val urgencyScore: Float = 0.5f,
    val itemsUsedFromStock: List<RecipePantryUsage> = emptyList(),
    val missingItemsForGrocery: List<RecipeMissingItem> = emptyList(),
    val stepByStepInstructions: List<String> = emptyList(),
    val nutritionalEstimates: NutritionalEstimates? = null,
) {
    val totalTimeMinutes: Int get() = prepTimeMinutes + cookTimeMinutes

    val wastePreventionCount: Int get() = itemsUsedFromStock.count { it.preventsWaste }

    fun scaleToPortions(newPortions: Int): MealRecipe {
        require(newPortions in MIN_PORTIONS..MAX_PORTIONS) { "Target portions must be between 1 and 12" }
        if (newPortions == targetPortions) return this

        val scaledStockItems =
            itemsUsedFromStock.map { item ->
                val scaledQty =
                    CulinaryScaling.scaleQuantity(
                        baseQuantity = item.quantityUsed,
                        basePortions = targetPortions,
                        targetPortions = newPortions,
                        isAromaticOrSeasoning = item.isAromaticOrSeasoning,
                    )
                item.copy(quantityUsed = scaledQty)
            }

        val scaledMissingItems =
            missingItemsForGrocery.map { item ->
                val scaledQty =
                    CulinaryScaling.scaleQuantity(
                        baseQuantity = item.quantityNeeded,
                        basePortions = targetPortions,
                        targetPortions = newPortions,
                        isAromaticOrSeasoning = false,
                    )
                item.copy(quantityNeeded = scaledQty)
            }

        return copy(
            targetPortions = newPortions,
            itemsUsedFromStock = scaledStockItems,
            missingItemsForGrocery = scaledMissingItems,
        )
    }
}
