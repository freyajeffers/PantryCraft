package com.jeffers.pantrycraft.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.PantryTaxonomy
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

private val ExpiredColor = Color(0xFFB3261E)
private val ExpiringSoonColor = Color(0xFFFFB300)
private val FreshColor = Color(0xFF2E7D32)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryScreen(viewModel: PantryViewModel) {
    val items by viewModel.visibleItems.collectAsStateWithLifecycle()
    val selectedLocation by viewModel.selectedLocation.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var newItemName by rememberSaveable { mutableStateOf("") }
    var newCategory by rememberSaveable { mutableStateOf("OTHER") }
    var shelfLifeDays by rememberSaveable { mutableStateOf(0) }
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
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add pantry item") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newItemName,
                        onValueChange = { newItemName = it },
                        singleLine = true,
                        label = { Text("Name") },
                    )
                    Text("Category", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(
                            PantryTaxonomy.categories,
                        ) { category ->
                            FilterChip(
                                selected = newCategory == category,
                                onClick = { newCategory = category },
                                label = { Text(category) },
                            )
                        }
                    }
                    Text("Shelf life", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(PantryTaxonomy.shelfLifeOptionsDays) { days ->
                            FilterChip(
                                selected = shelfLifeDays == days,
                                onClick = { shelfLifeDays = days },
                                label = { Text(if (days == 0) "None" else "$days days") },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newItemName.isNotBlank(),
                    onClick = {
                        viewModel.add(
                            PantryItem(
                                name = newItemName.trim(),
                                quantity = 1f,
                                threshold = 0f,
                                category = newCategory,
                                expirationTimestamp =
                                    shelfLifeDays.takeIf { it > 0 }?.let {
                                        System.currentTimeMillis() +
                                            TimeUnit.DAYS.toMillis(it.toLong())
                                    },
                            ),
                        )
                        newItemName = ""
                        newCategory = "OTHER"
                        shelfLifeDays = 0
                        showAddDialog = false
                    },
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text("Pantry") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add pantry item",
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::setSearchQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                label = { Text("Search pantry") },
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(PantryLocationFilter.entries) { filter ->
                    FilterChip(
                        selected = selectedLocation == filter,
                        onClick = { viewModel.selectLocation(filter) },
                        label = { Text(filter.label) },
                    )
                }
            }

            if (items.isEmpty()) {
                Text("Your pantry is empty.", modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn {
                    items(items, key = { it.id }) { item ->
                        PantryItemRow(
                            item = item,
                            onDecrement = { viewModel.decrement(item, 1f) },
                        ) {
                            viewModel.delete(item)
                            scope.launch {
                                val result =
                                    snackbarHostState.showSnackbar(
                                        message = "${item.name} deleted",
                                        actionLabel = "Undo",
                                    )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.restore(item)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryItemRow(
    item: PantryItem,
    onDecrement: () -> Unit,
    onDelete: () -> Unit,
) {
    val expiration = expirationStatus(item.expirationTimestamp)
    val dismissState =
        rememberSwipeToDismissBoxState(confirmValueChange = { value ->
            value != SwipeToDismissBoxValue.Settled
        })
    LaunchedEffect(dismissState.currentValue) {
        when (dismissState.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> {
                onDecrement()
                dismissState.reset()
            }
            SwipeToDismissBoxValue.EndToStart -> onDelete()
            SwipeToDismissBoxValue.Settled -> Unit
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {},
        content = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                    Text("${item.quantity} ${item.unit.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "${item.storageLocation} • ${expiration.label}",
                        color = expiration.color,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = onDecrement, enabled = item.quantity > 0f) { Text("Use 1") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        },
    )
}

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
                "$message\n\nFree device storage, then retry the operation. Temporary inference media was cleared automatically.",
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
        remaining < TimeUnit.HOURS.toMillis(48) -> ExpirationStatus.EXPIRING_SOON
        remaining <= TimeUnit.DAYS.toMillis(5) -> ExpirationStatus.EXPIRING_SOON
        else -> ExpirationStatus.FRESH
    }
}
