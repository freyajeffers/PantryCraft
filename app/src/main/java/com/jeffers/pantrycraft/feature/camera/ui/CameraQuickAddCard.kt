package com.jeffers.pantrycraft.feature.camera.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.PantryTaxonomy
import com.jeffers.pantrycraft.data.Repository
import com.jeffers.pantrycraft.feature.camera.barcode.BarcodeMatch
import com.jeffers.pantrycraft.feature.camera.vision.NormalizedBox
import com.jeffers.pantrycraft.feature.camera.vision.ParsedVisionItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val MILLIS_PER_DAY = 86_400_000L

internal data class CameraQuickAddActions(
    val repository: Repository,
    val scope: CoroutineScope,
    val onDismiss: () -> Unit,
    val onReview: (ParsedVisionItem) -> Unit,
    val onStatus: (String) -> Unit,
    val onConfirm: () -> Unit,
)

@Composable
internal fun BoxScope.CameraQuickAddCard(
    match: BarcodeMatch,
    actions: CameraQuickAddActions,
) {
    Card(
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        CameraQuickAddContent(match, actions)
    }
}

@Composable
private fun CameraQuickAddContent(
    match: BarcodeMatch,
    actions: CameraQuickAddActions,
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(match.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Category: ${match.category} · Unit: ${match.unit} · " +
                        "Shelf life: ${match.estimatedShelfLifeDays}d",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = actions.onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss barcode")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            QuickAddButton(match, actions)
            ReviewButton(match, actions)
        }
    }
}

@Composable
private fun RowScope.QuickAddButton(
    match: BarcodeMatch,
    actions: CameraQuickAddActions,
) {
    Button(
        onClick = {
            actions.scope.launch {
                val expiration =
                    match.estimatedShelfLifeDays.takeIf { it > 0 }?.let {
                        System.currentTimeMillis() + it * MILLIS_PER_DAY
                    }
                actions.repository.insertPantry(
                    PantryItem(
                        name = match.name,
                        barcode = match.barcode,
                        quantity = 1f,
                        unit = match.unit,
                        category = match.category,
                        storageLocation = "PANTRY",
                        expirationTimestamp = expiration,
                        isAromaticOrSeasoning = PantryTaxonomy.isAromaticOrSeasoningCategory(match.category),
                    ),
                )
                actions.onStatus("Added ${match.name} to pantry")
                actions.onDismiss()
                actions.onConfirm()
            }
        },
        modifier = Modifier.weight(1f),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
        Text("Quick Add (1)")
    }
}

@Composable
private fun ReviewButton(
    match: BarcodeMatch,
    actions: CameraQuickAddActions,
) {
    OutlinedButton(
        onClick = {
            actions.onReview(
                ParsedVisionItem(
                    name = match.name,
                    category = match.category,
                    storageLocation = "PANTRY",
                    quantity = 1f,
                    unit = match.unit,
                    confidence = 1f,
                    estimatedShelfLifeDays = match.estimatedShelfLifeDays,
                    isAromaticOrSeasoning = PantryTaxonomy.isAromaticOrSeasoningCategory(match.category),
                    boundingBox = NormalizedBox(0f, 0f, 1f, 1f),
                    conditionNotes = null,
                    barcode = match.barcode,
                ),
            )
            actions.onDismiss()
        },
    ) {
        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
        Text("Review")
    }
}
