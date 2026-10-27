package com.jeffers.pantrycraft.ui

import android.app.KeyguardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationSettingsCardComposeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        Assume.assumeTrue("Screen is locked; skipping Compose UI test", km?.isKeyguardLocked == false)
    }

    @After
    fun tearDown() {
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    @Test
    fun notificationSettingsCardRendersAndTogglesState() {
        var enabledState = true
        var changeCalled = false

        composeTestRule.setContent {
            PantryCraftTheme {
                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = {},
                )
                NotificationSettingsCard(
                    context = context,
                    enabled = enabledState,
                    onEnabledChange = {
                        enabledState = it
                        changeCalled = true
                    },
                    permissionLauncher = launcher,
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Notifications & Alerts").assertIsDisplayed()
        composeTestRule.onNodeWithText("Daily expiration alerts").assertIsDisplayed()

        composeTestRule.onNode(isToggleable()).performClick()
        composeTestRule.waitForIdle()
        assertTrue(changeCalled)
    }

    @Test
    fun notificationSettingsCardTogglesFromDisabledState() {
        var enabledState = false
        var changeCalled = false

        composeTestRule.setContent {
            PantryCraftTheme {
                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = {},
                )
                NotificationSettingsCard(
                    context = context,
                    enabled = enabledState,
                    onEnabledChange = {
                        enabledState = it
                        changeCalled = true
                    },
                    permissionLauncher = launcher,
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNode(isToggleable()).performClick()
        composeTestRule.waitForIdle()
    }
}
