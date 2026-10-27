package com.jeffers.pantrycraft.feature.camera.ui

import com.jeffers.pantrycraft.feature.camera.vision.LocalVisionModel
import com.jeffers.pantrycraft.feature.camera.vision.ParsedVisionItem
import com.jeffers.pantrycraft.feature.camera.vision.SnapshotFrame
import com.jeffers.pantrycraft.feature.camera.vision.SnapshotVisionService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraSnapshotFabUnitTest {

    @Test
    fun executeSnapshotCaptureSuccessEmitsStatusAndItems() = runBlocking {
        val sampleResponse = """
            {
              "detected_items": [
                {
                  "name": "Organic Banana",
                  "category": "PRODUCE",
                  "storage_location": "COUNTER",
                  "quantity": 3,
                  "unit": "COUNT"
                }
              ]
            }
        """.trimIndent()
        val service = SnapshotVisionService(LocalVisionModel { _, _ -> sampleResponse })
        val statusList = mutableListOf<String>()
        val parsedItems = mutableListOf<ParsedVisionItem>()

        executeSnapshotCapture(
            snapshotProvider = { SnapshotFrame(byteArrayOf(1, 2, 3)) },
            snapshotVisionService = service,
            onStatus = { statusList.add(it) },
            onItems = { parsedItems.addAll(it) },
        )

        assertEquals("Capturing shelf snapshot…", statusList.first())
        assertEquals("Detected 1 items from shelf", statusList.last())
        assertEquals(1, parsedItems.size)
        assertEquals("Organic Banana", parsedItems.first().name)
    }

    @Test
    fun executeSnapshotCaptureFailureReportsErrorMessage() = runBlocking {
        val service = SnapshotVisionService(LocalVisionModel { _, _ ->
            throw IllegalStateException("Vision engine failed")
        })
        val statusList = mutableListOf<String>()
        val parsedItems = mutableListOf<ParsedVisionItem>()

        executeSnapshotCapture(
            snapshotProvider = { SnapshotFrame(byteArrayOf(1, 2, 3)) },
            snapshotVisionService = service,
            onStatus = { statusList.add(it) },
            onItems = { parsedItems.addAll(it) },
        )

        assertEquals("Capturing shelf snapshot…", statusList.first())
        assertEquals("Snapshot failed: Vision engine failed", statusList.last())
        assertTrue(parsedItems.isEmpty())
    }

    @Test
    fun executeSnapshotCaptureFailureWithoutMessageReportsUnknown() = runBlocking {
        val service = SnapshotVisionService(LocalVisionModel { _, _ ->
            throw IllegalStateException(null as String?)
        })
        val statusList = mutableListOf<String>()

        executeSnapshotCapture(
            snapshotProvider = { SnapshotFrame(byteArrayOf(1, 2, 3)) },
            snapshotVisionService = service,
            onStatus = { statusList.add(it) },
            onItems = {},
        )

        assertEquals("Snapshot failed: Unknown error", statusList.last())
    }

    @Test
    fun executeSnapshotCaptureProviderFailureReportsError() = runBlocking {
        val service = SnapshotVisionService(LocalVisionModel { _, _ -> "{}" })
        val statusList = mutableListOf<String>()

        executeSnapshotCapture(
            snapshotProvider = { error("Camera capture is not ready") },
            snapshotVisionService = service,
            onStatus = { statusList.add(it) },
            onItems = {},
        )

        assertEquals("Snapshot failed: Camera capture is not ready", statusList.last())
    }
}
