package com.jeffers.pantrycraft.ui

import android.app.KeyguardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.pantrycraft.data.AiProvider
import com.jeffers.pantrycraft.data.ByokPreferences
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiSettingsCardComposeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var originalApiKey: String? = null
    private lateinit var originalProvider: AiProvider
    private lateinit var originalModel: String
    private lateinit var originalEndpoint: String

    @Before
    fun setUp() {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        Assume.assumeTrue("Screen is locked; skipping Compose test", km?.isKeyguardLocked == false)

        originalApiKey = ByokPreferences.loadApiKey(context)
        originalProvider = ByokPreferences.selectedProvider(context)
        originalModel = ByokPreferences.selectedModel(context)
        originalEndpoint = ByokPreferences.selectedEndpoint(context)
        ByokPreferences.saveConfiguration(
            context,
            AiProvider.OFFLINE_MLKIT,
            AiProvider.OFFLINE_MLKIT.defaultModel,
            AiProvider.OFFLINE_MLKIT.defaultEndpoint,
        )
        ByokPreferences.clearApiKey(context)
    }

    @After
    fun tearDown() {
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.DESTROYED)
        ByokPreferences.saveConfiguration(context, originalProvider, originalModel, originalEndpoint)
        if (originalApiKey != null) {
            val keyChars = originalApiKey!!.toCharArray()
            ByokPreferences.saveApiKey(context, keyChars)
            keyChars.fill('\u0000')
        } else {
            ByokPreferences.clearApiKey(context)
        }
    }

    @Test
    fun aiSettingsCardRendersDefaultLocalMode() {
        composeTestRule.setContent {
            PantryCraftTheme {
                AiSettingsCard(context = context)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("AI Vision & Meal Engine (BYOKey / Local)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save AI Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Model Name").assertDoesNotExist()
    }

    @Test
    fun aiSettingsCardSwitchesToCloudProviderAndSavesApiKey() {
        composeTestRule.setContent {
            PantryCraftTheme {
                AiSettingsCard(context = context)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("OpenAI", substring = true).performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Model Name").assertIsDisplayed()
        composeTestRule.onNodeWithText("Endpoint URL").assertIsDisplayed()
        composeTestRule.onNodeWithText("API Key").assertIsDisplayed()

        composeTestRule.onNodeWithText("API Key").performTextInput("sk-sample-test-key")
        composeTestRule.onNodeWithText("Save AI Settings").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Clear Key").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Clear Key").assertDoesNotExist()
    }
}
