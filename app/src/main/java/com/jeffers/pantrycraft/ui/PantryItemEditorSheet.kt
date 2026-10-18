package com.jeffers.pantrycraft.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.feature.camera.ui.BatchReviewBottomSheet
import com.jeffers.pantrycraft.feature.camera.vision.NormalizedBox
import com.jeffers.pantrycraft.feature.camera.vision.ParsedVisionItem
import com.jeffers.pantrycraft.feature.camera.vision.toPantryItem
import com.jeffers.pantrycraft.feature.camera.vision.toParsedVisionItem
import com.jeffers.pantrycraft.feature.camera.vision.toUpdatedPantryItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryItemEditorSheet(
    editorItem: PantryItem?,
    onSaveDraft: (isNew: Boolean, item: PantryItem) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        BatchReviewBottomSheet(
            items = listOf(editorItem?.toParsedVisionItem() ?: newPantryDraft()),
            title = if (editorItem == null) "Add pantry item" else "Edit pantry item",
            description = "Review the pantry item details before saving.",
            onConfirm = { drafts ->
                drafts.firstOrNull()?.let { draft ->
                    if (editorItem == null) {
                        onSaveDraft(true, draft.toPantryItem())
                    } else {
                        onSaveDraft(false, draft.toUpdatedPantryItem(editorItem))
                    }
                }
                onDismiss()
            },
            onDismiss = onDismiss,
        )
    }
}

private fun newPantryDraft() =
    ParsedVisionItem(
        name = "",
        category = "OTHER",
        storageLocation = "PANTRY",
        quantity = 1f,
        unit = "COUNT",
        confidence = 1f,
        estimatedShelfLifeDays = 0,
        isAromaticOrSeasoning = false,
        boundingBox = NormalizedBox(0f, 0f, 1f, 1f),
        conditionNotes = null,
    )
