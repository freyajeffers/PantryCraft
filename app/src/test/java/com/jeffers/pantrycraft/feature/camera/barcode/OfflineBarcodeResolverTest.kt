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

    private class FakeDao(
        private var item: CustomBarcodeItem? = null,
    ) : CustomBarcodeDao {
        override suspend fun findByBarcode(barcode: String) = item?.takeIf { it.barcode == barcode }

        override suspend fun upsert(item: CustomBarcodeItem) {
            this.item = item
        }
    }
}
