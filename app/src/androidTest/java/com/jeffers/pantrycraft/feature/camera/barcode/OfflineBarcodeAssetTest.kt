package com.jeffers.pantrycraft.feature.camera.barcode

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineBarcodeAssetTest {
    @Test
    fun packagedCatalogResolvesOffline() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AssetBarcodeDatabase(context).use { database ->
            val product = database.findByBarcode("0000101209159")
            assertNotNull(product)
            assertEquals("0000101209159", product?.barcode)
            assertTrue(product?.name?.isNotBlank() == true)
        }
    }
}
