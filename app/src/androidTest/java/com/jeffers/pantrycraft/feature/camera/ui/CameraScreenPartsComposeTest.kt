package com.jeffers.pantrycraft.feature.camera.ui

import android.graphics.Color
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.pantrycraft.feature.camera.vision.SnapshotVisionService
import com.jeffers.pantrycraft.ui.PantryCraftTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraScreenPartsComposeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rememberLabelPaintAndBadgePaintInitializeCorrectly() {
        var labelPaint: Paint? = null
        var badgePaint: Paint? = null

        composeTestRule.setContent {
            PantryCraftTheme {
                labelPaint = rememberLabelPaint()
                badgePaint = rememberBadgePaint()
            }
        }
        composeTestRule.waitForIdle()

        assertNotNull(labelPaint)
        assertEquals(Color.WHITE, labelPaint?.color)
        assertTrue(labelPaint?.isAntiAlias == true)

        assertNotNull(badgePaint)
        assertEquals(Paint.Style.FILL, badgePaint?.style)
        assertTrue(badgePaint?.isAntiAlias == true)
        assertEquals(BADGE_ALPHA, Color.alpha(badgePaint?.color ?: 0))
    }

    @Test
    fun rememberSnapshotVisionServiceInitializesCorrectly() {
        var visionService: SnapshotVisionService? = null

        composeTestRule.setContent {
            PantryCraftTheme {
                val context = LocalContext.current
                visionService = rememberSnapshotVisionService(context)
            }
        }
        composeTestRule.waitForIdle()

        assertNotNull(visionService)
    }
}
