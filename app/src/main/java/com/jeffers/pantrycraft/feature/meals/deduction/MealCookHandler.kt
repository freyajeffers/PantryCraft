package com.jeffers.pantrycraft.feature.meals.deduction

import androidx.room.withTransaction
import com.jeffers.pantrycraft.data.AppDatabase
import com.jeffers.pantrycraft.data.CulinaryScaling
import com.jeffers.pantrycraft.data.GroceryItem
import com.jeffers.pantrycraft.data.GroceryRepository
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.PantryRepository
import com.jeffers.pantrycraft.data.Repository
import com.jeffers.pantrycraft.feature.meals.model.MealRecipe
import com.jeffers.pantrycraft.feature.meals.model.RecipeMissingItem
import com.jeffers.pantrycraft.feature.meals.model.RecipePantryUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class CookMealResult(
    val itemsDeductedCount: Int,
    val foodWastePreventedCount: Int,
    val groceryItemsAddedCount: Int,
)

object MealCookHandler {
    /**
     * Executes atomic stock deduction and grocery insertion within a Room transaction.
     * Complies strictly with PROJECT.md Interface Contract 1.
     */
    suspend fun executeCookMealTransactional(
        database: AppDatabase,
        recipe: MealRecipe,
        pantryRepository: PantryRepository,
        groceryRepository: GroceryRepository,
        addMissingToGrocery: Boolean = true,
    ): CookMealResult =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                executeDeductionLogic(
                    recipe = recipe,
                    pantryRepository = pantryRepository,
                    groceryRepository = groceryRepository,
                    addMissingToGrocery = addMissingToGrocery,
                )
            }
        }

    /**
     * Backward-compatible entry point. Automatically resolves [AppDatabase] if available;
     * falls back to in-memory execution for unit test doubles.
     */
    suspend fun executeCookMeal(
        recipe: MealRecipe,
        pantryRepository: PantryRepository,
        groceryRepository: GroceryRepository,
        addMissingToGrocery: Boolean = true,
        database: AppDatabase? = (pantryRepository as? Repository)?.db,
    ): CookMealResult =
        if (database != null) {
            executeCookMealTransactional(
                database = database,
                recipe = recipe,
                pantryRepository = pantryRepository,
                groceryRepository = groceryRepository,
                addMissingToGrocery = addMissingToGrocery,
            )
        } else {
            executeDeductionLogic(
                recipe = recipe,
                pantryRepository = pantryRepository,
                groceryRepository = groceryRepository,
                addMissingToGrocery = addMissingToGrocery,
            )
        }

    private suspend fun executeDeductionLogic(
        recipe: MealRecipe,
        pantryRepository: PantryRepository,
        groceryRepository: GroceryRepository,
        addMissingToGrocery: Boolean,
    ): CookMealResult {
        val currentPantry = pantryRepository.observePantryItems().first()
        val (deductedCount, wastePreventedCount) =
            deductStockItems(
                currentPantry = currentPantry,
                itemsUsed = recipe.itemsUsedFromStock,
                pantryRepository = pantryRepository,
            )

        val groceryAddedCount =
            if (addMissingToGrocery && recipe.missingItemsForGrocery.isNotEmpty()) {
                insertMissingGroceryItems(recipe.missingItemsForGrocery, groceryRepository)
            } else {
                0
            }

        return CookMealResult(
            itemsDeductedCount = deductedCount,
            foodWastePreventedCount = wastePreventedCount,
            groceryItemsAddedCount = groceryAddedCount,
        )
    }

    private suspend fun deductStockItems(
        currentPantry: List<PantryItem>,
        itemsUsed: List<RecipePantryUsage>,
        pantryRepository: PantryRepository,
    ): Pair<Int, Int> {
        var deductedCount = 0
        var wastePreventedCount = 0

        for (usage in itemsUsed) {
            val deducted = deductSingleUsage(currentPantry, usage, pantryRepository)
            if (deducted) {
                deductedCount++
                if (usage.preventsWaste) {
                    wastePreventedCount++
                }
            }
        }
        return Pair(deductedCount, wastePreventedCount)
    }

    private suspend fun deductSingleUsage(
        pantry: List<PantryItem>,
        usage: RecipePantryUsage,
        pantryRepository: PantryRepository,
    ): Boolean {
        val validUsage = isDeltaValid(usage.quantityUsed)
        val matchedItem = if (validUsage) resolveMatchedItem(pantry, usage) else null
        val delta = matchedItem?.let { calculateDeductionDelta(usage, it) }
        return if (matchedItem != null && delta != null && isDeltaValid(delta)) {
            pantryRepository.decrementQuantity(matchedItem, delta)
            true
        } else {
            false
        }
    }

    private fun resolveMatchedItem(
        pantry: List<PantryItem>,
        usage: RecipePantryUsage,
    ): PantryItem? {
        val targetId = usage.pantryItemId
        return if (targetId != null) {
            pantry.firstOrNull { it.id == targetId && !it.isDeleted }
        } else {
            val trimmed = usage.name.trim()
            pantry.firstOrNull { it.name.trim().equals(trimmed, ignoreCase = true) && !it.isDeleted }
        }
    }

    private fun calculateDeductionDelta(
        usage: RecipePantryUsage,
        stock: PantryItem,
    ): Float {
        val recipeUnit = usage.unit?.trim().orEmpty()
        val stockUnit = stock.unit?.trim().orEmpty()
        if (isSameOrBlankUnit(recipeUnit, stockUnit)) {
            return usage.quantityUsed
        }
        val converted = CulinaryScaling.convertVolume(usage.quantityUsed, recipeUnit, stockUnit)
        return converted ?: usage.quantityUsed
    }

    private fun isSameOrBlankUnit(
        unitA: String,
        unitB: String,
    ): Boolean = unitA.isEmpty() || unitB.isEmpty() || unitA.equals(unitB, ignoreCase = true)

    private fun isDeltaValid(delta: Float): Boolean = delta > 0f && delta.isFinite()

    private suspend fun insertMissingGroceryItems(
        missingItems: List<RecipeMissingItem>,
        groceryRepository: GroceryRepository,
    ): Int {
        var addedCount = 0
        for (missing in missingItems) {
            val name = missing.name.trim()
            if (name.isEmpty()) continue
            groceryRepository.insertGrocery(
                GroceryItem(
                    name = name,
                    quantity = missing.quantityNeeded,
                    unit = missing.unit?.trim(),
                    checked = false,
                ),
            )
            addedCount++
        }
        return addedCount
    }
}
