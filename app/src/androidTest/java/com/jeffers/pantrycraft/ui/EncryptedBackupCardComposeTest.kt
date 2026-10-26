package com.jeffers.pantrycraft.ui

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedBackupCardComposeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun encryptedBackupCardRendersDefaultEmptyState() {
        var setLocationClicked = false
        var openFileClicked = false
        var resetClicked = false

        val autoProps = AutomaticBackupProps(
            enabled = false,
            intervalMinutes = 60L,
            databaseHasUserData = false,
            onDisable = {},
            onEnableRequested = {},
            onIntervalSelected = {},
            onBackupNow = {},
        )

        val props = EncryptedBackupCardProps(
            autoBackupProps = autoProps,
            lastExportedUri = null,
            hasBackupRecords = false,
            autoBackupTreeUri = null,
            backupResult = null,
            onShareSavedBackup = {},
            onRestoreSavedBackup = {},
            onSelectAutoBackupLocation = { setLocationClicked = true },
            onOpenBackupFile = { openFileClicked = true },
            onResetDatabase = { resetClicked = true },
        )

        composeTestRule.setContent {
            PantryCraftTheme {
                EncryptedBackupCard(props = props)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Encrypted backup").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Choose a file location so it can be found during recovery.",
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("Set auto-backup location").assertIsDisplayed().performClick()
        assertTrue(setLocationClicked)

        composeTestRule.onNodeWithText("Open backup file to restore").assertIsDisplayed().performClick()
        assertTrue(openFileClicked)

        composeTestRule.onNodeWithText("Reset local database").assertIsDisplayed().performClick()
        assertTrue(resetClicked)
    }

    @Test
    fun encryptedBackupCardRendersSavedLocationRecordsAndResult() {
        var shareClicked = false
        var restoreClicked = false

        val autoProps = AutomaticBackupProps(
            enabled = true,
            intervalMinutes = 1440L,
            databaseHasUserData = true,
            onDisable = {},
            onEnableRequested = {},
            onIntervalSelected = {},
            onBackupNow = {},
        )

        val testUri = Uri.parse("content://com.android.providers.downloads.documents/document/123")
        val props = EncryptedBackupCardProps(
            autoBackupProps = autoProps,
            lastExportedUri = testUri,
            hasBackupRecords = true,
            autoBackupTreeUri = Uri.parse("content://tree/location"),
            backupResult = "Encrypted backup saved: pantrycraft-backup.pantrybackup",
            onShareSavedBackup = { shareClicked = true },
            onRestoreSavedBackup = { restoreClicked = true },
            onSelectAutoBackupLocation = {},
            onOpenBackupFile = {},
            onResetDatabase = {},
        )

        composeTestRule.setContent {
            PantryCraftTheme {
                EncryptedBackupCard(props = props)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Saved location remembered for recovery.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Change auto-backup location").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Encrypted backup saved: pantrycraft-backup.pantrybackup",
        ).assertIsDisplayed()

        composeTestRule.onNodeWithText("Share saved backup").assertIsDisplayed().performClick()
        assertTrue(shareClicked)

        composeTestRule.onNodeWithText("Restore saved backup").assertIsDisplayed().performClick()
        assertTrue(restoreClicked)
    }
}
