package com.jeffers.pantrycraft.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jeffers.pantrycraft.data.PantryItem
import kotlinx.coroutines.launch

@Composable
@Suppress("LongMethod")
fun PantryScaffoldContent(props: PantryScaffoldProps) {
    var pendingDelete by remember { mutableStateOf<PantryItem?>(null) }
    Scaffold(
        snackbarHost = { SnackbarHost(props.snackbarHostState) },
        topBar = {
            PantryTopBar(
                selectedSortOrder = props.selectedSortOrder,
                onSortSelected = props.onSortSelected,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = props.onAddClick) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add pantry item",
                )
            }
        },
    ) { _ ->
        PantryListContent(
            PantryListContentProps(
                items = props.items,
                selectedLocation = props.selectedLocation,
                searchQuery = props.searchQuery,
                onSearchQueryChange = props.onSearchQueryChange,
                onLocationSelected = props.onLocationSelected,
                itemContent = { item ->
                    PantryItemRow(
                        item = item,
                        actions =
                            PantryItemRowActions(
                                onDecrement = { props.onDecrement(item) },
                                onIncrement = { props.onIncrement(item) },
                                onUse = { props.onUse(item) },
                                onEdit = { props.onEdit(item) },
                                onMarkOpened = { props.onMarkOpened(item) },
                                onAddToGrocery = { props.onAddToGrocery(item) },
                                onDelete = { pendingDelete = item },
                            ),
                    )
                },
            ),
        )
    }
    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${item.name}?") },
            text = { Text("This removes the item from the local pantry.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        props.onDelete(item)
                        props.scope.launch {
                            val result =
                                props.snackbarHostState.showSnackbar(
                                    message = "${item.name} deleted",
                                    actionLabel = "Undo",
                                )
                            if (result == SnackbarResult.ActionPerformed) props.onRestore(item)
                        }
                    },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}
