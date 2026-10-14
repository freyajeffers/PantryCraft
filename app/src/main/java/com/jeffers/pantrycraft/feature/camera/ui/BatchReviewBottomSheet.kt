package com.jeffers.pantrycraft.feature.camera.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jeffers.pantrycraft.feature.camera.vision.ParsedVisionItem

@Composable
fun BatchReviewBottomSheet(
    items: List<ParsedVisionItem>,
    onConfirm: (List<ParsedVisionItem>) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Review captured items", style = MaterialTheme.typography.headlineSmall)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium)
                            Text("${item.quantity} ${item.unit} · ${item.storageLocation}")
                            Text("Confidence ${(item.confidence * 100).toInt()}%")
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onDismiss) { Text("Cancel") }
            Button(onClick = { onConfirm(items) }) { Text("Add all") }
        }
    }
}
