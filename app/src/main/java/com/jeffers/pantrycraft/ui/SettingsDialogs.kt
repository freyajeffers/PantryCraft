package com.jeffers.pantrycraft.ui

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jeffers.pantrycraft.data.AppDatabase
import com.jeffers.pantrycraft.data.DatabaseBackupManager.BackupRecord

internal data class CatalogDialog(
    val title: String,
    val message: String,
    val isError: Boolean,
)

internal data class SettingsDialogsState(
    val showPinDialog: Boolean,
    val pinText: String,
    val onPinTextChange: (String) -> Unit,
    val onDismissPinDialog: () -> Unit,
    val onConfirmPinDialog: () -> Unit,
    val showBackupList: Boolean,
    val backupRecords: List<BackupRecord>,
    val onDismissBackupList: () -> Unit,
    val onSelectBackup: (BackupRecord) -> Unit,
    val catalogDialog: CatalogDialog?,
    val onDismissCatalogDialog: () -> Unit,
    val confirmDatabaseReset: Boolean,
    val onDismissDatabaseReset: () -> Unit,
    val confirmCatalogDelete: Boolean,
    val onDismissCatalogDelete: () -> Unit,
    val onCatalogDeleted: () -> Unit,
    val onCatalogDeleteFailed: () -> Unit,
)

internal data class BackupListDialogState(
    val records: List<BackupRecord>,
    val onDismiss: () -> Unit,
    val onSelect: (BackupRecord) -> Unit,
)

@Composable
internal fun SettingsDialogs(context: Context, state: SettingsDialogsState) {
    if (state.showPinDialog) {
        BackupPinDialog(
            pinText = state.pinText,
            onPinTextChange = state.onPinTextChange,
            onDismiss = state.onDismissPinDialog,
            onConfirm = state.onConfirmPinDialog,
        )
    }
    if (state.showBackupList) {
        BackupListDialog(
            BackupListDialogState(
                records = state.backupRecords,
                onDismiss = state.onDismissBackupList,
                onSelect = state.onSelectBackup,
            ),
        )
    }
    SettingsCatalogAlertDialog(state.catalogDialog, state.onDismissCatalogDialog)
    SettingsResetDatabaseDialog(
        context = context,
        visible = state.confirmDatabaseReset,
        onDismiss = state.onDismissDatabaseReset,
    )
    if (state.confirmCatalogDelete) {
        CatalogDeleteDialog(
            context = context,
            onDismiss = state.onDismissCatalogDelete,
            onDeleted = state.onCatalogDeleted,
            onFailure = state.onCatalogDeleteFailed,
        )
    }
}

@Composable
internal fun BackupListDialog(state: BackupListDialogState) {
    AlertDialog(
        onDismissRequest = state.onDismiss,
        title = { Text("Choose a backup to restore") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.records.isEmpty()) {
                    Text("No backups have been created yet.")
                } else {
                    state.records.forEach { record ->
                        TextButton(onClick = { state.onSelect(record) }, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(record.createdAtLabel)
                                Text(
                                    record.exportedUri?.let {
                                        "File: ${Uri.parse(it).lastPathSegment ?: it}"
                                    } ?: "Location: app backup storage",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(record.backupName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = state.onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun BackupPinDialog(
    pinText: String,
    onPinTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter backup PIN") },
        text = {
            OutlinedTextField(
                value = pinText,
                onValueChange = onPinTextChange,
                label = { Text("PIN") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun SettingsCatalogAlertDialog(dialog: CatalogDialog?, onDismiss: () -> Unit) {
    dialog?.let {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(it.title) },
            text = { Text(it.message) },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("OK") }
            },
        )
    }
}

@Composable
internal fun SettingsResetDatabaseDialog(
    context: Context,
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset local database?") },
        text = {
            Text(
                "This permanently deletes pantry and grocery data on this device. " +
                    "Restore a backup first if needed.",
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    AppDatabase.resetEncryptedDatabase(context)
                    (context as? Activity)?.recreate()
                },
            ) { Text("Reset database") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
