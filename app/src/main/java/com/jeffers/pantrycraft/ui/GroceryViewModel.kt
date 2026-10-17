package com.jeffers.pantrycraft.ui

import android.database.sqlite.SQLiteFullException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeffers.pantrycraft.data.GroceryItem
import com.jeffers.pantrycraft.data.Repository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STATE_TIMEOUT_MILLIS = 5_000L

class GroceryViewModel(
    private val repo: Repository,
) : ViewModel() {
    val items: StateFlow<List<GroceryItem>> =
        repo.observeGroceryItems().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
            initialValue = emptyList(),
        )

    private val _operationErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val operationErrors: SharedFlow<String> = _operationErrors

    fun add(item: GroceryItem) =
        launchDatabaseOperation("Unable to add grocery item") {
            repo.insertGrocery(item)
        }

    fun update(item: GroceryItem) =
        launchDatabaseOperation("Unable to update grocery item") {
            repo.updateGrocery(item)
        }

    fun delete(item: GroceryItem) =
        launchDatabaseOperation("Unable to delete grocery item") {
            repo.deleteGrocery(item)
        }

    fun clearChecked() =
        launchDatabaseOperation("Unable to clear checked grocery items") {
            repo.clearCheckedGrocery()
        }

    fun restockChecked() =
        launchDatabaseOperation("Unable to restock checked grocery items") {
            val count = repo.restockCheckedGroceryToPantry()
            _operationErrors.emit("Restocked $count items to pantry")
        }

    private fun launchDatabaseOperation(
        errorMessage: String,
        operation: suspend () -> Unit,
    ) = viewModelScope.launch {
        try {
            operation()
        } catch (error: CancellationException) {
            throw error
        } catch (_: SQLiteFullException) {
            repo.reclaimTemporaryMedia()
            _operationErrors.emit("Storage is full; temporary media cleared; free space and try again")
        } catch (_: Exception) {
            _operationErrors.emit(errorMessage)
        }
    }
}
