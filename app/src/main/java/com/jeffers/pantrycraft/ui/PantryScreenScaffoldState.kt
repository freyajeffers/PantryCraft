package com.jeffers.pantrycraft.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.markOpened
import kotlinx.coroutines.CoroutineScope

internal data class PantryScreenScaffoldState(
    val viewModel: PantryViewModel,
    val items: List<PantryItem>,
    val selectedLocation: PantryLocationFilter,
    val selectedSortOrder: PantrySortOrder,
    val searchQuery: String,
    val onOpenEditor: (PantryItem?) -> Unit,
    val onUseItem: (PantryItem) -> Unit,
    val snackbarHostState: SnackbarHostState,
    val scope: CoroutineScope,
)

internal fun buildPantryScaffoldProps(state: PantryScreenScaffoldState): PantryScaffoldProps =
    PantryScaffoldProps(
        selectedSortOrder = state.selectedSortOrder,
        onSortSelected = state.viewModel::selectSortOrder,
        onAddClick = { state.onOpenEditor(null) },
        items = state.items,
        selectedLocation = state.selectedLocation,
        searchQuery = state.searchQuery,
        onSearchQueryChange = state.viewModel::setSearchQuery,
        onLocationSelected = state.viewModel::selectLocation,
        onDecrement = { state.viewModel.decrement(it, 1f) },
        onIncrement = { state.viewModel.update(it.copy(quantity = it.quantity + 1f)) },
        onUse = state.onUseItem,
        onEdit = state.onOpenEditor,
        onMarkOpened = { state.viewModel.update(it.markOpened()) },
        onAddToGrocery = {
            state.viewModel.addGroceryItem(it.name, 1f, it.unit, fromPantryId = it.id)
        },
        onDelete = state.viewModel::delete,
        onRestore = state.viewModel::restore,
        snackbarHostState = state.snackbarHostState,
        scope = state.scope,
    )

@Composable
internal fun PantryScreenScaffold(state: PantryScreenScaffoldState) {
    PantryScaffoldContent(buildPantryScaffoldProps(state))
}
