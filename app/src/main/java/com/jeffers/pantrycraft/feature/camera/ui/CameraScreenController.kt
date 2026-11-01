package com.jeffers.pantrycraft.feature.camera.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionResult
import com.jeffers.pantrycraft.feature.camera.barcode.BarcodeMatch
import com.jeffers.pantrycraft.feature.camera.vision.ParsedVisionItem

@Composable
internal fun CameraScreenController() {
    val context = LocalContext.current
    val state = rememberCameraScreenState(context)
    var snapshotStatus by remember { mutableStateOf<String?>(null) }
    var barcodeMatch by remember { mutableStateOf<BarcodeMatch?>(null) }
    var unresolvedBarcode by remember { mutableStateOf<String?>(null) }
    var associateDialogBarcode by remember { mutableStateOf<String?>(null) }
    var parsedItems by remember { mutableStateOf<List<ParsedVisionItem>>(emptyList()) }
    var latestResult by remember { mutableStateOf<LiveVisionResult?>(null) }
    var latestFrameTime by remember { mutableStateOf<Long?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }
    val focusState = rememberCameraFocusPoint()
    var focusPoint by focusState::value
    val labelPaint = rememberLabelPaint()
    val badgePaint = rememberBadgePaint()
    val runtime =
        rememberCameraRuntimeForScreen(
            state = state,
            onResult = { latestResult = it },
            onFrameTime = { latestFrameTime = it },
            onBarcodeResolved = {
                barcodeMatch = it
                unresolvedBarcode = null
            },
            onBarcodeUnresolved = { unresolvedBarcode = it },
        )
    val cameraManager = runtime.cameraManager
    CameraScreenBox(
        args =
            CameraScreenBoxArgs(
                previewView = state.previewView,
                latestResult = latestResult,
                focusPoint = focusPoint,
                labelPaint = labelPaint,
                badgePaint = badgePaint,
                cameraManager = cameraManager,
                repository = state.repository,
                scope = state.scope,
                barcodeMatch = barcodeMatch,
                unresolvedBarcode = unresolvedBarcode,
                associateDialogBarcode = associateDialogBarcode,
                parsedItems = parsedItems,
                snapshotVisionService = state.snapshotVisionService,
                barcodeResolver = state.barcodeResolver,
                isTorchOn = isTorchOn,
                onToggleTorch = {
                    isTorchOn = !isTorchOn
                    cameraManager.setTorch(isTorchOn)
                },
                onClearBarcodeMatch = { barcodeMatch = null },
                onParsedItemsChange = { parsedItems = it },
                onUnresolvedBarcodeChange = { unresolvedBarcode = it },
                onAssociateDialogBarcodeChange = { associateDialogBarcode = it },
                onSnapshotStatusChange = { snapshotStatus = it },
                view = LocalView.current,
            ),
    )
}
