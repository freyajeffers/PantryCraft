package com.jeffers.pantrycraft.ui

import android.app.KeyguardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
class PantryItemRowComposeTest {

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
    fun pantryItemRowIntegerUnitsLowStockAndActionCallbacks() {
        var decrementCalled = false
        var incrementCalled = false
        var editCalled = false
        var deleteCalled = false
        var markOpenedCalled = false
        var addToGroceryCalled = false

        val item = PantryItem(
            id = 1L,
            name = "Organic Eggs",
            quantity = 2f,
            unit = "COUNT",
            threshold = 6f,
            isOpened = false,
            storageLocation = "FRIDGE",
        )
        val actions = PantryItemRowActions(
            onDecrement = { decrementCalled = true },
            onIncrement = { incrementCalled = true },
            onUse = {},
            onEdit = { editCalled = true },
            onMarkOpened = { markOpenedCalled = true },
            onAddToGrocery = { addToGroceryCalled = true },
            onDelete = { deleteCalled = true },
        )

        composeTestRule.setContent {
            PantryCraftTheme {
                PantryItemRow(item = item, actions = actions)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Organic Eggs").assertIsDisplayed()
        composeTestRule.onNodeWithText("• Low Stock").assertIsDisplayed()

        composeTestRule.onNodeWithText("Open").performClick()
        assertTrue(markOpenedCalled)

        composeTestRule.onNodeWithContentDescription("Add to grocery list").performClick()
        assertTrue(addToGroceryCalled)

        composeTestRule.onNodeWithContentDescription("Increase quantity").performClick()
        assertTrue(incrementCalled)

        composeTestRule.onNodeWithContentDescription("Decrease quantity").performClick()
        assertTrue(decrementCalled)

        composeTestRule.onNodeWithText("Edit").performClick()
        assertTrue(editCalled)

        composeTestRule.onNodeWithText("Delete").performClick()
        assertTrue(deleteCalled)
    }

    @Test
    fun pantryItemRowDecimalUnitsAndOpenedBadge() {
        var useCalled = false
        val item = PantryItem(
            id = 2L,
            name = "Olive Oil",
            quantity = 0.75f,
            unit = "LITERS",
            threshold = 0f,
            isOpened = true,
            storageLocation = "PANTRY",
        )
        val actions = PantryItemRowActions(
            onDecrement = {},
            onIncrement = {},
            onUse = { useCalled = true },
            onEdit = {},
            onMarkOpened = {},
            onAddToGrocery = {},
            onDelete = {},
        )

        composeTestRule.setContent {
            PantryCraftTheme {
                PantryItemRow(item = item, actions = actions)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Olive Oil").assertIsDisplayed()
        composeTestRule.onNodeWithText("• Opened").assertIsDisplayed()
        composeTestRule.onNodeWithText("Use").performClick()
        assertTrue(useCalled)
    }

    @Test
    fun usePantryItemDialogInputAndConfirmDismiss() {
        var confirmedAmount = 0f
        var dismissed = false
        var currentAmount = "1.5"

        val item = PantryItem(
            id = 3L,
            name = "Sugar",
            quantity = 5f,
            unit = "KG",
        )

        composeTestRule.setContent {
            PantryCraftTheme {
                UsePantryItemDialog(
                    item = item,
                    amount = currentAmount,
                    onAmountChange = { currentAmount = it },
                    onConfirm = { confirmedAmount = it },
                    onDismiss = { dismissed = true },
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Use Sugar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Use").performClick()
        assertEquals(1.5f, confirmedAmount)

        composeTestRule.onNodeWithText("Cancel").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun pantryScreenOverlaysDisplaysUseDialog() {
        var confirmedUse = 0f
        val item = PantryItem(id = 4L, name = "Flour", quantity = 2f, unit = "CUSTOM", customUnitName = "cups")
        val props = PantryScreenOverlayProps(
            showAddDialog = false,
            editorItem = null,
            onSaveDraft = { _, _ -> },
            onDismissEditor = {},
            useItem = item,
            useAmount = "0.5",
            onAmountChange = {},
            onConfirmUse = { confirmedUse = it },
            onDismissUse = {},
            storageError = null,
            onDismissStorageError = {},
        )

        composeTestRule.setContent {
            PantryCraftTheme {
                PantryScreenOverlays(props)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Use Flour").assertIsDisplayed()
        composeTestRule.onNodeWithText("Amount (cups)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Use").performClick()
        assertEquals(0.5f, confirmedUse)
    }
}
