package com.jeffers.pantrycraft.feature.meals.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jeffers.pantrycraft.feature.meals.model.MealRecipe
import com.jeffers.pantrycraft.feature.meals.swap.SmartSwapSuggestion

@Composable
fun MealRecipeCard(
    recipe: MealRecipe,
    swaps: Map<String, List<SmartSwapSuggestion>>,
    onCook: (MealRecipe) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().testTag("recipe_card_${recipe.recipeId}"),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RecipeHeaderSection(recipe)
            RecipeStockSection(recipe)
            RecipeMissingAndSwapsSection(recipe, swaps)
            RecipeInstructionsSection(recipe)
            RecipeNutritionSection(recipe)
            Button(
                onClick = { onCook(recipe) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .testTag("cook_meal_button_${recipe.recipeId}"),
            ) {
                Icon(Icons.Filled.Restaurant, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Cook This Meal (${recipe.targetPortions} Servings)")
            }
        }
    }
}

@Composable
private fun RecipeHeaderSection(recipe: MealRecipe) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(recipe.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
            Text("${recipe.totalTimeMinutes}m", style = MaterialTheme.typography.labelMedium)
        }
    }
    Text(
        "${recipe.matchMode.title} • ${recipe.targetPortions} Servings",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun RecipeStockSection(recipe: MealRecipe) {
    Text("From Your Pantry:", style = MaterialTheme.typography.labelLarge)
    recipe.itemsUsedFromStock.forEach { item ->
        val unitStr = item.unit?.let { " $it" }.orEmpty()
        val wasteTag = if (item.preventsWaste) " (Rescued!)" else ""
        Text("• ${item.name}: ${item.quantityUsed}$unitStr$wasteTag", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RecipeMissingAndSwapsSection(
    recipe: MealRecipe,
    swaps: Map<String, List<SmartSwapSuggestion>>,
) {
    if (recipe.missingItemsForGrocery.isNotEmpty()) {
        Text("Needed / Smart Swaps:", style = MaterialTheme.typography.labelLarge)
        recipe.missingItemsForGrocery.forEach { missing ->
            val unitStr = missing.unit?.let { " $it" }.orEmpty()
            Text("• ${missing.name} (${missing.quantityNeeded}$unitStr)", style = MaterialTheme.typography.bodyMedium)
            val availableSwaps = swaps[missing.name].orEmpty()
            availableSwaps.forEach { swap ->
                SuggestionChip(
                    onClick = {},
                    label = { Text("Swap: ${swap.substituteName} (${swap.ratioExplanation})") },
                )
            }
        }
    }
}

@Composable
private fun RecipeInstructionsSection(recipe: MealRecipe) {
    Text("Instructions:", style = MaterialTheme.typography.labelLarge)
    recipe.stepByStepInstructions.forEach { step ->
        Text(step, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RecipeNutritionSection(recipe: MealRecipe) {
    val n = recipe.nutritionalEstimates ?: return
    Text(
        "Per Portion: ${n.calories} kcal • ${n.proteinGrams}g Protein • ${n.carbsGrams}g Carbs • ${n.fatGrams}g Fat",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
