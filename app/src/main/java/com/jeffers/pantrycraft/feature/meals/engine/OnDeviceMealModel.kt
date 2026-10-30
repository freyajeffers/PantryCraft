package com.jeffers.pantrycraft.feature.meals.engine

import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.feature.meals.model.MealRecipe

/**
 * Hardware and runtime abstraction for on-device culinary generative reasoning.
 */
interface OnDeviceMealModel {
    /**
     * Indicates whether on-device hardware (NPU/GPU/RAM) and model assets
     * (Android AICore Gemini Nano or LiteRT-LM weights) are initialized and ready for execution.
     */
    val isAvailable: Boolean

    /**
     * Executes on-device inference to generate structured meal recipes.
     * Implementations MUST execute heavy computation on Dispatchers.Default,
     * enforce prompt/output memory hygiene (byte zeroing), and gracefully fall back
     * to FallbackMealSynthesizer if model execution fails or is uninitialized.
     */
    suspend fun generateMealRecipes(
        systemPrompt: String,
        userPrompt: String,
        onHandItems: List<PantryItem>,
    ): Result<List<MealRecipe>>
}
