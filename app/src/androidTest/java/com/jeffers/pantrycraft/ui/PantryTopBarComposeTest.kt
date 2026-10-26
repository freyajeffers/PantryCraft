package com.jeffers.pantrycraft.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PantryTopBarComposeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pantryTopBarRendersTitleAndOpensSortDropdown() {
        composeTestRule.setContent {
            PantryCraftTheme {
                PantryTopBar(
                    title = "My Custom Pantry",
                    selectedSortOrder = PantrySortOrder.NAME_ASC,
                    onSortSelected = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("My Custom Pantry").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Sort pantry items").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("✓ Name (A-Z)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Expiring soonest").assertIsDisplayed()
        composeTestRule.onNodeWithText("Recently updated").assertIsDisplayed()
        composeTestRule.onNodeWithText("Category").assertIsDisplayed()
        composeTestRule.onNodeWithText("Storage zone").assertIsDisplayed()
    }

    @Test
    fun pantryTopBarSelectingSortDispatchesCallback() {
        var selectedOrder: PantrySortOrder? = null

        composeTestRule.setContent {
            PantryCraftTheme {
                PantryTopBar(
                    selectedSortOrder = PantrySortOrder.NAME_ASC,
                    onSortSelected = { selectedOrder = it },
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Sort pantry items").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Expiring soonest").performClick()
        composeTestRule.waitForIdle()

        assertEquals(PantrySortOrder.EXPIRATION_SOONEST, selectedOrder)
    }
}
