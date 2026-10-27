package com.jeffers.pantrycraft.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeComposeTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        Assume.assumeTrue(
            "Screen is locked; skipping Compose UI test",
            km?.isKeyguardLocked == false,
        )
    }

    @After
    fun tearDown() {
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    @Test
    fun themeRendersContentWithPantryCraftTheme() {
        composeTestRule.setContent {
            PantryCraftTheme {
                Text("Theme test node text")
            }
        }
        composeTestRule.onNodeWithText("Theme test node text").assertIsDisplayed()
    }

    @Test
    fun themeRendersContentWithDarkTheme() {
        val darkConfig = Configuration(composeTestRule.activity.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        }
        composeTestRule.setContent {
            CompositionLocalProvider(LocalConfiguration provides darkConfig) {
                PantryCraftTheme {
                    Text("Dark theme node text")
                }
            }
        }
        composeTestRule.onNodeWithText("Dark theme node text").assertIsDisplayed()
    }
}
