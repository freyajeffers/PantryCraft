package com.jeffers.pantrycraft.ui

import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.pantrycraft.MainActivity
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenComposeTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        org.junit.Assume.assumeTrue(
            "Screen is locked; skipping Compose UI test",
            km?.isKeyguardLocked == false,
        )
    }

    @After
    fun tearDown() {
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    @Test
    fun dashboardDisplaysMetricsAndNavigatesToPantryAndGrocery() {
        composeTestRule.waitForIdle()

        // 1. Navigate to Dashboard destination
        composeTestRule.onNodeWithContentDescription("Dashboard").performClick()
        composeTestRule.waitForIdle()

        // 2. Verify Home header and key sections
        composeTestRule.onNodeWithText("PantryCraft").assertIsDisplayed()
        composeTestRule.onNodeWithText("Minimalist, Local-First AI Kitchen Assistant").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("In Stock").assertIsDisplayed().assertHasClickAction()
        composeTestRule.onNodeWithContentDescription("Expiring").assertIsDisplayed().assertHasClickAction()
        composeTestRule.onNodeWithContentDescription("Low Stock").assertIsDisplayed().assertHasClickAction()
        composeTestRule.onNodeWithText("Quick Ingestion").assertIsDisplayed()
        composeTestRule.onNodeWithText("Grocery Checklist").assertIsDisplayed()
        composeTestRule.onNodeWithText("AI Meal Synthesis (Phase 3)").assertIsDisplayed()

        // 3. Click "Add Item" button under Quick Ingestion -> Navigates to Pantry
        composeTestRule.onNodeWithText("Add Item").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Add pantry item").assertIsDisplayed()

        // 4. Navigate back to Dashboard
        composeTestRule.onNodeWithContentDescription("Dashboard").performClick()
        composeTestRule.waitForIdle()

        // 5. Click "View List" under Grocery Checklist -> Navigates to Grocery
        composeTestRule.onNodeWithText("View List").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Grocery list").assertIsDisplayed()
    }
}
