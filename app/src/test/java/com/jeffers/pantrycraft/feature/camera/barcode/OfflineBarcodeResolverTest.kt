package com.jeffers.pantrycraft.feature.camera.barcode

import com.jeffers.pantrycraft.data.CustomBarcodeDao
import com.jeffers.pantrycraft.data.CustomBarcodeItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineBarcodeResolverTest {
    @Test
    fun resolvesEmbeddedProductAndDebouncesDuplicate() =
        runBlocking {
            val resolver = OfflineBarcodeResolver(FakeDao())

            assertEquals("Coca-Cola", resolver.resolveBarcode("012000001056")?.name)
            assertNull(resolver.resolveBarcode("012000001056"))
        }

    @Test
    fun customCacheTakesPrecedence() =
        runBlocking {
            val resolver =
                OfflineBarcodeResolver(
                    FakeDao(
                        CustomBarcodeItem("012000001056", "My Cola"),
                    ),
                )

            assertEquals("My Cola", resolver.resolveBarcode("012000001056")?.name)
        }

    @Test
    fun associateBarcodePersistsAndResolves() =
        runBlocking {
            val dao = FakeDao()
            val resolver = OfflineBarcodeResolver(dao)

            val match =
                resolver.associateBarcode(
                    "999999999999",
                    "Special Blend Coffee",
                    "BEVERAGES",
                    "PACKAGE",
                    180,
                )
            assertEquals("Special Blend Coffee", match.name)
            assertEquals("BEVERAGES", match.category)
            assertEquals("PACKAGE", match.unit)
            assertEquals(180, match.estimatedShelfLifeDays)

            val stored = dao.findByBarcode("999999999999")
            assertEquals("Special Blend Coffee", stored?.name)
        }

    @Test(expected = IllegalArgumentException::class)
    fun invalidBarcodeFormatThrowsException() =
        runBlocking {
            val resolver = OfflineBarcodeResolver(FakeDao())
            resolver.resolveBarcode("invalid-alpha-code")
            Unit
        }

    @Test(expected = IllegalArgumentException::class)
    fun shortBarcodeFormatThrowsException() =
        runBlocking {
            val resolver = OfflineBarcodeResolver(FakeDao())
            resolver.resolveBarcode("123")
            Unit
        }

    @Test
    fun unknownBarcodeReturnsNull() =
        runBlocking {
            val resolver = OfflineBarcodeResolver(FakeDao())
            assertNull(resolver.resolveBarcode("888888888888"))
        }

    @Test
    fun closeClosesCleanlyAndRepeatedly() {
        val resolver = OfflineBarcodeResolver(FakeDao())
        resolver.close()
        resolver.close()
    }

    @Test
    fun associateBarcodeDefaultsEmptyCategoryAndUnitAndCoercesNegativeShelfLife() = runBlocking {
        val dao = FakeDao()
        val resolver = OfflineBarcodeResolver(dao)

        val match = resolver.associateBarcode(
            barcode = "  123456789012  ",
            name = "  Organic Apples  ",
            category = "   ",
            defaultUnit = "   ",
            estimatedShelfLifeDays = -5,
        )

        assertEquals("123456789012", match.barcode)
        assertEquals("Organic Apples", match.name)
        assertEquals("OTHER", match.category)
        assertEquals("COUNT", match.unit)
        assertEquals(0, match.estimatedShelfLifeDays)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rememberRejectsInvalidBarcodeFormat() = runBlocking {
        val resolver = OfflineBarcodeResolver(FakeDao())
        resolver.remember(CustomBarcodeItem(barcode = "short", name = "Test"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun resolveBarcodeRejectsFifteenDigitBarcode() = runBlocking {
        val resolver = OfflineBarcodeResolver(FakeDao())
        resolver.resolveBarcode("123456789012345")
        Unit
    }

    @Test
    fun reloadCatalogInvokesProvider() {
        var providerCalls = 0
        val resolver = OfflineBarcodeResolver(
            customBarcodeDao = FakeDao(),
            assetBarcodeDatabaseProvider = {
                providerCalls++
                null
            },
        )

        resolver.reloadCatalog()
        assertEquals(1, providerCalls)
    }

    private class FakeDao(
        private var item: CustomBarcodeItem? = null,
    ) : CustomBarcodeDao {
        override suspend fun findByBarcode(barcode: String) = item?.takeIf { it.barcode == barcode }

        override suspend fun upsert(item: CustomBarcodeItem) {
            this.item = item
        }

        override suspend fun delete(item: CustomBarcodeItem) {
            if (this.item?.barcode == item.barcode) this.item = null
        }

        override suspend fun deleteByBarcodePrefix(prefix: String): Int =
            if (this.item?.barcode?.startsWith(prefix) == true) {
                this.item = null
                1
            } else {
                0
            }
    }
}

