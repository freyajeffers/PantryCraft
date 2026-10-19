package com.jeffers.pantrycraft.feature.camera.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jeffers.pantrycraft.data.PantryTaxonomy
import com.jeffers.pantrycraft.feature.camera.vision.ParsedVisionItem

internal class BatchReviewSheetState(
    items: List<ParsedVisionItem>,
) {
    var reviewItems by mutableStateOf(items.map { ReviewItem(it.copy(quantity = it.quantity.coerceAtLeast(1f)), true) })
    var editingIndex by mutableStateOf<Int?>(null)
    var showEmbeddedBarcodeScanner by mutableStateOf(false)

    // Editor fields (moved from the composable)
    var editName by mutableStateOf("")
    var editCategory by mutableStateOf("")
    var editUnit by mutableStateOf("")
    var editCustomUnitName by mutableStateOf("")
    var editStorageLocation by mutableStateOf("")
    var editShelfLifeDays by mutableStateOf(0)
    var editNotes by mutableStateOf("")
    var editIsAromatic by mutableStateOf(false)
    var editIsOpened by mutableStateOf(false)
    var editThresholdText by mutableStateOf("0")
    var editBarcode by mutableStateOf<String?>(null)

    val quantityTextByIndex = mutableStateMapOf<Int, String>()

    val selectedItems: List<ParsedVisionItem>
        get() = reviewItems.filter { it.included }.map { it.item }

    fun loadSingleItem(item: ParsedVisionItem) {
        editName = item.name
        editCategory = item.category
        editUnit = item.unit
        editCustomUnitName = item.customUnitName ?: ""
        editStorageLocation = item.storageLocation
        editShelfLifeDays = item.estimatedShelfLifeDays
        editNotes = item.conditionNotes ?: ""
        editIsAromatic = item.isAromaticOrSeasoning || PantryTaxonomy.isAromaticOrSeasoningCategory(item.category)
        editIsOpened = item.isOpened
        editThresholdText = formatQuantity(item.threshold)
        editBarcode = item.barcode
    }

    fun loadForEditing(index: Int) {
        val item = reviewItems[index].item
        loadSingleItem(item)
        quantityTextByIndex[index] = formatQuantity(item.quantity)
        editingIndex = index
    }

    fun applyEditsTo(item: ParsedVisionItem): ParsedVisionItem =
        item.copy(
            name = editName.trim(),
            category = editCategory.trim().ifEmpty { "OTHER" },
            unit = editUnit.trim().ifEmpty { "COUNT" },
            customUnitName = if (editUnit == "CUSTOM") editCustomUnitName.trim().takeIf { it.isNotEmpty() } else null,
            storageLocation = editStorageLocation.trim().ifEmpty { PantryTaxonomy.storageLocations.first() },
            estimatedShelfLifeDays = editShelfLifeDays,
            conditionNotes = editNotes.trim().takeIf { it.isNotEmpty() },
            isAromaticOrSeasoning = editIsAromatic,
            isOpened = editIsOpened,
            threshold = editThresholdText.toFloatOrNull()?.coerceAtLeast(0f) ?: 0f,
            barcode = editBarcode,
        )

    fun updateItem(
        index: Int,
        transform: (ParsedVisionItem) -> ParsedVisionItem,
    ) {
        reviewItems =
            reviewItems.toMutableList().also { current ->
                current[index] = current[index].copy(item = transform(current[index].item))
            }
    }

    fun toggleIncluded(index: Int) {
        reviewItems =
            reviewItems.toMutableList().also { current ->
                current[index] = current[index].copy(included = !current[index].included)
            }
    }

    fun decreaseQuantity(index: Int) {
        updateItem(index) { it.copy(quantity = (it.quantity - 1f).coerceAtLeast(1f)) }
        quantityTextByIndex[index] = formatQuantity(reviewItems[index].item.quantity)
    }

    fun increaseQuantity(index: Int) {
        updateItem(index) { it.copy(quantity = it.quantity + 1f) }
        quantityTextByIndex[index] = formatQuantity(reviewItems[index].item.quantity)
    }

    fun setQuantityText(
        index: Int,
        text: String,
    ) {
        quantityTextByIndex[index] = text
        text.toFloatOrNull()?.takeIf { it > 0f }?.let { q ->
            updateItem(index) { it.copy(quantity = q) }
        }
    }
}
