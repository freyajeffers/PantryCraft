package com.jeffers.pantrycraft.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryTopBar(
    title: String = "Pantry",
    selectedSortOrder: PantrySortOrder,
    onSortSelected: (PantrySortOrder) -> Unit,
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(title) },
        actions = {
            IconButton(onClick = { sortMenuExpanded = true }) {
                Icon(imageVector = Icons.Filled.Sort, contentDescription = "Sort pantry items")
            }
            DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                PantrySortOrder.entries.forEach { order ->
                    DropdownMenuItem(
                        text = {
                            Text(if (selectedSortOrder == order) "✓ ${order.label}" else order.label)
                        },
                        onClick = {
                            onSortSelected(order)
                            sortMenuExpanded = false
                        },
                    )
                }
            }
        },
    )
}
