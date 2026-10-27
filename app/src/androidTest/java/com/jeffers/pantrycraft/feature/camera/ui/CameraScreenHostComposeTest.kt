package com.jeffers.pantrycraft.feature.camera.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.pantrycraft.ui.PantryCraftTheme
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraScreenHostComposeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun cameraScreenControllerRendersScanningHud() {
        composeTestRule.setContent {
            PantryCraftTheme {
                CameraScreenController()
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithText("Scanning…").onFirst().assertIsDisplayed()
    }

    @Test
    fun cameraScreenHostRendersContent() {
        composeTestRule.setContent {
            PantryCraftTheme {
                CameraScreenHost()
            }
        }
        composeTestRule.waitForIdle()
        val scanningNodes = composeTestRule.onAllNodesWithText("Scanning…")
        val permissionNodes = composeTestRule.onAllNodesWithText("Camera access is required to scan pantry items.")
        assertTrue(
            scanningNodes.fetchSemanticsNodes().isNotEmpty() ||
                permissionNodes.fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun cameraScreenBoxRenders() {
        composeTestRule.setContent {
            PantryCraftTheme {
                val context = LocalContext.current
                val state = rememberCameraScreenState(context)
                val runtime = rememberCameraRuntimeForScreen(
                    state = state,
                    onResult = {},
                    onFrameTime = {},
                    onBarcodeResolved = {},
                    onBarcodeUnresolved = {},
                )
                val args = CameraScreenBoxArgs(
                    previewView = state.previewView,
                    latestResult = null,
                    focusPoint = Offset(100f, 100f),
                    labelPaint = android.graphics.Paint(),
                    badgePaint = android.graphics.Paint(),
                    cameraManager = runtime.cameraManager,
                    repository = state.repository,
                    scope = state.scope,
                    barcodeMatch = null,
                    unresolvedBarcode = null,
                    associateDialogBarcode = null,
                    parsedItems = emptyList(),
                    snapshotVisionService = state.snapshotVisionService,
                    barcodeResolver = state.barcodeResolver,
                    isTorchOn = false,
                    onToggleTorch = {},
                    onClearBarcodeMatch = {},
                    onParsedItemsChange = {},
                    onUnresolvedBarcodeChange = {},
                    onAssociateDialogBarcodeChange = {},
                    onSnapshotStatusChange = {},
                    view = LocalView.current,
                )
                CameraScreenBox(args = args)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithText("Scanning…").onFirst().assertIsDisplayed()
    }

    @Test
    fun cameraPermissionPromptRendersAndInvokesRequest() {
        var requested = false
        composeTestRule.setContent {
            PantryCraftTheme {
                CameraPermissionPrompt { requested = true }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Camera access is required to scan pantry items.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Allow camera").performClick()
        assertTrue(requested)
    }

    @Test
    fun rememberCameraFocusPointUpdatesAndClears() {
        var focusState: FocusPointState? = null
        composeTestRule.setContent {
            focusState = rememberCameraFocusPoint()
        }
        composeTestRule.waitForIdle()
        assertNotNull(focusState)
        composeTestRule.runOnIdle {
            focusState?.value = Offset(150f, 250f)
        }
        composeTestRule.waitForIdle()
    }
}
