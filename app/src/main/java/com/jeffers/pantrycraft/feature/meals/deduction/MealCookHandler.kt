package com.jeffers.pantrycraft.feature.meals.deduction

import com.jeffers.pantrycraft.data.GroceryItem
import com.jeffers.pantrycraft.data.GroceryRepository
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.PantryRepository
import com.jeffers.pantrycraft.feature.meals.model.MealRecipe
import kotlinx.coroutines.flow.first

data class CookMealResult(
    val itemsDeductedCount: Int,
    val foodWastePreventedCount: Int,
    val groceryItemsAddedCount: Int,
)

object MealCookHandler {

    suspend fun executeCookMeal(
        recipe: MealRecipe,
        pantryRepository: PantryRepository,
        groceryRepository: GroceryRepository,
        addMissingToGrocery: Boolean = true,
    ): CookMealResult {
        val currentPantry = pantryRepository.observePantryItems().first()
        var deductedCount = 0
        var wastePreventedCount = 0

        for (usage in recipe.itemsUsedFromStock) {
            val matchedItem: PantryItem? = if (usage.pantryItemId != null) {
                currentPantry.firstOrNull { it.id == usage.pantryItemId }
            } else {
                currentPantry.firstOrNull { it.name.equals(usage.name, ignoreCase = true) }
            }

            if (matchedItem != null && usage.quantityUsed > 0f) {
                pantryRepository.decrementQuantity(matchedItem, usage.quantityUsed)
                deductedCount++
                if (usage.preventsWaste) wastePreventedCount++
            }
        }

        var groceryAddedCount = 0
        if (addMissingToGrocery && recipe.missingItemsForGrocery.isNotEmpty()) {
            for (missing in recipe.missingItemsForGrocery) {
                groceryRepository.insertGrocery(
                    GroceryItem(
                        name = missing.name,
                        quantity = missing.quantityNeeded,
                        unit = missing.unit,
                        checked = false,
                    ),
                )
                groceryAddedCount++
            }
        }

        return CookMealResult(
            itemsDeductedCount = deductedCount,
            foodWastePreventedCount = wastePreventedCount,
            groceryItemsAddedCount = groceryAddedCount,
        )
    }
}
