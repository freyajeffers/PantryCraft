package com.jeffers.pantrycraft.feature.camera.ui

import android.app.KeyguardManager
import android.content.Context
import android.graphics.Paint
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.jeffers.pantrycraft.data.Repository
import com.jeffers.pantrycraft.data.offlineBarcodeResolver
import com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionAnalyzer
import com.jeffers.pantrycraft.feature.camera.barcode.BarcodeMatch
import com.jeffers.pantrycraft.feature.camera.core.CameraManager
import com.jeffers.pantrycraft.feature.camera.vision.LocalVisionModel
import com.jeffers.pantrycraft.feature.camera.vision.ParsedVisionItem
import com.jeffers.pantrycraft.feature.camera.vision.SnapshotVisionService
import com.jeffers.pantrycraft.ui.PantryCraftTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
class CameraQuickAddComposeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        Assume.assumeTrue("Screen is locked; skipping Compose test", km?.isKeyguardLocked == false)
        cleanUpDatabase(context)
    }

    @After
    fun tearDown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        cleanUpDatabase(context)
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    private fun cleanUpDatabase(context: Context) {
        kotlinx.coroutines.runBlocking {
            com.jeffers.pantrycraft.data.AppDatabase
                .getDatabase(context)
                .pantryDao()
                .deleteByBarcodePrefix("9988776655")
        }
    }

    @Test
    fun cameraQuickAddCardRendersAndQuickAdds() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val repository = Repository.getInstance(context)
        val match = BarcodeMatch(
            barcode = "9988776655",
            name = "Organic Rolled Oats",
            category = "GRAINS",
            unit = "G",
            estimatedShelfLifeDays = 365,
        )
        var dismissed = false
        var confirmed = false
        var statusMsg: String? = null

        composeTestRule.setContent {
            PantryCraftTheme {
                Box {
                    val scope = rememberCoroutineScope()
                    val actions = CameraQuickAddActions(
                        repository = repository,
                        scope = scope,
                        onDismiss = { dismissed = true },
                        onReview = {},
                        onStatus = { statusMsg = it },
                        onConfirm = { confirmed = true },
                    )
                    CameraQuickAddCard(match = match, actions = actions)
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Organic Rolled Oats").assertIsDisplayed()
        composeTestRule.onNodeWithText("Quick Add (1)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Quick Add (1)").performClick()
        composeTestRule.waitUntil(5000) { confirmed }

        assertTrue(dismissed)
        assertTrue(confirmed)
        assertNotNull(statusMsg)
        assertTrue(statusMsg!!.contains("Added Organic Rolled Oats"))
    }

    @Test
    fun cameraQuickAddCardReviewInvokesCallback() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val repository = Repository.getInstance(context)
        val match = BarcodeMatch("11223344", "Cold Brew", "BEVERAGES", "ML", 30)
        var reviewedItem: ParsedVisionItem? = null
        var dismissed = false

        composeTestRule.setContent {
            PantryCraftTheme {
                Box {
                    val scope = rememberCoroutineScope()
                    val actions = CameraQuickAddActions(
                        repository = repository,
                        scope = scope,
                        onDismiss = { dismissed = true },
                        onReview = { reviewedItem = it },
                        onStatus = {},
                        onConfirm = {},
                    )
                    CameraQuickAddCard(match = match, actions = actions)
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Review").assertIsDisplayed()
        composeTestRule.onNodeWithText("Review").performClick()
        composeTestRule.waitForIdle()

        assertTrue(dismissed)
        assertNotNull(reviewedItem)
        assertEquals("Cold Brew", reviewedItem?.name)
        assertEquals("11223344", reviewedItem?.barcode)
    }

    @Test
    fun cameraQuickAddCardDismissInvokesCallback() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val repository = Repository.getInstance(context)
        val match = BarcodeMatch("55667788", "Greek Yogurt", "DAIRY", "G", 14)
        var dismissed = false

        composeTestRule.setContent {
            PantryCraftTheme {
                Box {
                    val scope = rememberCoroutineScope()
                    val actions = CameraQuickAddActions(
                        repository = repository,
                        scope = scope,
                        onDismiss = { dismissed = true },
                        onReview = {},
                        onStatus = {},
                        onConfirm = {},
                    )
                    CameraQuickAddCard(match = match, actions = actions)
                }
            }
        }
        composeTestRule.waitForIdle()

        val dismissButton = composeTestRule.onNodeWithContentDescription("Dismiss barcode")
        dismissButton.assertIsDisplayed()
        dismissButton.performClick()
        composeTestRule.waitForIdle()

        assertTrue(dismissed)
    }

    @Test
    fun cameraOverlayControlsRendersHeaderAndCards() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val repository = Repository.getInstance(context)
        val executor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient()
        val options = ObjectDetectorOptions.Builder().build()
        val detector = ObjectDetection.getClient(options)
        val analyzer = LiveVisionAnalyzer(scanner, detector, executor, {})
        val cameraManager = CameraManager(context, analyzer, executor)
        val snapshotService = SnapshotVisionService(LocalVisionModel { _, _ -> "{}" })
        val resolver = repository.offlineBarcodeResolver()
        val match = BarcodeMatch("77889900", "Dark Chocolate", "SNACKS", "G", 180)
        var dismissed = false

        composeTestRule.setContent {
            PantryCraftTheme {
                Box {
                    val scope = rememberCoroutineScope()
                    val args = CameraOverlayArgs(
                        latestResult = null,
                        focusPoint = null,
                        labelPaint = Paint(),
                        badgePaint = Paint(),
                        cameraManager = cameraManager,
                        repository = repository,
                        scope = scope,
                        barcodeMatch = match,
                        unresolvedBarcode = null,
                        associateDialogBarcode = null,
                        parsedItems = emptyList(),
                        snapshotVisionService = snapshotService,
                        barcodeResolver = resolver,
                        isTorchOn = false,
                        onToggleTorch = {},
                        onClearBarcodeMatch = { dismissed = true },
                        onParsedItemsChange = {},
                        onUnresolvedBarcodeChange = {},
                        onAssociateDialogBarcodeChange = {},
                        onSnapshotStatusChange = {},
                        view = View(context),
                    )
                    CameraOverlayControls(args)
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Dark Chocolate").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Dismiss barcode").performClick()
        composeTestRule.waitForIdle()
        assertTrue(dismissed)

        analyzer.close()
        snapshotService.close()
        executor.shutdown()
    }
}
