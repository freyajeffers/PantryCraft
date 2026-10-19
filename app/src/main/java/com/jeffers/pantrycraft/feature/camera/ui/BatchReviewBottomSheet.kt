package com.jeffers.pantrycraft.feature.camera.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jeffers.pantrycraft.data.PantryTaxonomy
import com.jeffers.pantrycraft.feature.camera.vision.ParsedVisionItem

@Composable
fun BatchReviewBottomSheet(
    items: List<ParsedVisionItem>,
    onConfirm: (List<ParsedVisionItem>) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Review captured items",
    description: String = "Choose what to add, then adjust quantities before saving.",
) {
    val state = remember(items) { BatchReviewSheetState(items) }
    val selectedItems = state.selectedItems

    LaunchedEffect(items) {
        if (items.size == 1) state.loadSingleItem(items.first())
    }

    if (state.showEmbeddedBarcodeScanner) {
        BatchReviewBarcodeScanner(state)
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
        BatchReviewList(state, items)

        if (items.size == 1) {
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            ) {
                BatchReviewEditor(
                    state = state,
                    onScanBarcodeClick = { state.showEmbeddedBarcodeScanner = true },
                    onOpenedChange = {
                        state.editIsOpened = it
                        if (it && state.editShelfLifeDays == 0) {
                            state.editShelfLifeDays = PantryTaxonomy.openedShelfLifeDays(state.editCategory)
                        }
                    },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onDismiss) { Text("Cancel") }
            Button(
                enabled = selectedItems.isNotEmpty() && (items.size > 1 || state.editName.isNotBlank()),
                onClick = {
                    onConfirm(
                        if (items.size == 1) state.selectedItems.map(state::applyEditsTo) else state.selectedItems,
                    )
                },
            ) {
                Text(if (items.size == 1) "Save" else "Save ${selectedItems.size} selected")
            }
        }
    }
    state.editingIndex?.let { index ->
        BatchReviewEditDialog(state, index)
    }
}
