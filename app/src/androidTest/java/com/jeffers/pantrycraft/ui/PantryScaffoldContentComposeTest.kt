package com.jeffers.pantrycraft.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.pantrycraft.data.PantryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PantryScaffoldContentComposeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pantryScaffoldContentRendersFabAndInvokesAdd() {
        var addClicked = false
        var sortSelected: PantrySortOrder? = null

        composeTestRule.setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            val props = PantryScaffoldProps(
                selectedSortOrder = PantrySortOrder.NAME_ASC,
                onSortSelected = { sortSelected = it },
                onAddClick = { addClicked = true },
                items = emptyList(),
                selectedLocation = PantryLocationFilter.ALL,
                searchQuery = "",
                onSearchQueryChange = {},
                onLocationSelected = {},
                onDecrement = {},
                onIncrement = {},
                onUse = {},
                onEdit = {},
                onMarkOpened = {},
                onAddToGrocery = {},
                onDelete = {},
                onRestore = {},
                snackbarHostState = snackbarHostState,
                scope = scope,
            )
            PantryCraftTheme {
                PantryScaffoldContent(props = props)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Add pantry item").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertTrue(addClicked)

        composeTestRule.onNodeWithContentDescription("Sort pantry items").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Recently updated").performClick()
        composeTestRule.waitForIdle()
        assertEquals(PantrySortOrder.RECENTLY_UPDATED, sortSelected)
    }

    @Test
    fun pantryScaffoldContentRendersItemsAndItemActions() {
        var incrementItem: PantryItem? = null
        var decrementItem: PantryItem? = null

        val sampleItem = PantryItem(
            id = 42L,
            name = "Greek Yogurt",
            quantity = 3f,
            unit = "COUNT",
            storageLocation = "FRIDGE",
            category = "DAIRY",
        )

        composeTestRule.setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            val props = PantryScaffoldProps(
                selectedSortOrder = PantrySortOrder.NAME_ASC,
                onSortSelected = {},
                onAddClick = {},
                items = listOf(sampleItem),
                selectedLocation = PantryLocationFilter.ALL,
                searchQuery = "",
                onSearchQueryChange = {},
                onLocationSelected = {},
                onDecrement = { decrementItem = it },
                onIncrement = { incrementItem = it },
                onUse = {},
                onEdit = {},
                onMarkOpened = {},
                onAddToGrocery = {},
                onDelete = {},
                onRestore = {},
                snackbarHostState = snackbarHostState,
                scope = scope,
            )
            PantryCraftTheme {
                PantryScaffoldContent(props = props)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Greek Yogurt").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Increase Greek Yogurt quantity").performClick()
        composeTestRule.waitForIdle()
        assertEquals(42L, incrementItem?.id)

        composeTestRule.onNodeWithContentDescription("Decrease Greek Yogurt quantity").performClick()
        composeTestRule.waitForIdle()
        assertEquals(42L, decrementItem?.id)
    }
}
