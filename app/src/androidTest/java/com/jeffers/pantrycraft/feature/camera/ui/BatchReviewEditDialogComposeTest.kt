package com.jeffers.pantrycraft.feature.camera.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.pantrycraft.feature.camera.vision.NormalizedBox
import com.jeffers.pantrycraft.feature.camera.vision.ParsedVisionItem
import com.jeffers.pantrycraft.ui.PantryCraftTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BatchReviewEditDialogComposeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

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
    fun batchReviewEditDialogRendersAndSaves() {
        val state = createBatchReviewSheetState()
        state.loadForEditing(0)

        composeTestRule.setContent {
            PantryCraftTheme {
                BatchReviewEditDialog(state = state, index = 0)
            }
        }

        composeTestRule.onNodeWithText("Edit captured item").assertIsDisplayed()
        composeTestRule.onNodeWithText("Apples").performTextClearance()
        composeTestRule.onNodeWithText("Name").performTextInput("Honeycrisp")
        composeTestRule.onNodeWithText("Save").performClick()

        assertNull(state.editingIndex)
        assertEquals("Honeycrisp", state.reviewItems[0].item.name)
    }

    @Test
    fun batchReviewEditDialogCancelDismisses() {
        val state = createBatchReviewSheetState()
        state.loadForEditing(0)

        composeTestRule.setContent {
            PantryCraftTheme {
                BatchReviewEditDialog(state = state, index = 0)
            }
        }

        composeTestRule.onNodeWithText("Edit captured item").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()

        assertNull(state.editingIndex)
        assertEquals("Apples", state.reviewItems[0].item.name)
    }

    @Test
    fun batchReviewEditDialogSaveDisabledWhenBlank() {
        val state = createBatchReviewSheetState()
        state.loadForEditing(0)
        state.editName = "  "

        composeTestRule.setContent {
            PantryCraftTheme {
                BatchReviewEditDialog(state = state, index = 0)
            }
        }

        composeTestRule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun batchReviewEditDialogScanBarcodeClick() {
        val state = createBatchReviewSheetState()
        state.loadForEditing(0)

        composeTestRule.setContent {
            PantryCraftTheme {
                BatchReviewEditDialog(state = state, index = 0)
            }
        }

        composeTestRule.onNodeWithContentDescription("Scan barcode to fill item").performClick()
        assertTrue(state.showEmbeddedBarcodeScanner)
    }
}
