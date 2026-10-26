package com.jeffers.pantrycraft.ui

import android.app.KeyguardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.pantrycraft.data.AppDatabase
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.Repository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenDirectComposeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repository: Repository
    private val prefix = "settings-direct-test-${System.nanoTime()}-"

    @Before
    fun setUp() {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        Assume.assumeTrue("Screen is locked; skipping Compose UI test", km?.isKeyguardLocked == false)
        repository = Repository.getInstance(context)
        cleanupTestData()
    }

    @After
    fun tearDown() {
        cleanupTestData()
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    private fun cleanupTestData() = runBlocking {
        val db = AppDatabase.getDatabase(context)
        db.pantryDao().deleteByNamePrefix(prefix)
    }

    @Test
    fun settingsScreenRendersWithUserDataPresent() = runBlocking {
        val item = PantryItem(name = "${prefix}Granola", quantity = 2f, unit = "BOX")
        repository.insertPantry(item)

        composeTestRule.setContent {
            PantryCraftTheme {
                SettingsScreen(context = context, repository = repository)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("FoodFacts barcode catalog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Notifications & Alerts").assertIsDisplayed()
        composeTestRule.onNodeWithText("Data Sovereignty & Portability").performScrollTo().assertIsDisplayed()
        composeTestRule
            .onNodeWithText("AI Vision & Meal Engine (BYOKey / Local)")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun settingsScreenRendersWithEmptyDatabase() {
        composeTestRule.setContent {
            PantryCraftTheme {
                SettingsScreen(context = context, repository = repository)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Data Sovereignty & Portability").performScrollTo().assertIsDisplayed()
    }
}
