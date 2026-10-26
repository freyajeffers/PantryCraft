package com.jeffers.pantrycraft.feature.camera.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.pantrycraft.feature.camera.vision.NormalizedBox
import com.jeffers.pantrycraft.feature.camera.vision.ParsedVisionItem
import com.jeffers.pantrycraft.ui.PantryCraftTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BatchReviewBottomSheetComposeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun createParsedItem(
        name: String,
        shelfLife: Int = 14,
        category: String = "DAIRY",
    ): ParsedVisionItem = ParsedVisionItem(
        name = name,
        category = category,
        storageLocation = "FRIDGE",
        quantity = 1f,
        unit = "CARTON",
        confidence = 0.95f,
        estimatedShelfLifeDays = shelfLife,
        isAromaticOrSeasoning = false,
        boundingBox = NormalizedBox(0f, 0f, 1f, 1f),
        conditionNotes = null,
    )

    @Test
    fun multiItemBatchReviewRendersAndCancels() {
        var dismissed = false
        val items = listOf(createParsedItem("Greek Yogurt"), createParsedItem("Cheddar Cheese"))

        composeTestRule.setContent {
            PantryCraftTheme {
                BatchReviewBottomSheet(
                    items = items,
                    onConfirm = {},
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Review captured items").assertIsDisplayed()
        composeTestRule.onNodeWithText("Greek Yogurt").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cheddar Cheese").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun multiItemBatchReviewConfirmsSelected() {
        var confirmed: List<ParsedVisionItem>? = null
        val items = listOf(createParsedItem("Almond Milk"), createParsedItem("Butter"))

        composeTestRule.setContent {
            PantryCraftTheme {
                BatchReviewBottomSheet(
                    items = items,
                    onConfirm = { confirmed = it },
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Save 2 selected").assertIsDisplayed().performClick()
        assertEquals(2, confirmed?.size)
        assertEquals("Almond Milk", confirmed?.get(0)?.name)
        assertEquals("Butter", confirmed?.get(1)?.name)
    }

    @Test
    fun singleItemBatchReviewRendersEditorAndSaves() {
        var confirmed: List<ParsedVisionItem>? = null
        val items = listOf(createParsedItem("Oat Milk"))

        composeTestRule.setContent {
            PantryCraftTheme {
                BatchReviewBottomSheet(
                    items = items,
                    onConfirm = { confirmed = it },
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Save").assertIsDisplayed()
        composeTestRule.onNodeWithText("Oat Milk").performTextClearance()
        composeTestRule.onNodeWithText("Save").assertIsNotEnabled()

        composeTestRule.onNodeWithText("Item Name").performTextInput("Organic Oat Milk")
        composeTestRule.onNodeWithText("Save").performClick()

        assertEquals(1, confirmed?.size)
        assertEquals("Organic Oat Milk", confirmed?.first()?.name)
    }

    @Test
    fun singleItemBatchReviewTogglesOpenedSetsShelfLife() {
        var confirmed: List<ParsedVisionItem>? = null
        val items = listOf(createParsedItem("Fresh Milk", shelfLife = 0, category = "DAIRY"))

        composeTestRule.setContent {
            PantryCraftTheme {
                BatchReviewBottomSheet(
                    items = items,
                    onConfirm = { confirmed = it },
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Already opened (accelerates perishability)").performClick()
        composeTestRule.onNodeWithText("Save").performClick()

        assertEquals(1, confirmed?.size)
        assertTrue(confirmed?.first()?.estimatedShelfLifeDays ?: 0 > 0)
    }
}
