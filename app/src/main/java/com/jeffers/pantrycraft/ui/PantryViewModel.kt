package com.jeffers.pantrycraft.ui

import android.database.sqlite.SQLiteFullException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.Repository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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

    private val _operationErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val operationErrors: SharedFlow<String> = _operationErrors

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

    fun add(item: PantryItem) = launchDatabaseOperation("Unable to add pantry item") { repo.insertPantry(item) }

    fun update(item: PantryItem) = launchDatabaseOperation("Unable to update pantry item") { repo.updatePantry(item) }

    fun decrement(
        item: PantryItem,
        delta: Float,
    ) = launchDatabaseOperation("Unable to update pantry quantity") {
        repo.decrementQuantity(item, delta)
    }

    fun delete(item: PantryItem) =
        launchDatabaseOperation("Unable to delete pantry item") {
            repo.deletePantry(item)
        }

    fun restore(item: PantryItem) =
        launchDatabaseOperation("Unable to restore pantry item") {
            repo.restorePantry(item)
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

enum class PantryLocationFilter(
    val label: String,
    val storageLocation: String?,
) {
    ALL("All", null),
    PANTRY("Pantry", "PANTRY"),
    FRIDGE("Fridge", "FRIDGE"),
    FREEZER("Freezer", "FREEZER"),
    COUNTER("Counter", "COUNTER"),
    EXPIRING_SOON("Expiring soon", null),
}

fun filterPantryItems(
    items: List<PantryItem>,
    filter: PantryLocationFilter,
    now: Long = System.currentTimeMillis(),
): List<PantryItem> =
    when (filter) {
        PantryLocationFilter.ALL -> items
        PantryLocationFilter.EXPIRING_SOON -> {
            val cutoff = now + 5 * 24 * 60 * 60 * 1_000L
            items.filter { item ->
                item.expirationTimestamp?.let { it in now..cutoff } == true
            }
        }
        else -> {
            val location = requireNotNull(filter.storageLocation)
            items.filter { it.storageLocation.equals(location, ignoreCase = true) }
        }
    }
