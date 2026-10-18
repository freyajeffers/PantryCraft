package com.jeffers.pantrycraft.ui

import androidx.compose.material3.SnackbarHostState
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.PantryLocationFilter
import com.jeffers.pantrycraft.data.PantrySortOrder
import kotlinx.coroutines.CoroutineScope

data class PantryScaffoldProps(
    val selectedSortOrder: PantrySortOrder,
    val onSortSelected: (PantrySortOrder) -> Unit,
    val onAddClick: () -> Unit,
    val items: List<PantryItem>,
    val selectedLocation: PantryLocationFilter,
    val searchQuery: String,
    val onSearchQueryChange: (String) -> Unit,
    val onLocationSelected: (PantryLocationFilter) -> Unit,
    val onDecrement: (PantryItem) -> Unit,
    val onIncrement: (PantryItem) -> Unit,
    val onUse: (PantryItem) -> Unit,
    val onEdit: (PantryItem) -> Unit,
    val onMarkOpened: (PantryItem) -> Unit,
    val onAddToGrocery: (PantryItem) -> Unit,
    val onDelete: (PantryItem) -> Unit,
    val onRestore: (PantryItem) -> Unit,
    val snackbarHostState: SnackbarHostState,
    val scope: CoroutineScope,
)
