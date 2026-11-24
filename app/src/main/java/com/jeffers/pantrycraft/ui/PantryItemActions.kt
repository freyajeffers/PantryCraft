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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
            Modifier.semantics {
                contentDescription = "Open"
                stateDescription = item.actionStateDescription()
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
            Modifier.semantics {
                contentDescription = "Add to grocery list"
                stateDescription = "${item.name}, ${item.storageLocation}"
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
            Modifier.semantics {
                contentDescription = "Decrease quantity"
                stateDescription = item.actionStateDescription()
            },
    ) { Icon(Icons.Filled.Remove, contentDescription = null) }
    Text(formatPantryQuantity(item.quantity))
    IconButton(
        onClick = actions.onIncrement,
        modifier =
            Modifier.semantics {
                contentDescription = "Increase quantity"
                stateDescription = item.actionStateDescription()
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
            Modifier.semantics {
                contentDescription = "Use"
                stateDescription = item.actionStateDescription()
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
            Modifier.semantics {
                contentDescription = "Edit"
                stateDescription = item.actionStateDescription()
            },
    ) { Text("Edit") }
    TextButton(
        onClick = actions.onDelete,
        modifier =
            Modifier.semantics {
                contentDescription = "Delete"
                stateDescription = item.actionStateDescription()
            },
    ) { Text("Delete") }
}

private fun PantryItem.actionStateDescription(): String =
    buildString {
        append(name)
        append(", ")
        append(storageLocation)
        append(", quantity ")
        append(formatPantryQuantity(quantity))
    }
