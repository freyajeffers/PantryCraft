package com.jeffers.pantrycraft.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.pantrycraft.MainActivity
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PantryUiComposeTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @After
    fun tearDown() {
        composeTestRule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.DESTROYED)
    }

    @Test
    fun appLaunchesAndNavigatesBetweenScreens() {
        composeTestRule.waitForIdle()

        // Pantry screen title is displayed
        composeTestRule.onAllNodesWithText("Pantry")[0].assertIsDisplayed()

        // Switch to Grocery destination via bottom navigation
        composeTestRule.onNodeWithContentDescription("Grocery").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // Switch to Settings destination
        composeTestRule.onNodeWithContentDescription("Settings").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // Switch to Dashboard destination
        composeTestRule.onNodeWithContentDescription("Dashboard").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
    }
}
