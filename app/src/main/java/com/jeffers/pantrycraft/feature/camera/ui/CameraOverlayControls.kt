package com.jeffers.pantrycraft.feature.camera.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable

@Composable
internal fun BoxScope.CameraOverlayControls(args: CameraOverlayArgs) {
    val latestResult = args.latestResult
    val cameraManager = args.cameraManager
    val repository = args.repository
    val scope = args.scope
    val barcodeMatch = args.barcodeMatch
    val unresolvedBarcode = args.unresolvedBarcode
    val associateDialogBarcode = args.associateDialogBarcode
    val parsedItems = args.parsedItems
    val snapshotVisionService = args.snapshotVisionService
    val barcodeResolver = args.barcodeResolver
    val isTorchOn = args.isTorchOn
    val onToggleTorch = args.onToggleTorch
    val onClearBarcodeMatch = args.onClearBarcodeMatch
    val onParsedItemsChange = args.onParsedItemsChange
    val onUnresolvedBarcodeChange = args.onUnresolvedBarcodeChange
    val onAssociateDialogBarcodeChange = args.onAssociateDialogBarcodeChange
    val onSnapshotStatusChange = args.onSnapshotStatusChange
    val view = args.view

    CameraStatusHeader(latestResult, barcodeMatch, isTorchOn, onToggleTorch)
    barcodeMatch?.let { match ->
        CameraQuickAddCard(
            match = match,
            actions =
                CameraQuickAddActions(
                    repository = repository,
                    scope = scope,
                    onDismiss = onClearBarcodeMatch,
                    onReview = { onParsedItemsChange(listOf(it)) },
                    onStatus = { onSnapshotStatusChange(it) },
                    onConfirm = { view.performHapticFeedback(HapticFeedbackConstants.CONFIRM) },
                ),
        )
    }
    UnknownBarcodeCard(
        code = unresolvedBarcode,
        visible = barcodeMatch == null,
        onName = onAssociateDialogBarcodeChange,
    )
    CameraSnapshotFab(
        cameraManager = cameraManager,
        snapshotVisionService = snapshotVisionService,
        scope = scope,
        onStatus = onSnapshotStatusChange,
        onItems = onParsedItemsChange,
    )
    CameraOverlayDialogs(args)
}

@Composable
private fun BoxScope.CameraOverlayDialogs(args: CameraOverlayArgs) {
    val parsedItems = args.parsedItems
    val repository = args.repository
    val scope = args.scope
    val view = args.view
    val onParsedItemsChange = args.onParsedItemsChange
    val onSnapshotStatusChange = args.onSnapshotStatusChange
    val associateDialogBarcode = args.associateDialogBarcode
    val barcodeResolver = args.barcodeResolver
    val onAssociateDialogBarcodeChange = args.onAssociateDialogBarcodeChange
    val onUnresolvedBarcodeChange = args.onUnresolvedBarcodeChange

    if (parsedItems.isNotEmpty()) {
        CameraBatchReview(
            CameraBatchReviewState(
                items = parsedItems,
                repository = repository,
                scope = scope,
                view = view,
                onDismiss = { onParsedItemsChange(emptyList()) },
                onStatus = onSnapshotStatusChange,
            ),
        )
    }
    associateDialogBarcode?.let { barcode ->
        CameraAssociationDialog(
            CameraAssociationState(
                barcode = barcode,
                resolver = barcodeResolver,
                repository = repository,
                scope = scope,
                view = view,
                onStatus = onSnapshotStatusChange,
                onDismiss = {
                    onAssociateDialogBarcodeChange(null)
                    onUnresolvedBarcodeChange(null)
                },
            ),
        )
    }
}
