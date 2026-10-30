package com.jeffers.pantrycraft.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jeffers.pantrycraft.data.Repository
import com.jeffers.pantrycraft.data.downloadOfflineCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FoodFactsCard(
    repository: Repository,
    scope: CoroutineScope,
    props: FoodFactsProps,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            FoodFactsSummary(props)
            FoodFactsProgress(props)
            FoodFactsActions(repository, scope, props)
        }
    }
}

@Composable
private fun FoodFactsSummary(props: FoodFactsProps) {
    Text("FoodFacts barcode catalog", style = MaterialTheme.typography.titleLarge)
    Text(
        "This optional catalog helps PantryCraft recognize packaged foods when you scan a barcode. " +
            "Download it once to make barcode lookups work offline. The catalog is saved only inside " +
            "the app and does not contain your pantry or grocery list.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        if (props.catalogReady) {
            "Ready for offline barcode scans · ${formatFileSize(props.catalogFileSize ?: 0L)}"
        } else {
            "Not downloaded"
        },
        color =
            if (props.catalogReady) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun FoodFactsProgress(props: FoodFactsProps) {
    if (!props.catalogDownloading) return
    val progress =
        if (props.catalogTotalBytes > 0) {
            props.catalogDownloadedBytes.toFloat() / props.catalogTotalBytes
        } else {
            null
        }
    if (progress == null) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
    } else {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
    }
    Text(
        if (props.catalogTotalBytes > 0) {
            "Downloading ${formatFileSize(props.catalogDownloadedBytes)} of " +
                formatFileSize(props.catalogTotalBytes)
        } else {
            "Downloading ${formatFileSize(props.catalogDownloadedBytes)}"
        },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun FoodFactsActions(
    repository: Repository,
    scope: CoroutineScope,
    props: FoodFactsProps,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!props.catalogReady) {
            Button(
                enabled = !props.catalogDownloading,
                onClick = {
                    props.onStartDownload()
                    scope.launch {
                        val result =
                            withContext(Dispatchers.IO) {
                                runCatching { repository.downloadOfflineCatalog(props.onProgress) }
                            }
                        props.onDownloadResult(result)
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text("Download food catalog") }
        }
        if (props.catalogReady) {
            OutlinedButton(
                onClick = props.onDelete,
                enabled = !props.catalogDownloading,
            ) { Text("Delete") }
        }
    }
}

private const val BYTES_IN_KB = 1024L
private const val BYTES_IN_MB = BYTES_IN_KB * 1024L

internal fun formatFileSize(bytes: Long): String =
    when {
        bytes < BYTES_IN_KB -> "$bytes B"
        bytes < BYTES_IN_MB -> "%.1f KB".format(bytes / BYTES_IN_KB.toDouble())
        else -> "%.1f MB".format(bytes / BYTES_IN_MB.toDouble())
    }
