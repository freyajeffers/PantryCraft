package com.jeffers.pantrycraft.feature.camera.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jeffers.pantrycraft.data.PantryTaxonomy
import com.jeffers.pantrycraft.feature.camera.vision.ParsedVisionItem

internal class BatchReviewEditorState {
    var name by mutableStateOf("")
    var category by mutableStateOf("")
    var unit by mutableStateOf("")
    var customUnitName by mutableStateOf("")
    var storageLocation by mutableStateOf("")
    var shelfLifeDays by mutableStateOf(0)
    var notes by mutableStateOf("")
    var isAromatic by mutableStateOf(false)
    var isOpened by mutableStateOf(false)
    var thresholdText by mutableStateOf("0")
    var barcode by mutableStateOf<String?>(null)

    fun loadFrom(item: ParsedVisionItem) {
        name = item.name
        category = item.category
        unit = item.unit
        customUnitName = item.customUnitName ?: ""
        storageLocation = item.storageLocation
        shelfLifeDays = item.estimatedShelfLifeDays
        notes = item.conditionNotes ?: ""
        isAromatic = item.isAromaticOrSeasoning || PantryTaxonomy.isAromaticOrSeasoningCategory(item.category)
        isOpened = item.isOpened
        thresholdText = formatQuantity(item.threshold)
        barcode = item.barcode
    }

    fun applyTo(item: ParsedVisionItem): ParsedVisionItem =
        item.copy(
            name = name.trim(),
            category = category.trim().ifEmpty { "OTHER" },
            unit = unit.trim().ifEmpty { "COUNT" },
            customUnitName = if (unit == "CUSTOM") customUnitName.trim().takeIf { it.isNotEmpty() } else null,
            storageLocation = storageLocation.trim().ifEmpty { PantryTaxonomy.storageLocations.first() },
            estimatedShelfLifeDays = shelfLifeDays,
            conditionNotes = notes.trim().takeIf { it.isNotEmpty() },
            isAromaticOrSeasoning = isAromatic,
            isOpened = isOpened,
            threshold = thresholdText.toFloatOrNull()?.coerceAtLeast(0f) ?: 0f,
            barcode = barcode,
        )
}
