package com.jeffers.pantrycraft.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jeffers.pantrycraft.data.Repository
import com.jeffers.pantrycraft.data.exportInventoryToJson
import com.jeffers.pantrycraft.data.exportPantryToCsv
import com.jeffers.pantrycraft.data.importInventoryFromJson
import com.jeffers.pantrycraft.data.importPantryFromCsv
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DataSovereigntyCard(props: DataSovereigntyProps) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Data Sovereignty & Portability", style = MaterialTheme.typography.titleLarge)
            Text(
                "Export your inventory in human-readable and standard formats (JSON or CSV) for backup, " +
                    "spreadsheets, or data migration. You can also import previously exported " +
                    "JSON inventory files.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DataSovereigntyActions(props)
        }
    }
}

@Composable
private fun DataSovereigntyActions(props: DataSovereigntyProps) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = props.onExportJson, modifier = Modifier.weight(1f)) {
            Text("Export JSON")
        }
        OutlinedButton(onClick = props.onExportCsv, modifier = Modifier.weight(1f)) {
            Text("Export CSV")
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = props.onImportJson, modifier = Modifier.weight(1f)) {
            Text("Import JSON")
        }
        OutlinedButton(onClick = props.onImportCsv, modifier = Modifier.weight(1f)) {
            Text("Import CSV")
        }
    }
}

@Composable
internal fun rememberDataSovereigntyProps(
    context: Context,
    repository: Repository,
    scope: CoroutineScope,
): DataSovereigntyProps {
    val exportActions = rememberExportActions(context, repository, scope)
    val importActions = rememberImportActions(context, repository, scope)
    return DataSovereigntyProps(
        onExportJson = exportActions.first,
        onExportCsv = exportActions.second,
        onImportJson = importActions.first,
        onImportCsv = importActions.second,
    )
}

@Composable
private fun rememberExportActions(
    context: Context,
    repository: Repository,
    scope: CoroutineScope,
): Pair<() -> Unit, () -> Unit> {
    val exportJsonLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
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
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
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
    return Pair(
        { exportJsonLauncher.launch("pantrycraft_inventory.json") },
        { exportCsvLauncher.launch("pantrycraft_pantry.csv") },
    )
}

@Composable
private fun rememberImportActions(
    context: Context,
    repository: Repository,
    scope: CoroutineScope,
): Pair<() -> Unit, () -> Unit> {
    val importJsonLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch {
                    runCatching {
                        val content =
                            context.contentResolver.openInputStream(uri)?.use { it.reader().readText() }
                                ?: error("Unable to read selected JSON file")
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
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch {
                    runCatching {
                        val csvText =
                            context.contentResolver.openInputStream(uri)?.use { it.reader().readText() }
                                ?: error("Unable to read selected CSV file")
                        withContext(Dispatchers.IO) { repository.importPantryFromCsv(csvText) }
                    }.onSuccess { count ->
                        Toast.makeText(context, "Imported $count pantry items from CSV", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(context, "CSV import failed: ${it.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    return Pair(
        { importJsonLauncher.launch(arrayOf("application/json", "text/*")) },
        { importCsvLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/*")) },
    )
}
