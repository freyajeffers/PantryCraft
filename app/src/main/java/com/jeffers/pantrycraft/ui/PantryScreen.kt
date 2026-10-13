package com.jeffers.pantrycraft.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add pantry item") },
            text = {
                OutlinedTextField(
                    value = newItemName,
                    onValueChange = { newItemName = it },
                    singleLine = true,
                    label = { Text("Name") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newItemName.isNotBlank(),
                    onClick = {
                        viewModel.add(PantryItem(name = newItemName.trim(), quantity = 1f, threshold = 0f))
                        newItemName = ""
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
                Text("+")
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
                        PantryItemRow(item) {
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

@Composable
fun PantryItemRow(
    item: PantryItem,
    onDelete: () -> Unit,
) {
    val expiration = expirationStatus(item.expirationTimestamp)
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
        TextButton(onClick = onDelete) { Text("Delete") }
    }
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
