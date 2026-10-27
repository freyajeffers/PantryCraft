package com.jeffers.pantrycraft.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jeffers.pantrycraft.data.Repository
import com.jeffers.pantrycraft.data.listBackupFiles
import com.jeffers.pantrycraft.data.persistedBackupUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    context: android.content.Context,
    repository: Repository,
) {
    val scope = rememberCoroutineScope()
    val pantryItems by repository.observePantryItems().collectAsState(initial = emptyList())
    val groceryItems by repository.observeGroceryItems().collectAsState(initial = emptyList())
    val databaseHasUserData = pantryItems.isNotEmpty() || groceryItems.isNotEmpty()

    val state = rememberSettingsScreenState(context, repository, scope, databaseHasUserData)

    SettingsDialogs(context, state.dialogsState)
    SettingsScreenContent(
        context = context,
        repository = repository,
        scope = scope,
        state = state,
    )
}

@Composable
private fun SettingsScreenContent(
    context: android.content.Context,
    repository: Repository,
    scope: CoroutineScope,
    state: SettingsScreenState,
) {
    state.restoreResult?.let { message ->
        if (message.isNotBlank()) {
            Text(message, color = MaterialTheme.colorScheme.error)
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsHeader()
        FoodFactsCard(repository = repository, scope = scope, props = state.foodFactsProps)
        NotificationSettingsCard(
            context = context,
            enabled = state.expirationAlertsEnabled,
            onEnabledChange = state.onExpirationAlertsEnabledChange,
            permissionLauncher = state.notificationPermissionLauncher,
        )
        EncryptedBackupCard(props = state.encryptedBackupCardProps)
        DataSovereigntyCard(props = state.dataSovereigntyProps)
        AiSettingsCard(context = context)
    }
}

@Composable
private fun SettingsHeader() {
    Text("Settings", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Manage offline barcode data and protect your pantry information.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RecoveryResetDialogHost(
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (visible) RecoveryResetDialog(onDismiss = onDismiss, onConfirm = onConfirm)
}

@Composable
private fun RecoveryResetDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset local database?") },
        text = {
            Text(
                "This permanently deletes the local pantry and grocery database. " +
                    "Restore a backup first if you need to keep it.",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Reset database") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

internal data class RecoveryContentState(
    val backups: List<String>,
    val selectedBackup: String?,
    val pin: String,
    val error: String?,
    val onSelectBackup: (String) -> Unit,
    val onPinChange: (String) -> Unit,
    val onOpenBackup: () -> Unit,
    val onRestore: () -> Unit,
    val onReset: () -> Unit,
)

@Composable
private fun RecoveryContent(state: RecoveryContentState) {
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
        if (state.backups.isEmpty()) {
            Text("No encrypted backups are available on this device.")
        } else {
            state.backups.forEach { backup ->
                TextButton(onClick = { state.onSelectBackup(backup) }) {
                    val label =
                        if (backup.startsWith("content://")) {
                            Uri.parse(backup).lastPathSegment ?: "Saved backup file"
                        } else {
                            backup
                        }
                    Text(if (backup == state.selectedBackup) "✓ $label" else label)
                }
            }
            OutlinedTextField(
                value = state.pin,
                onValueChange = state.onPinChange,
                label = { Text("Backup PIN") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                enabled = state.pin.isNotEmpty(),
                onClick = state.onOpenBackup,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("Open backup file") }
            Spacer(modifier = Modifier.padding(4.dp))
            Button(
                enabled = state.selectedBackup != null && state.pin.isNotEmpty(),
                onClick = state.onRestore,
            ) { Text("Restore selected backup") }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = state.onReset) { Text("Reset local database") }
    }
}

internal fun launchRestore(
    context: android.content.Context,
    scope: CoroutineScope,
    restore: suspend () -> Unit,
    onResult: (String?) -> Unit,
) {
    scope.launch {
        val result = withContext(Dispatchers.IO) { runCatching { restore() }.exceptionOrNull()?.message }
        if (result == null) (context as? android.app.Activity)?.recreate()
        onResult(result)
    }
}

@Composable
fun DatabaseRecoveryScreen(
    context: android.content.Context,
    onRestore: (String, CharArray) -> Unit,
    onReset: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val backups =
        remember {
            listBackupFiles(context) +
                listOfNotNull(persistedBackupUri(context)?.toString())
        }
    var selectedBackup by remember { mutableStateOf(backups.firstOrNull()) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var pendingRestorePin by remember { mutableStateOf<CharArray?>(null) }
    val openBackupLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val suppliedPin = pendingRestorePin
            pendingRestorePin = null
            if (uri != null && suppliedPin != null) {
                scope.launch {
                    error =
                        withContext(Dispatchers.IO) {
                            runCatching { onRestore(uri.toString(), suppliedPin) }.exceptionOrNull()?.message
                        }
                }
            }
        }
    RecoveryResetDialogHost(confirmReset, { confirmReset = false }) {
        confirmReset = false
        onReset()
    }
    RecoveryContent(
        RecoveryContentState(
            backups = backups,
            selectedBackup = selectedBackup,
            pin = pin,
            error = error,
            onSelectBackup = { selectedBackup = it },
            onPinChange = { pin = it },
            onOpenBackup = {
                pendingRestorePin = pin.toCharArray()
                pin = ""
                openBackupLauncher.launch(arrayOf("application/octet-stream", "application/*"))
            },
            onRestore = {
                selectedBackup?.let { backup ->
                    val suppliedPin = pin.toCharArray()
                    pin = ""
                    scope.launch {
                        error =
                            withContext(Dispatchers.IO) {
                                runCatching { onRestore(backup, suppliedPin) }.exceptionOrNull()?.message
                            }
                    }
                }
            },
            onReset = { confirmReset = true },
        ),
    )
}
