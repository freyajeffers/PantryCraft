package com.jeffers.pantrycraft.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.PantryTaxonomy

@Composable
internal fun PantryItemActions(
    item: PantryItem,
    isLowStock: Boolean,
    actions: PantryItemRowActions,
) {
    if (!item.isOpened) PantryItemOpenAction(item, actions.onMarkOpened)
    if (isLowStock) PantryItemGroceryAction(item, actions.onAddToGrocery)
    if (item.unit.orEmpty() in PantryTaxonomy.integerQuantityUnits) {
        PantryItemQuantityActions(item, actions)
    } else {
        PantryItemUseAction(item, actions.onUse)
    }
    PantryItemEditDeleteActions(item, actions)
}

@Composable
private fun PantryItemOpenAction(
    item: PantryItem,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier =
            Modifier.clearAndSetSemantics {
                contentDescription = "Open ${item.name}, ${item.storageLocation}, quantity ${formatPantryQuantity(item.quantity)}"
            },
    ) { Text("Open") }
}

@Composable
private fun PantryItemGroceryAction(
    item: PantryItem,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier =
            Modifier.clearAndSetSemantics {
                contentDescription = "Add ${item.name}, ${item.storageLocation}, to grocery list"
            },
    ) {
        Icon(
            Icons.Filled.AddShoppingCart,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun PantryItemQuantityActions(
    item: PantryItem,
    actions: PantryItemRowActions,
) {
    IconButton(
        onClick = actions.onDecrement,
        enabled = item.quantity > 0f,
        modifier =
            Modifier.clearAndSetSemantics {
                contentDescription = "Decrease ${item.name}, ${item.storageLocation}, quantity ${formatPantryQuantity(item.quantity)}"
            },
    ) { Icon(Icons.Filled.Remove, contentDescription = null) }
    Text(formatPantryQuantity(item.quantity))
    IconButton(
        onClick = actions.onIncrement,
        modifier =
            Modifier.clearAndSetSemantics {
                contentDescription = "Increase ${item.name}, ${item.storageLocation}, quantity ${formatPantryQuantity(item.quantity)}"
            },
    ) { Icon(Icons.Filled.Add, contentDescription = null) }
}

@Composable
private fun PantryItemUseAction(
    item: PantryItem,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = item.quantity > 0f,
        modifier =
            Modifier.clearAndSetSemantics {
                contentDescription = "Use ${item.name}, ${item.storageLocation}, quantity ${formatPantryQuantity(item.quantity)}"
            },
    ) { Text("Use") }
}

@Composable
private fun PantryItemEditDeleteActions(
    item: PantryItem,
    actions: PantryItemRowActions,
) {
    TextButton(
        onClick = actions.onEdit,
        modifier =
            Modifier.clearAndSetSemantics {
                contentDescription = "Edit ${item.name}, ${item.storageLocation}, quantity ${formatPantryQuantity(item.quantity)}"
            },
    ) { Text("Edit") }
    TextButton(
        onClick = actions.onDelete,
        modifier =
            Modifier.clearAndSetSemantics {
                contentDescription = "Delete ${item.name}, ${item.storageLocation}, quantity ${formatPantryQuantity(item.quantity)}"
            },
    ) { Text("Delete") }
}
