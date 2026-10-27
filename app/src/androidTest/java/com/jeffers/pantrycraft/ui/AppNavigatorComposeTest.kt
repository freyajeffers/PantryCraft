package com.jeffers.pantrycraft.ui

import android.app.KeyguardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class AppNavigatorComposeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        Assume.assumeTrue("Screen is locked; skipping Compose UI test", km?.isKeyguardLocked == false)
    }

    @After
    fun tearDown() {
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    @Test
    fun appNavigatorRendersAndNavigatesToAllScreens() {
        composeTestRule.setContent {
            PantryCraftTheme {
                AppNavigator()
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Dashboard").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("PantryCraft").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Grocery").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Grocery list").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Scan").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Pantry").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithText("Pantry").onFirst().assertIsDisplayed()
    }

    @Test
    fun databaseLoadingScreenRendersPrompt() {
        composeTestRule.setContent {
            PantryCraftTheme {
                DatabaseLoadingScreen()
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Opening encrypted pantry").assertIsDisplayed()
    }

    @Test
    fun appBottomBarSelectsDestinations() {
        var selectedRoute = "home"
        composeTestRule.setContent {
            PantryCraftTheme {
                AppBottomBar(destination = selectedRoute) { selectedRoute = it }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Dashboard").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        org.junit.Assert.assertEquals("settings", selectedRoute)
    }

    @Test
    fun databaseRecoveryScreenHostRenders() {
        composeTestRule.setContent {
            PantryCraftTheme {
                DatabaseRecoveryScreenHost(composeTestRule.activity)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Encrypted database recovery required").assertIsDisplayed()
    }

    @Test
    fun pantryCraftAppRendersRootSurface() {
        composeTestRule.setContent {
            PantryCraftApp()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Dashboard").assertIsDisplayed()
    }
}
