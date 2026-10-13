package com.jeffers.pantrycraft.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class DatabaseKeyProviderTest {
    @Test
    fun keyMaterialIsStableAnd256Bit() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = DatabaseKeyProvider.getKey(context)
        val second = DatabaseKeyProvider.getKey(context)

        assertEquals(32, first.size)
        assertArrayEquals(first, second)
    }
}
