---
name: android-camerax-mlkit-pipeline
description: Implement lifecycle-aware CameraX pipelines with ML Kit object detection, barcode scanning, and memory-safe frame buffer pooling.
---

# Android CameraX & ML Kit Pipeline

Specialized engineering guidance for configuring, optimizing, and debugging dual-mode CameraX pipelines on Android with Google ML Kit.

## When to Use
- Implementing continuous real-time camera viewfinder analysis (15 FPS HUD).
- Chaining ML Kit Barcode Scanning and Object Detection analyzers without freezing camera preview.
- Mapping normalized bounding boxes `[ymin, xmin, ymax, xmax]` from sensor coordinates to Compose Canvas display coordinates.
- Capturing high-resolution still frames while maintaining memory safety.

## Core Architectural Invariants
1. **Mandatory ImageProxy Closure:** Every `ImageAnalysis.Analyzer` frame callback MUST call `imageProxy.close()` inside a `finally` block. Missing a single closure will exhaust the hardware buffer queue and permanently freeze the camera stream.
2. **Concurrency Lock & Frame Throttling:** Throttle analysis frames to 12–15 FPS using a timestamp check combined with an `AtomicBoolean` in-flight lock. Discard incoming frames if an ML Kit task is currently executing.
3. **Dedicated Background Execution:** The analyzer must execute on a dedicated single-thread background `ExecutorService`. Never run ML Kit inference on `Dispatchers.Main`.
4. **Normalized Coordinate Scaling:** Bounding box coordinates normalized from 0.0 to 1.0 (`[ymin, xmin, ymax, xmax]`) must be transformed to display screen pixels considering sensor orientation and aspect ratio cropping.

## Implementation Pattern (Kotlin / CameraX 1.4+)
```kotlin
class LiveVisionAnalyzer(
    private val barcodeScanner: BarcodeScanner,
    private val objectDetector: ObjectDetector,
    private val onBarcodeFound: (String) -> Unit,
    private val onObjectsDetected: (List<DetectedObject>) -> Unit
) : ImageAnalysis.Analyzer {
    private var lastAnalyzedTimestamp = 0L
    private val isAnalyzing = java.util.concurrent.atomic.AtomicBoolean(false)

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()
        if (currentTimestamp - lastAnalyzedTimestamp < 66 || !isAnalyzing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        lastAnalyzedTimestamp = currentTimestamp

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            isAnalyzing.set(false)
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        
        // Pass 1: Barcode Scan -> Pass 2: Object Detection -> Finally close
        barcodeScanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                barcodes.firstOrNull()?.rawValue?.let { onBarcodeFound(it) }
            }
            .continueWithTask {
                objectDetector.process(inputImage)
            }
            .addOnSuccessListener { objects ->
                onObjectsDetected(objects)
            }
            .addOnCompleteListener {
                try {
                    imageProxy.close()
                } finally {
                    isAnalyzing.set(false)
                }
            }
    }
}

/**
 * Transforms normalized coordinates [ymin, xmin, ymax, xmax] to Compose Canvas Rect
 */
fun mapNormalizedToCanvas(
    box: List<Float>, // [ymin, xmin, ymax, xmax]
    canvasWidth: Float,
    canvasHeight: Float
): androidx.compose.ui.geometry.Rect {
    val yMin = box[0] * canvasHeight
    val xMin = box[1] * canvasWidth
    val yMax = box[2] * canvasHeight
    val xMax = box[3] * canvasWidth
    return androidx.compose.ui.geometry.Rect(left = xMin, top = yMin, right = xMax, bottom = yMax)
}
```

## Failure Modes & Edge Case Recovery
- **Camera Freezes on Device Rotation:** Ensure `ProcessCameraProvider.unbindAll()` is invoked prior to re-binding use cases in Compose `DisposableEffect`.
- **Black Viewfinder Preview:** Verify that `PreviewView` implementation mode is set to `PreviewView.ImplementationMode.PERFORMANCE` (SurfaceView) or `COMPATIBLE` (TextureView).
- **CameraAccessException on Resume:** Catch `SecurityException` and check `ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)` before invoking `bindToLifecycle`.
