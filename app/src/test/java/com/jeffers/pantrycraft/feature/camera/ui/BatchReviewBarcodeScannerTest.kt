package com.jeffers.pantrycraft.feature.camera.ui

import com.jeffers.pantrycraft.feature.camera.barcode.BarcodeMatch
import com.jeffers.pantrycraft.feature.camera.vision.NormalizedBox
import com.jeffers.pantrycraft.feature.camera.vision.ParsedVisionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchReviewBarcodeScannerTest {

    private fun sampleItem(name: String = "Test Item") = ParsedVisionItem(
        name = name,
        category = "DAIRY",
        storageLocation = "FRIDGE",
        quantity = 1f,
        unit = "ITEM",
        confidence = 0.95f,
        estimatedShelfLifeDays = 7,
        isAromaticOrSeasoning = false,
        boundingBox = NormalizedBox(0f, 0f, 1f, 1f),
        conditionNotes = null,
    )

    @Test
    fun applyScannedBarcode_withNullMatch_updatesBarcodeAndDismisses() {
        val state = BatchReviewSheetState(listOf(sampleItem()))
        state.showEmbeddedBarcodeScanner = true
        state.editName = "Initial Item"

        applyScannedBarcodeToState(state, "123456789012", match = null)

        assertEquals("123456789012", state.editBarcode)
        assertEquals("Initial Item", state.editName)
        assertFalse(state.showEmbeddedBarcodeScanner)
    }

    @Test
    fun applyScannedBarcode_withMatchAndBlankName_populatesAllFields() {
        val state = BatchReviewSheetState(listOf(sampleItem("")))
        state.showEmbeddedBarcodeScanner = true
        state.editName = ""

        val match = BarcodeMatch(
            barcode = "012000001056",
            name = "Soda Can",
            category = "BEVERAGES",
            unit = "CAN",
            estimatedShelfLifeDays = 90,
        )

        applyScannedBarcodeToState(state, "012000001056", match)

        assertEquals("012000001056", state.editBarcode)
        assertEquals("Soda Can", state.editName)
        assertEquals("BEVERAGES", state.editCategory)
        assertEquals("CAN", state.editUnit)
        assertEquals(90, state.editShelfLifeDays)
        assertFalse(state.editIsAromatic)
        assertFalse(state.showEmbeddedBarcodeScanner)
    }

    @Test
    fun applyScannedBarcode_withMatchAndExistingName_retainsExistingName() {
        val state = BatchReviewSheetState(listOf(sampleItem()))
        state.editName = "Custom User Name"

        val match = BarcodeMatch(
            barcode = "041220576920",
            name = "Whole Milk",
            category = "DAIRY",
            unit = "GALLON",
            estimatedShelfLifeDays = 14,
        )

        applyScannedBarcodeToState(state, "041220576920", match)

        assertEquals("041220576920", state.editBarcode)
        assertEquals("Custom User Name", state.editName)
        assertEquals("DAIRY", state.editCategory)
        assertEquals("GALLON", state.editUnit)
        assertEquals(14, state.editShelfLifeDays)
    }

    @Test
    fun applyScannedBarcode_withAromaticMatch_setsEditIsAromaticTrue() {
        val state = BatchReviewSheetState(listOf(sampleItem()))
        state.editIsAromatic = false

        val match = BarcodeMatch(
            barcode = "052100012345",
            name = "Cinnamon Ground",
            category = "SPICES",
            unit = "JAR",
            estimatedShelfLifeDays = 365,
        )

        applyScannedBarcodeToState(state, "052100012345", match)

        assertTrue(state.editIsAromatic)
        assertEquals("SPICES", state.editCategory)
    }
}
