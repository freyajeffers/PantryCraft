package com.jeffers.pantrycraft.ui

import android.app.KeyguardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.pantrycraft.data.AppDatabase
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.Repository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenUnitComposeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repository: Repository
    private val prefix = "home-test-${System.nanoTime()}-"

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
    fun homeScreenRendersWithNullRepositoryAndNavigates() {
        var navigatedTarget = ""

        composeTestRule.setContent {
            PantryCraftTheme {
                HomeScreen(
                    repository = null,
                    onNavigate = { navigatedTarget = it },
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("PantryCraft").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pantry stock is healthy").assertIsDisplayed()

        composeTestRule.onNodeWithText("Scan Shelf").performClick()
        assertEquals("camera", navigatedTarget)

        composeTestRule.onNodeWithText("In Stock").performClick()
        assertEquals("pantry", navigatedTarget)

        composeTestRule.onNodeWithText("Expiring").performClick()
        assertEquals("pantry", navigatedTarget)

        composeTestRule.onNodeWithText("Low Stock").performClick()
        assertEquals("grocery", navigatedTarget)

        composeTestRule.onNodeWithText("Add Item").performClick()
        assertEquals("pantry", navigatedTarget)

        composeTestRule.onNodeWithText("View List").performClick()
        assertEquals("grocery", navigatedTarget)
    }

    @Test
    fun homeScreenDisplaysExpiredAndLowStockMetrics() = runBlocking {
        val now = System.currentTimeMillis()
        val expiredItem = PantryItem(
            name = "${prefix}ExpiredMilk",
            quantity = 1f,
            unit = "BOTTLE",
            expirationTimestamp = now - 100_000L,
        )
        val expiringItem = PantryItem(
            name = "${prefix}ExpiringYogurt",
            quantity = 1f,
            unit = "CUP",
            expirationTimestamp = now + 86_400_000L,
        )
        val lowStockItem = PantryItem(
            name = "${prefix}LowFlour",
            quantity = 1f,
            unit = "KG",
            threshold = 2f,
        )

        repository.insertPantry(expiredItem)
        repository.insertPantry(expiringItem)
        repository.insertPantry(lowStockItem)

        composeTestRule.setContent {
            PantryCraftTheme {
                HomeScreen(
                    repository = repository,
                    onNavigate = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("+1 expired").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 items are at or below threshold").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ready to scaffold with 3 ingredients currently in stock.").assertIsDisplayed()
    }
}
