package com.jeffers.pantrycraft.feature.camera.core

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionAnalyzer
import com.jeffers.pantrycraft.feature.camera.vision.SnapshotCaptureService
import com.jeffers.pantrycraft.feature.camera.vision.SnapshotFrame
import java.util.concurrent.ExecutorService

class CameraManager(
    context: Context,
    private val analyzer: LiveVisionAnalyzer,
    private val analysisExecutor: ExecutorService,
) {
    private val appContext = context.applicationContext
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null

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
                val imageCapture = ImageCapture.Builder().build()
                this@CameraManager.imageCapture = imageCapture
                provider.unbindAll()
                camera =
                    provider.bindToLifecycle(
                        owner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                        imageCapture,
                    )
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    fun setTorch(enabled: Boolean) {
        camera?.cameraControl?.enableTorch(enabled)
    }

    fun setLinearZoom(zoom: Float) {
        camera?.cameraControl?.setLinearZoom(zoom.coerceIn(0f, 1f))
    }

    fun focusOn(
        x: Float,
        y: Float,
        previewView: PreviewView,
    ) {
        val point = previewView.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point).build()
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    fun hasFlash(): Boolean = camera?.cameraInfo?.hasFlashUnit() == true

    suspend fun captureSnapshot(): SnapshotFrame =
        SnapshotCaptureService(
            appContext,
            imageCapture ?: error("Camera capture is not ready"),
        ).capture()

    fun unbind() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        imageCapture = null
        camera = null
    }
}
