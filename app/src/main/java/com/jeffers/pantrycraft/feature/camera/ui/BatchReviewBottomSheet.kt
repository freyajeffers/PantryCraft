package com.jeffers.pantrycraft.feature.camera.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jeffers.pantrycraft.feature.camera.vision.ParsedVisionItem

private data class ReviewItem(
    val item: ParsedVisionItem,
    val included: Boolean,
)

@Composable
fun BatchReviewBottomSheet(
    items: List<ParsedVisionItem>,
    onConfirm: (List<ParsedVisionItem>) -> Unit,
    onDismiss: () -> Unit,
) {
    var reviewItems by remember(items) {
        mutableStateOf(
            items.map {
                ReviewItem(
                    item = it.copy(quantity = it.quantity.coerceAtLeast(1f)),
                    included = true,
                )
            },
        )
    }
    val selectedItems = reviewItems.filter { it.included }.map { it.item }

    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Review captured items", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Choose what to add, then adjust quantities before saving.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(reviewItems) { index, reviewItem ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                reviewItem.item.name,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = {
                                    reviewItems =
                                        reviewItems.toMutableList().also {
                                            it[index] = reviewItem.copy(included = !reviewItem.included)
                                        }
                                },
                            ) {
                                Text(if (reviewItem.included) "Dismiss" else "Add")
                            }
                        }
                        Text(
                            "${reviewItem.item.unit} · Confidence ${(reviewItem.item.confidence * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Quantity", modifier = Modifier.weight(1f))
                            IconButton(
                                enabled = reviewItem.item.quantity > 1f,
                                onClick = {
                                    reviewItems =
                                        reviewItems.toMutableList().also {
                                            it[index] =
                                                reviewItem.copy(
                                                    item =
                                                        reviewItem.item.copy(
                                                            quantity =
                                                                (reviewItem.item.quantity - 1f).coerceAtLeast(1f),
                                                        ),
                                                )
                                        }
                                },
                            ) {
                                Icon(Icons.Filled.Remove, contentDescription = "Decrease quantity")
                            }
                            Text(formatQuantity(reviewItem.item.quantity))
                            IconButton(
                                onClick = {
                                    reviewItems =
                                        reviewItems.toMutableList().also {
                                            it[index] =
                                                reviewItem.copy(
                                                    item =
                                                        reviewItem.item.copy(
                                                            quantity = reviewItem.item.quantity + 1f,
                                                        ),
                                                )
                                        }
                                },
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Increase quantity")
                            }
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onDismiss) { Text("Cancel") }
            Button(
                enabled = selectedItems.isNotEmpty(),
                onClick = { onConfirm(selectedItems) },
            ) {
                Text("Add ${selectedItems.size} selected")
            }
        }
    }
}

private fun formatQuantity(quantity: Float) =
    when {
        quantity % 1f == 0f -> quantity.toInt().toString()
        else -> "%.1f".format(quantity)
    }
