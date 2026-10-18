package com.jeffers.pantrycraft.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.PantryLocationFilter

data class PantryListContentProps(
    val items: List<PantryItem>,
    val selectedLocation: PantryLocationFilter,
    val searchQuery: String,
    val onSearchQueryChange: (String) -> Unit,
    val onLocationSelected: (PantryLocationFilter) -> Unit,
    val itemContent: @Composable (PantryItem) -> Unit,
)

@Composable
fun PantryListContent(props: PantryListContentProps) {
    Column {
        OutlinedTextField(
            value = props.searchQuery,
            onValueChange = props.onSearchQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            label = { Text("Search pantry") },
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            items(PantryLocationFilter.entries) { filter ->
                FilterChip(
                    selected = props.selectedLocation == filter,
                    onClick = { props.onLocationSelected(filter) },
                    label = { Text(filter.label) },
                )
            }
        }
        if (props.items.isEmpty()) {
            Text("Your pantry is empty.", modifier = Modifier.padding(16.dp))
        } else {
            LazyColumn {
                items(props.items, key = { it.id }, content = props.itemContent)
            }
        }
    }
}
