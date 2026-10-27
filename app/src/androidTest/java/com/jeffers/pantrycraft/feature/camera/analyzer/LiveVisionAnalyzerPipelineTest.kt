package com.jeffers.pantrycraft.feature.camera.analyzer

import android.graphics.Rect
import androidx.camera.core.ImageProxy
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.common.internal.BarcodeSource
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetector
import java.lang.reflect.Proxy
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveVisionAnalyzerPipelineTest {

    private fun createMockBarcode(rawValue: String): Barcode {
        val barcodeSource = Proxy.newProxyInstance(
            BarcodeSource::class.java.classLoader,
            arrayOf(BarcodeSource::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getRawValue" -> rawValue
                "getBoundingBox" -> Rect(0, 0, 50, 50)
                else -> null
            }
        } as BarcodeSource
        return Barcode(barcodeSource)
    }

    private fun createMockImageProxy(width: Int, height: Int, onClosed: () -> Unit = {}): ImageProxy {
        return Proxy.newProxyInstance(
            ImageProxy::class.java.classLoader,
            arrayOf(ImageProxy::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getWidth" -> width
                "getHeight" -> height
                "close" -> {
                    onClosed()
                    null
                }
                else -> null
            }
        } as ImageProxy
    }

    private fun createDummyAnalyzer(executor: ExecutorService): LiveVisionAnalyzer {
        val barcodeScanner = Proxy.newProxyInstance(
            BarcodeScanner::class.java.classLoader,
            arrayOf(BarcodeScanner::class.java),
        ) { _, _, _ -> null } as BarcodeScanner
        val objectDetector = Proxy.newProxyInstance(
            ObjectDetector::class.java.classLoader,
            arrayOf(ObjectDetector::class.java),
        ) { _, _, _ -> null } as ObjectDetector
        return LiveVisionAnalyzer(
            barcodeScanner = barcodeScanner,
            objectDetector = objectDetector,
            executor = executor,
            onResult = {},
        )
    }

    @Test
    fun buildLiveVisionResultWithTransposedRotation() {
        val barcode = createMockBarcode("0123456789")
        val label = DetectedObject.Label("Soup", 0.85f, 0)
        val detected = DetectedObject(Rect(10, 20, 50, 80), 1, listOf(label))
        val imageProxy = createMockImageProxy(width = 640, height = 480)

        val result = buildLiveVisionResult(
            barcodeTask = Tasks.forResult(listOf(barcode)),
            objectTask = Tasks.forResult(listOf(detected)),
            imageProxy = imageProxy,
            rotation = 90,
        )

        assertEquals(listOf("0123456789"), result.barcodes)
        assertEquals(480, result.imageWidth)
        assertEquals(640, result.imageHeight)
        assertEquals(1, result.objectBoxes.size)
        assertEquals("Soup", result.objectBoxes.first().label)
        assertEquals(0.85f, result.objectBoxes.first().confidence ?: 0f, 0.001f)
    }

    @Test
    fun buildLiveVisionResultFiltersLowConfidenceAndAllowsUnlabeled() {
        val lowConfLabel = DetectedObject.Label("Cereal", 0.40f, 0)
        val lowConfObject = DetectedObject(Rect(5, 5, 20, 20), 1, listOf(lowConfLabel))
        val unlabeledObject = DetectedObject(Rect(30, 40, 70, 90), 2, emptyList())
        val imageProxy = createMockImageProxy(width = 1280, height = 720)

        val result = buildLiveVisionResult(
            barcodeTask = Tasks.forResult(emptyList()),
            objectTask = Tasks.forResult(listOf(lowConfObject, unlabeledObject)),
            imageProxy = imageProxy,
            rotation = 0,
        )

        assertTrue(result.barcodes.isEmpty())
        assertEquals(1280, result.imageWidth)
        assertEquals(720, result.imageHeight)
        assertEquals(1, result.objectBoxes.size)
        assertEquals(null, result.objectBoxes.first().label)
    }

    @Test
    fun safeExecuteWhenShutdownTriggersOnRejected() {
        val executor = Executors.newSingleThreadExecutor()
        executor.shutdown()
        val analyzer = createDummyAnalyzer(executor)

        val rejected = AtomicBoolean(false)
        val executed = AtomicBoolean(false)
        analyzer.safeExecute(
            command = Runnable { executed.set(true) },
            onRejected = { rejected.set(true) },
        )

        assertTrue(rejected.get())
        assertTrue(!executed.get())
    }

    @Test
    fun safeExecuteWhenRejectedExecutionExceptionTriggersOnRejected() {
        val throwingExecutor = object : AbstractExecutorService() {
            override fun shutdown() {
                // No-op for mock executor
            }
            override fun shutdownNow(): List<Runnable> = emptyList()
            override fun isShutdown(): Boolean = false
            override fun isTerminated(): Boolean = false
            override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = true
            override fun execute(command: Runnable) {
                throw RejectedExecutionException("Forced rejection")
            }
        }
        val analyzer = createDummyAnalyzer(throwingExecutor)

        val rejected = AtomicBoolean(false)
        analyzer.safeExecute(
            command = Runnable {},
            onRejected = { rejected.set(true) },
        )

        assertTrue(rejected.get())
    }

    @Test
    fun analyzeWhenExecutorShutdownClosesProxyImmediately() {
        val executor = Executors.newSingleThreadExecutor()
        executor.shutdown()
        val analyzer = createDummyAnalyzer(executor)

        val proxyClosed = AtomicBoolean(false)
        val proxy = createMockImageProxy(width = 640, height = 480) {
            proxyClosed.set(true)
        }

        analyzer.analyze(proxy)
        assertTrue(proxyClosed.get())
    }
}
