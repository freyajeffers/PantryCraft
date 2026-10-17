package com.jeffers.pantrycraft.feature.camera.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
    title: String = "Review captured items",
    description: String = "Choose what to add, then adjust quantities before saving.",
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
    var editCustomUnitName by remember { mutableStateOf("") }
    var editStorageLocation by remember { mutableStateOf("") }
    var editShelfLifeDays by remember { mutableStateOf(0) }
    var editNotes by remember { mutableStateOf("") }
    var editIsAromatic by remember { mutableStateOf(false) }
    var editIsOpened by remember { mutableStateOf(false) }
    var editThresholdText by remember { mutableStateOf("0") }
    var editBarcode by remember { mutableStateOf<String?>(null) }
    var showEmbeddedBarcodeScanner by remember { mutableStateOf(false) }

    val quantityTextByIndex = remember { mutableStateMapOf<Int, String>() }
    val selectedItems = reviewItems.filter { it.included }.map { it.item }

    LaunchedEffect(items) {
        if (items.size == 1) {
            val item = items.first()
            editName = item.name
            editCategory = item.category
            editUnit = item.unit
            editCustomUnitName = item.customUnitName ?: ""
            editStorageLocation = item.storageLocation
            editShelfLifeDays = item.estimatedShelfLifeDays
            editNotes = item.conditionNotes ?: ""
            editIsAromatic =
                item.isAromaticOrSeasoning ||
                PantryTaxonomy.isAromaticOrSeasoningCategory(item.category)
            editIsOpened = item.isOpened
            editThresholdText = formatQuantity(item.threshold)
            editBarcode = item.barcode
        }
    }

    fun applyEditsTo(item: ParsedVisionItem) =
        item.copy(
            name = editName.trim(),
            category = editCategory.trim().ifEmpty { "OTHER" },
            unit = editUnit.trim().ifEmpty { "COUNT" },
            customUnitName = if (editUnit == "CUSTOM") editCustomUnitName.trim().takeIf { it.isNotEmpty() } else null,
            storageLocation =
                editStorageLocation.trim().ifEmpty {
                    PantryTaxonomy.storageLocations.first()
                },
            estimatedShelfLifeDays = editShelfLifeDays,
            conditionNotes = editNotes.trim().takeIf { it.isNotEmpty() },
            isAromaticOrSeasoning = editIsAromatic,
            isOpened = editIsOpened,
            threshold = editThresholdText.toFloatOrNull()?.coerceAtLeast(0f) ?: 0f,
            barcode = editBarcode,
        )

    if (showEmbeddedBarcodeScanner) {
        EmbeddedBarcodeScannerDialog(
            onDismiss = { showEmbeddedBarcodeScanner = false },
            onBarcodeResolved = { scannedCode, match ->
                editBarcode = scannedCode
                if (match != null) {
                    if (editName.isBlank()) editName = match.name
                    editCategory = match.category
                    editUnit = match.unit
                    editShelfLifeDays = match.estimatedShelfLifeDays
                    if (PantryTaxonomy.isAromaticOrSeasoningCategory(match.category)) {
                        editIsAromatic = true
                    }
                }
                showEmbeddedBarcodeScanner = false
            },
        )
    }

    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(
            description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(reviewItems) { index, reviewItem ->
                val displayedName = if (items.size == 1 && editName.isNotBlank()) editName else reviewItem.item.name
                val displayedCategory = if (items.size == 1) editCategory else reviewItem.item.category
                val rawUnit = if (items.size == 1) editUnit else reviewItem.item.unit
                val displayedUnit =
                    if (items.size == 1 && editUnit == "CUSTOM" && editCustomUnitName.isNotBlank()) {
                        editCustomUnitName
                    } else if (reviewItem.item.unit == "CUSTOM" && !reviewItem.item.customUnitName.isNullOrBlank()) {
                        reviewItem.item.customUnitName
                    } else {
                        rawUnit
                    }
                val displayedStorage = if (items.size == 1) editStorageLocation else reviewItem.item.storageLocation
                val displayedShelfLife = if (items.size == 1) editShelfLifeDays else reviewItem.item.estimatedShelfLifeDays

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                displayedName.ifBlank { "New item" },
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            if (items.size > 1) {
                                TextButton(
                                    onClick = {
                                        editName = reviewItem.item.name
                                        editCategory = reviewItem.item.category
                                        editUnit = reviewItem.item.unit
                                        editCustomUnitName = reviewItem.item.customUnitName ?: ""
                                        editStorageLocation = reviewItem.item.storageLocation
                                        editShelfLifeDays = reviewItem.item.estimatedShelfLifeDays
                                        editNotes = reviewItem.item.conditionNotes ?: ""
                                        editIsAromatic = reviewItem.item.isAromaticOrSeasoning
                                        editIsOpened = reviewItem.item.isOpened
                                        editThresholdText = formatQuantity(reviewItem.item.threshold)
                                        editBarcode = reviewItem.item.barcode
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
                                                it[index] = it[index].copy(included = !it[index].included)
                                            }
                                    },
                                ) {
                                    Text(if (reviewItem.included) "Remove" else "Add")
                                }
                            }
                        }
                        Text(
                            "Category: $displayedCategory · Unit: $displayedUnit",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Storage: $displayedStorage",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Shelf life: ${displayedShelfLife.toShelfLifeLabel()}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (items.size > 1) {
                            Text(
                                "Confidence ${(reviewItem.item.confidence * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (rawUnit in PantryTaxonomy.integerQuantityUnits) {
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
                                label = { Text("Quantity ($displayedUnit)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                            )
                        }
                    }
                }
            }
        }
        if (items.size == 1) {
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            ) {
                ReviewEditorFields(
                    name = editName,
                    onNameChange = { editName = it },
                    category = editCategory,
                    onCategoryChange = {
                        editCategory = it
                        if (PantryTaxonomy.isAromaticOrSeasoningCategory(it)) editIsAromatic = true
                    },
                    unit = editUnit,
                    onUnitChange = { editUnit = it },
                    storageLocation = editStorageLocation,
                    onStorageLocationChange = { editStorageLocation = it },
                    shelfLifeDays = editShelfLifeDays,
                    onShelfLifeChange = { editShelfLifeDays = it },
                    notes = editNotes,
                    onNotesChange = { editNotes = it },
                    isAromatic = editIsAromatic,
                    onAromaticChange = { editIsAromatic = it },
                    customUnitName = editCustomUnitName,
                    onCustomUnitNameChange = { editCustomUnitName = it },
                    thresholdText = editThresholdText,
                    onThresholdTextChange = { editThresholdText = it },
                    isOpened = editIsOpened,
                    onOpenedChange = {
                        editIsOpened = it
                        if (it && editShelfLifeDays == 0) {
                            editShelfLifeDays = PantryTaxonomy.openedShelfLifeDays(editCategory)
                        }
                    },
                    barcode = editBarcode,
                    onScanBarcodeClick = { showEmbeddedBarcodeScanner = true },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onDismiss) { Text("Cancel") }
            Button(
                enabled = selectedItems.isNotEmpty() && (items.size > 1 || editName.isNotBlank()),
                onClick = {
                    onConfirm(
                        if (items.size == 1) selectedItems.map(::applyEditsTo) else selectedItems,
                    )
                },
            ) {
                Text(if (items.size == 1) "Save" else "Save ${selectedItems.size} selected")
            }
        }
    }
    editingIndex?.let { index ->
        AlertDialog(
            onDismissRequest = { editingIndex = null },
            title = { Text("Edit captured item") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ReviewEditorFields(
                        name = editName,
                        onNameChange = { editName = it },
                        category = editCategory,
                        onCategoryChange = {
                            editCategory = it
                            if (PantryTaxonomy.isAromaticOrSeasoningCategory(it)) editIsAromatic = true
                        },
                        unit = editUnit,
                        onUnitChange = { editUnit = it },
                        storageLocation = editStorageLocation,
                        onStorageLocationChange = { editStorageLocation = it },
                        shelfLifeDays = editShelfLifeDays,
                        onShelfLifeChange = { editShelfLifeDays = it },
                        notes = editNotes,
                        onNotesChange = { editNotes = it },
                        isAromatic = editIsAromatic,
                        onAromaticChange = { editIsAromatic = it },
                        customUnitName = editCustomUnitName,
                        onCustomUnitNameChange = { editCustomUnitName = it },
                        thresholdText = editThresholdText,
                        onThresholdTextChange = { editThresholdText = it },
                        isOpened = editIsOpened,
                        onOpenedChange = { editIsOpened = it },
                        barcode = editBarcode,
                        onScanBarcodeClick = { showEmbeddedBarcodeScanner = true },
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
                                it[index] = current.copy(item = applyEditsTo(current.item))
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

private fun Int.toShelfLifeLabel() = if (this == 0) "None" else "$this days"

@Composable
private fun ReviewEditorFields(
    name: String,
    onNameChange: (String) -> Unit,
    category: String,
    onCategoryChange: (String) -> Unit,
    unit: String,
    onUnitChange: (String) -> Unit,
    storageLocation: String,
    onStorageLocationChange: (String) -> Unit,
    shelfLifeDays: Int,
    onShelfLifeChange: (Int) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    isAromatic: Boolean,
    onAromaticChange: (Boolean) -> Unit,
    customUnitName: String = "",
    onCustomUnitNameChange: (String) -> Unit = {},
    thresholdText: String = "0",
    onThresholdTextChange: (String) -> Unit = {},
    isOpened: Boolean = false,
    onOpenedChange: (Boolean) -> Unit = {},
    barcode: String? = null,
    onScanBarcodeClick: () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = onScanBarcodeClick) {
                    Icon(
                        imageVector = Icons.Filled.QrCodeScanner,
                        contentDescription = "Scan barcode to fill item",
                    )
                }
            },
        )
        if (!barcode.isNullOrBlank()) {
            Text(
                "Barcode: $barcode",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        ReviewDropdown(
            label = "Category",
            value = category,
            options = PantryTaxonomy.categories,
            onSelected = onCategoryChange,
        )
        ReviewDropdown(
            label = "Unit",
            value = unit,
            options = PantryTaxonomy.units,
            onSelected = onUnitChange,
        )
        if (unit == "CUSTOM") {
            OutlinedTextField(
                value = customUnitName,
                onValueChange = onCustomUnitNameChange,
                label = { Text("Custom unit (e.g. bunch, cloves, stalks)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ReviewDropdown(
            label = "Storage location",
            value = storageLocation,
            options = PantryTaxonomy.storageLocations,
            onSelected = onStorageLocationChange,
        )
        ReviewShelfLifeEditor(
            label = "Shelf life",
            value = shelfLifeDays,
            presets = PantryTaxonomy.shelfLifePresetDays,
            onValueChange = onShelfLifeChange,
        )
        OutlinedTextField(
            value = thresholdText,
            onValueChange = onThresholdTextChange,
            label = { Text("Low-stock alert threshold") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = notes,
            onValueChange = onNotesChange,
            label = { Text("Notes / Condition") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = isOpened,
                onCheckedChange = onOpenedChange,
            )
            Text(
                "Already opened (accelerates perishability)",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = isAromatic,
                onCheckedChange = onAromaticChange,
            )
            Text(
                "Aromatic / Seasoning (spices, herbs, oils)",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun ReviewShelfLifeEditor(
    label: String,
    value: Int,
    presets: List<Int>,
    onValueChange: (Int) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                enabled = value > 0,
                onClick = { onValueChange((value - 1).coerceAtLeast(0)) },
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease $label")
            }
            Text(
                value.toShelfLifeLabel(),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            IconButton(
                onClick = { onValueChange(value + 1) },
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Increase $label")
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presets.forEach { preset ->
                FilterChip(
                    selected = value == preset,
                    onClick = { onValueChange(preset) },
                    label = { Text(preset.toShelfLifeLabel()) },
                )
            }
        }
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
