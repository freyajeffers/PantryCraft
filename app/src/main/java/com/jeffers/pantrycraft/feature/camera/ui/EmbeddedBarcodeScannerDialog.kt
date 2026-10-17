package com.jeffers.pantrycraft.feature.camera.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import com.jeffers.pantrycraft.data.Repository
import com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionAnalyzer
import com.jeffers.pantrycraft.feature.camera.barcode.BarcodeMatch
import com.jeffers.pantrycraft.feature.camera.core.CameraManager
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

internal fun triggerBarcodeHapticTick(
    context: Context,
    view: View,
) {
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && vibrator?.hasVibrator() == true) {
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
    } else {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
}

@Composable
fun EmbeddedBarcodeScannerDialog(
    onDismiss: () -> Unit,
    onBarcodeResolved: (barcode: String, match: BarcodeMatch?) -> Unit,
) {
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scan Item Barcode") },
        text = {
            if (!hasCameraPermission) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Camera permission is needed to scan a barcode into this item.")
                    Button(onClick = { requestPermission.launch(Manifest.permission.CAMERA) }) {
                        Text("Allow camera")
                    }
                }
            } else {
                val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
                val scope = rememberCoroutineScope()
                val repository = remember { Repository.getInstance(context) }
                val barcodeResolver = remember { repository.offlineBarcodeResolver() }
                var hasResolved by remember { mutableStateOf(false) }
                val previewView =
                    remember(context) {
                        PreviewView(context).apply {
                            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
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
                                .build()
                        LiveVisionAnalyzer(
                            barcodeScanner = BarcodeScanning.getClient(barcodeOptions),
                            objectDetector = ObjectDetection.getClient(objectOptions),
                            executor = analysisExecutor,
                            onResult = { result ->
                                val rawCode = result.barcodes.firstOrNull()
                                if (!hasResolved && rawCode != null) {
                                    hasResolved = true
                                    scope.launch {
                                        val match = runCatching { barcodeResolver.resolveBarcode(rawCode) }.getOrNull()
                                        triggerBarcodeHapticTick(context, view)
                                        onBarcodeResolved(rawCode, match)
                                    }
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
                        barcodeResolver.close()
                        analysisExecutor.shutdown()
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Point the camera at a UPC or EAN barcode to auto-fill item details.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .padding(top = 4.dp),
                    ) {
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
