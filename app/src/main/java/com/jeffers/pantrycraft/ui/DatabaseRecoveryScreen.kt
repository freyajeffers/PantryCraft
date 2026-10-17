package com.jeffers.pantrycraft.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DatabaseRecoveryScreen(
    context: android.content.Context,
    onRestore: (String, CharArray) -> Unit,
    onReset: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val backups =
        remember {
            DatabaseBackupManager.listBackups(context) +
                listOfNotNull(DatabaseBackupManager.persistedBackupUri(context)?.toString())
        }
    var selectedBackup by remember { mutableStateOf(backups.firstOrNull()) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var pendingRestorePin by remember { mutableStateOf<CharArray?>(null) }
    val openBackupLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            val suppliedPin = pendingRestorePin
            pendingRestorePin = null
            if (uri != null && suppliedPin != null) {
                scope.launch {
                    error =
                        withContext(Dispatchers.IO) {
                            runCatching { onRestore(uri.toString(), suppliedPin) }
                                .exceptionOrNull()
                                ?.message
                        }
                }
            }
        }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset local database?") },
            text = {
                Text("This permanently deletes the local pantry and grocery database. Restore a backup first if you need to keep it.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        onReset()
                    },
                ) { Text("Reset database") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            },
        )
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Encrypted database recovery required", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Restore a PIN-protected backup to recover the database and its SQLCipher key.",
            modifier = Modifier.padding(vertical = 16.dp),
        )
        if (backups.isEmpty()) {
            Text("No encrypted backups are available on this device.")
        } else {
            backups.forEach { backup ->
                TextButton(onClick = { selectedBackup = backup }) {
                    val label =
                        if (backup.startsWith("content://")) {
                            Uri.parse(backup).lastPathSegment ?: "Saved backup file"
                        } else {
                            backup
                        }
                    Text(if (backup == selectedBackup) "✓ $label" else label)
                }
            }
            OutlinedButton(
                value = pin,
                onValueChange = { pin = it },
                label = { Text("Backup PIN") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                enabled = pin.isNotEmpty(),
                onClick = {
                    pendingRestorePin = pin.toCharArray()
                    pin = ""
                    openBackupLauncher.launch(arrayOf("application/octet-stream", "application/*"))
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text("Open backup file")
            }
            Row(modifier = Modifier.padding(top = 8.dp)) {
                Button(
                    enabled = selectedBackup != null && pin.isNotEmpty(),
                    onClick = {
                        val backup = selectedBackup ?: return@Button
                        val suppliedPin = pin.toCharArray()
                        pin = ""
                        scope.launch {
                            error =
                                withContext(Dispatchers.IO) {
                                    runCatching { onRestore(backup, suppliedPin) }
                                        .exceptionOrNull()
                                        ?.message
                                }
                        }
                    },
                ) {
                    Text("Restore selected backup")
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = { confirmReset = true }) {
            Text("Reset local database")
        }
    }
}
