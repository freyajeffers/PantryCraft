package com.jeffers.pantrycraft.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PantryViewModel(private val repo: Repository) : ViewModel() {

    private val _items = MutableStateFlow<List<PantryItem>>(emptyList())
    val items: StateFlow<List<PantryItem>> = _items

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _items.value = repo.getAllPantryItems()
        }
    }

    fun add(item: PantryItem) {
        viewModelScope.launch {
            repo.insertPantry(item)
            refresh()
        }
    }

    fun update(item: PantryItem) {
        viewModelScope.launch {
            repo.updatePantry(item)
            refresh()
        }
    }

    fun delete(item: PantryItem) {
        viewModelScope.launch {
            repo.deletePantry(item)
            refresh()
        }
    }
}
