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
import androidx.compose.runtime.remember
import java.lang.Float

private const val LABEL_TEXT_SIZE = 34f
private const val BADGE_ALPHA = 200
private const val BADGE_ROUND_RADIUS = 12f
private const val BADGE_HORIZONTAL_PADDING = 12f
private const val BADGE_VERTICAL_PADDING = 32f
private const val BADGE_HEIGHT = 44f
private const val BADGE_OFFSET = 40f
private const val BOX_MIN_SIZE = 1f
private const val STROKE_WIDTH = 4f
private const val FOCUS_RADIUS = 48f
private const val FOCUS_STROKE = 3f

data class CameraHudState(
    val latestResult: com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionResult?,
    val latestFrameTime: Long?,
    val snapshotStatus: String?,
    val barcodeMatch: BarcodeMatch?,
    val isTorchOn: Boolean,
)

@Composable
fun rememberLabelPaint(): Paint = remember {
    Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = LABEL_TEXT_SIZE
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }
}

fun rememberBadgePaint(): Paint = remember {
    Paint().apply {
        color = android.graphics.Color.argb(BADGE_ALPHA, 20, 30, 45)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
}
}

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
                size = androidx.compose.ui.geometry.Size((right - left).coerceAtLeast(BOX_MIN_SIZE), (bottom - top).coerceAtLeast(BOX_MIN_SIZE)),
                style = Stroke(width = STROKE_WIDTH),
            )

            box.label?.let { label ->
                val confPercent = box.confidence?.let { " ${(it * 100).toInt()}%" } ?: ""
                val text = "$label$confPercent"
                val textWidth = labelPaint.measureText(text)
                val badgeTop = (top - BADGE_OFFSET).coerceAtLeast(8f)
                drawContext.canvas.nativeCanvas.drawRoundRect(
                    left,
                    badgeTop,
                    left + textWidth + BADGE_HORIZONTAL_PADDING * 2,
                    badgeTop + BADGE_HEIGHT,
                    BADGE_ROUND_RADIUS,
                    BADGE_ROUND_RADIUS,
                    badgePaint,
                )
                drawContext.canvas.nativeCanvas.drawText(
                    text,
                    left + BADGE_HORIZONTAL_PADDING,
                    badgeTop + BADGE_VERTICAL_PADDING,
                    labelPaint,
                )
            }
        }
        focusPoint?.let { pt ->
            drawCircle(
                color = androidx.compose.ui.graphics.Color.White,
                radius = FOCUS_RADIUS,
                center = pt,
                style = Stroke(width = FOCUS_STROKE),
            )
        }
    }
}

@Composable
fun CameraTopHud(hudState: CameraHudState, onToggleTorch: () -> Unit) {
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
