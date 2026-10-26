package com.jeffers.pantrycraft.ui

import android.app.KeyguardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
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
}
