package com.jeffers.pantrycraft.ui

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedBackupPropsAssembleTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun assembleEncryptedBackupPropsWiresStateAndCallbacks() {
        val uri = Uri.parse("content://test/backup.bin")
        val autoProps = AutomaticBackupProps(
            enabled = true,
            intervalMinutes = 60L,
            databaseHasUserData = true,
            onDisable = {},
            onEnableRequested = {},
            onIntervalSelected = {},
            onBackupNow = {},
        )
        val state = BackupStateProps(
            lastExportedUri = uri,
            hasBackupRecords = true,
            autoBackupTreeUri = uri,
            backupResult = "Success",
        )
        var treeLaunched = false
        var restoreLaunched = false
        val launchers = BackupLaunchers(
            launchCreateBackup = {},
            launchTree = { treeLaunched = true },
            launchRestore = { restoreLaunched = true },
        )
        var showBackupListCalled = false
        var resetDatabaseCalled = false
        val callbacks = BackupCallbacks(
            onShowBackupList = { showBackupListCalled = true },
            onResetDatabase = { resetDatabaseCalled = true },
            onRequirePin = {},
            onRequireRestore = {},
        )

        val assembled = assembleEncryptedBackupProps(context, autoProps, state, launchers, callbacks)

        assertEquals(uri, assembled.lastExportedUri)
        assertTrue(assembled.hasBackupRecords)
        assertEquals(uri, assembled.autoBackupTreeUri)
        assertEquals("Success", assembled.backupResult)

        assembled.onRestoreSavedBackup()
        assertTrue(showBackupListCalled)

        assembled.onSelectAutoBackupLocation()
        assertTrue(treeLaunched)

        assembled.onOpenBackupFile()
        assertTrue(restoreLaunched)

        assembled.onResetDatabase()
        assertTrue(resetDatabaseCalled)
    }

    @Test
    fun performEncryptedBackupHandlesShortPinFailure() = runBlocking {
        var successBackupName: String? = null
        val shortPin = "12".toCharArray()

        val resultMessage = performEncryptedBackup(context, shortPin) { createdName ->
            successBackupName = createdName
        }

        assertTrue(resultMessage.startsWith("Backup failed:"))
        assertEquals(null, successBackupName)
    }
}
