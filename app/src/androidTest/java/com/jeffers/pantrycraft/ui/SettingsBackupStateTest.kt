package com.jeffers.pantrycraft.ui

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.pantrycraft.data.AutomaticBackupPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsBackupStateTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var originalEnabled: Boolean = false
    private var originalInterval: Long = 1440L
    private var originalPin: CharArray? = null

    @Before
    fun setUp() {
        originalEnabled = AutomaticBackupPreferences.isEnabled(context)
        originalInterval = AutomaticBackupPreferences.intervalMinutes(context)
        originalPin = AutomaticBackupPreferences.loadPin(context)
    }

    @After
    fun tearDown() {
        AutomaticBackupPreferences.setSchedule(context, originalEnabled, originalInterval)
        if (originalPin != null) {
            AutomaticBackupPreferences.savePin(context, originalPin!!)
        } else {
            clearPinPreferences()
        }
    }

    private fun clearPinPreferences() {
        context
            .getSharedPreferences("backup_preferences", Context.MODE_PRIVATE)
            .edit()
            .remove("automatic_backup_pin_ciphertext")
            .remove("automatic_backup_pin_iv")
            .apply()
    }

    @Test
    fun buildAutoBackupPropsPassesThroughArguments() {
        val args = AutoBackupScheduleArgs(
            enabled = true,
            intervalMinutes = 720L,
            databaseHasUserData = true,
            onScheduleChange = { _, _ -> },
            onRequirePin = {},
            onPerformBackup = {},
        )

        val props = buildAutoBackupProps(context, args)

        assertTrue(props.enabled)
        assertEquals(720L, props.intervalMinutes)
        assertTrue(props.databaseHasUserData)
    }

    @Test
    fun onDisableDisablesScheduleAndScheduler() {
        AutomaticBackupPreferences.setSchedule(context, enabled = true, intervalMinutes = 1440L)
        var scheduleChangeCalled = false
        var updatedEnabled = true
        var updatedInterval = 0L

        val args = AutoBackupScheduleArgs(
            enabled = true,
            intervalMinutes = 1440L,
            databaseHasUserData = true,
            onScheduleChange = { enabled, minutes ->
                scheduleChangeCalled = true
                updatedEnabled = enabled
                updatedInterval = minutes
            },
            onRequirePin = {},
            onPerformBackup = {},
        )

        val props = buildAutoBackupProps(context, args)
        props.onDisable()

        assertTrue("onScheduleChange must be called", scheduleChangeCalled)
        assertFalse(updatedEnabled)
        assertEquals(1440L, updatedInterval)
        assertFalse(AutomaticBackupPreferences.isEnabled(context))
    }

    @Test
    fun onEnableRequestedSavesPinAndEnablesSchedule() {
        clearPinPreferences()
        AutomaticBackupPreferences.setSchedule(context, enabled = false, intervalMinutes = 1440L)

        var scheduleChangeCalled = false
        var updatedEnabled = false
        var updatedInterval = 0L

        val args = AutoBackupScheduleArgs(
            enabled = false,
            intervalMinutes = 1440L,
            databaseHasUserData = true,
            onScheduleChange = { enabled, minutes ->
                scheduleChangeCalled = true
                updatedEnabled = enabled
                updatedInterval = minutes
            },
            onRequirePin = { callback ->
                val testPin = "7890".toCharArray()
                callback(testPin)
            },
            onPerformBackup = {},
        )

        val props = buildAutoBackupProps(context, args)
        props.onEnableRequested()

        assertTrue("onScheduleChange must be called", scheduleChangeCalled)
        assertTrue(updatedEnabled)
        assertEquals(1440L, updatedInterval)
        assertTrue(AutomaticBackupPreferences.isEnabled(context))

        val loadedPin = AutomaticBackupPreferences.loadPin(context)
        assertEquals("7890", loadedPin?.concatToString())
        loadedPin?.fill('\u0000')
    }

    @Test
    fun onIntervalSelectedUpdatesScheduleAndInterval() {
        var scheduleChangeCalled = false
        var updatedEnabled = false
        var updatedInterval = 0L

        val args = AutoBackupScheduleArgs(
            enabled = false,
            intervalMinutes = 1440L,
            databaseHasUserData = true,
            onScheduleChange = { enabled, minutes ->
                scheduleChangeCalled = true
                updatedEnabled = enabled
                updatedInterval = minutes
            },
            onRequirePin = {},
            onPerformBackup = {},
        )

        val props = buildAutoBackupProps(context, args)
        props.onIntervalSelected(360L)

        assertTrue("onScheduleChange must be called", scheduleChangeCalled)
        assertTrue(updatedEnabled)
        assertEquals(360L, updatedInterval)
        assertEquals(360L, AutomaticBackupPreferences.intervalMinutes(context))
        assertTrue(AutomaticBackupPreferences.isEnabled(context))
    }

    @Test
    fun onBackupNowDelegatesToRequirePinAndPerformBackup() {
        var requirePinInvoked = false
        var backupPinSupplied: String? = null

        val args = AutoBackupScheduleArgs(
            enabled = true,
            intervalMinutes = 1440L,
            databaseHasUserData = true,
            onScheduleChange = { _, _ -> },
            onRequirePin = { action ->
                requirePinInvoked = true
                action("4321".toCharArray())
            },
            onPerformBackup = { pinChars ->
                backupPinSupplied = pinChars.concatToString()
            },
        )

        val props = buildAutoBackupProps(context, args)
        props.onBackupNow()

        assertTrue(requirePinInvoked)
        assertEquals("4321", backupPinSupplied)
    }

    @Test
    fun backupLaunchersCallbacksAndStatePropsContracts() {
        verifyLaunchersContract()
        val uri = Uri.parse("content://backup/test")
        verifyCallbacksContract(uri)
        verifyStatePropsContract(uri)
    }

    private fun verifyLaunchersContract() {
        var createName: String? = null
        var treeLaunched = false
        var restoreLaunched = false

        val launchers = BackupLaunchers(
            launchCreateBackup = { createName = it },
            launchTree = { treeLaunched = true },
            launchRestore = { restoreLaunched = true },
        )
        launchers.launchCreateBackup("test.db")
        launchers.launchTree()
        launchers.launchRestore()
        assertEquals("test.db", createName)
        assertTrue(treeLaunched && restoreLaunched)
        assertEquals(launchers, launchers.copy())
        assertEquals(launchers.hashCode(), launchers.copy().hashCode())
    }

    private fun verifyCallbacksContract(uri: Uri) {
        var savedUri: Uri? = null
        var failMsg: String? = null
        var treeUri: Uri? = null
        var pinPrompt = false
        var restorePrompt = false
        val callbacks = BackupLauncherCallbacks(
            onBackupSaved = { savedUri = it },
            onBackupFailed = { failMsg = it },
            onTreeSelected = { treeUri = it },
            onRequirePin = { pinPrompt = true },
            onRequireRestore = { restorePrompt = true },
        )
        callbacks.onBackupSaved(uri)
        callbacks.onBackupFailed("error")
        callbacks.onTreeSelected(uri)
        callbacks.onRequirePin { /* no-op */ }
        callbacks.onRequireRestore { /* no-op */ }
        assertEquals(uri, savedUri)
        assertEquals("error", failMsg)
        assertEquals(uri, treeUri)
        assertTrue(pinPrompt && restorePrompt)
        assertEquals(callbacks.onBackupSaved, callbacks.component1())
        assertEquals(callbacks.onBackupFailed, callbacks.component2())
        assertEquals(callbacks.onTreeSelected, callbacks.component3())
        assertEquals(callbacks.onRequirePin, callbacks.component4())
        assertEquals(callbacks.onRequireRestore, callbacks.component5())
        assertEquals(callbacks, callbacks.copy())
    }

    private fun verifyStatePropsContract(uri: Uri) {
        val stateProps = BackupStateProps(
            lastExportedUri = uri,
            hasBackupRecords = true,
            autoBackupTreeUri = uri,
            backupResult = "Success",
        )
        assertEquals(uri, stateProps.lastExportedUri)
        assertTrue(stateProps.hasBackupRecords)
        assertEquals(uri, stateProps.autoBackupTreeUri)
        assertEquals("Success", stateProps.backupResult)
        assertEquals(stateProps.hashCode(), stateProps.copy().hashCode())
        val updatedProps = stateProps.copy(backupResult = null)
        assertNull(updatedProps.backupResult)
        assertNotEquals(stateProps, updatedProps)
    }
}
