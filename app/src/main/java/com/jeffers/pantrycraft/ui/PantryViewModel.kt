package com.jeffers.pantrycraft.ui

import android.database.sqlite.SQLiteFullException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeffers.pantrycraft.data.GroceryItem
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.PantryTaxonomy
import com.jeffers.pantrycraft.data.Repository
import com.jeffers.pantrycraft.data.insertGrocery
import com.jeffers.pantrycraft.data.observePantryItems
import com.jeffers.pantrycraft.data.searchPantryItems
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

private const val SEARCH_DEBOUNCE_MILLIS = 50L
private const val EXPIRING_SOON_DAYS = 5L
private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1_000L
private const val STATE_TIMEOUT_MILLIS = 5_000L
private const val PANTRY_LOCATION_INDEX_PANTRY = 0
private const val PANTRY_LOCATION_INDEX_FRIDGE = 1
private const val PANTRY_LOCATION_INDEX_FREEZER = 2
private const val PANTRY_LOCATION_INDEX_COUNTER = 3

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class PantryViewModel(
    private val repo: Repository,
) : ViewModel() {
    val items: StateFlow<List<PantryItem>> =
        repo.observePantryItems().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
            initialValue = emptyList(),
        )

    private val _operationErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val operationErrors: SharedFlow<String> = _operationErrors

    private val _selectedLocation = MutableStateFlow(PantryLocationFilter.ALL)
    val selectedLocation: StateFlow<PantryLocationFilter> = _selectedLocation

    private val _selectedSortOrder = MutableStateFlow(PantrySortOrder.NAME_ASC)
    val selectedSortOrder: StateFlow<PantrySortOrder> = _selectedSortOrder

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val searchedItems: StateFlow<List<PantryItem>> =
        _searchQuery.debounce(SEARCH_DEBOUNCE_MILLIS).flatMapLatest(repo::searchPantryItems).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
            initialValue = emptyList(),
        )

    val visibleItems: StateFlow<List<PantryItem>> =
        combine(searchedItems, _selectedLocation, _selectedSortOrder) { items, location, sortOrder ->
            sortPantryItems(filterPantryItems(items, location), sortOrder)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
            initialValue = emptyList(),
        )

    fun selectLocation(filter: PantryLocationFilter) {
        _selectedLocation.value = filter
    }

    fun selectSortOrder(order: PantrySortOrder) {
        _selectedSortOrder.value = order
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

    fun addGroceryItem(
        name: String,
        quantity: Float = 1f,
        unit: String? = null,
        fromPantryId: Long? = null,
    ) = launchDatabaseOperation("Unable to add grocery item") {
        repo.insertGrocery(
            GroceryItem(
                name = name,
                quantity = quantity,
                unit = unit,
                fromPantryId = fromPantryId,
            ),
        )
        _operationErrors.emit("Added $name to grocery list")
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
    PANTRY("Pantry", PantryTaxonomy.storageLocations[PANTRY_LOCATION_INDEX_PANTRY]),
    FRIDGE("Fridge", PantryTaxonomy.storageLocations[PANTRY_LOCATION_INDEX_FRIDGE]),
    FREEZER("Freezer", PantryTaxonomy.storageLocations[PANTRY_LOCATION_INDEX_FREEZER]),
    COUNTER("Counter", PantryTaxonomy.storageLocations[PANTRY_LOCATION_INDEX_COUNTER]),
    EXPIRING_SOON("Expiring soon", null),
}

enum class PantrySortOrder(
    val label: String,
) {
    NAME_ASC("Name (A-Z)"),
    EXPIRATION_SOONEST("Expiring soonest"),
    RECENTLY_UPDATED("Recently updated"),
    CATEGORY("Category"),
    STORAGE_LOCATION("Storage zone"),
}

fun filterPantryItems(
    items: List<PantryItem>,
    filter: PantryLocationFilter,
    now: Long = System.currentTimeMillis(),
): List<PantryItem> =
    when (filter) {
        PantryLocationFilter.ALL -> items
        PantryLocationFilter.EXPIRING_SOON -> {
            val cutoff = now + EXPIRING_SOON_DAYS * MILLIS_PER_DAY
            items.filter { item ->
                item.expirationTimestamp?.let { it <= cutoff } == true
            }
        }
        else -> {
            val location = requireNotNull(filter.storageLocation)
            items.filter { it.storageLocation.equals(location, ignoreCase = true) }
        }
    }

fun sortPantryItems(
    items: List<PantryItem>,
    sortOrder: PantrySortOrder,
): List<PantryItem> =
    when (sortOrder) {
        PantrySortOrder.NAME_ASC -> items.sortedBy { it.name.lowercase() }
        PantrySortOrder.EXPIRATION_SOONEST ->
            items.sortedWith(
                compareBy<PantryItem> { it.expirationTimestamp == null }
                    .thenBy { it.expirationTimestamp }
                    .thenBy { it.name.lowercase() },
            )
        PantrySortOrder.RECENTLY_UPDATED -> items.sortedByDescending { it.updatedAt }
        PantrySortOrder.CATEGORY ->
            items.sortedWith(
                compareBy<PantryItem> { it.category }
                    .thenBy { it.name.lowercase() },
            )
        PantrySortOrder.STORAGE_LOCATION ->
            items.sortedWith(
                compareBy<PantryItem> { it.storageLocation }
                    .thenBy { it.name.lowercase() },
            )
    }
