package com.jeffers.pantrycraft.feature.camera.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import com.jeffers.pantrycraft.data.Repository
import com.jeffers.pantrycraft.feature.camera.vision.ParsedVisionItem
import com.jeffers.pantrycraft.feature.camera.vision.toPantryItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class CameraBatchReviewState(
    val items: List<ParsedVisionItem>,
    val repository: Repository,
    val scope: CoroutineScope,
    val view: View,
    val onDismiss: () -> Unit,
    val onStatus: (String) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CameraBatchReview(state: CameraBatchReviewState) {
    ModalBottomSheet(onDismissRequest = state.onDismiss) {
        BatchReviewBottomSheet(
            items = state.items,
            onConfirm = { confirmedItems ->
                state.scope.launch {
                    runCatching { state.repository.insertPantryBatch(confirmedItems.map { it.toPantryItem() }) }
                        .onSuccess {
                            state.onDismiss()
                            state.onStatus("Added ${confirmedItems.size} items to your pantry")
                            state.view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        }.onFailure { state.onStatus("Could not add items: ${it.message}") }
                }
            },
            onDismiss = state.onDismiss,
        )
    }
}
