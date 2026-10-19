package com.jeffers.pantrycraft.feature.camera.ui

import androidx.compose.runtime.Composable
import com.jeffers.pantrycraft.feature.camera.vision.ParsedVisionItem

@Composable
internal fun BatchReviewList(
    state: BatchReviewSheetState,
    items: List<ParsedVisionItem>,
) {
    ReviewListContent(
        ReviewListState(
            reviewItems = state.reviewItems,
            itemsSize = items.size,
            quantityTextByIndex = state.quantityTextByIndex,
            singleName = state.editName,
            singleCategory = state.editCategory,
            singleUnit = state.editUnit,
            singleCustomUnitName = state.editCustomUnitName,
            singleStorage = state.editStorageLocation,
            singleShelfLifeDays = state.editShelfLifeDays,
            onEdit = state::loadForEditing,
            onToggleIncluded = state::toggleIncluded,
            onDecrease = state::decreaseQuantity,
            onIncrease = state::increaseQuantity,
            onSetQuantityText = state::setQuantityText,
        ),
    )
}
