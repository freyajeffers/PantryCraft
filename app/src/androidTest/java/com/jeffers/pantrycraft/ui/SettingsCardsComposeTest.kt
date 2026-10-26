package com.jeffers.pantrycraft.ui

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.pantrycraft.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsCardsComposeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun automaticBackupCardRendersAndInteracts() {
        var disabled = false
        var enableRequested = false
        var selectedInterval = 0L
        var backupNow = false

        val props = AutomaticBackupProps(
            enabled = true,
            intervalMinutes = 60L,
            databaseHasUserData = true,
            onDisable = { disabled = true },
            onEnableRequested = { enableRequested = true },
            onIntervalSelected = { selectedInterval = it },
            onBackupNow = { backupNow = true },
        )

        composeTestRule.setContent {
            PantryCraftTheme {
                AutomaticBackupCard(props = props)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("✓ 1 hour").assertIsDisplayed()
        composeTestRule.onNodeWithText("3 hours").performClick()
        assertEquals(180L, selectedInterval)

        composeTestRule.onNodeWithText("Save encrypted backup file").performClick()
        assertTrue(backupNow)
    }

    @Test
    fun encryptedBackupCardRendersUnconfiguredState() {
        var selectLocationCalled = false
        var openFileCalled = false
        var resetDbCalled = false

        val dummyAuto = AutomaticBackupProps(
            enabled = false,
            intervalMinutes = 1440L,
            databaseHasUserData = false,
            onDisable = {},
            onEnableRequested = {},
            onIntervalSelected = {},
            onBackupNow = {},
        )

        val props = EncryptedBackupCardProps(
            autoBackupProps = dummyAuto,
            lastExportedUri = null,
            hasBackupRecords = false,
            autoBackupTreeUri = null,
            backupResult = "Backup complete",
            onShareSavedBackup = {},
            onRestoreSavedBackup = {},
            onSelectAutoBackupLocation = { selectLocationCalled = true },
            onOpenBackupFile = { openFileCalled = true },
            onResetDatabase = { resetDbCalled = true },
        )

        composeTestRule.setContent {
            PantryCraftTheme {
                EncryptedBackupCard(props = props)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText("Choose a file location so it can be found during recovery.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Set auto-backup location").performClick()
        assertTrue(selectLocationCalled)

        composeTestRule.onNodeWithText("Open backup file to restore").performClick()
        assertTrue(openFileCalled)

        composeTestRule.onNodeWithText("Reset local database").performClick()
        assertTrue(resetDbCalled)

        composeTestRule.onNodeWithText("Backup complete").assertIsDisplayed()
    }

    @Test
    fun encryptedBackupCardRendersConfiguredState() {
        var shareCalled = false
        var restoreSavedCalled = false
        var selectLocationCalled = false
        val dummyUri = Uri.parse("content://com.jeffers.pantrycraft/backup.db")

        val dummyAuto = AutomaticBackupProps(
            enabled = false,
            intervalMinutes = 1440L,
            databaseHasUserData = false,
            onDisable = {},
            onEnableRequested = {},
            onIntervalSelected = {},
            onBackupNow = {},
        )

        val props = EncryptedBackupCardProps(
            autoBackupProps = dummyAuto,
            lastExportedUri = dummyUri,
            hasBackupRecords = true,
            autoBackupTreeUri = dummyUri,
            backupResult = null,
            onShareSavedBackup = { shareCalled = true },
            onRestoreSavedBackup = { restoreSavedCalled = true },
            onSelectAutoBackupLocation = { selectLocationCalled = true },
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
        composeTestRule.onNodeWithText("Share saved backup").performClick()
        assertTrue(shareCalled)

        composeTestRule.onNodeWithText("Restore saved backup").performClick()
        assertTrue(restoreSavedCalled)

        composeTestRule.onNodeWithText("Change auto-backup location").performClick()
        assertTrue(selectLocationCalled)
    }

    @Test
    fun foodFactsCardRendersDownloadingAndReadyStates() {
        var deleteCalled = false
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repository = Repository.getInstance(context)
        val scope = CoroutineScope(Dispatchers.Main.immediate)

        val downloadingProps = FoodFactsProps(
            catalogReady = false,
            catalogDownloading = true,
            catalogDownloadedBytes = 500L,
            catalogTotalBytes = 1000L,
            catalogFileSize = null,
            onStartDownload = {},
            onDownloadResult = {},
            onProgress = { _, _ -> },
            onDelete = { deleteCalled = true },
        )

        composeTestRule.setContent {
            PantryCraftTheme {
                FoodFactsCard(repository = repository, scope = scope, props = downloadingProps)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Downloading 500 B of 1000 B").assertIsDisplayed()
        composeTestRule.onNodeWithText("Download food catalog").assertIsNotEnabled()

        val readyProps = downloadingProps.copy(
            catalogReady = true,
            catalogDownloading = false,
            catalogFileSize = 5_000_000L,
        )

        composeTestRule.setContent {
            PantryCraftTheme {
                FoodFactsCard(repository = repository, scope = scope, props = readyProps)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Ready for offline barcode scans · 4.8 MB").assertIsDisplayed()
        composeTestRule.onNodeWithText("Delete").performClick()
        assertTrue(deleteCalled)
    }
}
