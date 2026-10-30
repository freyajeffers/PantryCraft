package com.jeffers.pantrycraft.feature.camera.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionAnalyzer
import com.jeffers.pantrycraft.feature.camera.core.CameraManager

internal class CameraRuntime(
    val analyzer: LiveVisionAnalyzer,
    val cameraManager: CameraManager,
)

@Composable
internal fun rememberCameraRuntime(
    state: CameraScreenState,
    onResult: (com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionResult?) -> Unit,
    onFrameTime: (Long?) -> Unit,
    onBarcodeResolved: (com.jeffers.pantrycraft.feature.camera.barcode.BarcodeMatch?) -> Unit,
    onBarcodeUnresolved: (String?) -> Unit,
): CameraRuntime {
    val lifecycleOwner = LocalLifecycleOwner.current
    val analyzer =
        rememberCameraAnalyzer(
            CameraAnalyzerState(
                context = state.context,
                view = androidx.compose.ui.platform.LocalView.current,
                executor = state.analysisExecutor,
                scope = state.scope,
                barcodeResolver = state.barcodeResolver,
                objectDetector = state.objectDetector,
                frameTimingStats = state.frameTimingStats,
                onResult = onResult,
                onFrameTime = onFrameTime,
                onBarcodeResolved = onBarcodeResolved,
                onBarcodeUnresolved = onBarcodeUnresolved,
            ),
        )
    val manager = remember { CameraManager(state.context, analyzer, state.analysisExecutor) }
    BindCameraLifecycle(
        CameraLifecycleArgs(
            cameraManager = manager,
            lifecycleOwner = lifecycleOwner,
            previewView = state.previewView,
            analyzer = analyzer,
            barcodeResolver = state.barcodeResolver,
            snapshotVisionService = state.snapshotVisionService,
            analysisExecutor = state.analysisExecutor,
        ),
    )
    return remember(analyzer, manager) { CameraRuntime(analyzer, manager) }
}
