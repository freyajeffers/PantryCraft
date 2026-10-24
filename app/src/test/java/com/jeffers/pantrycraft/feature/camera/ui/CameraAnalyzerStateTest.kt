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
    fun dispatchBarcodeResolution_whenMatchFound_invokesOnBarcodeResolvedAndHaptic() = runBlocking {
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
    fun dispatchBarcodeResolution_whenBarcodeUnresolved_invokesOnBarcodeUnresolved() = runBlocking {
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
    fun dispatchBarcodeResolution_whenResolverThrows_invokesOnBarcodeUnresolvedGracefully() = runBlocking {
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
    fun dispatchBarcodeResolution_withDefaultHapticCallbackDoesNotThrow() = runBlocking {
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
