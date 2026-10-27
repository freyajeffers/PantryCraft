package com.jeffers.pantrycraft.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.pantrycraft.MainActivity
import com.jeffers.pantrycraft.data.AiProvider
import com.jeffers.pantrycraft.data.ByokPreferences
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenComposeTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var context: Context
    private lateinit var originalProvider: AiProvider
    private lateinit var originalModel: String
    private lateinit var originalEndpoint: String
    private var originalApiKey: String? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        com.jeffers.pantrycraft.data.AppDatabase.getDatabase(context)
        com.jeffers.pantrycraft.data.Repository.getInstance(context)
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        org.junit.Assume.assumeTrue(
            "Screen is locked; skipping Compose UI test",
            km?.isKeyguardLocked == false,
        )

        originalProvider = ByokPreferences.selectedProvider(context)
        originalModel = ByokPreferences.selectedModel(context)
        originalEndpoint = ByokPreferences.selectedEndpoint(context)
        originalApiKey = ByokPreferences.loadApiKey(context)

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

        if (::originalProvider.isInitialized && ::originalModel.isInitialized && ::originalEndpoint.isInitialized) {
            ByokPreferences.saveConfiguration(context, originalProvider, originalModel, originalEndpoint)
            if (originalApiKey != null) {
                val keyChars = originalApiKey!!.toCharArray()
                ByokPreferences.saveApiKey(context, keyChars)
                keyChars.fill('\u0000')
            } else {
                ByokPreferences.clearApiKey(context)
            }
        }
    }

    @Test
    fun settingsDisplaysCardsAndTogglesAiProviderFields() {
        composeTestRule.waitForIdle()

        // 1. Navigate to Settings destination
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitForIdle()

        // 2. Verify Settings headers and primary cards
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("FoodFacts barcode catalog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Notifications & Alerts").assertIsDisplayed()
        composeTestRule.onNodeWithText("Daily expiration alerts").assertIsDisplayed()

        // Scroll down to Data Sovereignty and AI Settings
        composeTestRule.onNodeWithText("Data Sovereignty & Portability").performScrollTo().assertExists()
        composeTestRule.onNodeWithText("Export JSON").performScrollTo().assertExists()
        composeTestRule.onNodeWithText("Export CSV").performScrollTo().assertExists()
        composeTestRule.onNodeWithText("Import JSON").performScrollTo().assertExists()
        composeTestRule.onNodeWithText("Import CSV").performScrollTo().assertExists()

        composeTestRule.onNodeWithText("AI Vision & Meal Engine (BYOKey / Local)").performScrollTo().assertExists()
        composeTestRule.onNodeWithText("Save AI Settings").performScrollTo()

        // 3. Switch to Google Gemini (BYOKey)
        composeTestRule
            .onNode(hasText(AiProvider.GEMINI.label, substring = true))
            .performClick()
        composeTestRule.waitForIdle()

        // Verify Model Name and Endpoint URL text fields appear for BYOKey provider
        composeTestRule.onNodeWithText("Model Name").performScrollTo().assertExists()
        composeTestRule.onNodeWithText("Endpoint URL").performScrollTo().assertExists()

        // 4. Switch back to 100% Offline ML Kit
        composeTestRule.onNodeWithText("Save AI Settings").performScrollTo()
        composeTestRule
            .onNode(hasText(AiProvider.OFFLINE_MLKIT.label, substring = true))
            .performClick()
        composeTestRule.waitForIdle()

        // Verify custom model and endpoint fields are hidden under 100% offline mode
        composeTestRule.onNodeWithText("Model Name").assertDoesNotExist()
        composeTestRule.onNodeWithText("Endpoint URL").assertDoesNotExist()
    }
}
