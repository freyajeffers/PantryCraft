package com.jeffers.pantrycraft.feature.camera.ui

import androidx.compose.runtime.Composable
import com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionResult
import com.jeffers.pantrycraft.feature.camera.barcode.BarcodeMatch

@Composable
internal fun rememberCameraRuntimeForScreen(
    state: CameraScreenState,
    onResult: (LiveVisionResult?) -> Unit,
    onFrameTime: (Long?) -> Unit,
    onBarcodeResolved: (BarcodeMatch?) -> Unit,
    onBarcodeUnresolved: (String?) -> Unit,
): CameraRuntime =
    rememberCameraRuntime(
        state = state,
        onResult = onResult,
        onFrameTime = onFrameTime,
        onBarcodeResolved = onBarcodeResolved,
        onBarcodeUnresolved = onBarcodeUnresolved,
    )
