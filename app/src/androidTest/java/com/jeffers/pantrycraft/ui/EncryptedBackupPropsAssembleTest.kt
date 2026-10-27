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

    @Test
    fun performEncryptedBackupSucceedsWithValidPin(): Unit = runBlocking {
        var successBackupName: String? = null
        val validPin = "123456".toCharArray()

        val resultMessage = performEncryptedBackup(context, validPin) { createdName ->
            successBackupName = createdName
        }

        assertTrue(resultMessage.startsWith("Backup created"))
        assertTrue(successBackupName != null)
    }

    @Test
    fun buildAutoBackupPropsWiresAllActions() {
        var scheduleChangedEnabled: Boolean? = null
        var scheduleChangedMinutes: Long? = null
        var requiredPinForEnable: ((CharArray) -> Unit)? = null
        var requiredPinForBackup: ((CharArray) -> Unit)? = null
        var performedBackupPin: CharArray? = null

        val args = AutoBackupScheduleArgs(
            enabled = true,
            intervalMinutes = 120L,
            databaseHasUserData = true,
            onScheduleChange = { enabled, minutes ->
                scheduleChangedEnabled = enabled
                scheduleChangedMinutes = minutes
            },
            onRequirePin = { action ->
                requiredPinForEnable = action
                requiredPinForBackup = action
            },
            onPerformBackup = { pin ->
                performedBackupPin = pin
            },
        )

        val props = buildAutoBackupProps(context, args)
        assertTrue(props.enabled)
        assertEquals(120L, props.intervalMinutes)
        assertTrue(props.databaseHasUserData)

        props.onDisable()
        assertEquals(false, scheduleChangedEnabled)

        props.onIntervalSelected(360L)
        assertEquals(true, scheduleChangedEnabled)
        assertEquals(360L, scheduleChangedMinutes)

        props.onEnableRequested()
        requiredPinForEnable?.invoke("1234".toCharArray())
        assertEquals(true, scheduleChangedEnabled)

        props.onBackupNow()
        requiredPinForBackup?.invoke("5678".toCharArray())
        assertEquals("5678", performedBackupPin?.let { String(it) })
    }
}
