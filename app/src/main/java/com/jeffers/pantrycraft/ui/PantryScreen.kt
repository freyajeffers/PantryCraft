package com.jeffers.pantrycraft.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jeffers.pantrycraft.data.PantryItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryScreen(viewModel: PantryViewModel) {
    val items by viewModel.items.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Pantry") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                viewModel.add(PantryItem(name = "New item", quantity = 1f, threshold = 0f))
            }) {
                Text("+")
            }
        },
    ) { padding ->
        if (items.isEmpty()) {
            Text("Your pantry is empty.", modifier = Modifier.padding(padding).padding(16.dp))
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(items, key = { it.id }) { item ->
                    PantryItemRow(item) { viewModel.delete(item) }
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
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium)
            Text("${item.quantity} ${item.unit.orEmpty()}", style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onDelete) { Text("Delete") }
    }
}
