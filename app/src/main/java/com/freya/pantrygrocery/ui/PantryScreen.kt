package com.freya.pantrygrocery.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.freya.pantrygrocery.data.PantryItem

@Composable
fun PantryScreen(viewModel: PantryViewModel) {
    val items by viewModel.items.collectAsState()

    Scaffold(
        topBar = { SmallTopAppBar(title = { Text("Pantry") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* show add sheet */ }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(items) { item ->
                PantryItemRow(item) {
                    viewModel.delete(item)
                }
            }
        }
    }
}

@Composable
fun PantryItemRow(item: PantryItem, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.name, style = MaterialTheme.typography.titleMedium)
            Text(text = "${item.quantity} ${item.unit ?: ""}", style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onDelete) { Text("Delete") }
    }
}
