package com.jeffers.pantrycraft.feature.camera.core

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionAnalyzer
import java.util.concurrent.ExecutorService

class CameraManager(
    context: Context,
    private val analyzer: LiveVisionAnalyzer,
    private val analysisExecutor: ExecutorService,
) {
    private val appContext = context.applicationContext
    private var cameraProvider: ProcessCameraProvider? = null

    fun bind(
        owner: LifecycleOwner,
        previewView: PreviewView,
    ) {
        val providerFuture = ProcessCameraProvider.getInstance(appContext)
        providerFuture.addListener(
            {
                val provider = providerFuture.get()
                cameraProvider = provider
                val preview =
                    Preview.Builder().build().apply {
                        setSurfaceProvider(previewView.surfaceProvider)
                    }
                val analysis =
                    ImageAnalysis
                        .Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .build()
                        .apply { setAnalyzer(analysisExecutor, analyzer) }
                provider.unbindAll()
                provider.bindToLifecycle(
                    owner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    fun unbind() {
        cameraProvider?.unbindAll()
        cameraProvider = null
    }
}
