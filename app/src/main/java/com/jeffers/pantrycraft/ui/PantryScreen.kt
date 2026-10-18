package com.jeffers.pantrycraft.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.PantryTaxonomy
import com.jeffers.pantrycraft.data.markOpened
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

private const val EXPIRED_COLOR_VALUE = 0xFFB3261E
private const val EXPIRING_SOON_COLOR_VALUE = 0xFFFFB300
private const val FRESH_COLOR_VALUE = 0xFF2E7D32
private val ExpiredColor = Color(EXPIRED_COLOR_VALUE)
private val ExpiringSoonColor = Color(EXPIRING_SOON_COLOR_VALUE)
private val FreshColor = Color(FRESH_COLOR_VALUE)
private const val EXPIRING_SOON_HOURS = 48L
private const val EXPIRING_SOON_DAYS = 5L

private data class PantryItemRowActions(
    val onDecrement: () -> Unit,
    val onIncrement: () -> Unit,
    val onUse: () -> Unit,
    val onEdit: () -> Unit,
    val onMarkOpened: () -> Unit,
    val onAddToGrocery: () -> Unit,
    val onDelete: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryScreen(viewModel: PantryViewModel) {
    val items by viewModel.visibleItems.collectAsStateWithLifecycle()
    val selectedLocation by viewModel.selectedLocation.collectAsStateWithLifecycle()
    val selectedSortOrder by viewModel.selectedSortOrder.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var editorItem by remember { mutableStateOf<PantryItem?>(null) }
    var useItem by remember { mutableStateOf<PantryItem?>(null) }
    var useAmount by rememberSaveable { mutableStateOf("") }
    var storageError by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.operationErrors.collect { message ->
            if (message.startsWith("Storage is full")) storageError = message
            snackbarHostState.showSnackbar(message)
        }
    }

    storageError?.let { message ->
        StorageDiagnosticDialog(
            message = message,
            onDismiss = { storageError = null },
        )
    }

    if (showAddDialog) {
        PantryItemEditorSheet(
            editorItem = editorItem,
            onSaveDraft = { isNew, item ->
                if (isNew) {
                    viewModel.add(item)
                } else {
                    viewModel.update(item)
                }
            },
            onDismiss = { showAddDialog = false },
        )
    }

    useItem?.let { item ->
        UsePantryItemDialog(
            item = item,
            amount = useAmount,
            onAmountChange = { useAmount = it },
            onConfirm = { amount ->
                viewModel.decrement(item, amount)
                useItem = null
            },
            onDismiss = { useItem = null },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            PantryTopBar(
                selectedSortOrder = selectedSortOrder,
                onSortSelected = viewModel::selectSortOrder,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editorItem = null
                showAddDialog = true
            }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add pantry item",
                )
            }
        },
    ) { _ ->
        PantryListContent(
            PantryListContentProps(
                items = items,
                selectedLocation = selectedLocation,
                searchQuery = searchQuery,
                onSearchQueryChange = viewModel::setSearchQuery,
                onLocationSelected = viewModel::selectLocation,
                itemContent = { item ->
                    PantryItemRow(
                        item = item,
                        actions = PantryItemRowActions(
                            onDecrement = { viewModel.decrement(item, 1f) },
                            onIncrement = { viewModel.update(item.copy(quantity = item.quantity + 1f)) },
                            onUse = {
                                useAmount = ""
                                useItem = item
                            },
                            onEdit = {
                                editorItem = item
                                showAddDialog = true
                            },
                            onMarkOpened = { viewModel.update(item.markOpened()) },
                            onAddToGrocery = {
                                viewModel.addGroceryItem(
                                    name = item.name,
                                    quantity = 1f,
                                    unit = item.unit,
                                    fromPantryId = item.id,
                                )
                            },
                            onDelete = {
                                viewModel.delete(item)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "${item.name} deleted",
                                        actionLabel = "Undo",
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.restore(item)
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

@Composable
private fun UsePantryItemDialog(
    item: PantryItem,
    amount: String,
    onAmountChange: (String) -> Unit,
    onConfirm: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val unitLabel = if (item.unit == "CUSTOM" && !item.customUnitName.isNullOrBlank()) {
        item.customUnitName
    } else {
        item.unit.orEmpty()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Use ${item.name}") },
        text = {
            OutlinedTextField(
                value = amount,
                onValueChange = onAmountChange,
                label = { Text("Amount ($unitLabel)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                enabled = amount.toFloatOrNull()?.let { it > 0f } == true,
                onClick = { amount.toFloatOrNull()?.takeIf { it > 0f }?.let(onConfirm) },
            ) { Text("Use") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PantrySwipeBackground(state: androidx.compose.material3.SwipeToDismissBoxState) {
    val direction = state.dismissDirection
    val color = when (state.targetValue) {
        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val alignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
    val icon = if (direction == SwipeToDismissBoxValue.StartToEnd) Icons.Filled.Remove else Icons.Filled.Delete
    val description = if (direction == SwipeToDismissBoxValue.StartToEnd) "Use 1" else "Delete"
    Box(
        modifier = Modifier.fillMaxSize().background(color).padding(horizontal = 20.dp),
        contentAlignment = alignment,
    ) {
        Icon(imageVector = icon, contentDescription = description)
    }
}

@Composable
private fun PantryItemContent(
    item: PantryItem,
    expiration: ExpirationStatus,
    isLowStock: Boolean,
    actions: PantryItemRowActions,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium)
            val unitDisplay =
                if (item.unit == "CUSTOM" && !item.customUnitName.isNullOrBlank()) {
                    item.customUnitName
                } else {
                    item.unit.orEmpty()
                }
            Text("${item.quantity} $unitDisplay", style = MaterialTheme.typography.bodySmall)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "${item.storageLocation} • ${expiration.label}",
                    color = expiration.color,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (item.isOpened) {
                    Text(
                        "• Opened",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (isLowStock) {
                    Text(
                        "• Low Stock",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        PantryItemActions(item, isLowStock, actions)
    }
}

@Composable
private fun PantryItemActions(item: PantryItem, isLowStock: Boolean, actions: PantryItemRowActions) {
        if (!item.isOpened) TextButton(onClick = actions.onMarkOpened) { Text("Open") }
        if (isLowStock) {
            IconButton(onClick = actions.onAddToGrocery) {
                Icon(
                    Icons.Filled.AddShoppingCart,
                    contentDescription = "Add to grocery list",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (item.unit.orEmpty() in PantryTaxonomy.integerQuantityUnits) {
            IconButton(onClick = actions.onDecrement, enabled = item.quantity > 0f) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease quantity")
            }
            Text(formatPantryQuantity(item.quantity))
            IconButton(onClick = actions.onIncrement) {
                Icon(Icons.Filled.Add, contentDescription = "Increase quantity")
            }
        } else {
            TextButton(onClick = actions.onUse, enabled = item.quantity > 0f) { Text("Use") }
        }
        TextButton(onClick = actions.onEdit) { Text("Edit") }
        TextButton(onClick = actions.onDelete) { Text("Delete") }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PantryItemRow(
    item: PantryItem,
    actions: PantryItemRowActions,
) {
    val expiration = expirationStatus(item.expirationTimestamp)
    val isLowStock = item.threshold > 0f && item.quantity <= item.threshold
    val haptics = LocalHapticFeedback.current

    val dismissState =
        rememberSwipeToDismissBoxState(confirmValueChange = { value ->
            value != SwipeToDismissBoxValue.Settled
        })
    LaunchedEffect(dismissState.currentValue) {
        when (dismissState.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                actions.onDecrement()
                dismissState.reset()
            }
            SwipeToDismissBoxValue.EndToStart -> {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                actions.onDelete()
                dismissState.reset()
            }
            SwipeToDismissBoxValue.Settled -> Unit
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { PantrySwipeBackground(dismissState) },
        content = {
            PantryItemContent(item, expiration, isLowStock, actions)
        },
    )
}

private fun formatPantryQuantity(quantity: Float) = BigDecimal(quantity.toString()).stripTrailingZeros().toPlainString()

@Composable
fun StorageDiagnosticDialog(
    message: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Storage space required") },
        text = {
            Text(
                "$message\n\nFree device storage, then retry the operation. " +
                    "Temporary inference media was cleared automatically.",
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

enum class ExpirationStatus(
    val label: String,
    val color: Color,
) {
    NONE("No expiration", FreshColor),
    FRESH("Fresh", FreshColor),
    EXPIRING_SOON("Expiring soon", ExpiringSoonColor),
    EXPIRED("Expired", ExpiredColor),
}

fun expirationStatus(
    expirationTimestamp: Long?,
    now: Long = System.currentTimeMillis(),
): ExpirationStatus {
    if (expirationTimestamp == null) return ExpirationStatus.NONE
    val remaining = expirationTimestamp - now
    return when {
        remaining < 0 -> ExpirationStatus.EXPIRED
        remaining < TimeUnit.HOURS.toMillis(EXPIRING_SOON_HOURS) -> ExpirationStatus.EXPIRING_SOON
        remaining <= TimeUnit.DAYS.toMillis(EXPIRING_SOON_DAYS) -> ExpirationStatus.EXPIRING_SOON
        else -> ExpirationStatus.FRESH
    }
}
