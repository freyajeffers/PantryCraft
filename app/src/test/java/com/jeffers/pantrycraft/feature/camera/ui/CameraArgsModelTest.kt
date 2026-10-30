package com.jeffers.pantrycraft.feature.camera.ui

import android.content.Context
import android.graphics.Paint
import android.view.View
import androidx.camera.view.PreviewView
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.LifecycleOwner
import com.jeffers.pantrycraft.data.Repository
import com.jeffers.pantrycraft.feature.camera.analyzer.FrameTimingStats
import com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionAnalyzer
import com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionResult
import com.jeffers.pantrycraft.feature.camera.barcode.BarcodeMatch
import com.jeffers.pantrycraft.feature.camera.barcode.OfflineBarcodeResolver
import com.jeffers.pantrycraft.feature.camera.core.CameraManager
import com.jeffers.pantrycraft.feature.camera.vision.SnapshotVisionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraArgsModelTest {
    @Test
    fun cameraDepsDataClassContract() {
        val context = DummyContext()
        val repo = createStub<Repository>()
        val executor = Executors.newSingleThreadExecutor()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val previewView = createStub<PreviewView>()
        val resolver = createStub<OfflineBarcodeResolver>()
        val snapshotService = createStub<SnapshotVisionService>()
        val stats = FrameTimingStats()

        val deps1 =
            CameraDeps(
                context = context,
                repository = repo,
                analysisExecutor = executor,
                scope = scope,
                previewView = previewView,
                barcodeResolver = resolver,
                snapshotVisionService = snapshotService,
                frameTimingStats = stats,
            )

        assertEquals(context, deps1.component1())
        assertEquals(repo, deps1.component2())
        assertEquals(executor, deps1.component3())
        assertEquals(scope, deps1.component4())
        assertEquals(previewView, deps1.component5())
        assertEquals(resolver, deps1.component6())
        assertEquals(snapshotService, deps1.component7())
        assertEquals(stats, deps1.component8())

        assertEquals(context, deps1.context)
        assertEquals(repo, deps1.repository)
        assertEquals(executor, deps1.analysisExecutor)
        assertEquals(scope, deps1.scope)
        assertEquals(previewView, deps1.previewView)
        assertEquals(resolver, deps1.barcodeResolver)
        assertEquals(snapshotService, deps1.snapshotVisionService)
        assertEquals(stats, deps1.frameTimingStats)

        val deps2 = deps1.copy(frameTimingStats = FrameTimingStats())
        assertNotNull(deps2)
        assertNotNull(deps1.toString())
        executor.shutdown()
    }

    @Test
    fun cameraLifecycleArgsDataClassContract() {
        val cameraManager = createStub<CameraManager>()
        val lifecycleOwner = DummyLifecycleOwner()
        val previewView = createStub<PreviewView>()
        val analyzer = createStub<LiveVisionAnalyzer>()
        val resolver = createStub<OfflineBarcodeResolver>()
        val snapshotService = createStub<SnapshotVisionService>()
        val executor = Executors.newSingleThreadExecutor()

        val args1 =
            CameraLifecycleArgs(
                cameraManager = cameraManager,
                lifecycleOwner = lifecycleOwner,
                previewView = previewView,
                analyzer = analyzer,
                barcodeResolver = resolver,
                snapshotVisionService = snapshotService,
                analysisExecutor = executor,
            )

        assertEquals(cameraManager, args1.component1())
        assertEquals(lifecycleOwner, args1.component2())
        assertEquals(previewView, args1.component3())
        assertEquals(analyzer, args1.component4())
        assertEquals(resolver, args1.component5())
        assertEquals(snapshotService, args1.component6())
        assertEquals(executor, args1.component7())

        assertEquals(cameraManager, args1.cameraManager)
        assertEquals(lifecycleOwner, args1.lifecycleOwner)
        assertEquals(previewView, args1.previewView)
        assertEquals(analyzer, args1.analyzer)
        assertEquals(resolver, args1.barcodeResolver)
        assertEquals(snapshotService, args1.snapshotVisionService)
        assertEquals(executor, args1.analysisExecutor)

        val args2 = args1.copy(analyzer = createStub<LiveVisionAnalyzer>())
        assertNotNull(args2)
        assertNotNull(args1.toString())
        executor.shutdown()
    }

    @Test
    fun cameraScreenBoxArgsDataClassContract() {
        val previewView = createStub<PreviewView>()
        val labelPaint = createStub<Paint>()
        val badgePaint = createStub<Paint>()
        val cameraManager = createStub<CameraManager>()
        val repo = createStub<Repository>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val snapshotService = createStub<SnapshotVisionService>()
        val resolver = createStub<OfflineBarcodeResolver>()
        val view = createStub<View>()

        val boxArgs1 =
            CameraScreenBoxArgs(
                previewView = previewView,
                latestResult = null,
                focusPoint = Offset(10f, 20f),
                labelPaint = labelPaint,
                badgePaint = badgePaint,
                cameraManager = cameraManager,
                repository = repo,
                scope = scope,
                barcodeMatch = null,
                unresolvedBarcode = null,
                associateDialogBarcode = null,
                parsedItems = emptyList(),
                snapshotVisionService = snapshotService,
                barcodeResolver = resolver,
                isTorchOn = false,
                onToggleTorch = {},
                onClearBarcodeMatch = {},
                onParsedItemsChange = {},
                onUnresolvedBarcodeChange = {},
                onAssociateDialogBarcodeChange = {},
                onSnapshotStatusChange = {},
                view = view,
            )

        assertEquals(previewView, boxArgs1.component1())
        assertEquals(Offset(10f, 20f), boxArgs1.component3())
        assertEquals(repo, boxArgs1.component7())
        assertEquals(view, boxArgs1.component22())
        verifyBoxGetters(boxArgs1)

        val boxArgs2 = boxArgs1.copy(isTorchOn = true)
        assertNotEquals(boxArgs1, boxArgs2)
        assertNotNull(boxArgs1.toString())
    }

    @Test
    fun cameraOverlayArgsDataClassContract() {
        val labelPaint = createStub<Paint>()
        val badgePaint = createStub<Paint>()
        val cameraManager = createStub<CameraManager>()
        val repo = createStub<Repository>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val snapshotService = createStub<SnapshotVisionService>()
        val resolver = createStub<OfflineBarcodeResolver>()
        val view = createStub<View>()

        val overlayArgs1 =
            CameraOverlayArgs(
                latestResult = null,
                focusPoint = null,
                labelPaint = labelPaint,
                badgePaint = badgePaint,
                cameraManager = cameraManager,
                repository = repo,
                scope = scope,
                barcodeMatch = BarcodeMatch("123", "Item", "OTHER", "COUNT", 0),
                unresolvedBarcode = "123",
                associateDialogBarcode = null,
                parsedItems = emptyList(),
                snapshotVisionService = snapshotService,
                barcodeResolver = resolver,
                isTorchOn = true,
                onToggleTorch = {},
                onClearBarcodeMatch = {},
                onParsedItemsChange = {},
                onUnresolvedBarcodeChange = {},
                onAssociateDialogBarcodeChange = {},
                onSnapshotStatusChange = {},
                view = view,
            )

        assertEquals(labelPaint, overlayArgs1.component3())
        assertEquals(badgePaint, overlayArgs1.component4())
        assertEquals(repo, overlayArgs1.component6())
        assertEquals("123", overlayArgs1.component8()?.barcode)
        assertEquals(view, overlayArgs1.component21())
        verifyOverlayGetters(overlayArgs1)

        val overlayArgs2 = overlayArgs1.copy(isTorchOn = false)
        assertNotEquals(overlayArgs1, overlayArgs2)
        assertNotNull(overlayArgs1.toString())
    }

    @Test
    fun cameraRuntimeContract() {
        val analyzer = createStub<LiveVisionAnalyzer>()
        val manager = createStub<CameraManager>()
        val runtime = CameraRuntime(analyzer, manager)
        assertEquals(analyzer, runtime.analyzer)
        assertEquals(manager, runtime.cameraManager)
    }

    @Test
    fun cameraStatusHudStateContract() {
        var torchToggled = false
        val state =
            CameraStatusHudState(
                latestResult = null,
                latestFrameTime = 25L,
                barcodeMatch = null,
                snapshotStatus = "Analyzing",
                isTorchOn = true,
                onToggleTorch = { torchToggled = true },
            )

        assertEquals(25L, state.latestFrameTime)
        assertEquals("Analyzing", state.snapshotStatus)
        assertEquals(true, state.isTorchOn)
        org.junit.Assert.assertNull(state.latestResult)
        org.junit.Assert.assertNull(state.barcodeMatch)

        state.onToggleTorch()
        org.junit.Assert.assertTrue(torchToggled)

        val copied = state.copy(isTorchOn = false)
        assertEquals(false, copied.isTorchOn)
        assertNotEquals(state, copied)
        assertEquals(state.hashCode(), state.copy().hashCode())
        org.junit.Assert.assertTrue(state.toString().contains("CameraStatusHudState"))
    }

    @Test
    fun cameraQuickAddActionsContract() {
        var dismissed = false
        var statusMsg = ""
        var confirmed = false
        var reviewedItem: com.jeffers.pantrycraft.feature.camera.vision.ParsedVisionItem? = null

        val actions =
            CameraQuickAddActions(
                repository = createStub<Repository>(),
                scope = CoroutineScope(Dispatchers.Unconfined),
                onDismiss = { dismissed = true },
                onReview = { reviewedItem = it },
                onStatus = { statusMsg = it },
                onConfirm = { confirmed = true },
            )

        assertNotNull(actions.repository)
        assertNotNull(actions.scope)

        actions.onDismiss()
        actions.onStatus("Ready")
        actions.onConfirm()

        val parsed =
            com.jeffers.pantrycraft.feature.camera.vision.ParsedVisionItem(
                name = "Milk",
                category = "DAIRY",
                storageLocation = "FRIDGE",
                quantity = 1f,
                unit = "BOTTLE",
                confidence = 0.95f,
                estimatedShelfLifeDays = 7,
                isAromaticOrSeasoning = false,
                boundingBox =
                    com.jeffers.pantrycraft.feature.camera.vision
                        .NormalizedBox(0f, 0f, 1f, 1f),
                conditionNotes = null,
            )
        actions.onReview(parsed)

        org.junit.Assert.assertTrue(dismissed)
        org.junit.Assert.assertTrue(confirmed)
        assertEquals("Ready", statusMsg)
        assertEquals(parsed, reviewedItem)
        assertEquals(actions, actions.copy())
        assertEquals(actions.hashCode(), actions.copy().hashCode())
        org.junit.Assert.assertTrue(actions.toString().contains("CameraQuickAddActions"))
    }

    @Test
    fun associateBarcodeFieldsStateContract() {
        var nameInput = ""
        var catInput = ""
        var unitInput = ""
        var locInput = ""
        var daysInput = ""

        val state =
            AssociateBarcodeFieldsState(
                barcode = "998877",
                name = "Apples",
                onNameChange = { nameInput = it },
                category = "PRODUCE",
                onCategoryChange = { catInput = it },
                unit = "COUNT",
                onUnitChange = { unitInput = it },
                storageLocation = "FRIDGE",
                onStorageLocationChange = { locInput = it },
                shelfLifeDaysText = "14",
                onShelfLifeChange = { daysInput = it },
            )

        assertEquals("998877", state.barcode)
        assertEquals("Apples", state.name)
        assertEquals("PRODUCE", state.category)
        assertEquals("COUNT", state.unit)
        assertEquals("FRIDGE", state.storageLocation)
        assertEquals("14", state.shelfLifeDaysText)

        state.onNameChange("Gala Apples")
        state.onCategoryChange("FRUIT")
        state.onUnitChange("KG")
        state.onStorageLocationChange("PANTRY")
        state.onShelfLifeChange("21")

        assertEquals("Gala Apples", nameInput)
        assertEquals("FRUIT", catInput)
        assertEquals("KG", unitInput)
        assertEquals("PANTRY", locInput)
        assertEquals("21", daysInput)

        assertEquals(state, state.copy())
        assertEquals(state.hashCode(), state.copy().hashCode())
        org.junit.Assert.assertTrue(state.toString().contains("AssociateBarcodeFieldsState"))
    }

    private fun verifyBoxGetters(b: CameraScreenBoxArgs) {
        assertEquals(Offset(10f, 20f), b.focusPoint)
        assertEquals(false, b.isTorchOn)
        assertNotNull(b.previewView)
        assertNotNull(b.repository)
        assertNotNull(b.snapshotVisionService)
        assertNotNull(b.barcodeResolver)
        assertNotNull(b.view)
        org.junit.Assert.assertNull(b.latestResult)
        org.junit.Assert.assertNull(b.barcodeMatch)
        org.junit.Assert.assertNull(b.unresolvedBarcode)
        org.junit.Assert.assertNull(b.associateDialogBarcode)
        assertNotNull(b.labelPaint)
        assertNotNull(b.badgePaint)
        assertNotNull(b.cameraManager)
        assertNotNull(b.scope)
        assertNotNull(b.parsedItems)
        assertNotNull(b.onToggleTorch)
        assertNotNull(b.onClearBarcodeMatch)
        assertNotNull(b.onParsedItemsChange)
        assertNotNull(b.onUnresolvedBarcodeChange)
        assertNotNull(b.onAssociateDialogBarcodeChange)
        assertNotNull(b.onSnapshotStatusChange)
    }

    private fun verifyOverlayGetters(o: CameraOverlayArgs) {
        assertEquals("123", o.unresolvedBarcode)
        assertEquals(true, o.isTorchOn)
        assertNotNull(o.repository)
        assertNotNull(o.barcodeMatch)
        assertNotNull(o.snapshotVisionService)
        assertNotNull(o.barcodeResolver)
        assertNotNull(o.view)
        org.junit.Assert.assertNull(o.latestResult)
        org.junit.Assert.assertNull(o.focusPoint)
        org.junit.Assert.assertNull(o.associateDialogBarcode)
        assertNotNull(o.labelPaint)
        assertNotNull(o.badgePaint)
        assertNotNull(o.cameraManager)
        assertNotNull(o.scope)
        assertNotNull(o.parsedItems)
        assertNotNull(o.onToggleTorch)
        assertNotNull(o.onClearBarcodeMatch)
        assertNotNull(o.onParsedItemsChange)
        assertNotNull(o.onUnresolvedBarcodeChange)
        assertNotNull(o.onAssociateDialogBarcodeChange)
        assertNotNull(o.onSnapshotStatusChange)
    }

    private inline fun <reified T : Any> createStub(): T {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val field = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = field.get(null)
        val allocate = unsafeClass.getMethod("allocateInstance", Class::class.java)
        return allocate.invoke(unsafe, T::class.java) as T
    }

    private class DummyContext : android.content.ContextWrapper(null)

    private class DummyLifecycleOwner : LifecycleOwner {
        override val lifecycle: androidx.lifecycle.Lifecycle
            get() = error("Dummy lifecycle")
    }
}
