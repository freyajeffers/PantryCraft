package com.jeffers.pantrycraft.ui

import androidx.activity.compose.ManagedActivityResultLauncher
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

    @Test
    fun settingsDialogsStateModel() {
        var pinTextChange = ""
        var selectedRec: BackupRecord? = null
        val triggered = mutableSetOf<String>()

        val dummyRecord = BackupRecord(
            backupName = "b1.db",
            createdAt = 500L,
            createdAtLabel = "label",
            exportedUri = "uri",
        )
        val catDialog = CatalogDialog("Title", "Msg", false)

        val state = SettingsDialogsState(
            showPinDialog = true,
            pinText = "1234",
            onPinTextChange = { pinTextChange = it },
            onDismissPinDialog = { triggered += "dismissPin" },
            onConfirmPinDialog = { triggered += "confirmPin" },
            showBackupList = true,
            backupRecords = listOf(dummyRecord),
            onDismissBackupList = { triggered += "dismissList" },
            onSelectBackup = { selectedRec = it },
            catalogDialog = catDialog,
            onDismissCatalogDialog = { triggered += "dismissCat" },
            confirmDatabaseReset = true,
            onDismissDatabaseReset = { triggered += "dismissReset" },
            confirmCatalogDelete = true,
            onDismissCatalogDelete = { triggered += "dismissCatDel" },
            onCatalogDeleted = { triggered += "catDeleted" },
            onCatalogDeleteFailed = { triggered += "catDeleteFailed" },
        )

        assertTrue(state.showPinDialog)
        assertEquals("1234", state.pinText)
        assertTrue(state.showBackupList)
        assertEquals(listOf(dummyRecord), state.backupRecords)
        assertEquals(catDialog, state.catalogDialog)
        assertTrue(state.confirmDatabaseReset && state.confirmCatalogDelete)

        state.onPinTextChange("5678")
        state.onDismissPinDialog()
        state.onConfirmPinDialog()
        state.onDismissBackupList()
        state.onSelectBackup(dummyRecord)
        state.onDismissCatalogDialog()
        state.onDismissDatabaseReset()
        state.onDismissCatalogDelete()
        state.onCatalogDeleted()
        state.onCatalogDeleteFailed()

        assertEquals("5678", pinTextChange)
        assertEquals(dummyRecord, selectedRec)
        assertEquals(8, triggered.size)

        assertEquals(state, state.copy())
        assertEquals(state.hashCode(), state.copy().hashCode())
        assertTrue(state.toString().contains("SettingsDialogsState"))
    }

    @Test
    fun settingsScreenStateModel() {
        val launcher = createDummyLauncher()
        var alertToggle = false
        val dialogs = createDummyDialogsState()
        val foodFacts = createDummyFoodFactsProps()
        val backupProps = createDummyEncryptedBackupProps()
        val sovereignty = DataSovereigntyProps({}, {}, {}, {})

        val screenState = SettingsScreenState(
            dialogsState = dialogs,
            foodFactsProps = foodFacts,
            encryptedBackupCardProps = backupProps,
            dataSovereigntyProps = sovereignty,
            expirationAlertsEnabled = true,
            onExpirationAlertsEnabledChange = { alertToggle = it },
            notificationPermissionLauncher = launcher,
            restoreResult = "Success",
        )

        assertEquals(dialogs, screenState.dialogsState)
        assertEquals(foodFacts, screenState.foodFactsProps)
        assertEquals(backupProps, screenState.encryptedBackupCardProps)
        assertEquals(sovereignty, screenState.dataSovereigntyProps)
        assertTrue(screenState.expirationAlertsEnabled)
        screenState.onExpirationAlertsEnabledChange(true)
        assertTrue(alertToggle)
        assertEquals(launcher, screenState.notificationPermissionLauncher)
        assertEquals("Success", screenState.restoreResult)
        assertEquals(screenState, screenState.copy())
        assertEquals(screenState.hashCode(), screenState.copy().hashCode())
        assertTrue(screenState.toString().contains("SettingsScreenState"))
    }

    @Test
    fun settingsDialogStateHolderOperations() {
        val dummyContext = object : android.content.ContextWrapper(null) {
            override fun getFilesDir(): java.io.File = java.io.File("/tmp")
        }
        val holder = SettingsDialogStateHolder(dummyContext)
        assertFalse(holder.showPinDialog)
        assertFalse(holder.showBackupList)
        assertFalse(holder.confirmDatabaseReset)
        assertFalse(holder.confirmCatalogDelete)
        org.junit.Assert.assertNull(holder.catalogDialog)

        holder.pinDialogAction(charArrayOf('a'))
        holder.pinDialogAction = { /* no-op */ }

        var pinSubmitted = ""
        holder.openPin { pinSubmitted = String(it) }
        assertTrue(holder.showPinDialog)
        assertEquals("", holder.pinText)

        holder.pinText = "9876"
        holder.confirmPin()
        assertFalse(holder.showPinDialog)
        assertEquals("9876", pinSubmitted)

        holder.openPin {}
        holder.closePin()
        assertFalse(holder.showPinDialog)

        holder.catalogDialog = CatalogDialog("Title", "Message", false)
        assertEquals("Title", holder.catalogDialog?.title)

        holder.confirmDatabaseReset = true
        assertTrue(holder.confirmDatabaseReset)

        holder.confirmCatalogDelete = true
        assertTrue(holder.confirmCatalogDelete)

        holder.openBackupList()
        assertTrue(holder.showBackupList)
    }
}

private fun createDummyLauncher(): ManagedActivityResultLauncher<String, Boolean> {
    val unsafeClass = Class.forName("sun.misc.Unsafe")
    val field = unsafeClass.getDeclaredField("theUnsafe")
    field.isAccessible = true
    val unsafeInstance = field.get(null)
    val allocate = unsafeClass.getMethod("allocateInstance", Class::class.java)
    @Suppress("UNCHECKED_CAST")
    return allocate.invoke(unsafeInstance, ManagedActivityResultLauncher::class.java)
        as ManagedActivityResultLauncher<String, Boolean>
}

private fun createDummyDialogsState() = SettingsDialogsState(
    showPinDialog = false,
    pinText = "",
    onPinTextChange = {},
    onDismissPinDialog = {},
    onConfirmPinDialog = {},
    showBackupList = false,
    backupRecords = emptyList(),
    onDismissBackupList = {},
    onSelectBackup = {},
    catalogDialog = null,
    onDismissCatalogDialog = {},
    confirmDatabaseReset = false,
    onDismissDatabaseReset = {},
    confirmCatalogDelete = false,
    onDismissCatalogDelete = {},
    onCatalogDeleted = {},
    onCatalogDeleteFailed = {},
)

private fun createDummyFoodFactsProps() = FoodFactsProps(
    catalogReady = true,
    catalogFileSize = 1024L,
    catalogDownloading = false,
    catalogDownloadedBytes = 0L,
    catalogTotalBytes = 1024L,
    onStartDownload = {},
    onDownloadResult = {},
    onProgress = { _, _ -> },
    onDelete = {},
)

private fun createDummyEncryptedBackupProps() = EncryptedBackupCardProps(
    autoBackupProps = AutomaticBackupProps(
        enabled = false,
        intervalMinutes = 1440L,
        databaseHasUserData = false,
        onDisable = {},
        onEnableRequested = {},
        onIntervalSelected = {},
        onBackupNow = {},
    ),
    lastExportedUri = null,
    hasBackupRecords = false,
    autoBackupTreeUri = null,
    backupResult = null,
    onShareSavedBackup = {},
    onRestoreSavedBackup = {},
    onSelectAutoBackupLocation = {},
    onOpenBackupFile = {},
    onResetDatabase = {},
)
