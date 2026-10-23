package com.jeffers.pantrycraft.feature.camera.ui

import android.content.Context
import androidx.camera.view.PreviewView
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jeffers.pantrycraft.data.Repository
import com.jeffers.pantrycraft.data.offlineBarcodeResolver
import com.jeffers.pantrycraft.feature.camera.analyzer.FrameTimingStats
import com.jeffers.pantrycraft.feature.camera.barcode.BarcodeMatch
import com.jeffers.pantrycraft.feature.camera.vision.LocalVisionModel
import com.jeffers.pantrycraft.feature.camera.vision.SnapshotVisionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
class CameraScreenStateTest {
    private lateinit var context: Context
    private lateinit var executor: ExecutorService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        executor = Executors.newSingleThreadExecutor()
    }

    @After
    fun tearDown() {
        executor.shutdown()
    }

    @Test
    fun cameraScreenStateInitializesWithDefaultsAndMutatesCleanly() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val repository = Repository.getInstance(context)
            val resolver = repository.offlineBarcodeResolver()
            val snapshotService = SnapshotVisionService(LocalVisionModel { _, _ -> "{}" })
            val frameTimingStats = FrameTimingStats()
            val previewView = PreviewView(context)
            val scope = CoroutineScope(Dispatchers.Main.immediate)

            val deps = CameraDeps(
                context = context,
                repository = repository,
                analysisExecutor = executor,
                scope = scope,
                previewView = previewView,
                barcodeResolver = resolver,
                snapshotVisionService = snapshotService,
                frameTimingStats = frameTimingStats,
            )

            val state = CameraScreenState(deps)

            // Verify defaults
            assertNull(state.snapshotStatus)
            assertNull(state.barcodeMatch)
            assertNull(state.unresolvedBarcode)
            assertNull(state.associateDialogBarcode)
            assertTrue(state.parsedItems.isEmpty())
            assertNull(state.latestResult)
            assertNull(state.latestFrameTime)
            assertFalse(state.isTorchOn)
            assertNull(state.focusPoint)
            assertEquals(0f, state.zoomRatio)

            // Verify mutations
            state.snapshotStatus = "Scanning complete"
            assertEquals("Scanning complete", state.snapshotStatus)

            state.isTorchOn = true
            assertTrue(state.isTorchOn)

            state.zoomRatio = 0.75f
            assertEquals(0.75f, state.zoomRatio)

            state.focusPoint = Offset(100f, 200f)
            assertEquals(Offset(100f, 200f), state.focusPoint)

            val match = BarcodeMatch(
                barcode = "12345678",
                name = "Test Item",
                category = "PANTRY_STAPLE",
                unit = "COUNT",
                estimatedShelfLifeDays = 30,
            )
            state.barcodeMatch = match
            assertEquals("Test Item", state.barcodeMatch?.name)

            resolver.close()
            snapshotService.close()
        }
    }
}
