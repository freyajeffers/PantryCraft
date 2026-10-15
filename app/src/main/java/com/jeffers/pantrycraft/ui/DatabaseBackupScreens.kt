package com.jeffers.pantrycraft.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.jeffers.pantrycraft.data.Repository
import com.jeffers.pantrycraft.feature.camera.barcode.CatalogDownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class CatalogDialog(
    val title: String,
    val message: String,
    val isError: Boolean,
)

private fun formatFileSize(bytes: Long): String =
    when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }

@Composable
fun SettingsScreen(
    context: android.content.Context,
    repository: Repository,
) {
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var backupResult by remember { mutableStateOf<String?>(null) }
    var catalogReady by remember {
        mutableStateOf(
            File(context.noBackupFilesDir, CatalogDownloadService.CATALOG_FILE_NAME).isFile,
        )
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
    var catalogDialog by remember { mutableStateOf<CatalogDialog?>(null) }
    var confirmCatalogDelete by remember { mutableStateOf(false) }
    catalogDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = { catalogDialog = null },
            title = { Text(dialog.title) },
            text = { Text(dialog.message) },
            confirmButton = {
                TextButton(onClick = { catalogDialog = null }) { Text("OK") }
            },
        )
    }
    if (confirmCatalogDelete) {
        AlertDialog(
            onDismissRequest = { confirmCatalogDelete = false },
            title = { Text("Delete downloaded catalog?") },
            text = {
                Text(
                    "This removes the offline barcode catalog from this device. " +
                        "You can download it again later.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmCatalogDelete = false
                        val file = File(context.noBackupFilesDir, CatalogDownloadService.CATALOG_FILE_NAME)
                        if (file.delete()) {
                            catalogReady = false
                            catalogFileSize = null
                        } else {
                            catalogDialog =
                                CatalogDialog("Delete failed", "The downloaded catalog could not be removed.", true)
                        }
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmCatalogDelete = false }) { Text("Cancel") }
            },
        )
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Manage offline barcode data and protect your pantry information.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("FoodFacts barcode catalog", style = MaterialTheme.typography.titleLarge)
                Text(
                    "This optional catalog helps PantryCraft recognize packaged foods when you scan a barcode. " +
                        "Download it once to make barcode lookups work offline. The catalog is saved only inside " +
                        "the app and does not contain your pantry or grocery list.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (catalogReady) {
                        "Ready for offline barcode scans · ${formatFileSize(catalogFileSize ?: 0L)}"
                    } else {
                        "Not downloaded"
                    },
                    color =
                        if (catalogReady) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 12.dp),
                )
                if (catalogDownloading) {
                    val progress =
                        if (catalogTotalBytes > 0) {
                            catalogDownloadedBytes.toFloat() / catalogTotalBytes
                        } else {
                            null
                        }
                    if (progress == null) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        )
                    }
                    Text(
                        if (catalogTotalBytes > 0) {
                            "Downloading ${formatFileSize(catalogDownloadedBytes)} of ${formatFileSize(catalogTotalBytes)}"
                        } else {
                            "Downloading ${formatFileSize(catalogDownloadedBytes)}"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!catalogReady) {
                        Button(
                            enabled = !catalogDownloading,
                            onClick = {
                                catalogDownloading = true
                                catalogDownloadedBytes = 0L
                                catalogTotalBytes = -1L
                                scope.launch {
                                    val result =
                                        withContext(Dispatchers.IO) {
                                            runCatching {
                                                repository.downloadOfflineCatalog { downloaded, total ->
                                                    catalogDownloadedBytes = downloaded
                                                    catalogTotalBytes = total
                                                }
                                            }
                                        }
                                    catalogDownloading = false
                                    result.fold(
                                        onSuccess = { file ->
                                            catalogReady = true
                                            catalogFileSize = file.length()
                                            catalogDialog =
                                                CatalogDialog(
                                                    "Download complete",
                                                    "The food catalog is ready for offline barcode scans (${formatFileSize(
                                                        file.length(),
                                                    )}).",
                                                    false,
                                                )
                                        },
                                        onFailure = {
                                            catalogDialog =
                                                CatalogDialog(
                                                    "Download failed",
                                                    it.message
                                                        ?: "The catalog could not be downloaded. " +
                                                        "Check your connection and try again.",
                                                    true,
                                                )
                                        },
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Download food catalog") }
                    }
                    if (catalogReady) {
                        OutlinedButton(
                            onClick = { confirmCatalogDelete = true },
                            enabled = !catalogDownloading,
                        ) { Text("Delete") }
                    }
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
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
                            backupResult =
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
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Create encrypted backup")
                }
                backupResult?.let { Text(it, modifier = Modifier.padding(top = 12.dp)) }
            }
        }
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
    var confirmReset by remember { mutableStateOf(false) }
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
        OutlinedButton(onClick = { confirmReset = true }) {
            Text("Reset local database")
        }
    }
}
