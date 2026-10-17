package com.jeffers.pantrycraft.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
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
            TopAppBar(
                title = { Text("Grocery list") },
                actions = {
                    if (checkedCount > 0) {
                        IconButton(
                            onClick = { viewModel.restockChecked() },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Inventory2,
                                contentDescription = "Restock checked items to pantry",
                            )
                        }
                        IconButton(
                            onClick = { viewModel.clearChecked() },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ClearAll,
                                contentDescription = "Clear checked items",
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                text = { Text("Add") },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.AddShoppingCart,
                        contentDescription = "Add grocery item",
                    )
                },
            )
        },
    ) { padding ->
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
                        onChecked = { checked ->
                            viewModel.update(item.copy(checked = checked))
                        },
                        onDelete = { viewModel.delete(item) },
                    )
                }
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
private fun AddGroceryItemDialog(
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
