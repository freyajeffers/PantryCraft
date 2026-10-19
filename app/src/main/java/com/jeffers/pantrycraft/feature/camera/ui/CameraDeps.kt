package com.jeffers.pantrycraft.feature.camera.ui

import android.content.Context
import androidx.camera.view.PreviewView
import com.jeffers.pantrycraft.data.Repository
import com.jeffers.pantrycraft.feature.camera.analyzer.FrameTimingStats
import com.jeffers.pantrycraft.feature.camera.barcode.OfflineBarcodeResolver
import com.jeffers.pantrycraft.feature.camera.vision.SnapshotVisionService
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ExecutorService

data class CameraDeps(
    val context: Context,
    val repository: Repository,
    val analysisExecutor: ExecutorService,
    val scope: CoroutineScope,
    val previewView: PreviewView,
    val barcodeResolver: OfflineBarcodeResolver,
    val snapshotVisionService: SnapshotVisionService,
    val frameTimingStats: FrameTimingStats,
)
