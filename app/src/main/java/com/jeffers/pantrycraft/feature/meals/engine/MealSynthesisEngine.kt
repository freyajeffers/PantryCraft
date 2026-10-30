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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MealSynthesisEngine(
    private val context: Context,
    private val textModel: TextGenerationModel = ByokTextModel(context),
    private val onDeviceModel: OnDeviceMealModel = LiteRtModelEngine(context),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend fun synthesizeMeals(
        inventory: List<PantryItem>,
        mode: MealMatchMode,
        targetPortions: Int,
        constraints: MealConstraints = MealConstraints(),
    ): List<MealRecipe> =
        withContext(dispatcher) {
            val active = inventory.filter { !it.isDeleted && it.quantity > 0f }
            if (active.isEmpty()) return@withContext emptyList()

            val provider = ByokPreferences.selectedProvider(context)
            val compressedInventory = InventoryContextCompressor.compress(active)
            val systemPrompt = MealPromptBuilder.buildSystemPrompt()
            val userPrompt =
                MealPromptBuilder.buildUserPrompt(
                    mode = mode,
                    targetPortions = targetPortions,
                    inventoryContext = compressedInventory,
                    constraints = constraints,
                )

            if (provider == AiProvider.OFFLINE_MLKIT) {
                val onDeviceResult = onDeviceModel.generateMealRecipes(systemPrompt, userPrompt, active)
                val recipes = onDeviceResult.getOrNull().orEmpty()
                return@withContext if (recipes.isNotEmpty()) {
                    recipes.map { it.scaleToPortions(targetPortions) }
                } else {
                    FallbackMealSynthesizer.synthesize(
                        items = active,
                        mode = mode,
                        targetPortions = targetPortions,
                    )
                }
            }

            val useLocalOnly = provider.requiresApiKey && !ByokPreferences.hasApiKey(context)
            if (useLocalOnly) {
                return@withContext FallbackMealSynthesizer.synthesize(
                    items = active,
                    mode = mode,
                    targetPortions = targetPortions,
                )
            }

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
