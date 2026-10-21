package com.jeffers.pantrycraft.feature.camera.analyzer

import android.graphics.Rect
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetector
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

/** Normalized bounding box in [ymin, xmin, ymax, xmax] order with optional classification. */
data class NormalizedVisionBox(
    val ymin: Float,
    val xmin: Float,
    val ymax: Float,
    val xmax: Float,
    val label: String? = null,
    val confidence: Float? = null,
)

data class LiveVisionResult(
    val barcodes: List<String>,
    val objectBoxes: List<NormalizedVisionBox>,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
)

class LiveVisionAnalyzer(
    private val barcodeScanner: BarcodeScanner,
    private val objectDetector: ObjectDetector,
    private val executor: ExecutorService,
    private val onResult: (LiveVisionResult) -> Unit,
    private val onError: (Exception) -> Unit = {},
    private val onProcessingDurationMillis: (Long) -> Unit = {},
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
        val startedAt = SystemClock.elapsedRealtime()

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            isAnalyzing.set(false)
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
        val barcodeTask = barcodeScanner.process(inputImage)
        val objectTask = objectDetector.process(inputImage)
        Tasks
            .whenAllComplete(barcodeTask, objectTask)
            .continueWith(executor) {
                val barcodes = barcodeTask.result.orEmpty().mapNotNull { it.rawValue }

                // ML Kit detects objects in the rotated upright input image coordinate space.
                // When rotation is 90 or 270 degrees, effective width/height are transposed.
                val isTransposed = rotation % RIGHT_ANGLE_DEGREES != NO_REMAINDER
                val effectiveWidth = if (isTransposed) imageProxy.height else imageProxy.width
                val effectiveHeight = if (isTransposed) imageProxy.width else imageProxy.height

                val boxes =
                    objectTask.result
                        .orEmpty()
                        .filter { detected ->
                            val topLabel = detected.labels.firstOrNull()
                            topLabel == null || topLabel.confidence >= CONFIDENCE_THRESHOLD
                        }.map { detected ->
                            val label = detected.labels.firstOrNull()?.text
                            val confidence = detected.labels.firstOrNull()?.confidence
                            detected.boundingBox.toNormalized(effectiveWidth, effectiveHeight, label, confidence)
                        }
                LiveVisionResult(
                    barcodes = barcodes,
                    objectBoxes = boxes,
                    imageWidth = effectiveWidth,
                    imageHeight = effectiveHeight,
                )
            }.addOnSuccessListener(executor) { onResult(it) }
            .addOnFailureListener(executor) { onError(it) }
            .addOnCompleteListener(executor) {
                try {
                    imageProxy.close()
                } finally {
                    onProcessingDurationMillis(SystemClock.elapsedRealtime() - startedAt)
                    isAnalyzing.set(false)
                }
            }
    }

    fun close() {
        barcodeScanner.close()
        objectDetector.close()
    }

    companion object {
        private const val FRAME_INTERVAL_MS = 66L // ~15 FPS throttle
        private const val RIGHT_ANGLE_DEGREES = 180
        private const val NO_REMAINDER = 0
        const val CONFIDENCE_THRESHOLD = 0.70f
    }
}

internal fun Rect.toNormalized(
    width: Int,
    height: Int,
    label: String? = null,
    confidence: Float? = null,
): NormalizedVisionBox =
    NormalizedVisionBox(
        ymin = (top.toFloat() / height).coerceIn(0f, 1f),
        xmin = (left.toFloat() / width).coerceIn(0f, 1f),
        ymax = (bottom.toFloat() / height).coerceIn(0f, 1f),
        xmax = (right.toFloat() / width).coerceIn(0f, 1f),
        label = label,
        confidence = confidence,
    )

class FrameTimingStats {
    var sampleCount: Int = 0
        private set
    var maxDurationMillis: Long = 0
        private set

    fun record(durationMillis: Long) {
        sampleCount += 1
        maxDurationMillis = maxOf(maxDurationMillis, durationMillis)
    }
}
