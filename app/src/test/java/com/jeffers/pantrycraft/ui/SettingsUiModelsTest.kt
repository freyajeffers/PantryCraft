package com.jeffers.pantrycraft.ui

import com.jeffers.pantrycraft.data.AiProvider
import com.jeffers.pantrycraft.data.DatabaseBackupManager.BackupRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsUiModelsTest {

    @Test
    fun catalogDialogModel() {
        val defaultDialog = CatalogDialog(title = "Info", message = "All good", isError = false)
        assertEquals("Info", defaultDialog.title)
        assertEquals("All good", defaultDialog.message)
        assertFalse(defaultDialog.isError)

        val errorDialog = CatalogDialog(title = "Error", message = "Failed", isError = true)
        assertTrue(errorDialog.isError)

        val copied = defaultDialog.copy(isError = true)
        assertEquals(errorDialog.isError, copied.isError)
        assertNotEquals(defaultDialog, copied)
        assertEquals(defaultDialog, defaultDialog.copy())
        assertEquals(defaultDialog.hashCode(), defaultDialog.copy().hashCode())
        assertTrue(defaultDialog.toString().contains("CatalogDialog"))
    }

    @Test
    fun backupListDialogStateModel() {
        var dismissed = false
        var selectedRecord: BackupRecord? = null
        val dummyRecord = BackupRecord(
            backupName = "backup.db",
            createdAt = 1000L,
            createdAtLabel = "1970-01-01",
            exportedUri = "content://backup/1",
        )

        val state = BackupListDialogState(
            records = listOf(dummyRecord),
            onDismiss = { dismissed = true },
            onSelect = { selectedRecord = it },
        )

        assertEquals(listOf(dummyRecord), state.records)
        state.onDismiss()
        state.onSelect(dummyRecord)
        assertTrue(dismissed)
        assertEquals(dummyRecord, selectedRecord)

        val copied = state.copy(records = emptyList())
        assertTrue(copied.records.isEmpty())
        assertNotEquals(state, copied)
    }

    @Test
    fun autoBackupScheduleArgsModel() {
        var scheduleChanged = false
        var pinRequired = false
        var backupPerformed = false

        val args = AutoBackupScheduleArgs(
            enabled = true,
            intervalMinutes = 360L,
            databaseHasUserData = true,
            onScheduleChange = { _, _ -> scheduleChanged = true },
            onRequirePin = { pinRequired = true },
            onPerformBackup = { backupPerformed = true },
        )

        assertTrue(args.enabled)
        assertEquals(360L, args.intervalMinutes)
        assertTrue(args.databaseHasUserData)

        args.onScheduleChange(false, 720L)
        args.onRequirePin { /* no-op */ }
        args.onPerformBackup(charArrayOf('1', '2'))

        assertTrue(scheduleChanged && pinRequired && backupPerformed)
        assertEquals(args, args.copy())
    }

    @Test
    fun backupLaunchersModel() {
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
    }

    @Test
    fun settingsDialogActionsModel() {
        var catalogShown = false
        var deleteCatalog = false
        var pinRequired = false
        var listOpened = false
        var resetDb = false

        val actions = SettingsDialogActions(
            showCatalogDialog = { catalogShown = true },
            requestDeleteCatalog = { deleteCatalog = true },
            requirePin = { pinRequired = true },
            openBackupList = { listOpened = true },
            requestResetDatabase = { resetDb = true },
        )

        actions.showCatalogDialog(CatalogDialog("T", "M", false))
        actions.requestDeleteCatalog()
        actions.requirePin { /* no-op */ }
        actions.openBackupList()
        actions.requestResetDatabase()

        assertTrue(catalogShown && deleteCatalog && pinRequired && listOpened && resetDb)
        assertEquals(actions, actions.copy())
    }

    @Test
    fun aiCustomFieldsPropsModel() {
        var model = "claude"
        var url = "http://localhost"
        var key = "secret"

        val props = AiCustomFieldsProps(
            provider = AiProvider.OPENAI,
            modelName = model,
            onModelNameChange = { model = it },
            endpointUrl = url,
            onEndpointUrlChange = { url = it },
            apiKey = key,
            onApiKeyChange = { key = it },
            hasStoredApiKey = true,
        )

        assertEquals(AiProvider.OPENAI, props.provider)
        assertTrue(props.hasStoredApiKey)
        assertEquals(model, props.modelName)
        assertEquals(url, props.endpointUrl)
        assertEquals(key, props.apiKey)

        props.onModelNameChange("gpt-4")
        props.onEndpointUrlChange("https://api.openai.com")
        props.onApiKeyChange("new-secret")

        assertEquals("gpt-4", model)
        assertEquals("https://api.openai.com", url)
        assertEquals("new-secret", key)
        assertEquals(props, props.copy())
        assertEquals(props.hashCode(), props.copy().hashCode())
        assertTrue(props.toString().contains("AiCustomFieldsProps"))
    }

    @Test
    fun recoveryContentStateModel() {
        var selected = "none"
        var pinInput = ""
        var openCalled = false
        var restoreCalled = false
        var resetCalled = false

        val state = RecoveryContentState(
            backups = listOf("backup-1.db"),
            selectedBackup = "backup-1.db",
            pin = "1234",
            error = "Invalid PIN",
            onSelectBackup = { selected = it },
            onPinChange = { pinInput = it },
            onOpenBackup = { openCalled = true },
            onRestore = { restoreCalled = true },
            onReset = { resetCalled = true },
        )

        assertEquals(listOf("backup-1.db"), state.backups)
        assertEquals("backup-1.db", state.selectedBackup)
        assertEquals("1234", state.pin)
        assertEquals("Invalid PIN", state.error)

        state.onSelectBackup("backup-2.db")
        state.onPinChange("5678")
        state.onOpenBackup()
        state.onRestore()
        state.onReset()

        assertEquals("backup-2.db", selected)
        assertEquals("5678", pinInput)
        assertTrue(openCalled && restoreCalled && resetCalled)
    }
}
