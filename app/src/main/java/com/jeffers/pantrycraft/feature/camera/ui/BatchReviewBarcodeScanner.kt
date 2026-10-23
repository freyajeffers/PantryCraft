package com.jeffers.pantrycraft.feature.camera.ui

import androidx.compose.runtime.Composable
import com.jeffers.pantrycraft.data.PantryTaxonomy
import com.jeffers.pantrycraft.feature.camera.barcode.BarcodeMatch

internal fun applyScannedBarcodeToState(
    state: BatchReviewSheetState,
    scannedCode: String,
    match: BarcodeMatch?,
) {
    state.editBarcode = scannedCode
    match?.let {
        if (state.editName.isBlank()) state.editName = it.name
        state.editCategory = it.category
        state.editUnit = it.unit
        state.editShelfLifeDays = it.estimatedShelfLifeDays
        if (PantryTaxonomy.isAromaticOrSeasoningCategory(it.category)) state.editIsAromatic = true
    }
    state.showEmbeddedBarcodeScanner = false
}

@Composable
internal fun BatchReviewBarcodeScanner(state: BatchReviewSheetState) {
    EmbeddedBarcodeScannerDialog(
        onDismiss = { state.showEmbeddedBarcodeScanner = false },
        onBarcodeResolved = { scannedCode, match ->
            applyScannedBarcodeToState(state, scannedCode, match)
        },
    )
}
