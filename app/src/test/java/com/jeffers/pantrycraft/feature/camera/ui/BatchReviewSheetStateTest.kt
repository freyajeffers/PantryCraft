package com.jeffers.pantrycraft.feature.camera.ui

import com.jeffers.pantrycraft.feature.camera.vision.NormalizedBox
import com.jeffers.pantrycraft.feature.camera.vision.ParsedVisionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchReviewSheetStateTest {
    @Test
    fun initialization_wrapsItemsAndDefaultsQuantities() {
        val rawItems = listOf(
            createItem(name = "Apples", quantity = 3f),
            createItem(name = "Salt", quantity = 0f),
            createItem(name = "Pepper", quantity = -2f),
        )

        val state = BatchReviewSheetState(rawItems)

        assertEquals(3, state.reviewItems.size)
        assertTrue(state.reviewItems.all { it.included })
        assertEquals(3f, state.reviewItems[0].item.quantity)
        assertEquals(1f, state.reviewItems[1].item.quantity)
        assertEquals(1f, state.reviewItems[2].item.quantity)
        assertEquals(3, state.selectedItems.size)
    }

    @Test
    fun toggleIncluded_togglesInclusionAndFiltersSelectedItems() {
        val state = BatchReviewSheetState(
            listOf(
                createItem("Milk", 1f),
                createItem("Butter", 2f),
            ),
        )

        state.toggleIncluded(0)
        assertFalse(state.reviewItems[0].included)
        assertTrue(state.reviewItems[1].included)

        assertEquals(1, state.selectedItems.size)
        assertEquals("Butter", state.selectedItems[0].name)

        state.toggleIncluded(0)
        assertTrue(state.reviewItems[0].included)
        assertEquals(2, state.selectedItems.size)
    }

    @Test
    fun quantityAdjustments_increaseAndDecreaseWithFloorOfOne() {
        val state = BatchReviewSheetState(listOf(createItem("Eggs", 2f)))

        state.increaseQuantity(0)
        assertEquals(3f, state.reviewItems[0].item.quantity)
        assertEquals("3", state.quantityTextByIndex[0])

        state.decreaseQuantity(0)
        assertEquals(2f, state.reviewItems[0].item.quantity)
        assertEquals("2", state.quantityTextByIndex[0])

        state.decreaseQuantity(0)
        assertEquals(1f, state.reviewItems[0].item.quantity)

        // Decreasing at 1 remains 1
        state.decreaseQuantity(0)
        assertEquals(1f, state.reviewItems[0].item.quantity)
        assertEquals("1", state.quantityTextByIndex[0])
    }

    @Test
    fun setQuantityText_acceptsPositiveFloatsAndIgnoresInvalid() {
        val state = BatchReviewSheetState(listOf(createItem("Flour", 1f)))

        state.setQuantityText(0, "4.5")
        assertEquals(4.5f, state.reviewItems[0].item.quantity)
        assertEquals("4.5", state.quantityTextByIndex[0])

        // Negative or non-numeric input preserves previous quantity value
        state.setQuantityText(0, "-1")
        assertEquals(4.5f, state.reviewItems[0].item.quantity)
        assertEquals("-1", state.quantityTextByIndex[0])

        state.setQuantityText(0, "not-a-number")
        assertEquals(4.5f, state.reviewItems[0].item.quantity)
    }

    @Test
    fun loadForEditing_populatesEditorFields() {
        val item = createItem("Extra Virgin Olive Oil", 2f).copy(
            category = "OILS",
            storageLocation = "PANTRY",
            unit = "BOTTLE",
            estimatedShelfLifeDays = 365,
            conditionNotes = "Cold pressed",
            isAromaticOrSeasoning = true,
            isOpened = true,
            threshold = 1f,
            barcode = "012345678901",
        )

        val state = BatchReviewSheetState(listOf(item))
        state.loadForEditing(0)

        assertEquals(0, state.editingIndex)
        assertEquals("Extra Virgin Olive Oil", state.editName)
        assertEquals("OILS", state.editCategory)
        assertEquals("PANTRY", state.editStorageLocation)
        assertEquals("BOTTLE", state.editUnit)
        assertEquals(365, state.editShelfLifeDays)
        assertEquals("Cold pressed", state.editNotes)
        assertTrue(state.editIsAromatic)
        assertTrue(state.editIsOpened)
        assertEquals("1", state.editThresholdText)
        assertEquals("012345678901", state.editBarcode)
    }

    @Test
    fun applyEditsTo_appliesDefaultsAndTrimsInput() {
        val state = BatchReviewSheetState(listOf(createItem("Old Name", 1f)))
        state.loadForEditing(0)

        state.editName = "  Fresh Rosemary  "
        state.editCategory = "" // Should default to OTHER
        state.editUnit = "CUSTOM"
        state.editCustomUnitName = "  sprig  "
        state.editStorageLocation = "" // Should default to first valid storage location
        state.editShelfLifeDays = 14
        state.editNotes = "  Freshly picked  "
        state.editIsAromatic = true
        state.editIsOpened = false
        state.editThresholdText = "2.5"
        state.editBarcode = "111222333"

        val originalItem = state.reviewItems[0].item
        val updatedItem = state.applyEditsTo(originalItem)

        assertEquals("Fresh Rosemary", updatedItem.name)
        assertEquals("OTHER", updatedItem.category)
        assertEquals("CUSTOM", updatedItem.unit)
        assertEquals("sprig", updatedItem.customUnitName)
        assertFalse(updatedItem.storageLocation.isBlank())
        assertEquals(14, updatedItem.estimatedShelfLifeDays)
        assertEquals("Freshly picked", updatedItem.conditionNotes)
        assertTrue(updatedItem.isAromaticOrSeasoning)
        assertFalse(updatedItem.isOpened)
        assertEquals(2.5f, updatedItem.threshold)
        assertEquals("111222333", updatedItem.barcode)
    }

    @Test
    fun loadSingleItemAutomaticallySetsAromaticForSpicesAndCondiments() {
        val spiceItem = createItem("Cumin Seeds", 1f).copy(category = "SPICES", isAromaticOrSeasoning = false)
        val state = BatchReviewSheetState(listOf(spiceItem))

        state.loadSingleItem(spiceItem)
        assertTrue(state.editIsAromatic)

        val condimentItem = createItem("Soy Sauce", 1f).copy(category = "CONDIMENTS", isAromaticOrSeasoning = false)
        state.loadSingleItem(condimentItem)
        assertTrue(state.editIsAromatic)
    }

    @Test
    fun applyEditsToNullsCustomUnitNameWhenUnitIsNotCustom() {
        val state = BatchReviewSheetState(listOf(createItem("Flour", 1f)))
        state.loadForEditing(0)

        state.editUnit = "KG"
        state.editCustomUnitName = "Bag"

        val updated = state.applyEditsTo(state.reviewItems[0].item)
        assertEquals("KG", updated.unit)
        assertNull(updated.customUnitName)
    }

    @Test
    fun applyEditsToHandlesInvalidOrNegativeThreshold() {
        val state = BatchReviewSheetState(listOf(createItem("Sugar", 2f)))
        state.loadForEditing(0)

        state.editThresholdText = "-10"
        val clampedNegative = state.applyEditsTo(state.reviewItems[0].item)
        assertEquals(0f, clampedNegative.threshold)

        state.editThresholdText = "not_a_number"
        val nonNumeric = state.applyEditsTo(state.reviewItems[0].item)
        assertEquals(0f, nonNumeric.threshold)
    }

    @Test
    fun updateItemDirectlyModifiesIndexedReviewItem() {
        val state = BatchReviewSheetState(listOf(createItem("Apple", 1f), createItem("Orange", 2f)))

        state.updateItem(1) { it.copy(name = "Blood Orange", quantity = 5f) }

        assertEquals("Blood Orange", state.reviewItems[1].item.name)
        assertEquals(5f, state.reviewItems[1].item.quantity)
        assertEquals("Apple", state.reviewItems[0].item.name)
    }

    private fun createItem(
        name: String,
        quantity: Float,
    ) = ParsedVisionItem(
        name = name,
        category = "OTHER",
        storageLocation = "PANTRY",
        quantity = quantity,
        unit = "COUNT",
        customUnitName = null,
        estimatedShelfLifeDays = 30,
        confidence = 0.9f,
        isAromaticOrSeasoning = false,
        isOpened = false,
        threshold = 0f,
        conditionNotes = null,
        barcode = null,
        boundingBox = NormalizedBox(0.1f, 0.1f, 0.5f, 0.5f),
    )
}
