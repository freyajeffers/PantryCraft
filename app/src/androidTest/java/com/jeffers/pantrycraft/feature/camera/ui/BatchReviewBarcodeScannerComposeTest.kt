package com.jeffers.pantrycraft.feature.camera.ui

import android.content.Context
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.pantrycraft.feature.camera.barcode.BarcodeMatch
import com.jeffers.pantrycraft.feature.camera.vision.NormalizedBox
import com.jeffers.pantrycraft.feature.camera.vision.ParsedVisionItem
import com.jeffers.pantrycraft.ui.PantryCraftTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BatchReviewBarcodeScannerComposeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun createBatchReviewSheetState(): BatchReviewSheetState {
        val item = ParsedVisionItem(
            name = "Apples",
            category = "PRODUCE",
            storageLocation = "PANTRY",
            quantity = 2f,
            unit = "COUNT",
            confidence = 0.95f,
            estimatedShelfLifeDays = 7,
            isAromaticOrSeasoning = false,
            boundingBox = NormalizedBox(0f, 0f, 1f, 1f),
            conditionNotes = null,
        )
        return BatchReviewSheetState(items = listOf(item))
    }

    @Test
    fun batchReviewBarcodeScannerRendersDialogAndDismisses() {
        val state = createBatchReviewSheetState()
        state.showEmbeddedBarcodeScanner = true

        composeTestRule.setContent {
            PantryCraftTheme {
                Box {
                    if (state.showEmbeddedBarcodeScanner) {
                        BatchReviewBarcodeScanner(state = state)
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Scan Item Barcode").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()

        assertFalse(state.showEmbeddedBarcodeScanner)
    }

    @Test
    fun embeddedBarcodeScannerDialogDismisses() {
        var dismissed = false
        composeTestRule.setContent {
            PantryCraftTheme {
                Box {
                    EmbeddedBarcodeScannerDialog(
                        onDismiss = { dismissed = true },
                        onBarcodeResolved = { _, _ -> },
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Scan Item Barcode").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()

        assertTrue(dismissed)
    }

    @Test
    fun triggerBarcodeHapticTickExecutesWithoutError() {
        val view = View(context)
        triggerBarcodeHapticTick(context, view)
    }

    @Test
    fun applyScannedBarcodeToStateAppliesMatchDetails() {
        val state = createBatchReviewSheetState()
        state.showEmbeddedBarcodeScanner = true
        state.editName = ""

        val match = BarcodeMatch(
            barcode = "012345678905",
            name = "Organic Black Peppercorn",
            category = "SPICES",
            unit = "G",
            estimatedShelfLifeDays = 365,
        )

        applyScannedBarcodeToState(state, "012345678905", match)

        assertEquals("012345678905", state.editBarcode)
        assertEquals("Organic Black Peppercorn", state.editName)
        assertEquals("SPICES", state.editCategory)
        assertEquals("G", state.editUnit)
        assertEquals(365, state.editShelfLifeDays)
        assertTrue(state.editIsAromatic)
        assertFalse(state.showEmbeddedBarcodeScanner)
    }
}
