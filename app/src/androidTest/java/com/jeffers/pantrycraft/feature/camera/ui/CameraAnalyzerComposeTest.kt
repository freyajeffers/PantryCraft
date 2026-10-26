package com.jeffers.pantrycraft.feature.camera.ui

import android.content.Context
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.pantrycraft.data.AppDatabase
import com.jeffers.pantrycraft.feature.camera.analyzer.FrameTimingStats
import com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionAnalyzer
import com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionResult
import com.jeffers.pantrycraft.feature.camera.barcode.OfflineBarcodeResolver
import com.jeffers.pantrycraft.ui.PantryCraftTheme
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
class CameraAnalyzerComposeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

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
}
