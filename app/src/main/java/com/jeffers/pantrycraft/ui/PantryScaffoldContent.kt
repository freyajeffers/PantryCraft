package com.jeffers.pantrycraft.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch

@Composable
fun PantryScaffoldContent(props: PantryScaffoldProps) {
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
                                onDelete = {
                                    props.onDelete(item)
                                    props.scope.launch {
                                        val result =
                                            props.snackbarHostState.showSnackbar(
                                                message = "${item.name} deleted",
                                                actionLabel = "Undo",
                                            )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            props.onRestore(item)
                                        }
                                    }
                                },
                            ),
                    )
                },
            ),
        )
    }
}
