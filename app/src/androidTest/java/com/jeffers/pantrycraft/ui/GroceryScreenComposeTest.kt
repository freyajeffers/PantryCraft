package com.jeffers.pantrycraft.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.pantrycraft.MainActivity
import com.jeffers.pantrycraft.data.AppDatabase
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jeffers.pantrycraft.data.GroceryItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroceryScreenComposeTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()
    private var previouslyCheckedItems: List<GroceryItem> = emptyList()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        org.junit.Assume.assumeTrue(
            "Screen is locked; skipping Compose UI test",
            km?.isKeyguardLocked == false,
        )

        runBlocking {
            val dao = AppDatabase.getDatabase(context).groceryDao()
            dao.deleteByNamePrefix("Organic Honey Honey")
            previouslyCheckedItems = dao.observeAllGroceryItems().first().filter { it.checked }
            previouslyCheckedItems.forEach { dao.update(it.copy(checked = false)) }
        }
    }

    @After
    fun tearDown() {
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.DESTROYED)
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking {
            val dao = AppDatabase.getDatabase(context).groceryDao()
            dao.deleteByNamePrefix("Organic Honey Honey")
            previouslyCheckedItems.forEach { dao.update(it) }
        }
    }



    @Test
    fun addCheckAndClearGroceryItemFlow() {
        composeTestRule.waitForIdle()

        // 1. Navigate to Grocery destination
        composeTestRule.onNodeWithContentDescription("Grocery").performClick()
        composeTestRule.waitForIdle()

        // 2. Open Add Grocery Item dialog
        composeTestRule.onNodeWithContentDescription("Add grocery item").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Add Grocery Item").assertIsDisplayed()

        // 3. Enter item name and add item
        val testItemName = "Organic Honey Honey"
        composeTestRule.onNodeWithText("Item Name").performTextInput(testItemName)
        composeTestRule.waitForIdle()

        // Click Add in dialog (not the FAB)
        composeTestRule
            .onNode(hasText("Add") and !hasContentDescription("Add grocery item"))
            .performClick()
        composeTestRule.waitForIdle()

        // 4. Verify item is displayed in list
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(testItemName).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(testItemName).assertIsDisplayed()

        // 5. Toggle checkbox to mark as checked
        composeTestRule.onNode(isToggleable()).performClick()
        composeTestRule.waitForIdle()

        // 6. Top bar should now show clear checked items action
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodes(hasContentDescription("Clear checked items")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule
            .onNodeWithContentDescription("Clear checked items")
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()

        // 7. Verify item is removed
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(testItemName).fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithText(testItemName).assertDoesNotExist()
    }
}
