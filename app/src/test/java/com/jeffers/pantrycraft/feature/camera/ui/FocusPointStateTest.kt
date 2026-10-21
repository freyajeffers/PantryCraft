package com.jeffers.pantrycraft.feature.camera.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FocusPointStateTest {
    @Test
    fun defaultFocusPointIsNull() {
        val state = FocusPointState()
        assertNull(state.value)
    }

    @Test
    fun updateFocusPointStoresOffset() {
        val state = FocusPointState()
        val offset = Offset(150.5f, 320.0f)
        state.value = offset

        assertEquals(offset, state.value)
        assertEquals(150.5f, state.value?.x)
        assertEquals(320.0f, state.value?.y)

        state.value = null
        assertNull(state.value)
    }
}
