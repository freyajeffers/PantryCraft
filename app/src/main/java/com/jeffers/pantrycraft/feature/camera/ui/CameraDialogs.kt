package com.jeffers.pantrycraft.feature.camera.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.jeffers.pantrycraft.data.PantryTaxonomy
import kotlinx.coroutines.CoroutineScope

@Composable
fun AssociateBarcodeDialogImpl(
    barcode: String,
    onDismiss: () -> Unit,
    onSave: (name: String, category: String, unit: String, storageLocation: String, shelfLifeDays: Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("OTHER") }
    var unit by remember { mutableStateOf("COUNT") }
    var storageLocation by remember { mutableStateOf(PantryTaxonomy.storageLocations.first()) }
    var shelfLifeDaysText by remember { mutableStateOf("14") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name Scanned Barcode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Barcode: $barcode", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category (e.g. PRODUCE, DAIRY, SNACKS)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unit (COUNT, G, KG, ML, L, CAN)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = storageLocation,
                    onValueChange = { storageLocation = it },
                    label = { Text("Storage (PANTRY, FRIDGE, FREEZER, COUNTER)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = shelfLifeDaysText,
                    onValueChange = { shelfLifeDaysText = it },
                    label = { Text("Estimated Shelf Life (Days)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardOptions.Default.keyboardType),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    val cleanStorage =
                        storageLocation
                            .trim()
                            .uppercase()
                            .takeIf { it in PantryTaxonomy.storageLocations }
                            ?: PantryTaxonomy.storageLocations.first()
                    val cleanDays = shelfLifeDaysText.toIntOrNull()?.coerceAtLeast(0) ?: 0
                    onSave(
                        name.trim(),
                        category.trim().uppercase().ifEmpty { "OTHER" },
                        unit.trim().uppercase().ifEmpty { "COUNT" },
                        cleanStorage,
                        cleanDays,
                    )
                },
            ) {
                Text("Save & Remember")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
