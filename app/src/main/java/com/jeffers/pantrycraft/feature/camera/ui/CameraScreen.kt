package com.jeffers.pantrycraft.feature.camera.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.PantryTaxonomy
import com.jeffers.pantrycraft.data.Repository
import com.jeffers.pantrycraft.feature.camera.analyzer.FrameTimingStats
import com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionAnalyzer
import com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionResult
import com.jeffers.pantrycraft.feature.camera.barcode.BarcodeMatch
import com.jeffers.pantrycraft.feature.camera.core.CameraManager
import com.jeffers.pantrycraft.feature.camera.vision.MlKitSnapshotModel
import com.jeffers.pantrycraft.feature.camera.vision.NormalizedBox
import com.jeffers.pantrycraft.feature.camera.vision.ParsedVisionItem
import com.jeffers.pantrycraft.feature.camera.vision.SnapshotVisionService
import com.jeffers.pantrycraft.feature.camera.vision.toPantryItem
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CameraScreen() {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val requestPermission =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { hasCameraPermission = it }

    if (!hasCameraPermission) {
        CameraPermissionPrompt { requestPermission.launch(Manifest.permission.CAMERA) }
        return
    }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val scope = rememberCoroutineScope()
    var snapshotStatus by remember { mutableStateOf<String?>(null) }
    var barcodeMatch by remember { mutableStateOf<BarcodeMatch?>(null) }
    var unresolvedBarcode by remember { mutableStateOf<String?>(null) }
    var associateDialogBarcode by remember { mutableStateOf<String?>(null) }
    var parsedItems by remember { mutableStateOf<List<ParsedVisionItem>>(emptyList()) }
    val repository = remember { Repository.getInstance(context) }
    val barcodeResolver = remember { repository.offlineBarcodeResolver() }
    val snapshotVisionService =
        remember {
            SnapshotVisionService(
                MlKitSnapshotModel(
                    ObjectDetection.getClient(
                        ObjectDetectorOptions
                            .Builder()
                            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                            .enableMultipleObjects()
                            .enableClassification()
                            .build(),
                    ),
                ),
            )
        }
    val previewView =
        remember(context) {
            PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            }
        }
    var latestResult by remember { mutableStateOf<LiveVisionResult?>(null) }
    val frameTimingStats = remember { FrameTimingStats() }
    var latestFrameTime by remember { mutableStateOf<Long?>(null) }

    val labelPaint =
        remember {
            Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 34f
                isAntiAlias = true
                typeface = Typeface.DEFAULT_BOLD
            }
        }
    val badgePaint =
        remember {
            Paint().apply {
                color = android.graphics.Color.argb(200, 20, 30, 45)
                style = Paint.Style.FILL
                isAntiAlias = true
            }
        }

    val analyzer =
        remember {
            val barcodeOptions =
                BarcodeScannerOptions
                    .Builder()
                    .setBarcodeFormats(
                        Barcode.FORMAT_UPC_A,
                        Barcode.FORMAT_UPC_E,
                        Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_QR_CODE,
                    ).build()
            val objectOptions =
                ObjectDetectorOptions
                    .Builder()
                    .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                    .enableMultipleObjects()
                    .enableClassification()
                    .build()
            LiveVisionAnalyzer(
                barcodeScanner = BarcodeScanning.getClient(barcodeOptions),
                objectDetector = ObjectDetection.getClient(objectOptions),
                executor = analysisExecutor,
                onResult = { result ->
                    latestResult = result
                    result.barcodes.firstOrNull()?.let { barcode ->
                        scope.launch {
                            val match = barcodeResolver.resolveBarcode(barcode)
                            if (match != null) {
                                barcodeMatch = match
                                unresolvedBarcode = null
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            } else {
                                unresolvedBarcode = barcode
                            }
                        }
                    }
                },
                onProcessingDurationMillis = { durationMillis ->
                    frameTimingStats.record(durationMillis)
                    scope.launch { latestFrameTime = durationMillis }
                },
            )
        }
    val cameraManager = remember { CameraManager(context, analyzer, analysisExecutor) }

    DisposableEffect(cameraManager, lifecycleOwner) {
        cameraManager.bind(lifecycleOwner, previewView)
        onDispose {
            cameraManager.unbind()
            analyzer.close()
            snapshotVisionService.close()
            analysisExecutor.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            latestResult?.objectBoxes?.forEach { box ->
                val left = box.xmin * size.width
                val top = box.ymin * size.height
                val right = box.xmax * size.width
                val bottom = box.ymax * size.height

                drawRect(
                    color = androidx.compose.ui.graphics.Color.Cyan,
                    topLeft =
                        androidx.compose.ui.geometry
                            .Offset(left, top),
                    size =
                        androidx.compose.ui.geometry
                            .Size(right - left, bottom - top),
                    style =
                        androidx.compose.ui.graphics.drawscope
                            .Stroke(width = 4f),
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
        }

        // Top Status HUD
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text =
                    latestResult
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

        // Live Barcode Match Quick-Add Card
        barcodeMatch?.let { match ->
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
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                match.name,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "Category: ${match.category} · Unit: ${match.unit} · Shelf life: ${match.estimatedShelfLifeDays}d",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { barcodeMatch = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "Dismiss barcode")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val now = System.currentTimeMillis()
                                    val exp =
                                        if (match.estimatedShelfLifeDays > 0) {
                                            now + match.estimatedShelfLifeDays * 86_400_000L
                                        } else {
                                            null
                                        }
                                    repository.insertPantry(
                                        PantryItem(
                                            name = match.name,
                                            barcode = match.barcode,
                                            quantity = 1f,
                                            unit = match.unit,
                                            category = match.category,
                                            storageLocation = "PANTRY",
                                            expirationTimestamp = exp,
                                            isAromaticOrSeasoning =
                                                PantryTaxonomy.isAromaticOrSeasoningCategory(match.category),
                                        ),
                                    )
                                    snapshotStatus = "Added ${match.name} to pantry"
                                    barcodeMatch = null
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                            Text("Quick Add (1)")
                        }
                        OutlinedButton(
                            onClick = {
                                parsedItems =
                                    listOf(
                                        ParsedVisionItem(
                                            name = match.name,
                                            category = match.category,
                                            storageLocation = "PANTRY",
                                            quantity = 1f,
                                            unit = match.unit,
                                            confidence = 1f,
                                            estimatedShelfLifeDays = match.estimatedShelfLifeDays,
                                            isAromaticOrSeasoning =
                                                PantryTaxonomy.isAromaticOrSeasoningCategory(match.category),
                                            boundingBox = NormalizedBox(0f, 0f, 1f, 1f),
                                            conditionNotes = null,
                                        ),
                                    )
                                barcodeMatch = null
                            },
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                            Text("Review")
                        }
                    }
                }
            }
        }

        // Unknown Barcode Association Banner
        if (barcodeMatch == null && unresolvedBarcode != null) {
            val code = unresolvedBarcode!!
            Card(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 96.dp, start = 16.dp, end = 16.dp)
                        .fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
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
                    Button(onClick = { associateDialogBarcode = code }) {
                        Icon(Icons.Filled.QrCode, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("Name")
                    }
                }
            }
        }

        // Shelf Snapshot FAB
        FloatingActionButton(
            onClick = {
                snapshotStatus = "Capturing shelf snapshot…"
                scope.launch {
                    runCatching {
                        snapshotVisionService.analyze(cameraManager.captureSnapshot())
                    }.onSuccess {
                        parsedItems = it
                        snapshotStatus = "Detected ${it.size} items from shelf"
                    }.onFailure {
                        snapshotStatus = "Snapshot failed: ${it.message ?: "Unknown error"}"
                    }
                }
            },
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoCamera,
                contentDescription = "Capture shelf for scanning",
            )
        }

        // Batch Review Bottom Sheet
        if (parsedItems.isNotEmpty()) {
            ModalBottomSheet(onDismissRequest = { parsedItems = emptyList() }) {
                BatchReviewBottomSheet(
                    items = parsedItems,
                    onConfirm = { confirmedItems ->
                        scope.launch {
                            runCatching {
                                repository.insertPantryBatch(confirmedItems.map { it.toPantryItem() })
                            }.onSuccess {
                                parsedItems = emptyList()
                                snapshotStatus = "Added ${confirmedItems.size} items to your pantry"
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            }.onFailure {
                                snapshotStatus = "Could not add items: ${it.message}"
                            }
                        }
                    },
                    onDismiss = { parsedItems = emptyList() },
                )
            }
        }

        // Unknown Barcode Association Dialog
        associateDialogBarcode?.let { barcode ->
            AssociateBarcodeDialog(
                barcode = barcode,
                onDismiss = {
                    associateDialogBarcode = null
                    unresolvedBarcode = null
                },
                onSave = { name, category, unit, storageLocation, shelfLifeDays ->
                    scope.launch {
                        barcodeResolver.associateBarcode(
                            barcode = barcode,
                            name = name,
                            category = category,
                            defaultUnit = unit,
                            estimatedShelfLifeDays = shelfLifeDays,
                        )
                        val now = System.currentTimeMillis()
                        val exp = if (shelfLifeDays > 0) now + shelfLifeDays * 86_400_000L else null
                        repository.insertPantry(
                            PantryItem(
                                name = name,
                                barcode = barcode,
                                quantity = 1f,
                                unit = unit,
                                category = category,
                                storageLocation = storageLocation,
                                expirationTimestamp = exp,
                                isAromaticOrSeasoning =
                                    PantryTaxonomy.isAromaticOrSeasoningCategory(category),
                            ),
                        )
                        snapshotStatus = "Saved and remembered $name"
                        associateDialogBarcode = null
                        unresolvedBarcode = null
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }
                },
            )
        }
    }
}

@Composable
private fun AssociateBarcodeDialog(
    barcode: String,
    onDismiss: () -> Unit,
    onSave: (name: String, category: String, unit: String, storageLocation: String, shelfLifeDays: Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("OTHER") }
    var unit by remember { mutableStateOf("COUNT") }
    var storageLocation by remember { mutableStateOf(PantryTaxonomy.storageLocations.first()) }
    var shelfLifeDays by remember { mutableStateOf(0) }

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
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(name.trim(), category.trim().uppercase(), unit.trim().uppercase(), storageLocation, shelfLifeDays)
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
private fun CameraPermissionPrompt(onRequestPermission: () -> Unit) {
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
