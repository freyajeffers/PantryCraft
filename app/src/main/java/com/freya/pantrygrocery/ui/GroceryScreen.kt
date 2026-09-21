package com.freya.pantrygrocery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.freya.pantrygrocery.data.GroceryItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroceryScreen(viewModel: GroceryViewModel) {
    val items by viewModel.items.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Grocery list") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.add(GroceryItem(name = "New item")) },
                text = { Text("Add") },
                icon = {}
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            Text("Your grocery list is empty.", modifier = Modifier.padding(padding).padding(16.dp))
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(items, key = { it.id }) { item ->
                    GroceryItemRow(item) { checked ->
                        viewModel.update(item.copy(checked = checked))
                    }
                }
            }
        }
    }
}

@Composable
fun GroceryItemRow(item: GroceryItem, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium)
            Text("${item.quantity} ${item.unit.orEmpty()}", style = MaterialTheme.typography.bodySmall)
        }
        Checkbox(checked = item.checked, onCheckedChange = onChecked)
    }
}
