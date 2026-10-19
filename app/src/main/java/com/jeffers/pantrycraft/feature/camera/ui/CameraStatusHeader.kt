package com.jeffers.pantrycraft.feature.camera.ui

import androidx.compose.runtime.Composable
import com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionResult
import com.jeffers.pantrycraft.feature.camera.barcode.BarcodeMatch

@Composable
internal fun CameraStatusHeader(
    latestResult: LiveVisionResult?,
    barcodeMatch: BarcodeMatch?,
    isTorchOn: Boolean,
    onToggleTorch: () -> Unit,
) {
    CameraStatusHud(
        CameraStatusHudState(
            latestResult = latestResult,
            latestFrameTime = null,
            barcodeMatch = barcodeMatch,
            snapshotStatus = null,
            isTorchOn = isTorchOn,
            onToggleTorch = onToggleTorch,
        ),
    )
}
