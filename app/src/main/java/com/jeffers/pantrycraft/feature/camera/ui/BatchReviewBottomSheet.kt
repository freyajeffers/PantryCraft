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

private const val PERCENT_SCALE = 100

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
        ReviewListContent(
            ReviewListState(
                reviewItems = reviewItems,
                itemsSize = items.size,
                quantityTextByIndex = quantityTextByIndex,
                singleName = editName,
                singleCategory = editCategory,
                singleUnit = editUnit,
                singleCustomUnitName = editCustomUnitName,
                singleStorage = editStorageLocation,
                singleShelfLifeDays = editShelfLifeDays,
                onEdit = { index ->
                val reviewItem = reviewItems[index]
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
            onToggleIncluded = { index ->
                reviewItems = reviewItems.toMutableList().also {
                    it[index] = it[index].copy(included = !it[index].included)
                }
            },
            onDecrease = { index ->
                reviewItems = reviewItems.toMutableList().also {
                    it[index] = it[index].copy(
                        item = it[index].item.copy(
                            quantity = (it[index].item.quantity - 1f).coerceAtLeast(1f),
                        ),
                    )
                }
                quantityTextByIndex[index] = formatQuantity((reviewItems[index].item.quantity).coerceAtLeast(1f))
            },
            onIncrease = { index ->
                reviewItems = reviewItems.toMutableList().also {
                    it[index] = it[index].copy(
                        item = it[index].item.copy(
                            quantity = it[index].item.quantity + 1f,
                        ),
                    )
                }
                quantityTextByIndex[index] = formatQuantity(reviewItems[index].item.quantity)
            },
            onSetQuantityText = { index, text ->
                quantityTextByIndex[index] = text
                text.toFloatOrNull()?.takeIf { it > 0f }?.let { quantity ->
                    reviewItems = reviewItems.toMutableList().also {
                        it[index] = it[index].copy(
                            item = it[index].item.copy(quantity = quantity),
                        )
                    }
                }
                },
            ),
        )
        if (items.size == 1) {
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            ) {
                ReviewEditorFields(
                    ReviewEditorProps(
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
                    ),
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
                        ReviewEditorProps(
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
                        ),
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

private data class ReviewEditorProps(
    val name: String,
    val onNameChange: (String) -> Unit,
    val category: String,
    val onCategoryChange: (String) -> Unit,
    val unit: String,
    val onUnitChange: (String) -> Unit,
    val storageLocation: String,
    val onStorageLocationChange: (String) -> Unit,
    val shelfLifeDays: Int,
    val onShelfLifeChange: (Int) -> Unit,
    val notes: String,
    val onNotesChange: (String) -> Unit,
    val isAromatic: Boolean,
    val onAromaticChange: (Boolean) -> Unit,
    val customUnitName: String = "",
    val onCustomUnitNameChange: (String) -> Unit = {},
    val thresholdText: String = "0",
    val onThresholdTextChange: (String) -> Unit = {},
    val isOpened: Boolean = false,
    val onOpenedChange: (Boolean) -> Unit = {},
    val barcode: String? = null,
    val onScanBarcodeClick: () -> Unit = {},
)

private data class ReviewItemCardState(
    val index: Int,
    val itemsCount: Int,
    val reviewItem: ReviewItem,
    val displayedName: String,
    val displayedCategory: String,
    val displayedUnit: String,
    val displayedStorage: String,
    val displayedShelfLife: Int,
    val rawUnit: String,
    val quantityText: String?,
    val onEdit: () -> Unit,
    val onToggleIncluded: () -> Unit,
    val onDecrease: () -> Unit,
    val onIncrease: () -> Unit,
    val onSetQuantityText: (String) -> Unit,
)

private data class ReviewListState(
    val reviewItems: List<ReviewItem>,
    val itemsSize: Int,
    val quantityTextByIndex: Map<Int, String>,
    val singleName: String,
    val singleCategory: String,
    val singleUnit: String,
    val singleCustomUnitName: String,
    val singleStorage: String,
    val singleShelfLifeDays: Int,
    val onEdit: (Int) -> Unit,
    val onToggleIncluded: (Int) -> Unit,
    val onDecrease: (Int) -> Unit,
    val onIncrease: (Int) -> Unit,
    val onSetQuantityText: (Int, String) -> Unit,
)

@Composable
private fun ReviewListContent(state: ReviewListState) {
    LazyColumn(
        modifier = Modifier.weight(1f, fill = false),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(state.reviewItems) { index, reviewItem ->
            val displayedName = if (state.itemsSize == 1) state.singleName else reviewItem.item.name
            val displayedCategory = if (state.itemsSize == 1) state.singleCategory else reviewItem.item.category
            val rawUnit = if (state.itemsSize == 1) state.singleUnit else reviewItem.item.unit
            val customUnitName =
                if (state.itemsSize == 1) state.singleCustomUnitName else reviewItem.item.customUnitName
            val displayedUnit =
                if (rawUnit == "CUSTOM" && !customUnitName.isNullOrBlank()) customUnitName else rawUnit
            val displayedStorage = if (state.itemsSize == 1) state.singleStorage else reviewItem.item.storageLocation
            val displayedShelfLife =
                if (state.itemsSize == 1) state.singleShelfLifeDays else reviewItem.item.estimatedShelfLifeDays
            ReviewItemCard(
                ReviewItemCardState(
                    index = index,
                    itemsCount = state.itemsSize,
                    reviewItem = reviewItem,
                    displayedName = displayedName,
                    displayedCategory = displayedCategory,
                    displayedUnit = displayedUnit,
                    displayedStorage = displayedStorage,
                    displayedShelfLife = displayedShelfLife,
                    rawUnit = rawUnit,
                    quantityText = state.quantityTextByIndex[index],
                    onEdit = { state.onEdit(index) },
                    onToggleIncluded = { state.onToggleIncluded(index) },
                    onDecrease = { state.onDecrease(index) },
                    onIncrease = { state.onIncrease(index) },
                    onSetQuantityText = { state.onSetQuantityText(index, it) },
                ),
            )
        }
    }
}

@Composable
private fun ReviewItemCard(state: ReviewItemCardState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    state.displayedName.ifBlank { "New item" },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (state.itemsCount > 1) {
                    TextButton(onClick = state.onEdit) { Text("Edit") }
                    TextButton(onClick = state.onToggleIncluded) {
                        Text(if (state.reviewItem.included) "Remove" else "Add")
                    }
                }
            }
            Text(
                "Category: ${state.displayedCategory} · Unit: ${state.displayedUnit}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Storage: ${state.displayedStorage}", style = MaterialTheme.typography.bodySmall)
            Text(
                "Shelf life: ${state.displayedShelfLife.toShelfLifeLabel()}",
                style = MaterialTheme.typography.bodySmall,
            )
            if (state.itemsCount > 1) {
                Text(
                    "Confidence ${(state.reviewItem.item.confidence * PERCENT_SCALE).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state.rawUnit in PantryTaxonomy.integerQuantityUnits) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Quantity", modifier = Modifier.weight(1f))
                    IconButton(enabled = state.reviewItem.item.quantity > 1f, onClick = state.onDecrease) {
                        Icon(Icons.Filled.Remove, contentDescription = "Decrease quantity")
                    }
                    Text(formatQuantity(state.reviewItem.item.quantity))
                    IconButton(onClick = state.onIncrease) {
                        Icon(Icons.Filled.Add, contentDescription = "Increase quantity")
                    }
                }
            } else {
                OutlinedTextField(
                    value = state.quantityText ?: formatQuantity(state.reviewItem.item.quantity),
                    onValueChange = state.onSetQuantityText,
                    label = { Text("Quantity (${state.displayedUnit})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
            }
        }
    }
}

@Composable
private fun ReviewEditorFields(props: ReviewEditorProps) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReviewIdentityFields(props)
        ReviewInventoryFields(props)
        ReviewCheckbox(
            checked = props.isOpened,
            onCheckedChange = props.onOpenedChange,
            label = "Already opened (accelerates perishability)",
        )
        ReviewCheckbox(
            checked = props.isAromatic,
            onCheckedChange = props.onAromaticChange,
            label = "Aromatic / Seasoning (spices, herbs, oils)",
        )
    }
}

@Composable
private fun ReviewIdentityFields(props: ReviewEditorProps) {
    OutlinedTextField(
        value = props.name,
        onValueChange = props.onNameChange,
        label = { Text("Name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            IconButton(onClick = props.onScanBarcodeClick) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan barcode to fill item")
            }
        },
    )
    if (!props.barcode.isNullOrBlank()) {
        Text(
            "Barcode: ${props.barcode}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    ReviewDropdown("Category", props.category, PantryTaxonomy.categories, props.onCategoryChange)
    ReviewDropdown("Unit", props.unit, PantryTaxonomy.units, props.onUnitChange)
    if (props.unit == "CUSTOM") {
        OutlinedTextField(
            value = props.customUnitName,
            onValueChange = props.onCustomUnitNameChange,
            label = { Text("Custom unit (e.g. bunch, cloves, stalks)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    ReviewDropdown(
        "Storage location",
        props.storageLocation,
        PantryTaxonomy.storageLocations,
        props.onStorageLocationChange,
    )
}

@Composable
private fun ReviewInventoryFields(props: ReviewEditorProps) {
    ReviewShelfLifeEditor(
        label = "Shelf life",
        value = props.shelfLifeDays,
        presets = PantryTaxonomy.shelfLifePresetDays,
        onValueChange = props.onShelfLifeChange,
    )
    OutlinedTextField(
        value = props.thresholdText,
        onValueChange = props.onThresholdTextChange,
        label = { Text("Low-stock alert threshold") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = props.notes,
        onValueChange = props.onNotesChange,
        label = { Text("Notes / Condition") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ReviewCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 8.dp),
        )
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
