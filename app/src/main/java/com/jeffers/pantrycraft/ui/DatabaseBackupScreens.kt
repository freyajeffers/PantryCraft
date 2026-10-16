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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.jeffers.pantrycraft.data.AppDatabase
import com.jeffers.pantrycraft.data.AutomaticBackupPreferences
import com.jeffers.pantrycraft.data.AutomaticBackupScheduler
import com.jeffers.pantrycraft.data.ByokPreferences
import com.jeffers.pantrycraft.data.DatabaseBackupManager
import com.jeffers.pantrycraft.data.ExpirationAlertWorker
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
    val pantryItems by repository.observePantryItems().collectAsState(initial = emptyList())
    val groceryItems by repository.observeGroceryItems().collectAsState(initial = emptyList())
    val databaseHasUserData = pantryItems.isNotEmpty() || groceryItems.isNotEmpty()
    var backupResult by remember { mutableStateOf<String?>(null) }
    var pendingBackupName by remember { mutableStateOf<String?>(null) }
    var lastExportedUri by remember { mutableStateOf(DatabaseBackupManager.persistedBackupUri(context)) }
    var backupRecords by remember { mutableStateOf(DatabaseBackupManager.backupRecords(context)) }
    var autoBackupTreeUri by remember {
        mutableStateOf(DatabaseBackupManager.persistedAutoBackupTreeUri(context))
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
    val byokConfiguration = remember { ByokPreferences.read(context) }
    var byokProvider by remember { mutableStateOf(byokConfiguration.provider) }
    var byokEndpoint by remember { mutableStateOf(byokConfiguration.endpoint.orEmpty()) }
    var byokApiKey by remember { mutableStateOf("") }

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
                        backupRecords = DatabaseBackupManager.backupRecords(context)
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
                DatabaseBackupManager.persistAutoBackupTreeUri(context, uri)
                autoBackupTreeUri = uri
            }
        }
    // Pin entry dialog helper
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showPinDialog = false
                pinText = ""
            },
            title = { Text("Enter backup PIN") },
            text = {
                OutlinedTextField(
                    value = pinText,
                    onValueChange = { pinText = it },
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val pinChars = pinText.toCharArray()
                    pinText = ""
                    showPinDialog = false
                    pinDialogAction.value(pinChars)
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPinDialog = false
                    pinText = ""
                }) { Text("Cancel") }
            },
        )
    }

    fun restoreFromUri(
        uri: Uri,
        suppliedPin: CharArray,
    ) {
        scope.launch {
            restoreResult =
                withContext(Dispatchers.IO) {
                    runCatching {
                        DatabaseBackupManager.restoreEncryptedBackupFromUri(context, uri, suppliedPin)
                    }.exceptionOrNull()?.message
                }
            if (restoreResult == null) (context as? android.app.Activity)?.recreate()
        }
    }

    fun restoreFromInternal(
        backupName: String,
        suppliedPin: CharArray,
    ) {
        scope.launch {
            restoreResult =
                withContext(Dispatchers.IO) {
                    runCatching {
                        DatabaseBackupManager.restoreEncryptedBackup(context, backupName, suppliedPin)
                    }.exceptionOrNull()?.message
                }
            if (restoreResult == null) (context as? android.app.Activity)?.recreate()
        }
    }

    val openRestoreLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) {
                pinDialogAction.value = { suppliedPin -> restoreFromUri(uri, suppliedPin) }
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
        AlertDialog(
            onDismissRequest = { showBackupList = false },
            title = { Text("Choose a backup to restore") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (backupRecords.isEmpty()) {
                        Text("No backups have been created yet.")
                    } else {
                        backupRecords.forEach { record ->
                            TextButton(
                                onClick = {
                                    showBackupList = false
                                    pinDialogAction.value = { suppliedPin ->
                                        record.exportedUri?.let { restoreFromUri(Uri.parse(it), suppliedPin) }
                                            ?: restoreFromInternal(record.backupName, suppliedPin)
                                    }
                                    pinText = ""
                                    showPinDialog = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
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
            confirmButton = {
                TextButton(onClick = { showBackupList = false }) { Text("Cancel") }
            },
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
                Text("This permanently deletes pantry and grocery data on this device. Restore a backup first if needed.")
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
                Text("Optional AI model", style = MaterialTheme.typography.titleLarge)
                Text(
                    "PantryCraft stays offline unless you choose a provider. API keys are encrypted with Android Keystore and are never logged.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Provider: ${byokProvider.name}")
                    OutlinedButton(
                        onClick = {
                            val providers = ByokPreferences.Provider.entries
                            byokProvider = providers[(providers.indexOf(byokProvider) + 1) % providers.size]
                        },
                    ) { Text("Change") }
                }
                if (byokProvider != ByokPreferences.Provider.OFFLINE && byokProvider != ByokPreferences.Provider.OLLAMA) {
                    OutlinedTextField(
                        value = byokApiKey,
                        onValueChange = { byokApiKey = it },
                        label = { Text("API key") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                if (byokProvider == ByokPreferences.Provider.OLLAMA) {
                    OutlinedTextField(
                        value = byokEndpoint,
                        onValueChange = { byokEndpoint = it },
                        label = { Text("Ollama endpoint") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Button(
                        onClick = {
                            ByokPreferences.save(
                                context,
                                byokProvider,
                                byokApiKey.takeIf { it.isNotBlank() },
                                byokEndpoint.takeIf { it.isNotBlank() },
                            )
                            byokApiKey = ""
                            Toast.makeText(context, "AI model settings saved", Toast.LENGTH_SHORT).show()
                        },
                    ) { Text("Save") }
                    OutlinedButton(
                        onClick = {
                            ByokPreferences.clear(context)
                            byokProvider = ByokPreferences.Provider.OFFLINE
                            byokEndpoint = ""
                            byokApiKey = ""
                            Toast.makeText(context, "AI model disabled", Toast.LENGTH_SHORT).show()
                        },
                    ) { Text("Disable") }
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Notifications & Alerts", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Receive a private, local daily notification on this device if any pantry items are expiring within 48 hours. No external servers or cloud services are contacted.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Daily expiration alerts")
                        Text(
                            "Checks once daily for expiring food",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = expirationAlertsEnabled,
                        onCheckedChange = { enabled ->
                            expirationAlertsEnabled = enabled
                            ExpirationAlertWorker.setEnabled(context, enabled)
                        },
                        modifier = Modifier.padding(start = 12.dp),
                    )
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Automatic background backups")
                        Text(
                            "Backups run while the app is not open and stay on this device. " +
                                "The PIN is protected by Android Keystore.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = automaticBackupsEnabled,
                        onCheckedChange = { enabled ->
                            if (!enabled) {
                                automaticBackupsEnabled = false
                                AutomaticBackupPreferences.setSchedule(
                                    context,
                                    enabled = false,
                                    intervalMinutes = automaticBackupIntervalMinutes,
                                )
                                AutomaticBackupScheduler.cancel(context)
                            } else {
                                pinDialogAction.value = { suppliedPin ->
                                    AutomaticBackupPreferences.savePin(context, suppliedPin)
                                    AutomaticBackupPreferences.setSchedule(
                                        context,
                                        enabled = true,
                                        intervalMinutes = automaticBackupIntervalMinutes,
                                    )
                                    automaticBackupsEnabled = true
                                    AutomaticBackupScheduler.schedule(
                                        context,
                                        automaticBackupIntervalMinutes,
                                    )
                                }
                                pinText = ""
                                showPinDialog = true
                            }
                        },
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                if (automaticBackupsEnabled) {
                    Text("Backup interval", modifier = Modifier.padding(top = 12.dp))
                    Row(
                        modifier =
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            60L to "1 hour",
                            3 * 60L to "3 hours",
                            6 * 60L to "6 hours",
                            12 * 60L to "12 hours",
                            24 * 60L to "Daily",
                            7 * 24 * 60L to "Weekly",
                            30 * 24 * 60L to "Monthly",
                        ).forEach { interval ->
                            val selected = automaticBackupIntervalMinutes == interval.first
                            if (selected) {
                                Button(onClick = {
                                    automaticBackupIntervalMinutes = interval.first
                                    AutomaticBackupPreferences.setSchedule(
                                        context,
                                        enabled = true,
                                        intervalMinutes = interval.first,
                                    )
                                    AutomaticBackupScheduler.schedule(context, interval.first)
                                }) { Text("✓ ${interval.second}") }
                            } else {
                                OutlinedButton(onClick = {
                                    automaticBackupIntervalMinutes = interval.first
                                    AutomaticBackupPreferences.setSchedule(
                                        context,
                                        enabled = true,
                                        intervalMinutes = interval.first,
                                    )
                                    AutomaticBackupScheduler.schedule(context, interval.first)
                                }) { Text(interval.second) }
                            }
                        }
                    }
                }
                if (databaseHasUserData) {
                    Button(
                        onClick = {
                            // prompt for PIN then create backup
                            pinDialogAction.value = { suppliedPin ->
                                scope.launch {
                                    backupResult =
                                        withContext(Dispatchers.IO) {
                                            runCatching {
                                                DatabaseBackupManager.createEncryptedBackup(
                                                    context,
                                                    suppliedPin,
                                                )
                                            }.fold(
                                                onSuccess = {
                                                    backupRecords = DatabaseBackupManager.backupRecords(context)
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
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Save encrypted backup file") }
                }
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
                            backupRecords = DatabaseBackupManager.backupRecords(context)
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
                        "spreadsheets, or data migration. You can also import previously exported JSON inventory files.",
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
                OutlinedButton(
                    onClick = { importJsonLauncher.launch(arrayOf("application/json", "text/*")) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) { Text("Import from JSON") }
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
            OutlinedTextField(
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
