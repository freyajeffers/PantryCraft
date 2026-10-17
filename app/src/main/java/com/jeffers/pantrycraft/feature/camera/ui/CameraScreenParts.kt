package com.jeffers.pantrycraft.feature.camera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionResult
import com.jeffers.pantrycraft.feature.camera.barcode.BarcodeMatch

@Composable
fun CameraPreviewCanvas(
    latestResult: LiveVisionResult?,
    latestFrameTime: Long?,
    focusPoint: Offset?,
    labelPaint: Paint,
    badgePaint: Paint,
) {
    // Preserve original drawing behavior but kept here to shorten CameraScreen.
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().padding(0.dp)) {
        val result = latestResult
        val imgW = result?.imageWidth?.takeIf { it > 0 }?.toFloat() ?: size.width
        val imgH = result?.imageHeight?.takeIf { it > 0 }?.toFloat() ?: size.height
        val scale = maxOf(size.width / imgW, size.height / imgH)
        val scaledW = imgW * scale
        val scaledH = imgH * scale
        val offsetX = (size.width - scaledW) / 2f
        val offsetY = (size.height - scaledH) / 2f

        result?.objectBoxes?.forEach { box ->
            val left = (box.xmin * scaledW + offsetX).coerceIn(0f, size.width)
            val top = (box.ymin * scaledH + offsetY).coerceIn(0f, size.height)
            val right = (box.xmax * scaledW + offsetX).coerceIn(0f, size.width)
            val bottom = (box.ymax * scaledH + offsetY).coerceIn(0f, size.height)

            drawRect(
                color = androidx.compose.ui.graphics.Color.Cyan,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size((right - left).coerceAtLeast(1f), (bottom - top).coerceAtLeast(1f)),
                style = Stroke(width = 4f),
            )

            box.label?.let { label ->
                val confPercent = box.confidence?.let { " ${(it * 100).toInt()}%" } ?: ""
                val text = "$label$confPercent"
                val textWidth = labelPaint.measureText(text)
                val badgeTop = (top - 40f).coerceAtLeast(8f)
                drawContext.canvas.nativeCanvas.drawRoundRect(
                    left,
                    badgeTop,
                    left + textWidth + 24f,
                    badgeTop + 44f,
                    12f,
                    12f,
                    badgePaint,
                )
                drawContext.canvas.nativeCanvas.drawText(
                    text,
                    left + 12f,
                    badgeTop + 32f,
                    labelPaint,
                )
            }
        }
        focusPoint?.let { pt ->
            drawCircle(
                color = androidx.compose.ui.graphics.Color.White,
                radius = 48f,
                center = pt,
                style = Stroke(width = 3f),
            )
        }
    }
}

@Composable
fun CameraTopHud(
    latestResult: LiveVisionResult?,
    latestFrameTime: Long?,
    snapshotStatus: String?,
    barcodeMatch: BarcodeMatch?,
    isTorchOn: Boolean,
    onToggleTorch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .padding(top = 16.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = latestResult
                    ?.let { result ->
                        buildString {
                            append("Objects: ")
                            append(result.objectBoxes.size)
                            if (result.barcodes.isNotEmpty()) {
                                append("  Barcodes: ")
                                append(result.barcodes.joinToString())
                            }
                            latestFrameTime?.let { append("  Analysis: ${it}ms") }
                        }
                    }?.let { text ->
                        barcodeMatch?.let { match -> "$text · ${match.name}" } ?: text
                    } ?: snapshotStatus ?: "Scanning…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onToggleTorch, modifier = Modifier.padding(start = 8.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))) {
            Icon(imageVector = if (isTorchOn) androidx.compose.material.icons.filled.FlashOn else androidx.compose.material.icons.filled.FlashOff, contentDescription = "Toggle flashlight", tint = if (isTorchOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun BarcodeMatchCard(match: BarcodeMatch, onDismiss: () -> Unit, onAdd: (com.jeffers.pantrycraft.feature.camera.vision.ParsedVisionItem) -> Unit, onReview: () -> Unit) {
    Card(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 96.dp, start = 16.dp, end = 16.dp)
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(match.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Category: ${match.category} · Unit: ${match.unit} · Shelf life: ${match.estimatedShelfLifeDays}d",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(androidx.compose.material.icons.filled.Close, contentDescription = "Dismiss barcode")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { onAdd(com.jeffers.pantrycraft.feature.camera.vision.ParsedVisionItem(
                    name = match.name,
                    category = match.category,
                    storageLocation = "PANTRY",
                    quantity = 1f,
                    unit = match.unit,
                    confidence = 1f,
                    estimatedShelfLifeDays = match.estimatedShelfLifeDays,
                    isAromaticOrSeasoning = PantryTaxonomy.isAromaticOrSeasoningCategory(match.category),
                    boundingBox = com.jeffers.pantrycraft.feature.camera.vision.NormalizedBox(0f, 0f, 1f, 1f),
                    conditionNotes = null,
                    barcode = match.barcode
                )) }, modifier = Modifier.weight(1f)) {
                    Icon(androidx.compose.material.icons.filled.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text("Quick Add (1)")
                }
                OutlinedButton(onClick = onReview) {
                    Icon(androidx.compose.material.icons.filled.Edit, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text("Review")
                }
            }
        }
    }
}
