package com.jeffers.pantrycraft.feature.camera.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jeffers.pantrycraft.data.PantryTaxonomy
import com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionResult
import com.jeffers.pantrycraft.feature.camera.barcode.BarcodeMatch

internal const val FOCUS_CLEAR_DELAY_MILLIS = 1_200L
internal const val LABEL_TEXT_SIZE = 34f
internal const val BADGE_ALPHA = 200
internal const val BADGE_COLOR_RED = 20
internal const val BADGE_COLOR_GREEN = 30
internal const val BADGE_COLOR_BLUE = 45
internal const val BADGE_TEXT_Y_OFFSET = 32f
internal const val BADGE_TOP_OFFSET = 40f
internal const val BOX_STROKE_WIDTH = 4f
internal const val BADGE_HORIZONTAL_PADDING = 24f
internal const val BADGE_HEIGHT = 44f
internal const val BADGE_CORNER_RADIUS = 12f
internal const val FOCUS_CIRCLE_RADIUS = 48f
internal const val FOCUS_CIRCLE_STROKE = 3f
internal const val ZOOM_SENSITIVITY = 0.5f
internal const val CONFIDENCE_PERCENT_SCALE = 100
internal const val MIN_BADGE_TOP = 8f

internal data class CameraStatusHudState(
    val latestResult: LiveVisionResult?,
    val latestFrameTime: Long?,
    val barcodeMatch: BarcodeMatch?,
    val snapshotStatus: String?,
    val isTorchOn: Boolean,
    val onToggleTorch: () -> Unit,
)

@Composable
fun CameraScreen() = CameraScreenHost()

@Composable
internal fun BoxScope.UnknownBarcodeCard(
    code: String?,
    visible: Boolean,
    onName: (String) -> Unit,
) {
    if (!visible || code == null) return
    Card(
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Unknown Barcode: $code", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Tap to name this item and save for future scans",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Button(onClick = { onName(code) }) {
                Icon(Icons.Filled.QrCode, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text("Name")
            }
        }
    }
}

@Composable
internal fun CameraStatusHud(state: CameraStatusHudState) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val statusText =
            state.latestResult
                ?.let { result ->
                    buildString {
                        append("Objects: ${result.objectBoxes.size}")
                        if (result.barcodes.isNotEmpty()) append("  Barcodes: ${result.barcodes.joinToString()}")
                        state.latestFrameTime?.let { append("  Analysis: ${it}ms") }
                    }
                }?.let { text -> state.barcodeMatch?.let { "$text · ${it.name}" } ?: text }
                ?: state.snapshotStatus ?: "Scanning…"
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(statusText, style = MaterialTheme.typography.bodyMedium)
        }
        IconButton(
            onClick = state.onToggleTorch,
            modifier =
                Modifier
                    .padding(start = 8.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(12.dp)),
        ) {
            Icon(
                imageVector = if (state.isTorchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                contentDescription = "Toggle flashlight",
                tint = if (state.isTorchOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
internal fun CameraPreviewOverlay(
    latestResult: LiveVisionResult?,
    focusPoint: Offset?,
    labelPaint: Paint,
    badgePaint: Paint,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val imgW = latestResult?.imageWidth?.takeIf { it > 0 }?.toFloat() ?: size.width
        val imgH = latestResult?.imageHeight?.takeIf { it > 0 }?.toFloat() ?: size.height
        val scale = maxOf(size.width / imgW, size.height / imgH)
        val scaledW = imgW * scale
        val scaledH = imgH * scale
        val offsetX = (size.width - scaledW) / 2f
        val offsetY = (size.height - scaledH) / 2f

        latestResult?.objectBoxes?.forEach { box ->
            val left = (box.xmin * scaledW + offsetX).coerceIn(0f, size.width)
            val top = (box.ymin * scaledH + offsetY).coerceIn(0f, size.height)
            val right = (box.xmax * scaledW + offsetX).coerceIn(0f, size.width)
            val bottom = (box.ymax * scaledH + offsetY).coerceIn(0f, size.height)
            drawRect(
                color = Color.Cyan,
                topLeft = Offset(left, top),
                size = Size((right - left).coerceAtLeast(1f), (bottom - top).coerceAtLeast(1f)),
                style = Stroke(width = BOX_STROKE_WIDTH),
            )
            box.label?.let { label ->
                val confidence = box.confidence?.let { " ${(it * CONFIDENCE_PERCENT_SCALE).toInt()}%" } ?: ""
                val text = "$label$confidence"
                val textWidth = labelPaint.measureText(text)
                val badgeTop = (top - BADGE_TOP_OFFSET).coerceAtLeast(MIN_BADGE_TOP)
                drawContext.canvas.nativeCanvas.drawRoundRect(
                    left,
                    badgeTop,
                    left + textWidth + BADGE_HORIZONTAL_PADDING,
                    badgeTop + BADGE_HEIGHT,
                    BADGE_CORNER_RADIUS,
                    BADGE_CORNER_RADIUS,
                    badgePaint,
                )
                drawContext.canvas.nativeCanvas.drawText(
                    text,
                    left + (BADGE_HORIZONTAL_PADDING / 2f),
                    badgeTop + BADGE_TEXT_Y_OFFSET,
                    labelPaint,
                )
            }
        }
        focusPoint?.let { point ->
            drawCircle(
                color = Color.White,
                radius = FOCUS_CIRCLE_RADIUS,
                center = point,
                style = Stroke(width = FOCUS_CIRCLE_STROKE),
            )
        }
    }
}

internal data class AssociateBarcodeFieldsState(
    val barcode: String,
    val name: String,
    val onNameChange: (String) -> Unit,
    val category: String,
    val onCategoryChange: (String) -> Unit,
    val unit: String,
    val onUnitChange: (String) -> Unit,
    val storageLocation: String,
    val onStorageLocationChange: (String) -> Unit,
    val shelfLifeDaysText: String,
    val onShelfLifeChange: (String) -> Unit,
)

@Composable
internal fun AssociateBarcodeFields(state: AssociateBarcodeFieldsState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Barcode: ${state.barcode}", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            state.name,
            state.onNameChange,
            label = { Text("Item Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            state.category,
            state.onCategoryChange,
            label = { Text("Category (e.g. PRODUCE, DAIRY, SNACKS)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            state.unit,
            state.onUnitChange,
            label = { Text("Unit (COUNT, G, KG, ML, L, CAN)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            state.storageLocation,
            state.onStorageLocationChange,
            label = { Text("Storage (PANTRY, FRIDGE, FREEZER, COUNTER)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            state.shelfLifeDaysText,
            state.onShelfLifeChange,
            label = { Text("Estimated Shelf Life (Days)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun AssociateBarcodeDialog(
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
            AssociateBarcodeFields(
                AssociateBarcodeFieldsState(
                    barcode = barcode,
                    name = name,
                    onNameChange = { name = it },
                    category = category,
                    onCategoryChange = { category = it },
                    unit = unit,
                    onUnitChange = { unit = it },
                    storageLocation = storageLocation,
                    onStorageLocationChange = { storageLocation = it },
                    shelfLifeDaysText = shelfLifeDaysText,
                    onShelfLifeChange = { shelfLifeDaysText = it },
                ),
            )
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

@Composable
internal fun CameraPermissionPrompt(onRequestPermission: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Camera access is required to scan pantry items.")
        Button(onClick = onRequestPermission, modifier = Modifier.padding(top = 16.dp)) {
            Text("Allow camera")
        }
    }
}
