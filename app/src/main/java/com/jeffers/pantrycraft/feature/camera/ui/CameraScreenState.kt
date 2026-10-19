package com.jeffers.pantrycraft.feature.camera.ui

import android.content.Context
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.jeffers.pantrycraft.data.Repository
import com.jeffers.pantrycraft.data.offlineBarcodeResolver
import com.jeffers.pantrycraft.feature.camera.analyzer.FrameTimingStats
import com.jeffers.pantrycraft.feature.camera.barcode.BarcodeMatch
import java.util.concurrent.Executors

@Stable
class CameraScreenState(
    val deps: CameraDeps,
) {
    var snapshotStatus by mutableStateOf<String?>(null)
    var barcodeMatch by mutableStateOf<BarcodeMatch?>(null)
    var unresolvedBarcode by mutableStateOf<String?>(null)
    var associateDialogBarcode by mutableStateOf<String?>(null)
    var parsedItems by mutableStateOf<List<com.jeffers.pantrycraft.feature.camera.vision.ParsedVisionItem>>(emptyList())
    var latestResult by mutableStateOf<com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionResult?>(null)
    var latestFrameTime by mutableStateOf<Long?>(null)
    var isTorchOn by mutableStateOf(false)
    var focusPoint by mutableStateOf<androidx.compose.ui.geometry.Offset?>(null)
    var zoomRatio by mutableStateOf(0f)
    val previewView = deps.previewView
    val barcodeResolver = deps.barcodeResolver
    val snapshotVisionService = deps.snapshotVisionService
    val repository = deps.repository
    val analysisExecutor = deps.analysisExecutor
    val scope = deps.scope
    val frameTimingStats = deps.frameTimingStats
    val context = deps.context
}

@Composable
internal fun rememberCameraScreenState(context: Context): CameraScreenState {
    val repo = remember { Repository.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scope = rememberCoroutineScope()
    val previewView =
        remember(context) {
            PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        }
    val barcodeResolver = remember { repo.offlineBarcodeResolver() }
    val snapshotService = rememberSnapshotVisionService(context)
    val frameTimingStats = remember { FrameTimingStats() }

    return remember(context) {
        CameraScreenState(
            deps =
                CameraDeps(
                    context = context,
                    repository = repo,
                    analysisExecutor = executor,
                    scope = scope,
                    previewView = previewView,
                    barcodeResolver = barcodeResolver,
                    snapshotVisionService = snapshotService,
                    frameTimingStats = frameTimingStats,
                ),
        )
    }
}
