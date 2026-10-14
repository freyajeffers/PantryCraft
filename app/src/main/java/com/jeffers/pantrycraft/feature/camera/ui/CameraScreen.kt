package com.jeffers.pantrycraft.feature.camera.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.jeffers.pantrycraft.data.Repository
import com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionAnalyzer
import com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionResult
import com.jeffers.pantrycraft.feature.camera.barcode.BarcodeMatch
import com.jeffers.pantrycraft.feature.camera.core.CameraManager
import com.jeffers.pantrycraft.feature.camera.vision.MlKitSnapshotModel
import com.jeffers.pantrycraft.feature.camera.vision.ParsedVisionItem
import com.jeffers.pantrycraft.feature.camera.vision.SnapshotVisionService
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CameraScreen() {
    val context = LocalContext.current
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
    var parsedItems by remember { mutableStateOf<List<ParsedVisionItem>>(emptyList()) }
    val barcodeResolver = remember { Repository.getInstance(context).offlineBarcodeResolver() }
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
                onResult = {
                    latestResult = it
                    it.barcodes.firstOrNull()?.let { barcode ->
                        scope.launch { barcodeMatch = barcodeResolver.resolveBarcode(barcode) }
                    }
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
                drawRect(
                    color = androidx.compose.ui.graphics.Color.Cyan,
                    topLeft =
                        androidx.compose.ui.geometry.Offset(
                            box.xmin * size.width,
                            box.ymin * size.height,
                        ),
                    size =
                        androidx.compose.ui.geometry.Size(
                            (box.xmax - box.xmin) * size.width,
                            (box.ymax - box.ymin) * size.height,
                        ),
                    style =
                        androidx.compose.ui.graphics.drawscope
                            .Stroke(width = 4f),
                )
            }
        }
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
                        }
                    }?.let { text ->
                        barcodeMatch?.let { match -> "$text · ${match.name}" } ?: text
                    } ?: snapshotStatus ?: "Scanning…",
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Button(
            onClick = {
                snapshotStatus = "Capturing…"
                scope.launch {
                    runCatching {
                        snapshotVisionService.analyze(cameraManager.captureSnapshot())
                    }.onSuccess {
                        parsedItems = it
                        snapshotStatus = "Snapshot processed and cleared"
                    }.onFailure { snapshotStatus = "Snapshot failed" }
                }
            },
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp),
        ) {
            Text("Capture shelf")
        }
        if (parsedItems.isNotEmpty()) {
            ModalBottomSheet(onDismissRequest = { parsedItems = emptyList() }) {
                BatchReviewBottomSheet(
                    items = parsedItems,
                    onConfirm = { parsedItems = emptyList() },
                    onDismiss = { parsedItems = emptyList() },
                )
            }
        }
    }
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
