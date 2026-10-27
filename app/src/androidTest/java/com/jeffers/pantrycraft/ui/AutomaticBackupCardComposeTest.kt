package com.jeffers.pantrycraft.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutomaticBackupCardComposeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun automaticBackupCardDisabledRendersAndEnables() {
        var enableRequested = false
        var backupNowClicked = false

        val props = AutomaticBackupProps(
            enabled = false,
            intervalMinutes = 60L,
            databaseHasUserData = true,
            onDisable = {},
            onEnableRequested = { enableRequested = true },
            onIntervalSelected = {},
            onBackupNow = { backupNowClicked = true },
        )

        composeTestRule.setContent {
            PantryCraftTheme {
                AutomaticBackupCard(props = props)
            }
        }

        composeTestRule.onNodeWithText("Encrypted backup").assertIsDisplayed()
        composeTestRule.onNodeWithText("Automatic background backups").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save encrypted backup file").assertIsDisplayed().performClick()
        assertTrue(backupNowClicked)

        composeTestRule.onNode(isToggleable()).performClick()
        assertTrue(enableRequested)
    }

    @Test
    fun automaticBackupCardEnabledRendersIntervalsAndDisables() {
        var disabled = false
        var selectedInterval: Long? = null

        val props = AutomaticBackupProps(
            enabled = true,
            intervalMinutes = 1440L,
            databaseHasUserData = true,
            onDisable = { disabled = true },
            onEnableRequested = {},
            onIntervalSelected = { selectedInterval = it },
            onBackupNow = {},
        )

        composeTestRule.setContent {
            PantryCraftTheme {
                AutomaticBackupCard(props = props)
            }
        }

        composeTestRule.onNodeWithText("✓ Daily").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("1 hour").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(60L, selectedInterval)

        composeTestRule.onNode(isToggleable()).performClick()
        assertTrue(disabled)
    }

    @Test
    fun automaticBackupCardNoUserDataHidesBackupNowButton() {
        val props = AutomaticBackupProps(
            enabled = false,
            intervalMinutes = 60L,
            databaseHasUserData = false,
            onDisable = {},
            onEnableRequested = {},
            onIntervalSelected = {},
            onBackupNow = {},
        )

        composeTestRule.setContent {
            PantryCraftTheme {
                AutomaticBackupCard(props = props)
            }
        }

        composeTestRule.onNodeWithText("Encrypted backup").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save encrypted backup file").assertDoesNotExist()
    }
}
