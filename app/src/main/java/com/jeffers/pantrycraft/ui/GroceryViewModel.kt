package com.jeffers.pantrycraft.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeffers.pantrycraft.data.GroceryItem
import com.jeffers.pantrycraft.data.Repository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GroceryViewModel(
    private val repo: Repository,
) : ViewModel() {
    val items: StateFlow<List<GroceryItem>> =
        repo.observeGroceryItems().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun add(item: GroceryItem) = viewModelScope.launch { repo.insertGrocery(item) }

    fun update(item: GroceryItem) = viewModelScope.launch { repo.updateGrocery(item) }

    fun delete(item: GroceryItem) = viewModelScope.launch { repo.deleteGrocery(item) }

    fun clearChecked() = viewModelScope.launch { repo.clearCheckedGrocery() }
}
