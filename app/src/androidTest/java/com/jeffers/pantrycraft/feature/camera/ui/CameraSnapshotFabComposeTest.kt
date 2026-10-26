package com.jeffers.pantrycraft.feature.camera.ui

import android.app.KeyguardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionAnalyzer
import com.jeffers.pantrycraft.feature.camera.core.CameraManager
import com.jeffers.pantrycraft.feature.camera.vision.LocalVisionModel
import com.jeffers.pantrycraft.feature.camera.vision.SnapshotVisionService
import com.jeffers.pantrycraft.ui.PantryCraftTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
class CameraSnapshotFabComposeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        Assume.assumeTrue("Screen is locked; skipping Compose test", km?.isKeyguardLocked == false)
    }

    @After
    fun tearDown() {
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    @Test
    fun cameraSnapshotFabRendersAndInvokesClickHandler() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val executor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient()
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .build()
        val detector = ObjectDetection.getClient(options)
        val analyzer = LiveVisionAnalyzer(scanner, detector, executor, {})
        val cameraManager = CameraManager(context, analyzer, executor)
        val dummyModel = LocalVisionModel { _, _ -> "{\"detected_items\":[]}" }
        val snapshotService = SnapshotVisionService(dummyModel)

        var statusMessage: String? = null

        composeTestRule.setContent {
            PantryCraftTheme {
                Box {
                    val scope = rememberCoroutineScope()
                    CameraSnapshotFab(
                        cameraManager = cameraManager,
                        snapshotVisionService = snapshotService,
                        scope = scope,
                        onStatus = { statusMessage = it },
                        onItems = {},
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        val fabNode = composeTestRule.onNodeWithContentDescription("Capture shelf for scanning")
        fabNode.assertIsDisplayed()
        fabNode.performClick()
        composeTestRule.waitForIdle()

        assertTrue(
            "Snapshot FAB click must invoke status callback",
            statusMessage != null && statusMessage!!.contains("Snapshot"),
        )

        analyzer.close()
        snapshotService.close()
        executor.shutdown()
    }
}
