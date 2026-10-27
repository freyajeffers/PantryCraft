package com.jeffers.pantrycraft.feature.meals.engine

import android.content.Context
import com.jeffers.pantrycraft.data.AiProvider
import com.jeffers.pantrycraft.data.ByokPreferences
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.feature.meals.context.InventoryContextCompressor
import com.jeffers.pantrycraft.feature.meals.model.MealConstraints
import com.jeffers.pantrycraft.feature.meals.model.MealMatchMode
import com.jeffers.pantrycraft.feature.meals.model.MealRecipe
import com.jeffers.pantrycraft.feature.meals.model.MealRecipeParser
import com.jeffers.pantrycraft.feature.meals.prompt.MealPromptBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MealSynthesisEngine(
    private val context: Context,
    private val textModel: TextGenerationModel = ByokTextModel(context),
) {

    suspend fun synthesizeMeals(
        inventory: List<PantryItem>,
        mode: MealMatchMode,
        targetPortions: Int,
        constraints: MealConstraints = MealConstraints(),
    ): List<MealRecipe> = withContext(Dispatchers.Default) {
        val active = inventory.filter { !it.isDeleted && it.quantity > 0f }
        if (active.isEmpty()) return@withContext emptyList()

        val provider = ByokPreferences.selectedProvider(context)
        val useLocalOnly = provider == AiProvider.OFFLINE_MLKIT ||
            (provider.requiresApiKey && !ByokPreferences.hasApiKey(context))

        if (useLocalOnly) {
            return@withContext FallbackMealSynthesizer.synthesize(
                items = active,
                mode = mode,
                targetPortions = targetPortions,
            )
        }

        val compressedInventory = InventoryContextCompressor.compress(active)
        val systemPrompt = MealPromptBuilder.buildSystemPrompt()
        val userPrompt = MealPromptBuilder.buildUserPrompt(
            mode = mode,
            targetPortions = targetPortions,
            inventoryContext = compressedInventory,
            constraints = constraints,
        )

        val completion = runCatching { textModel.generate(systemPrompt, userPrompt) }.getOrNull()
        val recipes = completion?.let { MealRecipeParser.parseRecipes(it) }.orEmpty()

        if (recipes.isNotEmpty()) {
            recipes.map { it.scaleToPortions(targetPortions) }
        } else {
            FallbackMealSynthesizer.synthesize(
                items = active,
                mode = mode,
                targetPortions = targetPortions,
            )
        }
    }
}
