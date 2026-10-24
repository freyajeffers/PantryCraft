package com.jeffers.pantrycraft.ui

import android.net.Uri
import com.jeffers.pantrycraft.data.PantryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UiPropsModelTest {

    @Test
    fun automaticBackupPropsDataClassModel() {
        var disabledCalled = false
        var enableCalled = false
        var intervalSet: Long? = null
        var backupNowCalled = false

        val props = AutomaticBackupProps(
            enabled = true,
            intervalMinutes = 1440L,
            databaseHasUserData = true,
            onDisable = { disabledCalled = true },
            onEnableRequested = { enableCalled = true },
            onIntervalSelected = { intervalSet = it },
            onBackupNow = { backupNowCalled = true },
        )

        assertTrue(props.enabled)
        assertEquals(1440L, props.intervalMinutes)
        assertTrue(props.databaseHasUserData)

        props.onDisable()
        props.onEnableRequested()
        props.onIntervalSelected(720L)
        props.onBackupNow()

        assertTrue(disabledCalled)
        assertTrue(enableCalled)
        assertEquals(720L, intervalSet)
        assertTrue(backupNowCalled)

        val copied = props.copy(enabled = false)
        assertFalse(copied.enabled)
        assertNotEquals(props, copied)
    }

    @Test
    fun encryptedBackupCardPropsDataClassModel() {
        val dummyAutoProps = AutomaticBackupProps(
            enabled = false,
            intervalMinutes = 720L,
            databaseHasUserData = false,
            onDisable = {},
            onEnableRequested = {},
            onIntervalSelected = {},
            onBackupNow = {},
        )
        val dummyUri = Uri.parse("content://backups/1")

        var shareCalled = false
        var restoreSavedCalled = false
        var selectLocationCalled = false
        var openFileCalled = false
        var resetDbCalled = false

        val cardProps = EncryptedBackupCardProps(
            autoBackupProps = dummyAutoProps,
            lastExportedUri = dummyUri,
            hasBackupRecords = true,
            autoBackupTreeUri = dummyUri,
            backupResult = "Success",
            onShareSavedBackup = { shareCalled = true },
            onRestoreSavedBackup = { restoreSavedCalled = true },
            onSelectAutoBackupLocation = { selectLocationCalled = true },
            onOpenBackupFile = { openFileCalled = true },
            onResetDatabase = { resetDbCalled = true },
        )

        cardProps.onShareSavedBackup()
        cardProps.onRestoreSavedBackup()
        cardProps.onSelectAutoBackupLocation()
        cardProps.onOpenBackupFile()
        cardProps.onResetDatabase()

        assertTrue(shareCalled)
        assertTrue(restoreSavedCalled)
        assertTrue(selectLocationCalled)
        assertTrue(openFileCalled)
        assertTrue(resetDbCalled)

        assertEquals(dummyAutoProps, cardProps.autoBackupProps)
        assertEquals(dummyUri, cardProps.lastExportedUri)
        assertTrue(cardProps.hasBackupRecords)
        assertEquals(dummyUri, cardProps.autoBackupTreeUri)
        assertEquals("Success", cardProps.backupResult)

        val updated = cardProps.copy(backupResult = "Failed")
        assertEquals("Failed", updated.backupResult)
    }

    @Test
    fun dataSovereigntyPropsDataClassModel() {
        var exportJson = false
        var exportCsv = false
        var importJson = false
        var importCsv = false

        val props = DataSovereigntyProps(
            onExportJson = { exportJson = true },
            onExportCsv = { exportCsv = true },
            onImportJson = { importJson = true },
            onImportCsv = { importCsv = true },
        )

        props.onExportJson()
        props.onExportCsv()
        props.onImportJson()
        props.onImportCsv()

        assertTrue(exportJson)
        assertTrue(exportCsv)
        assertTrue(importJson)
        assertTrue(importCsv)
    }

    @Test
    fun backupCallbacksDataClassModel() {
        var pinPrompted = false
        var restoreRequested = false
        var showList = false
        var reset = false

        val callbacks = BackupCallbacks(
            onRequirePin = { pinPrompted = true },
            onRequireRestore = { restoreRequested = true },
            onShowBackupList = { showList = true },
            onResetDatabase = { reset = true },
        )

        callbacks.onRequirePin {}
        callbacks.onRequireRestore {}
        callbacks.onShowBackupList()
        callbacks.onResetDatabase()

        assertTrue(pinPrompted)
        assertTrue(restoreRequested)
        assertTrue(showList)
        assertTrue(reset)
    }

    @Test
    fun foodFactsPropsDataClassModel() {
        var downloadStarted = false
        var deleted = false
        var progressTotal = 0L

        val props = FoodFactsProps(
            catalogReady = true,
            catalogFileSize = 5000L,
            catalogDownloading = false,
            catalogDownloadedBytes = 2500L,
            catalogTotalBytes = 5000L,
            onStartDownload = { downloadStarted = true },
            onDownloadResult = {},
            onProgress = { _, total -> progressTotal = total },
            onDelete = { deleted = true },
        )

        assertTrue(props.catalogReady)
        assertEquals(5000L, props.catalogFileSize)
        assertFalse(props.catalogDownloading)
        assertEquals(2500L, props.catalogDownloadedBytes)
        assertEquals(5000L, props.catalogTotalBytes)

        props.onStartDownload()
        props.onDelete()
        props.onProgress(2500L, 5000L)
        props.onDownloadResult(Result.success(File("dummy")))

        assertTrue(downloadStarted)
        assertTrue(deleted)
        assertEquals(5000L, progressTotal)
    }

    @Test
    fun pantryListContentPropsDataClassModel() {
        var queryChanged: String? = null
        var selectedFilter: PantryLocationFilter? = null
        val items = listOf(PantryItem(name = "Apples", quantity = 3f))

        val props = PantryListContentProps(
            items = items,
            selectedLocation = PantryLocationFilter.FRIDGE,
            searchQuery = "app",
            onSearchQueryChange = { queryChanged = it },
            onLocationSelected = { selectedFilter = it },
            itemContent = {},
        )

        assertEquals(items, props.items)
        assertEquals(PantryLocationFilter.FRIDGE, props.selectedLocation)
        assertEquals("app", props.searchQuery)

        props.onSearchQueryChange("banana")
        props.onLocationSelected(PantryLocationFilter.FREEZER)

        assertEquals("banana", queryChanged)
        assertEquals(PantryLocationFilter.FREEZER, selectedFilter)
    }

    @Test
    fun pantryScreenOverlayPropsDataClassModel() {
        var editorDismissed = false
        var useDismissed = false
        var errorDismissed = false
        var confirmedUse = 0f
        val item = PantryItem(name = "Flour", quantity = 10f)

        val props = PantryScreenOverlayProps(
            showAddDialog = true,
            editorItem = item,
            onSaveDraft = { _, _ -> },
            onDismissEditor = { editorDismissed = true },
            useItem = item,
            useAmount = "2.5",
            onAmountChange = {},
            onConfirmUse = { confirmedUse = it },
            onDismissUse = { useDismissed = true },
            storageError = "Full",
            onDismissStorageError = { errorDismissed = true },
        )

        assertTrue(props.showAddDialog)
        assertEquals(item, props.editorItem)
        assertEquals("2.5", props.useAmount)
        assertEquals("Full", props.storageError)

        props.onDismissEditor()
        props.onDismissUse()
        props.onDismissStorageError()
        props.onConfirmUse(2.5f)

        assertTrue(editorDismissed)
        assertTrue(useDismissed)
        assertTrue(errorDismissed)
        assertEquals(2.5f, confirmedUse)
    }
}
