package com.jeffers.pantrycraft.feature.camera.vision

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotVisionServiceTest {
    @Test
    fun analyzeParsesModelOutputAndZerosFrameMemory() =
        runBlocking {
            val sampleResponse =
                """
                {
                  "detected_items": [
                    {
                      "name": "Avocado",
                      "category": "PRODUCE",
                      "storage_location": "COUNTER",
                      "quantity": 2,
                      "unit": "COUNT",
                      "confidence": 0.95,
                      "estimated_shelf_life_days": 5,
                      "is_aromatic_or_seasoning": false,
                      "bounding_box": [0.1, 0.2, 0.4, 0.5],
                      "condition_notes": "Ripe"
                    }
                  ]
                }
                """.trimIndent()

            val model = LocalVisionModel { _, _ -> sampleResponse }
            val service = SnapshotVisionService(model)

            val frameBytes = byteArrayOf(10, 20, 30, 40, 50)
            val frame = SnapshotFrame(frameBytes)

            val items = service.analyze(frame)

            assertEquals(1, items.size)
            assertEquals("Avocado", items.first().name)
            assertEquals("PRODUCE", items.first().category)
            assertEquals(2f, items.first().quantity)

            // Verify Ephemeral Media invariant: bytes zeroed out immediately after inference
            assertTrue("Frame bytes must be zeroed after inference", frameBytes.all { it == 0.toByte() })
        }

    @Test
    fun analyzeZerosFrameMemoryEvenWhenModelThrows() =
        runBlocking {
            val failingModel =
                LocalVisionModel { _, _ ->
                    throw IllegalStateException("Model inference failed")
                }
            val service = SnapshotVisionService(failingModel)

            val frameBytes = byteArrayOf(99, 88, 77, 66)
            val frame = SnapshotFrame(frameBytes)

            var errorCaught = false
            try {
                service.analyze(frame)
            } catch (_: IllegalStateException) {
                errorCaught = true
            }

            assertTrue(errorCaught)
            assertTrue("Frame bytes must be zeroed even on inference error", frameBytes.all { it == 0.toByte() })
        }

    @Test
    fun closeClosesUnderlyingAutoCloseableModel() {
        var closed = false
        val closeableModel =
            object : LocalVisionModel, AutoCloseable {
                override suspend fun analyze(
                    webpBytes: ByteArray,
                    prompt: String,
                ): String = "{}"

                override fun close() {
                    closed = true
                }
            }

        val service = SnapshotVisionService(closeableModel)
        service.close()

        assertTrue(closed)
    }

    @Test
    fun snapshotFrameDirectCloseZerosMemory() {
        val frameBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val frame = SnapshotFrame(frameBytes)
        frame.close()
        assertTrue("SnapshotFrame.close() must scrub internal byte buffer", frameBytes.all { it == 0.toByte() })
    }

    @Test
    fun closeDoesNotThrowWhenModelIsNotAutoCloseable() {
        val nonCloseableModel = LocalVisionModel { _, _ -> "{}" }
        val service = SnapshotVisionService(nonCloseableModel)
        service.close()
    }

    @Test
    fun analyzeReturnsEmptyListWhenNoItemsDetected() =
        runBlocking {
            val model = LocalVisionModel { _, _ -> """{"detected_items": []}""" }
            val service = SnapshotVisionService(model)
            val frameBytes = byteArrayOf(11, 22, 33)
            val items = service.analyze(SnapshotFrame(frameBytes))

            assertTrue(items.isEmpty())
            assertTrue("Frame memory must be zeroed even when no items detected", frameBytes.all { it == 0.toByte() })
        }

    @Test
    fun promptConstantContainsExpectedInstructions() {
        val prompt = SnapshotVisionService.PROMPT
        assertTrue(prompt.contains("detected_items"))
        assertTrue(prompt.contains("bounding_box"))
    }
}
