package com.jeffers.pantrycraft.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.Repository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PantryViewModel(private val repo: Repository) : ViewModel() {
    val items: StateFlow<List<PantryItem>> = repo.observePantryItems().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun add(item: PantryItem) = viewModelScope.launch { repo.insertPantry(item) }
    fun update(item: PantryItem) = viewModelScope.launch { repo.updatePantry(item) }
    fun delete(item: PantryItem) = viewModelScope.launch { repo.deletePantry(item) }
}