package com.jeffers.pantrycraft.feature.camera.ui

import androidx.compose.runtime.Composable
import com.jeffers.pantrycraft.data.PantryTaxonomy

@Composable
internal fun BatchReviewEditor(
    state: BatchReviewSheetState,
    onScanBarcodeClick: () -> Unit,
    onOpenedChange: (Boolean) -> Unit,
) {
    ReviewEditorFields(
        ReviewEditorProps(
            name = state.editName,
            onNameChange = { state.editName = it },
            category = state.editCategory,
            onCategoryChange = {
                state.editCategory = it
                if (PantryTaxonomy.isAromaticOrSeasoningCategory(state.editCategory)) state.editIsAromatic = true
            },
            unit = state.editUnit,
            onUnitChange = { state.editUnit = it },
            storageLocation = state.editStorageLocation,
            onStorageLocationChange = { state.editStorageLocation = it },
            shelfLifeDays = state.editShelfLifeDays,
            onShelfLifeChange = { state.editShelfLifeDays = it },
            notes = state.editNotes,
            onNotesChange = { state.editNotes = it },
            isAromatic = state.editIsAromatic,
            onAromaticChange = { state.editIsAromatic = it },
            customUnitName = state.editCustomUnitName,
            onCustomUnitNameChange = { state.editCustomUnitName = it },
            thresholdText = state.editThresholdText,
            onThresholdTextChange = { state.editThresholdText = it },
            isOpened = state.editIsOpened,
            onOpenedChange = onOpenedChange,
            barcode = state.editBarcode,
            onScanBarcodeClick = onScanBarcodeClick,
        ),
    )
}
