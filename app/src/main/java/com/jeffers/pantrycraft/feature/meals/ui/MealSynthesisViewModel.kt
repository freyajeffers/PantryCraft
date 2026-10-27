package com.jeffers.pantrycraft.feature.meals.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jeffers.pantrycraft.data.GroceryRepository
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.PantryRepository
import com.jeffers.pantrycraft.data.Repository
import com.jeffers.pantrycraft.feature.meals.deduction.MealCookHandler
import com.jeffers.pantrycraft.feature.meals.engine.MealSynthesisEngine
import com.jeffers.pantrycraft.feature.meals.model.MAX_PORTIONS
import com.jeffers.pantrycraft.feature.meals.model.MIN_PORTIONS
import com.jeffers.pantrycraft.feature.meals.model.MealMatchMode
import com.jeffers.pantrycraft.feature.meals.model.MealRecipe
import com.jeffers.pantrycraft.feature.meals.swap.SmartSwapEngine
import com.jeffers.pantrycraft.feature.meals.swap.SmartSwapSuggestion
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L

class MealSynthesisViewModel(
    private val pantryRepository: PantryRepository,
    private val groceryRepository: GroceryRepository,
    private val synthesisEngine: MealSynthesisEngine,
) : ViewModel() {

    val inventory: StateFlow<List<PantryItem>> = pantryRepository.observePantryItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS), emptyList())

    private val _selectedMode = MutableStateFlow(MealMatchMode.STRICT)
    val selectedMode: StateFlow<MealMatchMode> = _selectedMode.asStateFlow()

    private val _portionCount = MutableStateFlow(2)
    val portionCount: StateFlow<Int> = _portionCount.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _recipes = MutableStateFlow<List<MealRecipe>>(emptyList())
    val recipes: StateFlow<List<MealRecipe>> = _recipes.asStateFlow()

    private val _smartSwaps = MutableStateFlow<Map<String, List<SmartSwapSuggestion>>>(emptyMap())
    val smartSwaps: StateFlow<Map<String, List<SmartSwapSuggestion>>> = _smartSwaps.asStateFlow()

    private val _statusMessage = MutableSharedFlow<String>()
    val statusMessage: SharedFlow<String> = _statusMessage.asSharedFlow()

    fun selectMode(mode: MealMatchMode) {
        _selectedMode.value = mode
    }

    fun updatePortions(count: Int) {
        val clamped = count.coerceIn(MIN_PORTIONS, MAX_PORTIONS)
        _portionCount.value = clamped
        _recipes.value = _recipes.value.map { it.scaleToPortions(clamped) }
    }

    fun generateMeals() {
        viewModelScope.launch {
            _isLoading.value = true
            val items = inventory.value
            val mode = _selectedMode.value
            val portions = _portionCount.value
            val generated = runCatching {
                synthesisEngine.synthesizeMeals(items, mode, portions)
            }.getOrDefault(emptyList())

            _recipes.value = generated
            val missingNames = generated.flatMap { r -> r.missingItemsForGrocery.map { it.name } }
            _smartSwaps.value = SmartSwapEngine.enrichMissingItemsWithSwaps(missingNames, items)
            _isLoading.value = false
            if (generated.isEmpty()) {
                _statusMessage.emit("No recipes found with current inventory.")
            }
        }
    }

    fun triggerEmergencySalvage() {
        _selectedMode.value = MealMatchMode.EMERGENCY_SALVAGE
        generateMeals()
    }

    fun cookMeal(recipe: MealRecipe) {
        viewModelScope.launch {
            val result = MealCookHandler.executeCookMeal(
                recipe = recipe,
                pantryRepository = pantryRepository,
                groceryRepository = groceryRepository,
            )
            val msg = "Cooked '${recipe.title}'! Deducted ${result.itemsDeductedCount} items."
            _statusMessage.emit(msg)
        }
    }
}

class MealSynthesisViewModelFactory(
    private val repository: Repository,
    private val engine: MealSynthesisEngine,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        MealSynthesisViewModel(repository, repository, engine) as T
}
