package com.jeffers.pantrycraft.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.pantrycraft.data.persistBackupUri
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseRecoveryScreenComposeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        clearBackups()
    }

    @After
    fun tearDown() {
        clearBackups()
    }

    private fun clearBackups() {
        val prefs = context.getSharedPreferences("backup_preferences", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val backupDir = java.io.File(context.filesDir, "backups")
        if (backupDir.exists()) {
            backupDir.listFiles()?.forEach { it.delete() }
        }
    }

    @Test
    fun databaseRecoveryScreenRendersAndConfirmsReset() {
        var resetCalled = false
        composeTestRule.setContent {
            PantryCraftTheme {
                DatabaseRecoveryScreen(
                    context = context,
                    onRestore = { _, _ -> },
                    onReset = { resetCalled = true },
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Encrypted database recovery required").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reset local database").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Reset local database?").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Reset database").onFirst().performClick()
        composeTestRule.waitForIdle()

        assertTrue(resetCalled)
    }

    @Test
    fun databaseRecoveryScreenCancelResetDismissesDialog() {
        var resetCalled = false
        composeTestRule.setContent {
            PantryCraftTheme {
                DatabaseRecoveryScreen(
                    context = context,
                    onRestore = { _, _ -> },
                    onReset = { resetCalled = true },
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Reset local database").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Reset local database").assertIsDisplayed()
        org.junit.Assert.assertFalse(resetCalled)
    }

    @Test
    fun databaseRecoveryScreenWithPersistedBackupRestores() {
        val testUri = android.net.Uri.parse("content://com.jeffers.pantrycraft/backup_test.db")
        persistBackupUri(context, testUri)

        var restoredBackup: String? = null
        var restoredPin: String? = null

        composeTestRule.setContent {
            PantryCraftTheme {
                DatabaseRecoveryScreen(
                    context = context,
                    onRestore = { backup, pin ->
                        restoredBackup = backup
                        restoredPin = String(pin)
                    },
                    onReset = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("backup_test.db", substring = true).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Backup PIN").performTextInput("1234")
        composeTestRule.onNodeWithText("Restore selected backup").assertIsEnabled().performClick()
        composeTestRule.waitForIdle()

        assertEquals(testUri.toString(), restoredBackup)
        assertEquals("1234", restoredPin)
    }

    @Test
    fun databaseRecoveryScreenButtonsDisabledWhenPinEmpty() {
        val testUri = android.net.Uri.parse("content://com.jeffers.pantrycraft/backup_test.db")
        persistBackupUri(context, testUri)

        composeTestRule.setContent {
            PantryCraftTheme {
                DatabaseRecoveryScreen(
                    context = context,
                    onRestore = { _, _ -> },
                    onReset = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Open backup file").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Restore selected backup").assertIsNotEnabled()
    }

    @Test
    fun databaseRecoveryScreenOpenBackupFileEnabledWithPin() {
        val testUri = android.net.Uri.parse("content://com.jeffers.pantrycraft/backup_open.db")
        persistBackupUri(context, testUri)

        composeTestRule.setContent {
            PantryCraftTheme {
                DatabaseRecoveryScreen(
                    context = context,
                    onRestore = { _, _ -> },
                    onReset = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Backup PIN").performTextInput("1234")
        composeTestRule.onNodeWithText("Open backup file").assertIsEnabled().performClick()
    }

    @Test
    fun settingsScreenRendersDirectly() {
        val repo = com.jeffers.pantrycraft.data.Repository.getInstance(context)
        composeTestRule.setContent {
            PantryCraftTheme {
                SettingsScreen(context = context, repository = repo)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Manage offline barcode data and protect your pantry information.")
            .assertIsDisplayed()
    }
}
