package com.jeffers.pantrycraft.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jeffers.pantrycraft.data.DatabaseBackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DatabaseBackupScreen(context: android.content.Context) {
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Create encrypted backup", style = MaterialTheme.typography.headlineSmall)
        Text("The database and SQLCipher key will be encrypted together under this PIN.")
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it },
            label = { Text("Backup PIN") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        )
        Button(
            enabled = pin.isNotEmpty(),
            onClick = {
                val suppliedPin = pin.toCharArray()
                pin = ""
                scope.launch {
                    result =
                        withContext(Dispatchers.IO) {
                            runCatching {
                                DatabaseBackupManager.createEncryptedBackup(
                                    context,
                                    suppliedPin,
                                )
                            }.fold(
                                onSuccess = { "Backup created: $it" },
                                onFailure = { "Backup failed: ${it.message}" },
                            )
                        }
                }
            },
        ) {
            Text("Create backup")
        }
        result?.let { Text(it, modifier = Modifier.padding(top = 16.dp)) }
    }
}

@Composable
fun DatabaseRecoveryScreen(
    context: android.content.Context,
    onRestore: (String, CharArray) -> Unit,
    onReset: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val backups = remember { DatabaseBackupManager.listBackups(context) }
    var selectedBackup by remember { mutableStateOf(backups.firstOrNull()) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
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
                    Text(if (backup == selectedBackup) "✓ $backup" else backup)
                }
            }
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it },
                label = { Text("Backup PIN") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.padding(4.dp))
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
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = onReset) {
            Text("Reset local database")
        }
    }
}
