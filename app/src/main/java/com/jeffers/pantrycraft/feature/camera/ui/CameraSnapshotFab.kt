package com.jeffers.pantrycraft.feature.camera.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jeffers.pantrycraft.feature.camera.core.CameraManager
import com.jeffers.pantrycraft.feature.camera.vision.ParsedVisionItem
import com.jeffers.pantrycraft.feature.camera.vision.SnapshotVisionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun BoxScope.CameraSnapshotFab(
    cameraManager: CameraManager,
    snapshotVisionService: SnapshotVisionService,
    scope: CoroutineScope,
    onStatus: (String) -> Unit,
    onItems: (List<ParsedVisionItem>) -> Unit,
) {
    FloatingActionButton(
        onClick = {
            onStatus("Capturing shelf snapshot…")
            scope.launch {
                runCatching { snapshotVisionService.analyze(cameraManager.captureSnapshot()) }
                    .onSuccess {
                        onItems(it)
                        onStatus("Detected ${it.size} items from shelf")
                    }.onFailure {
                        onStatus("Snapshot failed: ${it.message ?: "Unknown error"}")
                    }
            }
        },
        modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
    ) {
        Icon(Icons.Filled.PhotoCamera, contentDescription = "Capture shelf for scanning")
    }
}
