package com.jeffers.pantrycraft.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.jeffers.pantrycraft.feature.camera.barcode.CatalogDownloadService
import java.io.File

@Composable
internal fun CatalogDeleteDialog(
    context: android.content.Context,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit,
    onFailure: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
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
                    onDismiss()
                    val file = File(context.noBackupFilesDir, CatalogDownloadService.CATALOG_FILE_NAME)
                    if (file.delete()) onDeleted() else onFailure()
                },
            ) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
