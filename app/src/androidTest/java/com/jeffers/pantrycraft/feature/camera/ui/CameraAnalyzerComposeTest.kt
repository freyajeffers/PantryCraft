package com.jeffers.pantrycraft.feature.camera.ui

import android.app.KeyguardManager
import android.content.Context
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.pantrycraft.data.AppDatabase
import com.jeffers.pantrycraft.feature.camera.analyzer.FrameTimingStats
import com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionAnalyzer
import com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionResult
import com.jeffers.pantrycraft.feature.camera.barcode.BarcodeMatch
import com.jeffers.pantrycraft.feature.camera.barcode.OfflineBarcodeResolver
import com.jeffers.pantrycraft.ui.PantryCraftTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
class CameraAnalyzerComposeTest {

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
    fun rememberCameraAnalyzerCreatesValidAnalyzerAndInvokesCallbacks() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val view = View(context)
        val executor = Executors.newSingleThreadExecutor()
        val resolver = OfflineBarcodeResolver(AppDatabase.getDatabase(context).customBarcodeDao())
        val stats = FrameTimingStats()

        var analyzerInstance: LiveVisionAnalyzer? = null
        var resultReceived: LiveVisionResult? = null
        var frameTimeReceived: Long? = null

        composeTestRule.setContent {
            PantryCraftTheme {
                Box {
                    val scope = rememberCoroutineScope()
                    val state = CameraAnalyzerState(
                        context = context,
                        view = view,
                        executor = executor,
                        scope = scope,
                        barcodeResolver = resolver,
                        frameTimingStats = stats,
                        onResult = { resultReceived = it },
                        onFrameTime = { frameTimeReceived = it },
                        onBarcodeResolved = {},
                        onBarcodeUnresolved = {},
                    )
                    val analyzer = rememberCameraAnalyzer(state)
                    analyzerInstance = analyzer
                }
            }
        }

        composeTestRule.waitForIdle()
        assertNotNull(analyzerInstance)

        analyzerInstance?.close()
        executor.shutdown()
    }

    @Test
    fun rememberCameraFocusPointInitialAndAutoClear() {
        var focusState: FocusPointState? = null
        composeTestRule.setContent {
            PantryCraftTheme {
                val state = rememberCameraFocusPoint()
                focusState = state
            }
        }
        composeTestRule.waitForIdle()
        assertNotNull(focusState)
        assertNull(focusState?.value)

        composeTestRule.runOnIdle {
            focusState?.value = Offset(50f, 100f)
        }
        assertEquals(Offset(50f, 100f), focusState?.value)

        composeTestRule.mainClock.advanceTimeBy(FOCUS_CLEAR_DELAY_MILLIS + 100L)
        composeTestRule.waitForIdle()
        assertNull(focusState?.value)
    }

    @Test
    fun cameraStatusHeaderRendersAndTogglesTorch() {
        var torchToggled = false
        val match = BarcodeMatch("123456", "Organic Tea", "BEVERAGES", "BOX", 30)

        composeTestRule.setContent {
            PantryCraftTheme {
                CameraStatusHeader(
                    latestResult = null,
                    barcodeMatch = match,
                    isTorchOn = false,
                    onToggleTorch = { torchToggled = true },
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Toggle flashlight").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertTrue("Torch toggling callback must be invoked", torchToggled)
    }
}
