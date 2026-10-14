package com.jeffers.pantrycraft.feature.camera.analyzer

import android.graphics.Rect
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetector
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

/** Normalized bounding box in [ymin, xmin, ymax, xmax] order. */
data class NormalizedVisionBox(
    val ymin: Float,
    val xmin: Float,
    val ymax: Float,
    val xmax: Float,
)

data class LiveVisionResult(
    val barcodes: List<String>,
    val objectBoxes: List<NormalizedVisionBox>,
)

class LiveVisionAnalyzer(
    private val barcodeScanner: BarcodeScanner,
    private val objectDetector: ObjectDetector,
    private val executor: ExecutorService,
    private val onResult: (LiveVisionResult) -> Unit,
    private val onError: (Exception) -> Unit = {},
) : ImageAnalysis.Analyzer {
    private val isAnalyzing = AtomicBoolean(false)
    private var lastAnalyzedAt = 0L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastAnalyzedAt < FRAME_INTERVAL_MS ||
            !isAnalyzing.compareAndSet(false, true)
        ) {
            imageProxy.close()
            return
        }
        lastAnalyzedAt = now

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            isAnalyzing.set(false)
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        barcodeScanner
            .process(inputImage)
            .continueWithTask(executor) { barcodeTask ->
                val barcodes = barcodeTask.result.orEmpty().mapNotNull { it.rawValue }
                objectDetector.process(inputImage).continueWith { objectTask ->
                    val boxes = objectTask.result.orEmpty().map { it.boundingBox.toNormalized(imageProxy.width, imageProxy.height) }
                    LiveVisionResult(barcodes, boxes)
                }
            }.addOnSuccessListener(executor) { onResult(it) }
            .addOnFailureListener(executor) { onError(it) }
            .addOnCompleteListener(executor) {
                try {
                    imageProxy.close()
                } finally {
                    isAnalyzing.set(false)
                }
            }
    }

    fun close() {
        barcodeScanner.close()
        objectDetector.close()
    }

    private fun Rect.toNormalized(
        width: Int,
        height: Int,
    ): NormalizedVisionBox =
        NormalizedVisionBox(
            ymin = (top.toFloat() / height).coerceIn(0f, 1f),
            xmin = (left.toFloat() / width).coerceIn(0f, 1f),
            ymax = (bottom.toFloat() / height).coerceIn(0f, 1f),
            xmax = (right.toFloat() / width).coerceIn(0f, 1f),
        )

    companion object {
        private const val FRAME_INTERVAL_MS = 66L
    }
}
