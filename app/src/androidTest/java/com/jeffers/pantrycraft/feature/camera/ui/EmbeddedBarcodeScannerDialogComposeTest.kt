package com.jeffers.pantrycraft.feature.camera.ui

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionResult
import com.jeffers.pantrycraft.ui.PantryCraftTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmbeddedBarcodeScannerDialogComposeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun triggerBarcodeHapticTickExecutesSafely() {
        val activity = composeTestRule.activity
        val view = View(activity)
        triggerBarcodeHapticTick(activity, view)
    }

    @Test
    fun embeddedBarcodeScannerDialogRendersAndDismisses() {
        var dismissed = false

        composeTestRule.setContent {
            PantryCraftTheme {
                EmbeddedBarcodeScannerDialog(
                    onDismiss = { dismissed = true },
                    onBarcodeResolved = { _, _ -> },
                )
            }
        }

        composeTestRule.onNodeWithText("Scan Item Barcode").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun embeddedBarcodeScannerDialogRendersWithCameraPermission() {
        val context = composeTestRule.activity
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, android.Manifest.permission.CAMERA)

        composeTestRule.setContent {
            PantryCraftTheme {
                EmbeddedBarcodeScannerDialog(
                    onDismiss = {},
                    onBarcodeResolved = { _, _ -> },
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Scan Item Barcode").assertIsDisplayed()
        composeTestRule.onNodeWithText("Point the camera at a UPC or EAN barcode to auto-fill item details.")
            .assertIsDisplayed()
    }

    @Test
    fun embeddedBarcodeScannerDialogRequestsPermissionWhenClicked() {
        composeTestRule.setContent {
            PantryCraftTheme {
                EmbeddedBarcodeScannerDialog(
                    onDismiss = {},
                    onBarcodeResolved = { _, _ -> },
                )
            }
        }
        composeTestRule.waitForIdle()
        val allowButton = composeTestRule.onAllNodesWithText("Allow camera")
        if (allowButton.fetchSemanticsNodes().isNotEmpty()) {
            allowButton.onFirst().performClick()
        }
    }

    @Test
    fun createBarcodeAnalyzerProcessesDetectedBarcode() {
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        var reportedBarcode: String? = null
        val analyzer = createBarcodeAnalyzer(executor) { barcode ->
            reportedBarcode = barcode
        }
        val onResultField = analyzer::class.java.getDeclaredField("onResult").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val onResult = onResultField.get(analyzer) as (LiveVisionResult) -> Unit
        val dummyResult = LiveVisionResult(
            barcodes = listOf("012345678905"),
            objectBoxes = emptyList(),
        )
        onResult.invoke(dummyResult)
        assertEquals("012345678905", reportedBarcode)
        analyzer.close()
        executor.shutdown()
    }
}
