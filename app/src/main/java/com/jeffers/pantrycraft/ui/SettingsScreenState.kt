package com.jeffers.pantrycraft.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jeffers.pantrycraft.data.DatabaseBackupManager
import com.jeffers.pantrycraft.data.DatabaseBackupManager.BackupRecord
import com.jeffers.pantrycraft.data.ExpirationAlertWorker
import com.jeffers.pantrycraft.data.Repository
import com.jeffers.pantrycraft.feature.camera.barcode.CatalogDownloadService
import kotlinx.coroutines.CoroutineScope
import java.io.File
import com.jeffers.pantrycraft.data.backupRecords as loadBackupRecords

internal data class SettingsScreenState(
    val dialogsState: SettingsDialogsState,
    val foodFactsProps: FoodFactsProps,
    val encryptedBackupCardProps: EncryptedBackupCardProps,
    val dataSovereigntyProps: DataSovereigntyProps,
    val expirationAlertsEnabled: Boolean,
    val onExpirationAlertsEnabledChange: (Boolean) -> Unit,
    val notificationPermissionLauncher: ManagedActivityResultLauncher<String, Boolean>,
    val restoreResult: String?,
)

internal data class SettingsDialogActions(
    val showCatalogDialog: (CatalogDialog) -> Unit,
    val requestDeleteCatalog: () -> Unit,
    val requirePin: ((CharArray) -> Unit) -> Unit,
    val openBackupList: () -> Unit,
    val requestResetDatabase: () -> Unit,
)

internal class SettingsDialogStateHolder(private val context: Context) {
    var showPinDialog by mutableStateOf(false)
    var pinDialogAction: (CharArray) -> Unit = {}
    var pinText by mutableStateOf("")
    var showBackupList by mutableStateOf(false)
    var confirmDatabaseReset by mutableStateOf(false)
    var confirmCatalogDelete by mutableStateOf(false)
    var catalogDialog by mutableStateOf<CatalogDialog?>(null)
    var backupRecords by mutableStateOf(loadBackupRecords(context))

    fun openPin(action: (CharArray) -> Unit) {
        pinDialogAction = action
        pinText = ""
        showPinDialog = true
    }

    fun closePin() {
        showPinDialog = false
        pinText = ""
    }

    fun confirmPin() {
        val chars = pinText.toCharArray()
        closePin()
        pinDialogAction(chars)
    }

    fun openBackupList() {
        showBackupList = true
        backupRecords = loadBackupRecords(context)
    }
}

@Composable
internal fun rememberSettingsScreenState(
    context: Context,
    repository: Repository,
    scope: CoroutineScope,
    databaseHasUserData: Boolean,
): SettingsScreenState {
    var restoreResult by remember { mutableStateOf<String?>(null) }
    var expirationAlertsEnabled by remember { mutableStateOf(ExpirationAlertWorker.isEnabled(context)) }
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            expirationAlertsEnabled = granted
            ExpirationAlertWorker.setEnabled(context, granted)
        }

    lateinit var dialogActions: SettingsDialogActions
    val (foodFactsProps, onCatalogDeleted) =
        rememberSettingsFoodFactsProps(
            context = context,
            onShowDialog = { dialogActions.showCatalogDialog(it) },
            onRequestDelete = { dialogActions.requestDeleteCatalog() },
        )

    val (dialogsState, actions) =
        rememberSettingsDialogsState(
            context = context,
            scope = scope,
            onCatalogDeleted = onCatalogDeleted,
            onRestoreResult = { restoreResult = it },
        )
    dialogActions = actions

    val encryptedBackupCardProps =
        rememberEncryptedBackupCardProps(
            context = context,
            scope = scope,
            databaseHasUserData = databaseHasUserData,
            callbacks =
                BackupCallbacks(
                    onRequirePin = dialogActions.requirePin,
                    onRequireRestore = { restore ->
                        launchRestore(context, scope, restore) { restoreResult = it }
                    },
                    onShowBackupList = dialogActions.openBackupList,
                    onResetDatabase = dialogActions.requestResetDatabase,
                ),
        )

    return SettingsScreenState(
        dialogsState = dialogsState,
        foodFactsProps = foodFactsProps,
        encryptedBackupCardProps = encryptedBackupCardProps,
        dataSovereigntyProps = rememberDataSovereigntyProps(context, repository, scope),
        expirationAlertsEnabled = expirationAlertsEnabled,
        onExpirationAlertsEnabledChange = { expirationAlertsEnabled = it },
        notificationPermissionLauncher = notificationPermissionLauncher,
        restoreResult = restoreResult,
    )
}

@Composable
internal fun rememberSettingsDialogsState(
    context: Context,
    scope: CoroutineScope,
    onCatalogDeleted: () -> Unit,
    onRestoreResult: (String?) -> Unit,
): Pair<SettingsDialogsState, SettingsDialogActions> {
    val holder = remember { SettingsDialogStateHolder(context) }
    val dialogsState =
        SettingsDialogsState(
            showPinDialog = holder.showPinDialog,
            pinText = holder.pinText,
            onPinTextChange = { holder.pinText = it },
            onDismissPinDialog = holder::closePin,
            onConfirmPinDialog = holder::confirmPin,
            showBackupList = holder.showBackupList,
            backupRecords = holder.backupRecords,
            onDismissBackupList = { holder.showBackupList = false },
            onSelectBackup = { record ->
                holder.showBackupList = false
                holder.openPin { suppliedPin ->
                    restoreBackupRecord(context, scope, record, suppliedPin, onRestoreResult)
                }
            },
            catalogDialog = holder.catalogDialog,
            onDismissCatalogDialog = { holder.catalogDialog = null },
            confirmDatabaseReset = holder.confirmDatabaseReset,
            onDismissDatabaseReset = { holder.confirmDatabaseReset = false },
            confirmCatalogDelete = holder.confirmCatalogDelete,
            onDismissCatalogDelete = { holder.confirmCatalogDelete = false },
            onCatalogDeleted = {
                holder.confirmCatalogDelete = false
                onCatalogDeleted()
            },
            onCatalogDeleteFailed = {
                holder.catalogDialog = CatalogDialog("Delete failed", "Catalog could not be removed.", true)
            },
        )
    val actions =
        SettingsDialogActions(
            showCatalogDialog = { holder.catalogDialog = it },
            requestDeleteCatalog = { holder.confirmCatalogDelete = true },
            requirePin = holder::openPin,
            openBackupList = holder::openBackupList,
            requestResetDatabase = { holder.confirmDatabaseReset = true },
        )
    return Pair(dialogsState, actions)
}

@Composable
private fun rememberSettingsFoodFactsProps(
    context: Context,
    onShowDialog: (CatalogDialog) -> Unit,
    onRequestDelete: () -> Unit,
): Pair<FoodFactsProps, () -> Unit> {
    var catalogReady by remember {
        mutableStateOf(File(context.noBackupFilesDir, CatalogDownloadService.CATALOG_FILE_NAME).isFile)
    }
    var catalogFileSize by remember {
        mutableStateOf(
            File(context.noBackupFilesDir, CatalogDownloadService.CATALOG_FILE_NAME)
                .takeIf { it.isFile }
                ?.length(),
        )
    }
    var catalogDownloading by remember { mutableStateOf(false) }
    var catalogDownloadedBytes by remember { mutableStateOf(0L) }
    var catalogTotalBytes by remember { mutableStateOf(-1L) }

    val props =
        FoodFactsProps(
            catalogReady = catalogReady,
            catalogFileSize = catalogFileSize,
            catalogDownloading = catalogDownloading,
            catalogDownloadedBytes = catalogDownloadedBytes,
            catalogTotalBytes = catalogTotalBytes,
            onStartDownload = {
                catalogDownloading = true
                catalogDownloadedBytes = 0L
                catalogTotalBytes = -1L
            },
            onDownloadResult = { result ->
                catalogDownloading = false
                handleDownloadResult(
                    context = context,
                    result = result,
                    onSuccess = { length ->
                        catalogReady = true
                        catalogFileSize = length
                    },
                    onFailure = onShowDialog,
                )
            },
            onProgress = { downloaded, total ->
                catalogDownloadedBytes = downloaded
                catalogTotalBytes = total
            },
            onDelete = onRequestDelete,
        )
    val onReset = {
        catalogReady = false
        catalogFileSize = null
    }
    return Pair(props, onReset)
}

internal fun handleDownloadResult(
    context: Context,
    result: Result<File>,
    onSuccess: (Long) -> Unit,
    onFailure: (CatalogDialog) -> Unit,
) {
    result.fold(
        onSuccess = { file ->
            onSuccess(file.length())
            Toast.makeText(
                context,
                "Food catalog downloaded (${formatFileSize(file.length())})",
                Toast.LENGTH_SHORT,
            ).show()
        },
        onFailure = {
            onFailure(
                CatalogDialog(
                    title = "Download failed",
                    message = it.message ?: "The catalog could not be downloaded.",
                    isError = true,
                ),
            )
        },
    )
}

internal fun restoreBackupRecord(
    context: Context,
    scope: CoroutineScope,
    record: BackupRecord,
    suppliedPin: CharArray,
    onResult: (String?) -> Unit,
) {
    record.exportedUri?.let { uri ->
        launchRestore(
            context,
            scope,
            { DatabaseBackupManager.restoreEncryptedBackupFromUri(context, Uri.parse(uri), suppliedPin) },
            onResult,
        )
    } ?: launchRestore(
        context,
        scope,
        { DatabaseBackupManager.restoreEncryptedBackup(context, record.backupName, suppliedPin) },
        onResult,
    )
}
