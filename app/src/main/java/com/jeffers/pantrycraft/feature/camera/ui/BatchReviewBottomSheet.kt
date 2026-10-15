package com.jeffers.pantrycraft.feature.camera.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jeffers.pantrycraft.data.PantryTaxonomy
import com.jeffers.pantrycraft.feature.camera.vision.ParsedVisionItem
import java.math.BigDecimal

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
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var editName by remember { mutableStateOf("") }
    var editCategory by remember { mutableStateOf("") }
    var editUnit by remember { mutableStateOf("") }
    val quantityTextByIndex = remember { mutableStateMapOf<Int, String>() }
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
                                    editName = reviewItem.item.name
                                    editCategory = reviewItem.item.category
                                    editUnit = reviewItem.item.unit
                                    quantityTextByIndex[index] = formatQuantity(reviewItem.item.quantity)
                                    editingIndex = index
                                },
                            ) {
                                Text("Edit")
                            }
                            TextButton(
                                onClick = {
                                    reviewItems =
                                        reviewItems.toMutableList().also {
                                            it[index] = reviewItem.copy(included = !reviewItem.included)
                                        }
                                },
                            ) {
                                Text(if (reviewItem.included) "Remove" else "Add")
                            }
                        }
                        Text(
                            "Category: ${reviewItem.item.category} · Unit: ${reviewItem.item.unit}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Confidence ${(reviewItem.item.confidence * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (reviewItem.item.unit in PantryTaxonomy.integerQuantityUnits) {
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
                                        quantityTextByIndex[index] = formatQuantity((reviewItem.item.quantity - 1f).coerceAtLeast(1f))
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
                                        quantityTextByIndex[index] = formatQuantity(reviewItem.item.quantity + 1f)
                                    },
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = "Increase quantity")
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = quantityTextByIndex[index] ?: formatQuantity(reviewItem.item.quantity),
                                onValueChange = { text ->
                                    quantityTextByIndex[index] = text
                                    text.toFloatOrNull()?.takeIf { it > 0f }?.let { quantity ->
                                        reviewItems =
                                            reviewItems.toMutableList().also {
                                                it[index] = reviewItem.copy(item = reviewItem.item.copy(quantity = quantity))
                                            }
                                    }
                                },
                                label = { Text("Quantity (${reviewItem.item.unit})") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                            )
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
                Text("Save ${selectedItems.size} selected")
            }
        }
    }
    editingIndex?.let { index ->
        AlertDialog(
            onDismissRequest = { editingIndex = null },
            title = { Text("Edit captured item") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Name") },
                        singleLine = true,
                    )
                    ReviewDropdown(
                        label = "Category",
                        value = editCategory,
                        options = PantryTaxonomy.categories,
                        onSelected = { editCategory = it },
                    )
                    ReviewDropdown(
                        label = "Unit",
                        value = editUnit,
                        options = PantryTaxonomy.units,
                        onSelected = { editUnit = it },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = editName.isNotBlank(),
                    onClick = {
                        reviewItems =
                            reviewItems.toMutableList().also {
                                val current = it[index]
                                it[index] =
                                    current.copy(
                                        item =
                                            current.item.copy(
                                                name = editName.trim(),
                                                category = editCategory.trim().ifEmpty { "OTHER" },
                                                unit = editUnit.trim().ifEmpty { "COUNT" },
                                            ),
                                    )
                            }
                        editingIndex = null
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingIndex = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ReviewDropdown(
    label: String,
    value: String,
    options: List<String>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { expanded = true },
            ) {
                Text(value.ifBlank { "Select $label" }, modifier = Modifier.weight(1f))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelected(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

private fun formatQuantity(quantity: Float) = BigDecimal(quantity.toString()).stripTrailingZeros().toPlainString()
