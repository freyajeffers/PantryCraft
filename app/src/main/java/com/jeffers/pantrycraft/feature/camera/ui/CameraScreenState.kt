package com.jeffers.pantrycraft.feature.camera.ui

import android.Manifest
import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.jeffers.pantrycraft.data.Repository
import com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionAnalyzer
import com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionResult
import com.jeffers.pantrycraft.feature.camera.barcode.BarcodeMatch
import com.jeffers.pantrycraft.feature.camera.core.CameraManager
import com.jeffers.pantrycraft.feature.camera.vision.MlKitSnapshotModel
import com.jeffers.pantrycraft.feature.camera.vision.ModelRouter
import com.jeffers.pantrycraft.feature.camera.vision.SnapshotVisionService
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.Executor
import java.util.concurrent.Executors

data class CameraScreenState(
    val context: Context,
    val view: View,
    val lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    val analysisExecutor: Executor,
    val scope: CoroutineScope,
    val snapshotStatus: MutableState<String?>,
    val barcodeMatch: MutableState<BarcodeMatch?>,
    val unresolvedBarcode: MutableState<String?>,
    val associateDialogBarcode: MutableState<String?>,
    val parsedItems: MutableState<List<com.jeffers.pantrycraft.feature.camera.vision.ParsedVisionItem>>,
    val repository: Repository,
    val barcodeResolver: com.jeffers.pantrycraft.feature.camera.barcode.OfflineBarcodeResolver,
    val snapshotVisionService: SnapshotVisionService,
    val previewView: PreviewView,
    val latestResult: MutableState<LiveVisionResult?>,
    val frameTimingStats: com.jeffers.pantrycraft.feature.camera.analyzer.FrameTimingStats,
    val latestFrameTime: MutableState<Long?>,
    val isTorchOn: MutableState<Boolean>,
    val focusPoint: MutableState<androidx.compose.ui.geometry.Offset?>,
    val zoomRatio: MutableState<Float>,
    val analyzer: LiveVisionAnalyzer,
    val cameraManager: CameraManager,
)

@Composable
fun rememberCameraScreenState(): CameraScreenState {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val scope = rememberCoroutineScope()
    val snapshotStatus = remember { mutableStateOf<String?>(null) }
    val barcodeMatch = remember { mutableStateOf<BarcodeMatch?>(null) }
    val unresolvedBarcode = remember { mutableStateOf<String?>(null) }
    val associateDialogBarcode = remember { mutableStateOf<String?>(null) }
    val parsedItems = remember { mutableStateOf<List<com.jeffers.pantrycraft.feature.camera.vision.ParsedVisionItem>>(emptyList()) }
    val repository = remember { Repository.getInstance(context) }
    val barcodeResolver = remember { repository.offlineBarcodeResolver() }
    val snapshotVisionService = remember {
        SnapshotVisionService(
            ModelRouter(
                context = context.applicationContext,
                localFallbackModel = MlKitSnapshotModel(
                    ObjectDetection.getClient(
                        ObjectDetectorOptions.Builder().setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE).enableMultipleObjects().enableClassification().build(),
                    ),
                ),
            ),
        )
    }
    val previewView = remember(context) { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.PERFORMANCE; scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val latestResult = remember { mutableStateOf<LiveVisionResult?>(null) }
    val frameTimingStats = remember { com.jeffers.pantrycraft.feature.camera.analyzer.FrameTimingStats() }
    val latestFrameTime = remember { mutableStateOf<Long?>(null) }
    val isTorchOn = remember { mutableStateOf(false) }
    val focusPoint = remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    val zoomRatio = remember { mutableStateOf(0f) }

    val barcodeOptions = BarcodeScannerOptions.Builder().setBarcodeFormats(
        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_A,
        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_E,
        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_8,
        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_13,
        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE,
    ).build()
    val objectOptions = ObjectDetectorOptions.Builder().setDetectorMode(ObjectDetectorOptions.STREAM_MODE).enableMultipleObjects().enableClassification().build()
    val analyzer = remember { LiveVisionAnalyzer(BarcodeScanning.getClient(barcodeOptions), ObjectDetection.getClient(objectOptions), analysisExecutor) }
    val cameraManager = remember { CameraManager(context, analyzer, analysisExecutor) }

    androidx.compose.runtime.DisposableEffect(cameraManager, lifecycleOwner) {
        cameraManager.bind(lifecycleOwner, previewView)
        onDispose {
            cameraManager.unbind()
            analyzer.close()
            barcodeResolver.close()
            snapshotVisionService.close()
            analysisExecutor.shutdown()
        }
    }

    return CameraScreenState(
        context = context,
        view = view,
        lifecycleOwner = lifecycleOwner,
        analysisExecutor = analysisExecutor,
        scope = scope,
        snapshotStatus = snapshotStatus,
        barcodeMatch = barcodeMatch,
        unresolvedBarcode = unresolvedBarcode,
        associateDialogBarcode = associateDialogBarcode,
        parsedItems = parsedItems,
        repository = repository,
        barcodeResolver = barcodeResolver,
        snapshotVisionService = snapshotVisionService,
        previewView = previewView,
        latestResult = latestResult,
        frameTimingStats = frameTimingStats,
        latestFrameTime = latestFrameTime,
        isTorchOn = isTorchOn,
        focusPoint = focusPoint,
        zoomRatio = zoomRatio,
        analyzer = analyzer,
        cameraManager = cameraManager,
    )
}
