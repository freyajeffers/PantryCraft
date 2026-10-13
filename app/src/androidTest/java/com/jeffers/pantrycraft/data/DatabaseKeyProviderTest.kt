package com.jeffers.pantrycraft.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class DatabaseKeyProviderTest {
    @Test
    fun keyMaterialIsStableAnd256Bit() {
        val first = DatabaseKeyProvider.getKey()
        val second = DatabaseKeyProvider.getKey()

        assertEquals(32, first.size)
        assertArrayEquals(first, second)
    }
}
