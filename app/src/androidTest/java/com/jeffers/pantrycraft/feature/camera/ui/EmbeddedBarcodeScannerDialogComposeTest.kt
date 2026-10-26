package com.jeffers.pantrycraft.feature.camera.ui

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.pantrycraft.ui.PantryCraftTheme
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
}
