package com.jeffers.pantrycraft.feature.camera.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun BatchReviewEditDialog(
    state: BatchReviewSheetState,
    index: Int,
) {
    AlertDialog(
        onDismissRequest = { state.editingIndex = null },
        title = { Text("Edit captured item") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                BatchReviewEditor(
                    state = state,
                    onScanBarcodeClick = { state.showEmbeddedBarcodeScanner = true },
                    onOpenedChange = { state.editIsOpened = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = state.editName.isNotBlank(),
                onClick = {
                    state.updateItem(index) { state.applyEditsTo(it) }
                    state.editingIndex = null
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = { state.editingIndex = null }) { Text("Cancel") }
        },
    )
}
