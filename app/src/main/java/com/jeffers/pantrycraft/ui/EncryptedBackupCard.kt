package com.jeffers.pantrycraft.ui

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EncryptedBackupCard(props: EncryptedBackupCardProps) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            EncryptedBackupHeader()
            AutomaticBackupCard(props = props.autoBackupProps)
            BackupRecoveryStatus(lastExportedUri = props.lastExportedUri)
            BackupActionButtons(props = props)
            props.backupResult?.let { Text(it, modifier = Modifier.padding(top = 12.dp)) }
        }
    }
}

@Composable
private fun EncryptedBackupHeader() {
    Text("Encrypted backup", style = MaterialTheme.typography.titleLarge)
    Text(
        "Save an encrypted copy of your pantry and grocery data on this device. " +
            "Choose a PIN that you can remember; you will need it to restore the backup. " +
            "Nothing is uploaded when you create a backup.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        "Backups are stored in the app's private storage.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun BackupRecoveryStatus(lastExportedUri: Uri?) {
    val message =
        if (lastExportedUri != null) {
            "Saved location remembered for recovery."
        } else {
            "Choose a file location so it can be found during recovery."
        }
    Text(
        text = message,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun BackupActionButtons(props: EncryptedBackupCardProps) {
    if (props.lastExportedUri != null) {
        OutlinedButton(
            onClick = props.onShareSavedBackup,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text("Share saved backup") }
    }
    if (props.hasBackupRecords) {
        OutlinedButton(
            onClick = props.onRestoreSavedBackup,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text("Restore saved backup") }
    }
    val autoBackupLabel =
        if (props.autoBackupTreeUri == null) {
            "Set auto-backup location"
        } else {
            "Change auto-backup location"
        }
    OutlinedButton(
        onClick = props.onSelectAutoBackupLocation,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) { Text(autoBackupLabel) }
    OutlinedButton(
        onClick = props.onOpenBackupFile,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) { Text("Open backup file to restore") }
    TextButton(
        onClick = props.onResetDatabase,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) { Text("Reset local database") }
}
