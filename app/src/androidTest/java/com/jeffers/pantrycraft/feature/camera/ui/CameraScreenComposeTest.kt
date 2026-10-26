package com.jeffers.pantrycraft.feature.camera.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionResult
import com.jeffers.pantrycraft.feature.camera.analyzer.NormalizedVisionBox
import com.jeffers.pantrycraft.feature.camera.barcode.BarcodeMatch
import com.jeffers.pantrycraft.ui.PantryCraftTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraScreenComposeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun cameraStatusHeaderRendersStatusAndTogglesTorch() {
        var torchToggled = false
        composeTestRule.setContent {
            PantryCraftTheme {
                CameraStatusHeader(
                    latestResult = null,
                    barcodeMatch = null,
                    isTorchOn = false,
                    onToggleTorch = { torchToggled = true },
                )
            }
        }
        composeTestRule.onNodeWithText("Scanning…").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Toggle flashlight").performClick()
        assertTrue(torchToggled)
    }

    @Test
    fun cameraStatusHudRendersObjectAndBarcodeDetails() {
        val result = LiveVisionResult(
            barcodes = listOf("123456"),
            objectBoxes = listOf(NormalizedVisionBox(0f, 0f, 0.5f, 0.5f, "Can", 0.92f)),
            imageWidth = 640,
            imageHeight = 480,
        )
        composeTestRule.setContent {
            PantryCraftTheme {
                CameraStatusHud(
                    CameraStatusHudState(
                        latestResult = result,
                        latestFrameTime = 42L,
                        barcodeMatch = null,
                        snapshotStatus = null,
                        isTorchOn = true,
                        onToggleTorch = {},
                    ),
                )
            }
        }
        composeTestRule.onNodeWithText("Objects: 1  Barcodes: 123456  Analysis: 42ms").assertIsDisplayed()
    }

    @Test
    fun cameraStatusHudRendersBarcodeMatchAndSnapshotStatus() {
        val match = BarcodeMatch(
            barcode = "999",
            name = "Almond Milk",
            category = "DAIRY",
            unit = "BOTTLE",
            estimatedShelfLifeDays = 14,
        )
        composeTestRule.setContent {
            PantryCraftTheme {
                CameraStatusHud(
                    CameraStatusHudState(
                        latestResult = null,
                        latestFrameTime = null,
                        barcodeMatch = match,
                        snapshotStatus = "Uploading frame…",
                        isTorchOn = false,
                        onToggleTorch = {},
                    ),
                )
            }
        }
        composeTestRule.onNodeWithText("Uploading frame…").assertIsDisplayed()
    }

    @Test
    fun unknownBarcodeCardRendersAndHandlesClick() {
        var namedCode: String? = null
        composeTestRule.setContent {
            PantryCraftTheme {
                Box {
                    UnknownBarcodeCard(
                        code = "78910",
                        visible = true,
                        onName = { namedCode = it },
                    )
                }
            }
        }
        composeTestRule.onNodeWithText("Unknown Barcode: 78910").assertIsDisplayed()
        composeTestRule.onNodeWithText("Name").performClick()
        assertEquals("78910", namedCode)
    }

    @Test
    fun cameraPermissionPromptRendersAndTriggersRequest() {
        var permissionRequested = false
        composeTestRule.setContent {
            PantryCraftTheme {
                CameraPermissionPrompt(onRequestPermission = { permissionRequested = true })
            }
        }
        composeTestRule.onNodeWithText("Camera access is required to scan pantry items.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Allow camera").performClick()
        assertTrue(permissionRequested)
    }

    @Test
    fun associateBarcodeDialogRendersInputsAndSaves() {
        var savedName = ""
        var savedCategory = ""
        var dismissed = false
        composeTestRule.setContent {
            PantryCraftTheme {
                AssociateBarcodeDialog(
                    barcode = "888999",
                    onDismiss = { dismissed = true },
                    onSave = { name, cat, _, _, _ ->
                        savedName = name
                        savedCategory = cat
                    },
                )
            }
        }
        composeTestRule.onNodeWithText("Name Scanned Barcode").assertIsDisplayed()
        composeTestRule.onNodeWithText("Barcode: 888999").assertIsDisplayed()
        composeTestRule.onNodeWithText("Item Name").performTextInput("Organic Olive Oil")
        composeTestRule.onNodeWithText("Save & Remember").performClick()
        assertEquals("Organic Olive Oil", savedName)
        assertEquals("OTHER", savedCategory)
    }

    @Test
    fun cameraPreviewOverlayRendersBoxesAndFocusPoint() {
        val result = LiveVisionResult(
            barcodes = emptyList(),
            objectBoxes = listOf(NormalizedVisionBox(0.1f, 0.1f, 0.4f, 0.4f, "Jar", 0.88f)),
            imageWidth = 1080,
            imageHeight = 1920,
        )
        composeTestRule.setContent {
            PantryCraftTheme {
                val labelPaint = rememberLabelPaint()
                val badgePaint = rememberBadgePaint()
                CameraPreviewOverlay(
                    latestResult = result,
                    focusPoint = Offset(200f, 300f),
                    labelPaint = labelPaint,
                    badgePaint = badgePaint,
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun rememberCameraScreenPaintsCompose() {
        var labelPaintReady = false
        var badgePaintReady = false
        composeTestRule.setContent {
            PantryCraftTheme {
                val labelPaint = rememberLabelPaint()
                val badgePaint = rememberBadgePaint()
                labelPaintReady = labelPaint.textSize > 0f
                badgePaintReady = badgePaint.isAntiAlias
            }
        }
        composeTestRule.waitForIdle()
        assertTrue(labelPaintReady)
        assertTrue(badgePaintReady)
    }
}
