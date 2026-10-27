package com.jeffers.pantrycraft.ui

import android.app.KeyguardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
class PantryComponentsComposeTest {

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
    fun pantryListContentRendersEmptyState() {
        composeTestRule.setContent {
            PantryCraftTheme {
                PantryListContent(
                    props = PantryListContentProps(
                        items = emptyList(),
                        selectedLocation = PantryLocationFilter.ALL,
                        searchQuery = "",
                        onSearchQueryChange = {},
                        onLocationSelected = {},
                        itemContent = {},
                    ),
                )
            }
        }

        composeTestRule.onNodeWithText("Your pantry is empty.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Search pantry").assertIsDisplayed()
    }

    @Test
    fun pantryListContentRendersItemsAndInteractions() {
        var queryInput = ""
        var selectedFilter: PantryLocationFilter? = null
        val items = listOf(
            PantryItem(id = 1L, name = "Basmati Rice", quantity = 2f, unit = "KG"),
        )

        composeTestRule.setContent {
            PantryCraftTheme {
                PantryListContent(
                    props = PantryListContentProps(
                        items = items,
                        selectedLocation = PantryLocationFilter.ALL,
                        searchQuery = queryInput,
                        onSearchQueryChange = { queryInput = it },
                        onLocationSelected = { selectedFilter = it },
                        itemContent = { item -> Text("Item: ${item.name}") },
                    ),
                )
            }
        }

        composeTestRule.onNodeWithText("Item: Basmati Rice").assertIsDisplayed()
        composeTestRule.onNodeWithText("Fridge").performClick()
        assertEquals(PantryLocationFilter.FRIDGE, selectedFilter)

        composeTestRule.onNodeWithText("Search pantry").performTextInput("rice")
        assertEquals("rice", queryInput)
    }

    @Test
    fun pantryTopBarOpensMenuAndSelectsSortOrder() {
        var chosenSort: PantrySortOrder? = null

        var sortOrder by mutableStateOf(PantrySortOrder.NAME_ASC)
        var topBarTitle by mutableStateOf("My Pantry")

        composeTestRule.setContent {
            PantryCraftTheme {
                PantryTopBar(
                    title = topBarTitle,
                    selectedSortOrder = sortOrder,
                    onSortSelected = { chosenSort = it },
                )
            }
        }

        composeTestRule.onNodeWithText("My Pantry").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Sort pantry items").performClick()

        composeTestRule.onNodeWithText("✓ Name (A-Z)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Expiring soonest").performClick()
        assertEquals(PantrySortOrder.EXPIRATION_SOONEST, chosenSort)

        topBarTitle = "Pantry"
        sortOrder = PantrySortOrder.CATEGORY
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Pantry").assertIsDisplayed()
    }

    @Test
    fun storageDiagnosticDialogDisplaysAndDismisses() {
        var dismissed = false

        composeTestRule.setContent {
            PantryCraftTheme {
                Box {
                    StorageDiagnosticDialog(
                        message = "Disk full error occurred",
                        onDismiss = { dismissed = true },
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Storage space required").assertIsDisplayed()
        composeTestRule.onNodeWithText("OK").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun pantryScreenOverlaysRendersStorageDiagnostic() {
        var errorDismissed = false
        val props = PantryScreenOverlayProps(
            showAddDialog = false,
            editorItem = null,
            onSaveDraft = { _, _ -> },
            onDismissEditor = {},
            useItem = null,
            useAmount = "",
            onAmountChange = {},
            onConfirmUse = {},
            onDismissUse = {},
            storageError = "Low disk space",
            onDismissStorageError = { errorDismissed = true },
        )

        composeTestRule.setContent {
            PantryCraftTheme {
                Box {
                    PantryScreenOverlays(props)
                }
            }
        }

        composeTestRule.onNodeWithText("Storage space required").assertIsDisplayed()
        composeTestRule.onNodeWithText("OK").performClick()
        assertTrue(errorDismissed)
    }
}
