package com.jeffers.pantrycraft.feature.camera.barcode

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineBarcodeAssetTest {
    @Test
    fun catalogIsNotPackagedAndCanBeDownloadedOptionally() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val topLevelAssets = context.assets.list("").orEmpty()
        assertFalse(topLevelAssets.contains("foodfacts.db"))
    }
}
