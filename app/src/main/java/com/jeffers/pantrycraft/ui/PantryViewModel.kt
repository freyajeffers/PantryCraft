package com.jeffers.pantrycraft.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.Repository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val searchedItems: StateFlow<List<PantryItem>> =
        _searchQuery.debounce(50).flatMapLatest(repo::searchPantryItems).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val visibleItems: StateFlow<List<PantryItem>> =
        combine(searchedItems, _selectedLocation, ::filterPantryItems).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun selectLocation(filter: PantryLocationFilter) {
        _selectedLocation.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
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
    EXPIRING_SOON("Expiring soon", null),
}

fun filterPantryItems(
    items: List<PantryItem>,
    filter: PantryLocationFilter,
): List<PantryItem> =
    when (filter) {
        PantryLocationFilter.ALL -> items
        PantryLocationFilter.EXPIRING_SOON -> {
            val cutoff = System.currentTimeMillis() + 5 * 24 * 60 * 60 * 1_000L
            items.filter { item ->
                item.expirationTimestamp?.let { it in System.currentTimeMillis()..cutoff } == true
            }
        }
        else -> {
            val location = requireNotNull(filter.storageLocation)
            items.filter { it.storageLocation.equals(location, ignoreCase = true) }
        }
    }
