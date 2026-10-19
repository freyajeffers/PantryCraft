package com.jeffers.pantrycraft.feature.camera.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView

private const val ZOOM_DELTA_SCALE = 0.5f

@Composable
internal fun CameraScreenBox(args: CameraScreenBoxArgs) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(args.previewView) {
                    detectTransformGestures { _, _, zoom, _ ->
                        val newZoom = (zoom - 1f) * ZOOM_DELTA_SCALE
                        args.cameraManager.setLinearZoom(newZoom.coerceIn(0f, 1f))
                    }
                }.pointerInput(args.previewView) {
                    detectTapGestures { offset ->
                        args.cameraManager.focusOn(offset.x, offset.y, args.previewView)
                    }
                },
    ) {
        AndroidView(
            factory = { args.previewView },
            modifier = Modifier.fillMaxSize(),
        )
        CameraPreviewOverlay(args.latestResult, args.focusPoint, args.labelPaint, args.badgePaint)
        CameraOverlayControls(
            args =
                CameraOverlayArgs(
                    latestResult = args.latestResult,
                    focusPoint = args.focusPoint,
                    labelPaint = args.labelPaint,
                    badgePaint = args.badgePaint,
                    cameraManager = args.cameraManager,
                    repository = args.repository,
                    scope = args.scope,
                    barcodeMatch = args.barcodeMatch,
                    unresolvedBarcode = args.unresolvedBarcode,
                    associateDialogBarcode = args.associateDialogBarcode,
                    parsedItems = args.parsedItems,
                    snapshotVisionService = args.snapshotVisionService,
                    barcodeResolver = args.barcodeResolver,
                    isTorchOn = args.isTorchOn,
                    onToggleTorch = args.onToggleTorch,
                    onClearBarcodeMatch = args.onClearBarcodeMatch,
                    onParsedItemsChange = args.onParsedItemsChange,
                    onUnresolvedBarcodeChange = args.onUnresolvedBarcodeChange,
                    onAssociateDialogBarcodeChange = args.onAssociateDialogBarcodeChange,
                    onSnapshotStatusChange = args.onSnapshotStatusChange,
                    view = args.view,
                ),
        )
    }
}
