package com.jeffers.pantrycraft.feature.camera.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.PantryTaxonomy
import com.jeffers.pantrycraft.data.Repository
import com.jeffers.pantrycraft.feature.camera.barcode.OfflineBarcodeResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val MILLIS_PER_DAY = 86_400_000L

internal data class CameraAssociationState(
    val barcode: String,
    val resolver: OfflineBarcodeResolver,
    val repository: Repository,
    val scope: CoroutineScope,
    val view: View,
    val onStatus: (String) -> Unit,
    val onDismiss: () -> Unit,
)

@Composable
internal fun CameraAssociationDialog(state: CameraAssociationState) {
    AssociateBarcodeDialog(
        barcode = state.barcode,
        onDismiss = state.onDismiss,
        onSave = { name, category, unit, storageLocation, shelfLifeDays ->
            state.scope.launch {
                state.resolver.associateBarcode(
                    barcode = state.barcode,
                    name = name,
                    category = category,
                    defaultUnit = unit,
                    estimatedShelfLifeDays = shelfLifeDays,
                )
                val expiration =
                    if (shelfLifeDays > 0) {
                        System.currentTimeMillis() + shelfLifeDays * MILLIS_PER_DAY
                    } else {
                        null
                    }
                state.repository.insertPantry(
                    PantryItem(
                        name = name,
                        barcode = state.barcode,
                        quantity = 1f,
                        unit = unit,
                        category = category,
                        storageLocation = storageLocation,
                        expirationTimestamp = expiration,
                        isAromaticOrSeasoning = PantryTaxonomy.isAromaticOrSeasoningCategory(category),
                    ),
                )
                state.onStatus("Saved and remembered $name")
                state.onDismiss()
                state.view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            }
        },
    )
}
