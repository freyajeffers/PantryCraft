package com.freya.pantrygrocery.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freya.pantrygrocery.data.GroceryItem
import com.freya.pantrygrocery.data.Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GroceryViewModel(private val repo: Repository) : ViewModel() {

    private val _items = MutableStateFlow<List<GroceryItem>>(emptyList())
    val items: StateFlow<List<GroceryItem>> = _items

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _items.value = repo.getAllGroceryItems()
        }
    }

    fun add(item: GroceryItem) {
        viewModelScope.launch {
            repo.insertGrocery(item)
            refresh()
        }
    }

    fun update(item: GroceryItem) {
        viewModelScope.launch {
            repo.updateGrocery(item)
            refresh()
        }
    }

    fun delete(item: GroceryItem) {
        viewModelScope.launch {
            repo.deleteGrocery(item)
            refresh()
        }
    }

    fun clearChecked() {
        viewModelScope.launch {
            repo.clearCheckedGrocery()
            refresh()
        }
    }
}
