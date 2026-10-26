package com.jeffers.pantrycraft.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jeffers.pantrycraft.data.AutomaticBackupPreferences
import com.jeffers.pantrycraft.data.AutomaticBackupScheduler
import com.jeffers.pantrycraft.data.DatabaseBackupManager
import com.jeffers.pantrycraft.data.persistAutoBackupTreeUri
import com.jeffers.pantrycraft.data.persistedAutoBackupTreeUri
import com.jeffers.pantrycraft.data.persistedBackupUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.jeffers.pantrycraft.data.backupRecords as loadBackupRecords

internal data class BackupLaunchers(
    val launchCreateBackup: (String) -> Unit,
    val launchTree: () -> Unit,
    val launchRestore: () -> Unit,
)

internal data class BackupLauncherCallbacks(
    val onBackupSaved: (Uri) -> Unit,
    val onBackupFailed: (String) -> Unit,
    val onTreeSelected: (Uri) -> Unit,
    val onRequirePin: ((CharArray) -> Unit) -> Unit,
    val onRequireRestore: (suspend () -> Unit) -> Unit,
)

internal data class AutoBackupScheduleArgs(
    val enabled: Boolean,
    val intervalMinutes: Long,
    val databaseHasUserData: Boolean,
    val onScheduleChange: (Boolean, Long) -> Unit,
    val onRequirePin: ((CharArray) -> Unit) -> Unit,
    val onPerformBackup: (CharArray) -> Unit,
)

internal data class BackupStateProps(
    val lastExportedUri: Uri?,
    val hasBackupRecords: Boolean,
    val autoBackupTreeUri: Uri?,
    val backupResult: String?,
)

@Composable
internal fun rememberEncryptedBackupCardProps(
    context: Context,
    scope: CoroutineScope,
    databaseHasUserData: Boolean,
    callbacks: BackupCallbacks,
): EncryptedBackupCardProps {
    var lastExportedUri by remember { mutableStateOf(persistedBackupUri(context)) }
    var backupRecords by remember { mutableStateOf(loadBackupRecords(context)) }
    var autoBackupTreeUri by remember { mutableStateOf(persistedAutoBackupTreeUri(context)) }
    var automaticBackupsEnabled by remember { mutableStateOf(AutomaticBackupPreferences.isEnabled(context)) }
    var automaticBackupIntervalMinutes by remember {
        mutableStateOf(AutomaticBackupPreferences.intervalMinutes(context))
    }
    var backupResult by remember { mutableStateOf<String?>(null) }

    lateinit var launchers: BackupLaunchers
    val autoBackupProps =
        buildAutoBackupProps(
            context = context,
            args =
                AutoBackupScheduleArgs(
                    enabled = automaticBackupsEnabled,
                    intervalMinutes = automaticBackupIntervalMinutes,
                    databaseHasUserData = databaseHasUserData,
                    onScheduleChange = { enabled, minutes ->
                        automaticBackupsEnabled = enabled
                        automaticBackupIntervalMinutes = minutes
                    },
                    onRequirePin = callbacks.onRequirePin,
                    onPerformBackup = { suppliedPin ->
                        scope.launch {
                            backupResult =
                                performEncryptedBackup(context, suppliedPin) { createdName ->
                                    backupRecords = loadBackupRecords(context)
                                    launchers.launchCreateBackup(createdName)
                                }
                        }
                    },
                ),
        )
    launchers =
        rememberBackupLaunchers(
            context = context,
            scope = scope,
            callbacks =
                BackupLauncherCallbacks(
                    onBackupSaved = { uri ->
                        lastExportedUri = uri
                        backupRecords = loadBackupRecords(context)
                        Toast.makeText(context, "Encrypted backup saved", Toast.LENGTH_SHORT).show()
                    },
                    onBackupFailed = { backupResult = it },
                    onTreeSelected = { autoBackupTreeUri = it },
                    onRequirePin = callbacks.onRequirePin,
                    onRequireRestore = callbacks.onRequireRestore,
                ),
        )
    return assembleEncryptedBackupProps(
        context = context,
        autoProps = autoBackupProps,
        state = BackupStateProps(lastExportedUri, backupRecords.isNotEmpty(), autoBackupTreeUri, backupResult),
        launchers = launchers,
        callbacks = callbacks,
    )
}

private fun assembleEncryptedBackupProps(
    context: Context,
    autoProps: AutomaticBackupProps,
    state: BackupStateProps,
    launchers: BackupLaunchers,
    callbacks: BackupCallbacks,
): EncryptedBackupCardProps =
    EncryptedBackupCardProps(
        autoBackupProps = autoProps,
        lastExportedUri = state.lastExportedUri,
        hasBackupRecords = state.hasBackupRecords,
        autoBackupTreeUri = state.autoBackupTreeUri,
        backupResult = state.backupResult,
        onShareSavedBackup = { state.lastExportedUri?.let { shareBackupFile(context, it) } },
        onRestoreSavedBackup = callbacks.onShowBackupList,
        onSelectAutoBackupLocation = launchers.launchTree,
        onOpenBackupFile = launchers.launchRestore,
        onResetDatabase = callbacks.onResetDatabase,
    )

@Composable
private fun rememberBackupLaunchers(
    context: Context,
    scope: CoroutineScope,
    callbacks: BackupLauncherCallbacks,
): BackupLaunchers {
    var pendingBackupName by remember { mutableStateOf<String?>(null) }
    val createBackupLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            val backupName = pendingBackupName
            pendingBackupName = null
            if (uri != null && backupName != null) {
                takePersistablePermissions(context, uri)
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) { DatabaseBackupManager.exportBackup(context, backupName, uri) }
                    }.onSuccess {
                        callbacks.onBackupSaved(uri)
                    }.onFailure {
                        callbacks.onBackupFailed("Backup export failed: ${it.message}")
                    }
                }
            }
        }
    val autoBackupTreeLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                takePersistablePermissions(context, uri)
                persistAutoBackupTreeUri(context, uri)
                callbacks.onTreeSelected(uri)
            }
        }
    val openRestoreLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                callbacks.onRequirePin { suppliedPin ->
                    callbacks.onRequireRestore {
                        DatabaseBackupManager.restoreEncryptedBackupFromUri(context, uri, suppliedPin)
                    }
                }
            }
        }
    return BackupLaunchers(
        launchCreateBackup = {
            pendingBackupName = it
            createBackupLauncher.launch(it)
        },
        launchTree = { autoBackupTreeLauncher.launch(null) },
        launchRestore = { openRestoreLauncher.launch(arrayOf("application/octet-stream", "application/*")) },
    )
}

internal fun buildAutoBackupProps(
    context: Context,
    args: AutoBackupScheduleArgs,
): AutomaticBackupProps =
    AutomaticBackupProps(
        enabled = args.enabled,
        intervalMinutes = args.intervalMinutes,
        databaseHasUserData = args.databaseHasUserData,
        onDisable = {
            args.onScheduleChange(false, args.intervalMinutes)
            AutomaticBackupPreferences.setSchedule(context, enabled = false, intervalMinutes = args.intervalMinutes)
            AutomaticBackupScheduler.cancel(context)
        },
        onEnableRequested = {
            args.onRequirePin { suppliedPin ->
                AutomaticBackupPreferences.savePin(context, suppliedPin)
                AutomaticBackupPreferences.setSchedule(context, enabled = true, intervalMinutes = args.intervalMinutes)
                args.onScheduleChange(true, args.intervalMinutes)
                AutomaticBackupScheduler.schedule(context, args.intervalMinutes)
            }
        },
        onIntervalSelected = { minutes ->
            args.onScheduleChange(true, minutes)
            AutomaticBackupPreferences.setSchedule(context, enabled = true, intervalMinutes = minutes)
            AutomaticBackupScheduler.schedule(context, minutes)
        },
        onBackupNow = { args.onRequirePin(args.onPerformBackup) },
    )

private suspend fun performEncryptedBackup(
    context: Context,
    pin: CharArray,
    onSuccess: (String) -> Unit,
): String =
    withContext(Dispatchers.IO) {
        runCatching {
            DatabaseBackupManager.createEncryptedBackup(context, pin)
        }.fold(
            onSuccess = {
                onSuccess(it)
                "Backup created. Choose where to save the encrypted file."
            },
            onFailure = { "Backup failed: ${it.message}" },
        )
    }

private fun takePersistablePermissions(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }
}

private fun shareBackupFile(context: Context, uri: Uri) {
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "Share encrypted backup",
        ),
    )
}
