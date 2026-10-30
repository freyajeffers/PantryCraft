package com.jeffers.pantrycraft.feature.meals.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jeffers.pantrycraft.feature.meals.model.MAX_PORTIONS
import com.jeffers.pantrycraft.feature.meals.model.MIN_PORTIONS
import com.jeffers.pantrycraft.feature.meals.model.MealMatchMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealSynthesisScreen(viewModel: MealSynthesisViewModel) {
    val selectedMode by viewModel.selectedMode.collectAsState()
    val portionCount by viewModel.portionCount.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val recipes by viewModel.recipes.collectAsState()
    val smartSwaps by viewModel.smartSwaps.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.statusMessage.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text("Portion-Aware Meal Synthesis") }) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PanicButtonBanner(onTrigger = viewModel::triggerEmergencySalvage)
            PortionSelectorRow(portions = portionCount, onUpdate = viewModel::updatePortions)
            ModeSelectorChips(selected = selectedMode, onSelect = viewModel::selectMode)
            Button(
                onClick = viewModel::generateMeals,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().testTag("synthesize_meals_button"),
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(if (isLoading) "Synthesizing Meals…" else "Synthesize Meals")
            }
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.testTag("meal_synthesis_loading"))
                }
            } else {
                RecipeListView(recipes = recipes, swaps = smartSwaps, onCook = viewModel::cookMeal)
            }
        }
    }
}

@Composable
private fun PanicButtonBanner(onTrigger: () -> Unit) {
    Button(
        onClick = onTrigger,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth().testTag("panic_button"),
    ) {
        Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text("15-Min Pantry Panic Button (Rescue Expiring Food)")
    }
}

@Composable
private fun PortionSelectorRow(
    portions: Int,
    onUpdate: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Servings / Portions:", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onUpdate(portions - 1) },
                enabled = portions > MIN_PORTIONS,
                modifier = Modifier.testTag("portion_decrease_button"),
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease portions")
            }
            Text(
                text = "$portions",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 8.dp).testTag("portion_value_text"),
            )
            IconButton(
                onClick = { onUpdate(portions + 1) },
                enabled = portions < MAX_PORTIONS,
                modifier = Modifier.testTag("portion_increase_button"),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Increase portions")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSelectorChips(
    selected: MealMatchMode,
    onSelect: (MealMatchMode) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(MealMatchMode.entries) { mode ->
            ElevatedFilterChip(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                label = { Text(mode.title) },
                modifier = Modifier.testTag("mode_chip_${mode.name}"),
            )
        }
    }
}

@Composable
private fun RecipeListView(
    recipes: List<com.jeffers.pantrycraft.feature.meals.model.MealRecipe>,
    swaps: Map<String, List<com.jeffers.pantrycraft.feature.meals.swap.SmartSwapSuggestion>>,
    onCook: (com.jeffers.pantrycraft.feature.meals.model.MealRecipe) -> Unit,
) {
    if (recipes.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "Select a mode, dial your portions, and tap 'Synthesize Meals' to generate recipes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.testTag("meal_recipe_list"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(recipes, key = { it.recipeId }) { recipe ->
                MealRecipeCard(recipe = recipe, swaps = swaps, onCook = onCook)
            }
        }
    }
}
