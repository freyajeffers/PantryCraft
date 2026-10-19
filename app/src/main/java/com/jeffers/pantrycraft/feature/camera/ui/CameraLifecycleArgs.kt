package com.jeffers.pantrycraft.feature.camera.ui

import androidx.camera.view.PreviewView
import com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionAnalyzer
import com.jeffers.pantrycraft.feature.camera.barcode.OfflineBarcodeResolver
import com.jeffers.pantrycraft.feature.camera.core.CameraManager
import com.jeffers.pantrycraft.feature.camera.vision.SnapshotVisionService
import java.util.concurrent.ExecutorService

internal data class CameraLifecycleArgs(
    val cameraManager: CameraManager,
    val lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    val previewView: PreviewView,
    val analyzer: LiveVisionAnalyzer,
    val barcodeResolver: OfflineBarcodeResolver,
    val snapshotVisionService: SnapshotVisionService,
    val analysisExecutor: ExecutorService,
)
