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

internal data class AssociatedBarcodeArgs(
    val name: String,
    val category: String,
    val unit: String,
    val storageLocation: String,
    val shelfLifeDays: Int,
    val currentTimeMillis: Long = System.currentTimeMillis(),
)

internal suspend fun saveAssociatedBarcode(
    state: CameraAssociationState,
    args: AssociatedBarcodeArgs,
) {
    state.resolver.associateBarcode(
        barcode = state.barcode,
        name = args.name,
        category = args.category,
        defaultUnit = args.unit,
        estimatedShelfLifeDays = args.shelfLifeDays,
    )
    val expiration =
        if (args.shelfLifeDays > 0) {
            args.currentTimeMillis + args.shelfLifeDays * MILLIS_PER_DAY
        } else {
            null
        }
    state.repository.insertPantry(
        PantryItem(
            name = args.name,
            barcode = state.barcode,
            quantity = 1f,
            unit = args.unit,
            category = args.category,
            storageLocation = args.storageLocation,
            expirationTimestamp = expiration,
            isAromaticOrSeasoning = PantryTaxonomy.isAromaticOrSeasoningCategory(args.category),
        ),
    )
    state.onStatus("Saved and remembered ${args.name}")
    state.onDismiss()
    state.view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
}

@Composable
internal fun CameraAssociationDialog(state: CameraAssociationState) {
    AssociateBarcodeDialog(
        barcode = state.barcode,
        onDismiss = state.onDismiss,
        onSave = { name, category, unit, storageLocation, shelfLifeDays ->
            state.scope.launch {
                saveAssociatedBarcode(
                    state = state,
                    args =
                        AssociatedBarcodeArgs(
                            name = name,
                            category = category,
                            unit = unit,
                            storageLocation = storageLocation,
                            shelfLifeDays = shelfLifeDays,
                        ),
                )
            }
        },
    )
}
