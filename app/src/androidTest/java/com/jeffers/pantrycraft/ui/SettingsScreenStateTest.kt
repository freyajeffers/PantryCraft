package com.jeffers.pantrycraft.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenStateTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var holder: SettingsDialogStateHolder

    @Before
    fun setUp() {
        holder = SettingsDialogStateHolder(context)
    }

    @org.junit.After
    fun tearDown() {
        com.jeffers.pantrycraft.data.AppDatabase.getDatabase(context)
        com.jeffers.pantrycraft.data.Repository.getInstance(context)
    }

    @Test
    fun defaultStateIsDismissed() {
        assertFalse(holder.showPinDialog)
        assertEquals("", holder.pinText)
        assertFalse(holder.showBackupList)
        assertFalse(holder.confirmDatabaseReset)
        assertFalse(holder.confirmCatalogDelete)
        assertNull(holder.catalogDialog)
    }

    @Test
    fun openPinSetsFlagAndResetsPinText() {
        holder.pinText = "previous-pin"
        var actionExecuted = false

        holder.openPin {
            actionExecuted = true
        }

        assertTrue(holder.showPinDialog)
        assertEquals("", holder.pinText)
        assertFalse(actionExecuted)
    }

    @Test
    fun closePinResetsDialogAndClearsPinText() {
        holder.openPin {}
        holder.pinText = "1234"

        holder.closePin()

        assertFalse(holder.showPinDialog)
        assertEquals("", holder.pinText)
    }

    @Test
    fun confirmPinClosesDialogAndDispatchesEnteredChars() {
        var dispatchedPin: String? = null
        holder.openPin { chars ->
            dispatchedPin = chars.concatToString()
        }
        holder.pinText = "5678"

        holder.confirmPin()

        assertFalse(holder.showPinDialog)
        assertEquals("", holder.pinText)
        assertEquals("5678", dispatchedPin)
    }

    @Test
    fun openBackupListSetsVisibilityFlag() {
        holder.openBackupList()
        assertTrue(holder.showBackupList)
    }

    @Test
    fun dialogFlagsCanBeToggled() {
        holder.confirmDatabaseReset = true
        assertTrue(holder.confirmDatabaseReset)
        holder.confirmDatabaseReset = false
        assertFalse(holder.confirmDatabaseReset)

        holder.confirmCatalogDelete = true
        assertTrue(holder.confirmCatalogDelete)
        holder.confirmCatalogDelete = false
        assertFalse(holder.confirmCatalogDelete)
    }

    @Test
    fun handleDownloadResultDispatchesSuccessAndFailure() {
        val testFile = java.io.File(context.cacheDir, "catalog.bin").apply { writeText("data") }
        var successBytes: Long? = null
        var failureDialog: CatalogDialog? = null

        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            handleDownloadResult(
                context = context,
                result = Result.success(testFile),
                onSuccess = { successBytes = it },
                onFailure = { failureDialog = it },
            )
        }
        assertEquals(4L, successBytes)
        assertNull(failureDialog)

        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            handleDownloadResult(
                context = context,
                result = Result.failure(java.io.IOException("Network offline")),
                onSuccess = { successBytes = it },
                onFailure = { failureDialog = it },
            )
        }
        assertEquals(true, failureDialog?.isError)
        assertEquals("Network offline", failureDialog?.message)
        testFile.delete()
    }

    @Test
    fun restoreBackupRecordHandlesExportedUriAndDirectName() = kotlinx.coroutines.runBlocking {
        val recordWithUri = com.jeffers.pantrycraft.data.DatabaseBackupManager.BackupRecord(
            backupName = "backup_with_uri.pcbk",
            createdAt = 1000L,
            createdAtLabel = "2026-01-01",
            exportedUri = "content://com.test/backup.pcbk",
        )
        restoreBackupRecord(
            context = context,
            scope = this,
            record = recordWithUri,
            suppliedPin = "1234".toCharArray(),
            onResult = {},
        )

        val recordWithoutUri = com.jeffers.pantrycraft.data.DatabaseBackupManager.BackupRecord(
            backupName = "backup_direct.pcbk",
            createdAt = 2000L,
            createdAtLabel = "2026-01-02",
            exportedUri = null,
        )
        restoreBackupRecord(
            context = context,
            scope = this,
            record = recordWithoutUri,
            suppliedPin = "1234".toCharArray(),
            onResult = {},
        )
    }
}
