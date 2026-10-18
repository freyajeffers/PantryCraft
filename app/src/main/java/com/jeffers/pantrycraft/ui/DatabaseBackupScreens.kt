package com.jeffers.pantrycraft.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import com.jeffers.pantrycraft.data.AiProvider
import com.jeffers.pantrycraft.data.AppDatabase
import com.jeffers.pantrycraft.data.AutomaticBackupPreferences
import com.jeffers.pantrycraft.data.AutomaticBackupScheduler
import com.jeffers.pantrycraft.data.ByokPreferences
import com.jeffers.pantrycraft.data.DatabaseBackupManager
import com.jeffers.pantrycraft.data.ExpirationAlertWorker
import com.jeffers.pantrycraft.data.Repository
import com.jeffers.pantrycraft.data.persistAutoBackupTreeUri
import com.jeffers.pantrycraft.data.listBackupFiles
import com.jeffers.pantrycraft.data.persistedBackupUri
import com.jeffers.pantrycraft.feature.camera.barcode.CatalogDownloadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.jeffers.pantrycraft.data.backupRecords as loadBackupRecords

private const val BYTES_IN_KB = 1024L
private const val BYTES_IN_MB = BYTES_IN_KB * 1024L

private data class CatalogDialog(
    val title: String,
    val message: String,
    val isError: Boolean,
)

private fun formatFileSize(bytes: Long): String =
    when {
        bytes < BYTES_IN_KB -> "$bytes B"
        bytes < BYTES_IN_MB -> "%.1f KB".format(bytes / BYTES_IN_KB.toDouble())
        else -> "%.1f MB".format(bytes / BYTES_IN_MB.toDouble())
    }

private data class BackupListDialogState(
    val records: List<BackupRecord>,
    val onDismiss: () -> Unit,
    val onSelect: (BackupRecord) -> Unit,
)

@Composable
private fun BackupListDialog(state: BackupListDialogState) {
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
fun SettingsScreen(
    context: android.content.Context,
    repository: Repository,
) {
    val scope = rememberCoroutineScope()
    val pantryItems by repository.observePantryItems().collectAsState(initial = emptyList())
    val groceryItems by repository.observeGroceryItems().collectAsState(initial = emptyList())
    val databaseHasUserData = pantryItems.isNotEmpty() || groceryItems.isNotEmpty()
    var backupResult by remember { mutableStateOf<String?>(null) }
    var pendingBackupName by remember { mutableStateOf<String?>(null) }
    var lastExportedUri by remember { mutableStateOf(persistedBackupUri(context)) }
    var backupRecords by remember { mutableStateOf(loadBackupRecords(context)) }
    var autoBackupTreeUri by remember {
        mutableStateOf(persistedAutoBackupTreeUri(context))
    }
    var automaticBackupsEnabled by remember {
        mutableStateOf(AutomaticBackupPreferences.isEnabled(context))
    }
    var expirationAlertsEnabled by remember {
        mutableStateOf(ExpirationAlertWorker.isEnabled(context))
    }
    var automaticBackupIntervalMinutes by remember {
        mutableStateOf(AutomaticBackupPreferences.intervalMinutes(context))
    }

    var restoreResult by remember { mutableStateOf<String?>(null) }
    var confirmDatabaseReset by remember { mutableStateOf(false) }

    var showPinDialog by remember { mutableStateOf(false) }
    val pinDialogAction = remember { mutableStateOf<(CharArray) -> Unit>({}) }
    var pinText by remember { mutableStateOf("") }
    var showBackupList by remember { mutableStateOf(false) }
    val createBackupLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream"),
        ) { uri ->
            val backupName = pendingBackupName
            pendingBackupName = null
            if (uri != null && backupName != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            DatabaseBackupManager.exportBackup(context, backupName, uri)
                        }
                    }.onSuccess {
                        lastExportedUri = uri
                        backupRecords = loadBackupRecords(context)
                        Toast.makeText(context, "Encrypted backup saved", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        backupResult = "Backup export failed: ${it.message}"
                    }
                }
            }
        }
    val autoBackupTreeLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
                persistAutoBackupTreeUri(context, uri)
                autoBackupTreeUri = uri
            }
        }
    // Pin entry dialog helper
    if (showPinDialog) {
        BackupPinDialog(
            pinText = pinText,
            onPinTextChange = { pinText = it },
            onDismiss = {
                showPinDialog = false
                pinText = ""
            },
            onConfirm = {
                val pinChars = pinText.toCharArray()
                pinText = ""
                showPinDialog = false
                pinDialogAction.value(pinChars)
            },
        )
    }

    val openRestoreLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) {
                pinDialogAction.value = { suppliedPin ->
                    launchRestore(
                        context = context,
                        scope = scope,
                        restore = { DatabaseBackupManager.restoreEncryptedBackupFromUri(context, uri, suppliedPin) },
                        onResult = { restoreResult = it },
                    )
                }
                pinText = ""
                showPinDialog = true
            }
        }
    val exportJsonLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    runCatching {
                        val json = withContext(Dispatchers.IO) { repository.exportInventoryToJson() }
                        context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                    }.onSuccess {
                        Toast.makeText(context, "Inventory exported to JSON", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(context, "JSON export failed: ${it.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    val exportCsvLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("text/csv"),
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    runCatching {
                        val csv = withContext(Dispatchers.IO) { repository.exportPantryToCsv() }
                        context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
                    }.onSuccess {
                        Toast.makeText(context, "Pantry exported to CSV", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(context, "CSV export failed: ${it.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    val importJsonLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    runCatching {
                        val content =
                            context.contentResolver.openInputStream(uri)?.use {
                                it.reader().readText()
                            } ?: error("Unable to read selected JSON file")
                        withContext(Dispatchers.IO) { repository.importInventoryFromJson(content) }
                    }.onSuccess { count ->
                        Toast.makeText(context, "Imported $count items from JSON", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(context, "JSON import failed: ${it.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    val importCsvLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    runCatching {
                        val csvText =
                            context.contentResolver.openInputStream(uri)?.use {
                                it.reader().readText()
                            } ?: error("Unable to read selected CSV file")
                        withContext(Dispatchers.IO) { repository.importPantryFromCsv(csvText) }
                    }.onSuccess { count ->
                        Toast.makeText(context, "Imported $count pantry items from CSV", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(context, "CSV import failed: ${it.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            expirationAlertsEnabled = granted
            ExpirationAlertWorker.setEnabled(context, granted)
        }
    var selectedAiProvider by remember { mutableStateOf(ByokPreferences.selectedProvider(context)) }
    var aiModelName by remember { mutableStateOf(ByokPreferences.selectedModel(context)) }
    var aiEndpointUrl by remember { mutableStateOf(ByokPreferences.selectedEndpoint(context)) }
    var aiApiKeyInput by remember { mutableStateOf("") }
    var hasStoredApiKey by remember { mutableStateOf(ByokPreferences.hasApiKey(context)) }
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
    if (showBackupList) {
        BackupListDialog(
            BackupListDialogState(
                records = backupRecords,
                onDismiss = { showBackupList = false },
                onSelect = { record ->
                    showBackupList = false
                    pinDialogAction.value = { suppliedPin ->
                        record.exportedUri?.let { uri ->
                            launchRestore(
                                context,
                                scope,
                                {
                                    DatabaseBackupManager.restoreEncryptedBackupFromUri(
                                        context,
                                        Uri.parse(uri),
                                        suppliedPin,
                                    )
                                },
                            ) { restoreResult = it }
                        } ?: launchRestore(
                            context,
                            scope,
                            { DatabaseBackupManager.restoreEncryptedBackup(context, record.backupName, suppliedPin) },
                        ) { restoreResult = it }
                    }
                    pinText = ""
                    showPinDialog = true
                },
            ),
        )
    }
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
    restoreResult?.let { message ->
        if (message.isNotBlank()) {
            Text(message, color = MaterialTheme.colorScheme.error)
        }
    }
    if (confirmDatabaseReset) {
        AlertDialog(
            onDismissRequest = { confirmDatabaseReset = false },
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
                        confirmDatabaseReset = false
                        AppDatabase.resetEncryptedDatabase(context)
                        (context as? android.app.Activity)?.recreate()
                    },
                ) { Text("Reset database") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDatabaseReset = false }) { Text("Cancel") }
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
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Manage offline barcode data and protect your pantry information.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FoodFactsCard(
            repository = repository,
            scope = scope,
            props =
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
                        result.fold(
                            onSuccess = { file ->
                                catalogReady = true
                                catalogFileSize = file.length()
                                Toast
                                    .makeText(
                                        context,
                                        "Food catalog downloaded (${formatFileSize(file.length())})",
                                        Toast.LENGTH_SHORT,
                                    ).show()
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
                    },
                    onProgress = { downloaded, total ->
                        catalogDownloadedBytes = downloaded
                        catalogTotalBytes = total
                    },
                    onDelete = { confirmCatalogDelete = true },
                    catalogDialogSetter = { catalogDialog = it },
                ),
        )
        NotificationSettingsCard(
            context = context,
            enabled = expirationAlertsEnabled,
            onEnabledChange = { expirationAlertsEnabled = it },
            permissionLauncher = notificationPermissionLauncher,
        )
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
                AutomaticBackupCard(
                    props =
                        AutomaticBackupProps(
                            enabled = automaticBackupsEnabled,
                            intervalMinutes = automaticBackupIntervalMinutes,
                            databaseHasUserData = databaseHasUserData,
                            onDisable = {
                                automaticBackupsEnabled = false
                                AutomaticBackupPreferences.setSchedule(
                                    context,
                                    enabled = false,
                                    intervalMinutes = automaticBackupIntervalMinutes,
                                )
                                AutomaticBackupScheduler.cancel(context)
                            },
                            onEnableRequested = {
                                pinDialogAction.value = { suppliedPin ->
                                    AutomaticBackupPreferences.savePin(context, suppliedPin)
                                    AutomaticBackupPreferences.setSchedule(
                                        context,
                                        enabled = true,
                                        intervalMinutes = automaticBackupIntervalMinutes,
                                    )
                                    automaticBackupsEnabled = true
                                    AutomaticBackupScheduler.schedule(context, automaticBackupIntervalMinutes)
                                }
                                pinText = ""
                                showPinDialog = true
                            },
                            onIntervalSelected = { minutes ->
                                automaticBackupIntervalMinutes = minutes
                                AutomaticBackupPreferences.setSchedule(
                                    context,
                                    enabled = true,
                                    intervalMinutes = minutes,
                                )
                                AutomaticBackupScheduler.schedule(context, minutes)
                            },
                            onBackupNow = {
                                pinDialogAction.value = { suppliedPin ->
                                    scope.launch {
                                        backupResult =
                                            withContext(Dispatchers.IO) {
                                                runCatching {
                                                    DatabaseBackupManager.createEncryptedBackup(context, suppliedPin)
                                                }.fold(
                                                    onSuccess = {
                                                        backupRecords = loadBackupRecords(context)
                                                        pendingBackupName = it
                                                        createBackupLauncher.launch(it)
                                                        "Backup created. Choose where to save the encrypted file."
                                                    },
                                                    onFailure = { "Backup failed: ${it.message}" },
                                                )
                                            }
                                    }
                                }
                                pinText = ""
                                showPinDialog = true
                            },
                        ),
                )
                Text(
                    if (lastExportedUri != null) {
                        "Saved location remembered for recovery."
                    } else {
                        "Choose a file location so it can be found during recovery."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (lastExportedUri != null) {
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "application/octet-stream"
                                        putExtra(Intent.EXTRA_STREAM, lastExportedUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    },
                                    "Share encrypted backup",
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { Text("Share saved backup") }
                }
                if (backupRecords.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            showBackupList = true
                            backupRecords = loadBackupRecords(context)
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { Text("Restore saved backup") }
                }
                OutlinedButton(
                    onClick = { autoBackupTreeLauncher.launch(null) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text(
                        if (autoBackupTreeUri == null) {
                            "Set auto-backup location"
                        } else {
                            "Change auto-backup location"
                        },
                    )
                }
                OutlinedButton(
                    onClick = {
                        openRestoreLauncher.launch(arrayOf("application/octet-stream", "application/*"))
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text("Open backup file to restore") }
                TextButton(
                    onClick = { confirmDatabaseReset = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text("Reset local database") }
                backupResult?.let { Text(it, modifier = Modifier.padding(top = 12.dp)) }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Data Sovereignty & Portability", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Export your inventory in human-readable and standard formats (JSON or CSV) for backup, " +
                        "spreadsheets, or data migration. You can also import previously exported " +
                        "JSON inventory files.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { exportJsonLauncher.launch("pantrycraft_inventory.json") },
                        modifier = Modifier.weight(1f),
                    ) { Text("Export JSON") }
                    OutlinedButton(
                        onClick = { exportCsvLauncher.launch("pantrycraft_pantry.csv") },
                        modifier = Modifier.weight(1f),
                    ) { Text("Export CSV") }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { importJsonLauncher.launch(arrayOf("application/json", "text/*")) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Import JSON") }
                    OutlinedButton(
                        onClick = {
                            importCsvLauncher.launch(
                                arrayOf("text/csv", "text/comma-separated-values", "text/*"),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Import CSV") }
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AI Vision & Meal Engine (BYOKey / Local)", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Choose between 100% offline on-device ML Kit inference or your own personal API key (BYOKey) " +
                        "for shelf snapshot decomposition and Phase 3 meal synthesis. API keys are encrypted with " +
                        "Android KeyStore StrongBox/TEE and never routed through proxy servers.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AiProvider.entries.forEach { provider ->
                        val selected = selectedAiProvider == provider
                        if (selected) {
                            Button(
                                onClick = {
                                    selectedAiProvider = provider
                                    aiModelName = provider.defaultModel
                                    aiEndpointUrl = provider.defaultEndpoint
                                },
                            ) { Text("✓ ${provider.label}") }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    selectedAiProvider = provider
                                    aiModelName = provider.defaultModel
                                    aiEndpointUrl = provider.defaultEndpoint
                                },
                            ) { Text(provider.label) }
                        }
                    }
                }
                if (selectedAiProvider != AiProvider.OFFLINE_MLKIT) {
                    OutlinedTextField(
                        value = aiModelName,
                        onValueChange = { aiModelName = it },
                        label = { Text("Model Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = aiEndpointUrl,
                        onValueChange = { aiEndpointUrl = it },
                        label = { Text("Endpoint URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (selectedAiProvider.requiresApiKey) {
                        OutlinedTextField(
                            value = aiApiKeyInput,
                            onValueChange = { aiApiKeyInput = it },
                            label = {
                                Text(
                                    if (hasStoredApiKey) {
                                        "API Key (Encrypted key saved · enter new key to replace)"
                                    } else {
                                        "API Key"
                                    },
                                )
                            },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            ByokPreferences.saveConfiguration(
                                context = context,
                                provider = selectedAiProvider,
                                modelName = aiModelName,
                                endpointUrl = aiEndpointUrl,
                            )
                            if (aiApiKeyInput.isNotBlank()) {
                                ByokPreferences.saveApiKey(context, aiApiKeyInput.toCharArray())
                                aiApiKeyInput = ""
                                hasStoredApiKey = ByokPreferences.hasApiKey(context)
                            }
                            Toast.makeText(context, "AI engine configuration saved", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Save AI Settings") }
                    if (hasStoredApiKey) {
                        OutlinedButton(
                            onClick = {
                                ByokPreferences.clearApiKey(context)
                                hasStoredApiKey = false
                                aiApiKeyInput = ""
                                Toast.makeText(context, "Stored API key cleared", Toast.LENGTH_SHORT).show()
                            },
                        ) { Text("Clear Key") }
                    }
                }
            }
        }
    }
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

private data class RecoveryContentState(
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

@Composable
private fun BackupPinDialog(
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

private fun launchRestore(
    context: android.content.Context,
    scope: CoroutineScope,
    restore: suspend () -> Unit,
    onResult: (String?) -> Unit,
) {
    scope.launch {
        val result = withContext(Dispatchers.IO) { runCatching { restore() }.exceptionOrNull()?.message }
        onResult(result)
        if (result == null) (context as? android.app.Activity)?.recreate()
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
