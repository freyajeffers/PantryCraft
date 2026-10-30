package com.jeffers.pantrycraft.feature.camera.ui

import com.jeffers.pantrycraft.data.CustomBarcodeDao
import com.jeffers.pantrycraft.data.CustomBarcodeItem
import com.jeffers.pantrycraft.feature.camera.barcode.BarcodeMatch
import com.jeffers.pantrycraft.feature.camera.barcode.OfflineBarcodeResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CameraAnalyzerStateTest {
    @Test
    fun dispatchBarcodeResolution_whenMatchFound_invokesOnBarcodeResolvedAndHaptic() =
        runBlocking {
            val resolver = OfflineBarcodeResolver(FakeCustomBarcodeDao())
            var resolvedMatch: BarcodeMatch? = null
            var unresolvedCode: String? = null
            var hapticTicks = 0

            dispatchBarcodeResolution(
                barcode = "012000001056",
                barcodeResolver = resolver,
                onBarcodeResolved = { resolvedMatch = it },
                onBarcodeUnresolved = { unresolvedCode = it },
                onHapticFeedback = { hapticTicks++ },
            )

            assertNotNull(resolvedMatch)
            assertEquals("Coca-Cola", resolvedMatch?.name)
            assertEquals(1, hapticTicks)
            assertNull(unresolvedCode)
        }

    @Test
    fun dispatchBarcodeResolution_whenBarcodeUnresolved_invokesOnBarcodeUnresolved() =
        runBlocking {
            val resolver = OfflineBarcodeResolver(FakeCustomBarcodeDao())
            var resolvedMatch: BarcodeMatch? = null
            var unresolvedCode: String? = null
            var hapticTicks = 0

            dispatchBarcodeResolution(
                barcode = "888888888888",
                barcodeResolver = resolver,
                onBarcodeResolved = { resolvedMatch = it },
                onBarcodeUnresolved = { unresolvedCode = it },
                onHapticFeedback = { hapticTicks++ },
            )

            assertNull(resolvedMatch)
            assertEquals("888888888888", unresolvedCode)
            assertEquals(0, hapticTicks)
        }

    @Test
    fun dispatchBarcodeResolution_whenResolverThrows_invokesOnBarcodeUnresolvedGracefully() =
        runBlocking {
            val resolver = OfflineBarcodeResolver(FakeCustomBarcodeDao())
            var resolvedMatch: BarcodeMatch? = null
            var unresolvedCode: String? = null
            var hapticTicks = 0

            dispatchBarcodeResolution(
                barcode = "non-numeric-code",
                barcodeResolver = resolver,
                onBarcodeResolved = { resolvedMatch = it },
                onBarcodeUnresolved = { unresolvedCode = it },
                onHapticFeedback = { hapticTicks++ },
            )

            assertNull(resolvedMatch)
            assertEquals("non-numeric-code", unresolvedCode)
            assertEquals(0, hapticTicks)
        }

    @Test
    fun dispatchBarcodeResolution_withDefaultHapticCallbackDoesNotThrow() =
        runBlocking {
            val resolver = OfflineBarcodeResolver(FakeCustomBarcodeDao())
            var resolvedMatch: BarcodeMatch? = null

            dispatchBarcodeResolution(
                barcode = "012000001056",
                barcodeResolver = resolver,
                onBarcodeResolved = { resolvedMatch = it },
                onBarcodeUnresolved = {},
            )

            assertNotNull(resolvedMatch)
        }

    @Test
    fun processLiveBarcodeResultDispatchesResolvedAndUnresolved() =
        runBlocking {
            val resolver = OfflineBarcodeResolver(FakeCustomBarcodeDao())
            val dummyContext = DummyContext()
            val dummyView = createStub<android.view.View>()
            val executor =
                java.util.concurrent.Executors
                    .newSingleThreadExecutor()
            val stats =
                com.jeffers.pantrycraft.feature.camera.analyzer
                    .FrameTimingStats()

            var resolvedMatch: BarcodeMatch? = null
            var unresolvedCode: String? = null

            val state =
                CameraAnalyzerState(
                    context = dummyContext,
                    view = dummyView,
                    executor = executor,
                    scope = this,
                    barcodeResolver = resolver,
                    frameTimingStats = stats,
                    onResult = {},
                    onFrameTime = {},
                    onBarcodeResolved = { resolvedMatch = it },
                    onBarcodeUnresolved = { unresolvedCode = it },
                )

            processLiveBarcodeResult(state, "012000001056")
            assertNotNull(resolvedMatch)
            assertEquals("Coca-Cola", resolvedMatch?.name)

            processLiveBarcodeResult(state, "999999999999")
            assertEquals("999999999999", unresolvedCode)

            executor.shutdown()
        }

    @Test
    fun cameraAnalyzerStateDataClassContract() {
        val resolver = OfflineBarcodeResolver(FakeCustomBarcodeDao())
        val dummyContext = DummyContext()
        val dummyView = createStub<android.view.View>()
        val executor =
            java.util.concurrent.Executors
                .newSingleThreadExecutor()
        val stats =
            com.jeffers.pantrycraft.feature.camera.analyzer
                .FrameTimingStats()

        val state1 =
            CameraAnalyzerState(
                context = dummyContext,
                view = dummyView,
                executor = executor,
                scope = kotlinx.coroutines.GlobalScope,
                barcodeResolver = resolver,
                frameTimingStats = stats,
                onResult = {},
                onFrameTime = {},
                onBarcodeResolved = {},
                onBarcodeUnresolved = {},
            )

        assertEquals(dummyContext, state1.component1())
        assertEquals(dummyView, state1.component2())
        assertEquals(executor, state1.component3())
        assertNotNull(state1.component4())
        assertEquals(resolver, state1.component5())
        assertEquals(stats, state1.component6())
        assertNotNull(state1.component7())
        assertNotNull(state1.component8())
        assertNotNull(state1.component9())
        assertNotNull(state1.component10())

        assertEquals(dummyContext, state1.context)
        assertEquals(dummyView, state1.view)
        assertEquals(executor, state1.executor)
        assertNotNull(state1.scope)
        assertEquals(resolver, state1.barcodeResolver)
        assertEquals(stats, state1.frameTimingStats)
        assertNotNull(state1.onResult)
        assertNotNull(state1.onFrameTime)
        assertNotNull(state1.onBarcodeResolved)
        assertNotNull(state1.onBarcodeUnresolved)

        val state2 =
            state1.copy(
                frameTimingStats =
                    com.jeffers.pantrycraft.feature.camera.analyzer
                        .FrameTimingStats(),
            )
        assertNotNull(state2)
        assertNotNull(state1.toString())
        assertEquals(state1, state1)
        assertEquals(state1.hashCode(), state1.hashCode())

        executor.shutdown()
    }

    private class DummyContext : android.content.ContextWrapper(null)

    private inline fun <reified T : Any> createStub(): T {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val field = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = field.get(null)
        val allocate = unsafeClass.getMethod("allocateInstance", Class::class.java)
        return allocate.invoke(unsafe, T::class.java) as T
    }

    private class FakeCustomBarcodeDao : CustomBarcodeDao {
        override suspend fun findByBarcode(barcode: String): CustomBarcodeItem? = null

        override suspend fun upsert(item: CustomBarcodeItem) {
            // No-op for test
        }

        override suspend fun delete(item: CustomBarcodeItem) {
            // No-op for test
        }

        override suspend fun deleteByBarcodePrefix(prefix: String): Int = 0
    }
}
