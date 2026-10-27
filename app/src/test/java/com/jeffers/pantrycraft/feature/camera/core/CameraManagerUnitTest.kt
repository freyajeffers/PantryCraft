package com.jeffers.pantrycraft.feature.camera.core

import android.content.Context
import android.content.ContextWrapper
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.objects.ObjectDetector
import com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionAnalyzer
import java.lang.reflect.Proxy
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class CameraManagerUnitTest {

    private class DummyContext : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    private lateinit var executor: ExecutorService
    private lateinit var analyzer: LiveVisionAnalyzer
    private lateinit var cameraManager: CameraManager

    @Before
    fun setUp() {
        executor = Executors.newSingleThreadExecutor()
        val dummyBarcode = Proxy.newProxyInstance(
            BarcodeScanner::class.java.classLoader,
            arrayOf(BarcodeScanner::class.java),
        ) { _, _, _ -> null } as BarcodeScanner
        val dummyDetector = Proxy.newProxyInstance(
            ObjectDetector::class.java.classLoader,
            arrayOf(ObjectDetector::class.java),
        ) { _, _, _ -> null } as ObjectDetector
        analyzer = LiveVisionAnalyzer(
            barcodeScanner = dummyBarcode,
            objectDetector = dummyDetector,
            executor = executor,
            onResult = {},
        )
        cameraManager = CameraManager(DummyContext(), analyzer, executor)
    }

    @After
    fun tearDown() {
        cameraManager.unbind()
        analyzer.close()
        executor.shutdown()
    }

    @Test
    fun hasFlashReturnsFalseWhenUnbound() {
        assertFalse(cameraManager.hasFlash())
    }

    @Test
    fun setTorchSafeWhenUnbound() {
        cameraManager.setTorch(true)
        cameraManager.setTorch(false)
        assertFalse(cameraManager.hasFlash())
    }

    @Test
    fun setLinearZoomSafeWhenUnbound() {
        cameraManager.setLinearZoom(-0.5f)
        cameraManager.setLinearZoom(0.5f)
        cameraManager.setLinearZoom(1.5f)
        assertFalse(cameraManager.hasFlash())
    }

    @Test
    fun captureSnapshotThrowsIllegalStateExceptionWhenUnbound() = runBlocking {
        try {
            cameraManager.captureSnapshot()
            fail("Should throw IllegalStateException when imageCapture is null")
        } catch (expected: IllegalStateException) {
            assertEquals("Camera capture is not ready", expected.message)
        }
    }

    @Test
    fun unbindIsIdempotent() {
        cameraManager.unbind()
        cameraManager.unbind()
        assertFalse(cameraManager.hasFlash())
    }
}
