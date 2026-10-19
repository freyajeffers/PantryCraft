package com.jeffers.pantrycraft.feature.camera.ui

import android.graphics.Paint
import android.view.View
import androidx.camera.view.PreviewView
import androidx.compose.ui.geometry.Offset
import com.jeffers.pantrycraft.data.Repository
import com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionResult
import com.jeffers.pantrycraft.feature.camera.barcode.BarcodeMatch
import com.jeffers.pantrycraft.feature.camera.barcode.OfflineBarcodeResolver
import com.jeffers.pantrycraft.feature.camera.core.CameraManager
import com.jeffers.pantrycraft.feature.camera.vision.ParsedVisionItem
import com.jeffers.pantrycraft.feature.camera.vision.SnapshotVisionService
import kotlinx.coroutines.CoroutineScope

data class CameraScreenBoxArgs(
    val previewView: PreviewView,
    val latestResult: LiveVisionResult?,
    val focusPoint: Offset?,
    val labelPaint: Paint,
    val badgePaint: Paint,
    val cameraManager: CameraManager,
    val repository: Repository,
    val scope: CoroutineScope,
    val barcodeMatch: BarcodeMatch?,
    val unresolvedBarcode: String?,
    val associateDialogBarcode: String?,
    val parsedItems: List<ParsedVisionItem>,
    val snapshotVisionService: SnapshotVisionService,
    val barcodeResolver: OfflineBarcodeResolver,
    val isTorchOn: Boolean,
    val onToggleTorch: () -> Unit,
    val onClearBarcodeMatch: () -> Unit,
    val onParsedItemsChange: (List<ParsedVisionItem>) -> Unit,
    val onUnresolvedBarcodeChange: (String?) -> Unit,
    val onAssociateDialogBarcodeChange: (String?) -> Unit,
    val onSnapshotStatusChange: (String?) -> Unit,
    val view: View,
)
