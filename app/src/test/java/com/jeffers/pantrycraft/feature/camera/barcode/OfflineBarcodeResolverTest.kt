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

    @Test
    fun rememberValidatesAndPersistsBarcodeItem() = runBlocking {
        val dao = FakeDao()
        val resolver = OfflineBarcodeResolver(dao)
        val validItem = CustomBarcodeItem(barcode = "123456789012", name = "Test Item")
        resolver.remember(validItem)
        assertEquals(validItem, dao.findByBarcode("123456789012"))

        try {
            resolver.remember(CustomBarcodeItem(barcode = "short", name = "Test"))
            org.junit.Assert.fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected for invalid barcode
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun resolveBarcodeRejectsFifteenDigitBarcode() = runBlocking {
        val resolver = OfflineBarcodeResolver(FakeDao())
        resolver.resolveBarcode("123456789012345")
        Unit
    }

    @Test
    fun reloadCatalogInvokesProviderAndClosesPrevious() {
        var providerCalls = 0
        var closedCalls = 0
        val fakeDb = FakeBarcodeDatabase(
            onClose = { closedCalls++ },
        )
        val resolver = OfflineBarcodeResolver(
            customBarcodeDao = FakeDao(),
            assetBarcodeDatabase = fakeDb,
            assetBarcodeDatabaseProvider = {
                providerCalls++
                null
            },
        )

        resolver.reloadCatalog()
        assertEquals(1, providerCalls)
        assertEquals(1, closedCalls)
    }

    @Test
    fun resolvesFromAssetDatabaseWithBrandFormatting() = runBlocking {
        val fakeDb = FakeBarcodeDatabase(
            matches = mapOf(
                "111122223333" to EmbeddedBarcodeMatch(
                    barcode = "111122223333",
                    name = "Olive Oil",
                    brand = "Bertolli",
                    category = "OILS",
                    shelfLifeDays = 365,
                ),
                "444455556666" to EmbeddedBarcodeMatch(
                    barcode = "444455556666",
                    name = "Bertolli Pasta Sauce",
                    brand = "Bertolli",
                    category = "SAUCES",
                    shelfLifeDays = 180,
                ),
                "777788889999" to EmbeddedBarcodeMatch(
                    barcode = "777788889999",
                    name = "Sea Salt",
                    brand = "",
                    category = "SPICES",
                    shelfLifeDays = 720,
                ),
            ),
        )

        val resolver = OfflineBarcodeResolver(
            customBarcodeDao = FakeDao(),
            assetBarcodeDatabase = fakeDb,
        )

        val match1 = resolver.resolveBarcode("111122223333")
        assertEquals("Bertolli Olive Oil", match1?.name)
        assertEquals("OILS", match1?.category)
        assertEquals("COUNT", match1?.unit)
        assertEquals(365, match1?.estimatedShelfLifeDays)

        val match2 = resolver.resolveBarcode("444455556666")
        assertEquals("Bertolli Pasta Sauce", match2?.name)

        val match3 = resolver.resolveBarcode("777788889999")
        assertEquals("Sea Salt", match3?.name)
    }

    @Test
    fun lazyProviderInitializesAssetDatabaseAndCloses() = runBlocking {
        var providerCalls = 0
        var closedCalls = 0
        val fakeDb = FakeBarcodeDatabase(
            matches = mapOf(
                "12345678" to EmbeddedBarcodeMatch(
                    barcode = "12345678",
                    name = "Apples",
                    brand = "",
                    category = "PRODUCE",
                    shelfLifeDays = 14,
                ),
            ),
            onClose = { closedCalls++ },
        )

        val resolver = OfflineBarcodeResolver(
            customBarcodeDao = FakeDao(),
            assetBarcodeDatabaseProvider = {
                providerCalls++
                fakeDb
            },
        )

        val match = resolver.resolveBarcode("12345678")
        assertEquals("Apples", match?.name)
        assertEquals(1, providerCalls)

        resolver.close()
        assertEquals(1, closedCalls)
    }

    @Test
    fun reloadCatalogAndCloseHandleExceptionsGracefully() {
        val crashingDb = object : BarcodeCatalogDatabase {
            override fun findByBarcode(barcode: String) = null
            override fun close() {
                error("Close failed")
            }
        }
        val resolver = OfflineBarcodeResolver(
            customBarcodeDao = FakeDao(),
            assetBarcodeDatabase = crashingDb,
            assetBarcodeDatabaseProvider = {
                error("Provider failed")
            },
        )

        resolver.reloadCatalog()
        resolver.close()

        val resolverWithoutDb = OfflineBarcodeResolver(
            customBarcodeDao = FakeDao(),
            assetBarcodeDatabaseProvider = { null },
        )
        resolverWithoutDb.reloadCatalog()
        resolverWithoutDb.close()
    }

    @Test
    fun activeDatabaseHandlesProviderExceptionWhenResolving() = runBlocking {
        val resolver = OfflineBarcodeResolver(
            customBarcodeDao = FakeDao(),
            assetBarcodeDatabaseProvider = {
                error("Lazy load failed")
            },
        )

        assertNull(resolver.resolveBarcode("12345678"))
    }

    @Test
    fun associateBarcodeWithDefaultsUsesStandardTaxonomyDefaults() = runBlocking {
        val resolver = OfflineBarcodeResolver(FakeDao())
        val match = resolver.associateBarcode("12345678", "Whole Milk")

        assertEquals("Whole Milk", match.name)
        assertEquals("OTHER", match.category)
        assertEquals("COUNT", match.unit)
        assertEquals(0, match.estimatedShelfLifeDays)
    }

    @Test
    fun closeHandlesCrashingDatabaseGracefully() {
        val crashingDb = object : BarcodeCatalogDatabase {
            override fun findByBarcode(barcode: String) = null
            override fun close() {
                error("Close failed")
            }
        }
        val resolver = OfflineBarcodeResolver(
            customBarcodeDao = FakeDao(),
            assetBarcodeDatabase = crashingDb,
        )
        resolver.close()
    }

    private class FakeBarcodeDatabase(
        private val matches: Map<String, EmbeddedBarcodeMatch> = emptyMap(),
        private val onClose: () -> Unit = {},
    ) : BarcodeCatalogDatabase {
        override fun findByBarcode(barcode: String): EmbeddedBarcodeMatch? = matches[barcode]
        override fun close() = onClose()
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

