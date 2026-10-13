package com.jeffers.pantrycraft.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PantryViewModel(
    private val repo: Repository,
) : ViewModel() {
    val items: StateFlow<List<PantryItem>> =
        repo.observePantryItems().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _selectedLocation = MutableStateFlow(PantryLocationFilter.ALL)
    val selectedLocation: StateFlow<PantryLocationFilter> = _selectedLocation

    val visibleItems: StateFlow<List<PantryItem>> =
        combine(items, _selectedLocation, ::filterPantryItems).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun selectLocation(filter: PantryLocationFilter) {
        _selectedLocation.value = filter
    }

    fun add(item: PantryItem) = viewModelScope.launch { repo.insertPantry(item) }

    fun update(item: PantryItem) = viewModelScope.launch { repo.updatePantry(item) }

    fun delete(item: PantryItem) = viewModelScope.launch { repo.deletePantry(item) }
}

enum class PantryLocationFilter(
    val label: String,
    val storageLocation: String?,
) {
    ALL("All", null),
    PANTRY("Pantry", "PANTRY"),
    FRIDGE("Fridge", "FRIDGE"),
    FREEZER("Freezer", "FREEZER"),
}

fun filterPantryItems(
    items: List<PantryItem>,
    filter: PantryLocationFilter,
): List<PantryItem> =
    filter.storageLocation?.let { location ->
        items.filter { it.storageLocation.equals(location, ignoreCase = true) }
    } ?: items
