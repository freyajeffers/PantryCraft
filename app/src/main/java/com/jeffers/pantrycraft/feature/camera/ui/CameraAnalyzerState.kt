package com.jeffers.pantrycraft.feature.camera.ui

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.jeffers.pantrycraft.feature.camera.analyzer.FrameTimingStats
import com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionAnalyzer
import com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionResult
import com.jeffers.pantrycraft.feature.camera.barcode.OfflineBarcodeResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService

data class CameraAnalyzerState(
    val context: Context,
    val view: View,
    val executor: ExecutorService,
    val scope: CoroutineScope,
    val barcodeResolver: OfflineBarcodeResolver,
    val frameTimingStats: FrameTimingStats,
    val onResult: (LiveVisionResult) -> Unit,
    val onFrameTime: (Long) -> Unit,
    val onBarcodeResolved: (com.jeffers.pantrycraft.feature.camera.barcode.BarcodeMatch) -> Unit,
    val onBarcodeUnresolved: (String) -> Unit,
)

@Composable
internal fun rememberCameraAnalyzer(state: CameraAnalyzerState): LiveVisionAnalyzer =
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
            executor = state.executor,
            onResult = { result ->
                state.onResult(result)
                result.barcodes.firstOrNull()?.let { barcode ->
                    state.scope.launch {
                        val match = runCatching { state.barcodeResolver.resolveBarcode(barcode) }.getOrNull()
                        if (match != null) {
                            state.onBarcodeResolved(match)
                            triggerBarcodeHapticTick(state.context, state.view)
                        } else {
                            state.onBarcodeUnresolved(barcode)
                        }
                    }
                }
            },
            onProcessingDurationMillis = { durationMillis ->
                state.frameTimingStats.record(durationMillis)
                state.scope.launch { state.onFrameTime(durationMillis) }
            },
        )
    }
