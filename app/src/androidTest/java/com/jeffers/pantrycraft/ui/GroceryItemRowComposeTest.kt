package com.jeffers.pantrycraft.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.pantrycraft.data.GroceryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroceryItemRowComposeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun groceryItemRowRendersAndDeletes() {
        var deleted = false
        var checkedState: Boolean? = null
        val item = GroceryItem(
            name = "Almond Butter",
            quantity = 1f,
            unit = "JAR",
            checked = false,
        )

        composeTestRule.setContent {
            PantryCraftTheme {
                GroceryItemRow(
                    item = item,
                    onChecked = { checkedState = it },
                    onDelete = { deleted = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Almond Butter").assertIsDisplayed()
        composeTestRule.onNodeWithText("1.0 JAR").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Delete Almond Butter").performClick()
        assertTrue(deleted)

        composeTestRule.onNode(isToggleable()).performClick()
        assertEquals(true, checkedState)
    }

    @Test
    fun groceryItemRowRendersCheckedState() {
        var checkedState: Boolean? = null
        val item = GroceryItem(
            name = "Rolled Oats",
            quantity = 2f,
            unit = "BAG",
            checked = true,
        )

        composeTestRule.setContent {
            PantryCraftTheme {
                GroceryItemRow(
                    item = item,
                    onChecked = { checkedState = it },
                    onDelete = {},
                )
            }
        }

        composeTestRule.onNode(isToggleable()).performClick()
        assertEquals(false, checkedState)
    }

    @Test
    fun groceryTopBarRendersActionsWhenChecked() {
        var restocked = false
        var cleared = false

        composeTestRule.setContent {
            PantryCraftTheme {
                GroceryTopBar(
                    checkedCount = 2,
                    onRestock = { restocked = true },
                    onClear = { cleared = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Grocery list").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Restock checked items to pantry").performClick()
        assertTrue(restocked)

        composeTestRule.onNodeWithContentDescription("Clear checked items").performClick()
        assertTrue(cleared)
    }

    @Test
    fun groceryListContentEmptyRendersPlaceholder() {
        composeTestRule.setContent {
            PantryCraftTheme {
                GroceryListContent(
                    items = emptyList(),
                    padding = PaddingValues(0.dp),
                    onChecked = { _, _ -> },
                    onDelete = {},
                )
            }
        }

        composeTestRule.onNodeWithText(
            "Your grocery list is empty. Tap Add to create items or replenish from low stock.",
        ).assertIsDisplayed()
    }

    @Test
    fun addGroceryItemDialogRendersAndDismisses() {
        var dismissed = false

        composeTestRule.setContent {
            PantryCraftTheme {
                AddGroceryItemDialog(
                    onDismiss = { dismissed = true },
                    onAdd = { _, _, _ -> },
                )
            }
        }

        composeTestRule.onNodeWithText("Add Grocery Item").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
        assertTrue(dismissed)
    }
}
