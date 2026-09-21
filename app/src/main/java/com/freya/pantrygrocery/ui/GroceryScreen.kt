package com.freya.pantrygrocery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.freya.pantrygrocery.data.GroceryItem
import kotlinx.coroutines.launch

@Composable
fun GroceryScreen(viewModel: GroceryViewModel) {
    val items by viewModel.items.collectAsState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { SmallTopAppBar(title = { Text("Grocery") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { /* add flow */ }, text = { Text("Add") })
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(items) { item ->
                GroceryItemRow(item, onChecked = { checked ->
                    scope.launch {
                        viewModel.update(item.copy(checked = checked))
                    }
                })
            }
        }
    }
}

@Composable
fun GroceryItemRow(item: GroceryItem, onChecked: (Boolean) -> Unit) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(text = item.name, style = MaterialTheme.typography.titleMedium)
            Text(text = "${item.quantity} ${item.unit ?: ""}", style = MaterialTheme.typography.bodySmall)
        }
        Checkbox(checked = item.checked, onCheckedChange = onChecked)
    }
}
