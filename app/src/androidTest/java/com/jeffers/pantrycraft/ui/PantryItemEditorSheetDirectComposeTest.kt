package com.jeffers.pantrycraft.ui

import android.app.KeyguardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.pantrycraft.data.PantryItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PantryItemEditorSheetDirectComposeTest {

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
    fun editorSheetNewItemEntersNameAndSaves() {
        var savedIsNew: Boolean? = null
        var savedItem: PantryItem? = null
        var dismissed = false

        composeTestRule.setContent {
            PantryCraftTheme {
                PantryItemEditorSheet(
                    editorItem = null,
                    onSaveDraft = { isNew, item ->
                        savedIsNew = isNew
                        savedItem = item
                    },
                    onDismiss = { dismissed = true },
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Add pantry item").assertIsDisplayed()
        composeTestRule.onNodeWithText("Name").performTextInput("Almond Milk")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Save").performClick()
        composeTestRule.waitForIdle()

        assertEquals(true, savedIsNew)
        assertEquals("Almond Milk", savedItem?.name)
        assertTrue(dismissed)
    }

    @Test
    fun editorSheetExistingItemSavesAndDismisses() {
        val existing = PantryItem(
            id = 55L,
            name = "Pearl Barley",
            quantity = 2f,
            unit = "KG",
            storageLocation = "PANTRY",
        )
        var savedIsNew: Boolean? = null
        var savedItem: PantryItem? = null

        composeTestRule.setContent {
            PantryCraftTheme {
                PantryItemEditorSheet(
                    editorItem = existing,
                    onSaveDraft = { isNew, item ->
                        savedIsNew = isNew
                        savedItem = item
                    },
                    onDismiss = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Edit pantry item").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save").performClick()
        composeTestRule.waitForIdle()

        assertEquals(false, savedIsNew)
        assertEquals(55L, savedItem?.id)
        assertEquals("Pearl Barley", savedItem?.name)
    }

    @Test
    fun editorSheetCancelTriggersDismiss() {
        var dismissed = false

        composeTestRule.setContent {
            PantryCraftTheme {
                PantryItemEditorSheet(
                    editorItem = null,
                    onSaveDraft = { _, _ -> },
                    onDismiss = { dismissed = true },
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitForIdle()
        assertTrue(dismissed)
    }
}
