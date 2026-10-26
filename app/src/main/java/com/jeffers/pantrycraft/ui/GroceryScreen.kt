package com.jeffers.pantrycraft.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jeffers.pantrycraft.data.GroceryItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroceryScreen(viewModel: GroceryViewModel) {
    val items by viewModel.items.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var storageError by rememberSaveable { mutableStateOf<String?>(null) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    val checkedCount = items.count { it.checked }

    LaunchedEffect(viewModel) {
        viewModel.operationErrors.collect { message ->
            if (message.startsWith("Storage is full")) storageError = message
            snackbarHostState.showSnackbar(message)
        }
    }

    storageError?.let { message ->
        StorageDiagnosticDialog(message = message, onDismiss = { storageError = null })
    }

    if (showAddDialog) {
        AddGroceryItemDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, quantity, unit ->
                viewModel.add(
                    GroceryItem(
                        name = name,
                        quantity = quantity,
                        unit = unit,
                    ),
                )
                showAddDialog = false
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GroceryTopBar(
                checkedCount = checkedCount,
                onRestock = viewModel::restockChecked,
                onClear = viewModel::clearChecked,
            )
        },
        floatingActionButton = {
            GroceryAddButton(onClick = { showAddDialog = true })
        },
    ) { padding ->
        GroceryListContent(
            items = items,
            padding = padding,
            onChecked = { item, checked -> viewModel.update(item.copy(checked = checked)) },
            onDelete = viewModel::delete,
        )
    }
}

@Composable
private fun GroceryAddButton(onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        text = { Text("Add") },
        icon = {
            Icon(
                imageVector = Icons.Filled.AddShoppingCart,
                contentDescription = "Add grocery item",
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroceryTopBar(
    checkedCount: Int,
    onRestock: () -> Unit,
    onClear: () -> Unit,
) {
    TopAppBar(
        title = { Text("Grocery list") },
        actions = {
            if (checkedCount > 0) {
                IconButton(onClick = onRestock) {
                    Icon(Icons.Filled.Inventory2, contentDescription = "Restock checked items to pantry")
                }
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.ClearAll, contentDescription = "Clear checked items")
                }
            }
        },
    )
}

@Composable
internal fun GroceryListContent(
    items: List<GroceryItem>,
    padding: PaddingValues,
    onChecked: (GroceryItem, Boolean) -> Unit,
    onDelete: (GroceryItem) -> Unit,
) {
    if (items.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Your grocery list is empty. Tap Add to create items or replenish from low stock.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(items, key = { it.id }) { item ->
                GroceryItemRow(
                    item = item,
                    onChecked = { checked -> onChecked(item, checked) },
                    onDelete = { onDelete(item) },
                )
            }
        }
    }
}

@Composable
fun GroceryItemRow(
    item: GroceryItem,
    onChecked: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.checked, onCheckedChange = onChecked)
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
        ) {
            Text(
                item.name,
                style =
                    if (item.checked) {
                        MaterialTheme.typography.titleMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
            )
            Text(
                "${item.quantity} ${item.unit.orEmpty()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Delete ${item.name}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun AddGroceryItemDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, quantity: Float, unit: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var quantityText by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf("COUNT") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Grocery Item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text("Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unit (COUNT, G, KG, ML, L, CAN)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    val qty = quantityText.toFloatOrNull()?.takeIf { it > 0f } ?: 1f
                    onAdd(name.trim(), qty, unit.trim().uppercase())
                },
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
