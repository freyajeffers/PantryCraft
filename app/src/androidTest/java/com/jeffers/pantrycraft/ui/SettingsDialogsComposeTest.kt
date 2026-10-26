package com.jeffers.pantrycraft.ui

import android.app.KeyguardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.pantrycraft.data.DatabaseBackupManager.BackupRecord
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsDialogsComposeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        Assume.assumeTrue("Screen is locked; skipping Compose test", km?.isKeyguardLocked == false)
    }

    @After
    fun tearDown() {
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    @Test
    fun backupPinDialogRendersAndConfirms() {
        var confirmed = false
        var dismissed = false
        var pinInput = ""

        composeTestRule.setContent {
            PantryCraftTheme {
                BackupPinDialog(
                    pinText = pinInput,
                    onPinTextChange = { pinInput = it },
                    onDismiss = { dismissed = true },
                    onConfirm = { confirmed = true },
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Enter backup PIN").assertIsDisplayed()
        composeTestRule.onNodeWithText("PIN").performTextInput("1234")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("OK").performClick()
        composeTestRule.waitForIdle()
        assertTrue("Confirm must be invoked on OK click", confirmed)

        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitForIdle()
        assertTrue("Dismiss must be invoked on Cancel click", dismissed)
    }

    @Test
    fun backupListDialogRendersEmptyAndWithRecords() {
        var selectedRecord: BackupRecord? = null
        var dismissed = false
        val record = BackupRecord(
            backupName = "2026-10-26-backup.db",
            createdAt = 1_700_000_000_000L,
            createdAtLabel = "Oct 26, 2026 10:00 AM",
            exportedUri = "content://com.android.providers.downloads/1",
        )

        val state = BackupListDialogState(
            records = listOf(record),
            onDismiss = { dismissed = true },
            onSelect = { selectedRecord = it },
        )

        composeTestRule.setContent {
            PantryCraftTheme {
                BackupListDialog(state)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Choose a backup to restore").assertIsDisplayed()
        composeTestRule.onNodeWithText("Oct 26, 2026 10:00 AM").assertIsDisplayed()
        composeTestRule.onNodeWithText("2026-10-26-backup.db").performClick()
        composeTestRule.waitForIdle()
        assertEquals(record, selectedRecord)

        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitForIdle()
        assertTrue(dismissed)
    }

    @Test
    fun settingsCatalogAlertDialogRenders() {
        var dismissed = false
        val dialog = CatalogDialog(
            title = "Catalog Downloaded",
            message = "Offline barcode database is ready.",
            isError = false,
        )

        composeTestRule.setContent {
            PantryCraftTheme {
                SettingsCatalogAlertDialog(dialog = dialog, onDismiss = { dismissed = true })
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Catalog Downloaded").assertIsDisplayed()
        composeTestRule.onNodeWithText("Offline barcode database is ready.").assertIsDisplayed()
        composeTestRule.onNodeWithText("OK").performClick()
        composeTestRule.waitForIdle()
        assertTrue(dismissed)
    }

    @Test
    fun catalogDeleteDialogRendersAndDismisses() {
        var dismissed = false
        var deleted = false
        var failed = false

        composeTestRule.setContent {
            PantryCraftTheme {
                CatalogDeleteDialog(
                    context = context,
                    onDismiss = { dismissed = true },
                    onDeleted = { deleted = true },
                    onFailure = { failed = true },
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Delete downloaded catalog?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitForIdle()
        assertTrue(dismissed)

        composeTestRule.onNodeWithText("Delete").performClick()
        composeTestRule.waitForIdle()
        assertTrue("Non-existent catalog file deletion should invoke onDeleted or onFailure", deleted || failed)
    }

    @Test
    fun settingsResetDatabaseDialogRendersAndDismisses() {
        var dismissed = false

        composeTestRule.setContent {
            PantryCraftTheme {
                SettingsResetDatabaseDialog(
                    context = context,
                    visible = true,
                    onDismiss = { dismissed = true },
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Reset local database?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitForIdle()
        assertTrue(dismissed)
    }
}
