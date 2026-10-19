package com.jeffers.pantrycraft.feature.camera.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.delay

@Composable
internal fun rememberCameraFocusPoint(): FocusPointState {
    val state = remember { FocusPointState() }
    LaunchedEffect(state.value) {
        if (state.value != null) {
            delay(FOCUS_CLEAR_DELAY_MILLIS)
            state.value = null
        }
    }
    return state
}

internal class FocusPointState {
    var value: Offset? by mutableStateOf(null)
}
