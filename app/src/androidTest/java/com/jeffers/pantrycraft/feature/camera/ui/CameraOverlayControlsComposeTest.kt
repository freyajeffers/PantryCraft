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
import com.jeffers.pantrycraft.data.AppDatabase
import com.jeffers.pantrycraft.data.Repository
import com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionAnalyzer
import com.jeffers.pantrycraft.feature.camera.barcode.BarcodeMatch
import com.jeffers.pantrycraft.feature.camera.barcode.OfflineBarcodeResolver
import com.jeffers.pantrycraft.feature.camera.core.CameraManager
import com.jeffers.pantrycraft.feature.camera.vision.LocalVisionModel
import com.jeffers.pantrycraft.feature.camera.vision.NormalizedBox
import com.jeffers.pantrycraft.feature.camera.vision.ParsedVisionItem
import com.jeffers.pantrycraft.feature.camera.vision.SnapshotVisionService
import com.jeffers.pantrycraft.ui.PantryCraftTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
class CameraOverlayControlsComposeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private lateinit var executor: ExecutorService
    private lateinit var repository: Repository
    private lateinit var cameraManager: CameraManager
    private lateinit var snapshotVisionService: SnapshotVisionService
    private lateinit var barcodeResolver: OfflineBarcodeResolver
    private lateinit var analyzer: LiveVisionAnalyzer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        Assume.assumeTrue("Screen is locked; skipping Compose UI test", km?.isKeyguardLocked == false)

        executor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient()
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .build()
        val detector = ObjectDetection.getClient(options)
        analyzer = LiveVisionAnalyzer(scanner, detector, executor, {})
        cameraManager = CameraManager(context, analyzer, executor)
        val dummyModel = LocalVisionModel { _, _ -> "{\"detected_items\":[]}" }
        snapshotVisionService = SnapshotVisionService(dummyModel)
        repository = Repository.getInstance(context)
        val db = AppDatabase.getDatabase(context)
        barcodeResolver = OfflineBarcodeResolver(db.customBarcodeDao())
    }

    @After
    fun tearDown() {
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.DESTROYED)
        analyzer.close()
        snapshotVisionService.close()
        barcodeResolver.close()
        executor.shutdown()
    }

    private fun buildOverlayArgs(
        scope: kotlinx.coroutines.CoroutineScope,
        barcodeMatch: BarcodeMatch? = null,
        unresolvedBarcode: String? = null,
        onTorchToggle: () -> Unit = {},
        onAssociate: (String?) -> Unit = {},
    ): CameraOverlayArgs = CameraOverlayArgs(
        latestResult = null,
        focusPoint = null,
        labelPaint = Paint(),
        badgePaint = Paint(),
        cameraManager = cameraManager,
        repository = repository,
        scope = scope,
        barcodeMatch = barcodeMatch,
        unresolvedBarcode = unresolvedBarcode,
        associateDialogBarcode = null,
        parsedItems = emptyList(),
        snapshotVisionService = snapshotVisionService,
        barcodeResolver = barcodeResolver,
        isTorchOn = false,
        onToggleTorch = onTorchToggle,
        onClearBarcodeMatch = {},
        onParsedItemsChange = {},
        onUnresolvedBarcodeChange = {},
        onAssociateDialogBarcodeChange = onAssociate,
        onSnapshotStatusChange = {},
        view = View(context),
    )

    @Test
    fun cameraOverlayControlsRendersHeaderAndTogglesTorch() {
        var torchToggled = false

        composeTestRule.setContent {
            PantryCraftTheme {
                Box {
                    val scope = rememberCoroutineScope()
                    val args = buildOverlayArgs(scope, onTorchToggle = { torchToggled = true })
                    CameraOverlayControls(args)
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Capture shelf for scanning").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Turn torch on").performClick()
        assertTrue(torchToggled)
    }

    @Test
    fun cameraOverlayControlsRendersUnknownBarcodeCardAndInvokesAssociate() {
        var associatedBarcode: String? = null

        composeTestRule.setContent {
            PantryCraftTheme {
                Box {
                    val scope = rememberCoroutineScope()
                    val args = buildOverlayArgs(
                        scope = scope,
                        unresolvedBarcode = "987654321098",
                        onAssociate = { associatedBarcode = it },
                    )
                    CameraOverlayControls(args)
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Unknown barcode: 987654321098").assertIsDisplayed()
        composeTestRule.onNodeWithText("Name item").performClick()
        assertEquals("987654321098", associatedBarcode)
    }

    @Test
    fun cameraOverlayControlsRendersQuickAddMatch() {
        val match = BarcodeMatch(
            barcode = "111222333",
            name = "Organic Rolled Oats",
            category = "GRAINS",
            unit = "BOX",
            estimatedShelfLifeDays = 365,
        )

        composeTestRule.setContent {
            PantryCraftTheme {
                Box {
                    val scope = rememberCoroutineScope()
                    val args = buildOverlayArgs(scope = scope, barcodeMatch = match)
                    CameraOverlayControls(args)
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Organic Rolled Oats").assertIsDisplayed()
    }

    @Test
    fun cameraOverlayControlsRendersAssociationDialogAndDismisses() {
        var dismissedAssociate: String? = "initial"

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
                        barcodeMatch = null,
                        unresolvedBarcode = null,
                        associateDialogBarcode = "777888",
                        parsedItems = emptyList(),
                        snapshotVisionService = snapshotVisionService,
                        barcodeResolver = barcodeResolver,
                        isTorchOn = false,
                        onToggleTorch = {},
                        onClearBarcodeMatch = {},
                        onParsedItemsChange = {},
                        onUnresolvedBarcodeChange = {},
                        onAssociateDialogBarcodeChange = { dismissedAssociate = it },
                        onSnapshotStatusChange = {},
                        view = View(context),
                    )
                    CameraOverlayControls(args)
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Name Scanned Barcode").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
        assertEquals(null, dismissedAssociate)
    }

    @Test
    fun cameraOverlayControlsRendersBatchReviewDialogAndDismisses() {
        var updatedItems: List<ParsedVisionItem>? = null
        val item = ParsedVisionItem(
            name = "Baby Spinach",
            category = "PRODUCE",
            storageLocation = "FRIDGE",
            quantity = 1f,
            unit = "BAG",
            confidence = 0.9f,
            estimatedShelfLifeDays = 7,
            isAromaticOrSeasoning = false,
            boundingBox = NormalizedBox(0f, 0f, 1f, 1f),
            conditionNotes = null,
        )

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
                        barcodeMatch = null,
                        unresolvedBarcode = null,
                        associateDialogBarcode = null,
                        parsedItems = listOf(item),
                        snapshotVisionService = snapshotVisionService,
                        barcodeResolver = barcodeResolver,
                        isTorchOn = false,
                        onToggleTorch = {},
                        onClearBarcodeMatch = {},
                        onParsedItemsChange = { updatedItems = it },
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

        composeTestRule.onNodeWithText("Review captured items").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
        assertEquals(emptyList<ParsedVisionItem>(), updatedItems)
    }
}
