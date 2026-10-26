package com.jeffers.pantrycraft.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.pantrycraft.MainActivity
import com.jeffers.pantrycraft.data.AppDatabase
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.Repository
import kotlinx.coroutines.runBlocking
import android.app.KeyguardManager
import org.junit.Assume
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PantryItemEditorSheetTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val testPrefix = "editor-test-"
    private val testItemName = "${testPrefix}espresso"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        Assume.assumeTrue(
            "Screen is locked; skipping Compose UI test",
            km?.isKeyguardLocked == false,
        )

        runBlocking {
            val dao = AppDatabase.getDatabase(context).pantryDao()
            dao.deleteByNamePrefix(testPrefix)
            Repository.getInstance(context).insertPantry(
                PantryItem(
                    name = testItemName,
                    quantity = 2f,
                    storageLocation = "PANTRY",
                    category = "BEVERAGES",
                ),
            )
        }
    }

    @After
    fun tearDown() {
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.DESTROYED)
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking {
            AppDatabase.getDatabase(context).pantryDao().deleteByNamePrefix(testPrefix)
        }
    }

    @Test
    fun addPantryItemFabOpensNewItemEditorSheet() {
        composeTestRule.waitForIdle()

        // 1. Click FAB to add pantry item
        composeTestRule
            .onNodeWithContentDescription("Add pantry item")
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()

        // 2. Verify sheet title is displayed
        composeTestRule.onNodeWithText("Add pantry item").assertIsDisplayed()
    }

    @Test
    fun existingPantryItemCanBeEditedFromList() {
        composeTestRule.waitForIdle()

        // 1. Verify inserted item is visible in list
        composeTestRule.onNodeWithText(testItemName).assertIsDisplayed()

        // 2. Open edit item sheet via the Edit text button on the row
        composeTestRule.onAllNodesWithText("Edit")[0].assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // 3. Verify Edit pantry item sheet appears with the item details
        composeTestRule.onNodeWithText("Edit pantry item").assertIsDisplayed()
        composeTestRule.onAllNodesWithText(testItemName)[0].assertIsDisplayed()
    }
}
