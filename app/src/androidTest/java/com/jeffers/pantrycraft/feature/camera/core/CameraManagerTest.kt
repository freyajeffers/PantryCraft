package com.jeffers.pantrycraft.feature.camera.core

import android.content.Context
import androidx.camera.view.PreviewView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionAnalyzer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
class CameraManagerTest {

    private lateinit var context: Context
    private lateinit var executor: ExecutorService
    private lateinit var analyzer: LiveVisionAnalyzer
    private lateinit var cameraManager: CameraManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        executor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient()
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .build()
        val detector = ObjectDetection.getClient(options)
        analyzer = LiveVisionAnalyzer(scanner, detector, executor, {})
        cameraManager = CameraManager(context, analyzer, executor)
    }

    @After
    fun tearDown() {
        cameraManager.unbind()
        analyzer.close()
        executor.shutdown()
    }

    @Test
    fun cameraManagerControlsAreSafeWhenUnbound() {
        assertFalse(cameraManager.hasFlash())
        cameraManager.setTorch(true)
        cameraManager.setTorch(false)
        cameraManager.setLinearZoom(0.5f)
        cameraManager.unbind()
    }

    @Test
    fun cameraManagerFocusOnUnboundPreviewIsSafe() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val previewView = PreviewView(context)
            cameraManager.focusOn(100f, 150f, previewView)
        }
    }

    @Test
    fun cameraManagerCaptureSnapshotThrowsWhenNotReady() = runBlocking {
        try {
            cameraManager.captureSnapshot()
            fail("captureSnapshot must throw IllegalStateException when not bound")
        } catch (expected: IllegalStateException) {
            org.junit.Assert.assertEquals("Camera capture is not ready", expected.message)
        }
    }

    @Test
    fun cameraManagerZoomClampsBoundsAndMultipleUnbindsAreIdempotent() {
        cameraManager.setLinearZoom(-1.0f)
        cameraManager.setLinearZoom(2.5f)
        cameraManager.unbind()
        cameraManager.unbind()
        assertFalse(cameraManager.hasFlash())
    }
}
