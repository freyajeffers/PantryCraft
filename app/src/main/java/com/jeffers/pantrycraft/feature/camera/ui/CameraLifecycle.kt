package com.jeffers.pantrycraft.feature.camera.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

@Composable
internal fun BindCameraLifecycle(args: CameraLifecycleArgs) {
    val cameraManager = args.cameraManager
    val lifecycleOwner = args.lifecycleOwner
    val previewView = args.previewView
    val analyzer = args.analyzer
    val barcodeResolver = args.barcodeResolver
    val snapshotVisionService = args.snapshotVisionService
    val analysisExecutor = args.analysisExecutor
    DisposableEffect(cameraManager, lifecycleOwner) {
        cameraManager.bind(lifecycleOwner, previewView)
        onDispose {
            cameraManager.unbind()
            analyzer.close()
            barcodeResolver.close()
            snapshotVisionService.close()
            analysisExecutor.shutdown()
        }
    }
}
