package com.jeffers.pantrycraft.feature.camera.analyzer

import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.objects.ObjectDetector
import java.lang.reflect.Proxy
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveVisionAnalyzerUnitTest {

    @Test
    fun liveVisionAnalyzerCloseClosesBothEngines() {
        val barcodeClosed = AtomicBoolean(false)
        val barcodeScanner = Proxy.newProxyInstance(
            BarcodeScanner::class.java.classLoader,
            arrayOf(BarcodeScanner::class.java),
        ) { _, method, _ ->
            if (method.name == "close") barcodeClosed.set(true)
            null
        } as BarcodeScanner

        val objectDetectorClosed = AtomicBoolean(false)
        val objectDetector = Proxy.newProxyInstance(
            ObjectDetector::class.java.classLoader,
            arrayOf(ObjectDetector::class.java),
        ) { _, method, _ ->
            if (method.name == "close") objectDetectorClosed.set(true)
            null
        } as ObjectDetector

        val executor = Executors.newSingleThreadExecutor()
        try {
            val analyzer = LiveVisionAnalyzer(
                barcodeScanner = barcodeScanner,
                objectDetector = objectDetector,
                executor = executor,
                onResult = {},
            )

            analyzer.close()

            assertTrue(barcodeClosed.get())
            assertTrue(objectDetectorClosed.get())
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun liveVisionAnalyzerNullMediaImageClosesProxyAndResetsAnalyzingFlag() {
        val barcodeScanner = Proxy.newProxyInstance(
            BarcodeScanner::class.java.classLoader,
            arrayOf(BarcodeScanner::class.java),
        ) { _, _, _ -> null } as BarcodeScanner

        val objectDetector = Proxy.newProxyInstance(
            ObjectDetector::class.java.classLoader,
            arrayOf(ObjectDetector::class.java),
        ) { _, _, _ -> null } as ObjectDetector

        val imageProxyClosed = AtomicBoolean(false)
        val imageProxy = Proxy.newProxyInstance(
            ImageProxy::class.java.classLoader,
            arrayOf(ImageProxy::class.java),
        ) { _, method, _ ->
            if (method.name == "close") imageProxyClosed.set(true)
            null
        } as ImageProxy

        val executor = Executors.newSingleThreadExecutor()
        try {
            val analyzer = LiveVisionAnalyzer(
                barcodeScanner = barcodeScanner,
                objectDetector = objectDetector,
                executor = executor,
                onResult = {},
            )

            analyzer.analyze(imageProxy)
            assertTrue(imageProxyClosed.get())

            // Immediate second analyze call triggers FRAME_INTERVAL_MS throttle
            val secondProxyClosed = AtomicBoolean(false)
            val secondProxy = Proxy.newProxyInstance(
                ImageProxy::class.java.classLoader,
                arrayOf(ImageProxy::class.java),
            ) { _, method, _ ->
                if (method.name == "close") secondProxyClosed.set(true)
                null
            } as ImageProxy

            analyzer.analyze(secondProxy)
            assertTrue(secondProxyClosed.get())
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun safeExecuteRunsCommandWhenExecutorActive() {
        val executor = Executors.newSingleThreadExecutor()
        val executed = AtomicBoolean(false)
        val dummyBarcode = Proxy.newProxyInstance(
            BarcodeScanner::class.java.classLoader,
            arrayOf(BarcodeScanner::class.java),
        ) { _, _, _ -> null } as BarcodeScanner
        val dummyDetector = Proxy.newProxyInstance(
            ObjectDetector::class.java.classLoader,
            arrayOf(ObjectDetector::class.java),
        ) { _, _, _ -> null } as ObjectDetector
        val analyzer = LiveVisionAnalyzer(
            barcodeScanner = dummyBarcode,
            objectDetector = dummyDetector,
            executor = executor,
            onResult = {},
        )
        try {
            analyzer.safeExecute({ executed.set(true) })
            Thread.sleep(50)
            assertTrue(executed.get())
        } finally {
            analyzer.close()
        }
    }

    @Test
    fun safeExecuteInvokesOnRejectedWhenExecutorShutdown() {
        val executor = Executors.newSingleThreadExecutor().apply { shutdown() }
        val rejected = AtomicBoolean(false)
        val dummyBarcode = Proxy.newProxyInstance(
            BarcodeScanner::class.java.classLoader,
            arrayOf(BarcodeScanner::class.java),
        ) { _, _, _ -> null } as BarcodeScanner
        val dummyDetector = Proxy.newProxyInstance(
            ObjectDetector::class.java.classLoader,
            arrayOf(ObjectDetector::class.java),
        ) { _, _, _ -> null } as ObjectDetector
        val analyzer = LiveVisionAnalyzer(
            barcodeScanner = dummyBarcode,
            objectDetector = dummyDetector,
            executor = executor,
            onResult = {},
        )
        analyzer.safeExecute({ }, onRejected = { rejected.set(true) })
        assertTrue(rejected.get())
    }
}
