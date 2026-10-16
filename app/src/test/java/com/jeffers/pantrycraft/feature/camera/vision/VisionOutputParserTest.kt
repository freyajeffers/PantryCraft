package com.jeffers.pantrycraft.feature.camera.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionOutputParserTest {
    @Test
    fun parsesMarkdownWrappedAndClampsBoxes() {
        val result =
            VisionOutputParser().parse(
                """
                ```json
                {"detected_items":[{"name":"Milk","category":"dairy","storage_location":"fridge","quantity":1,"unit":"count","confidence":1.2,"bounding_box":[-1.0,0.2,0.8,2.0]}]}
                ```
                """.trimIndent(),
            )

        assertEquals(1, result.size)
        assertEquals("Milk", result.single().name)
        assertEquals(1f, result.single().confidence)
        assertEquals(0f, result.single().boundingBox.ymin)
        assertEquals(1f, result.single().boundingBox.xmax)
    }

    @Test
    fun parsesObjectFormattedBoundingBoxes() {
        val result =
            VisionOutputParser().parse(
                """
                {"detected_items":[{"name":"Greek Yogurt","category":"DAIRY","storage_location":"FRIDGE","quantity":1.0,"unit":"COUNT","confidence":0.95,"bounding_box":{"x_min":0.15,"y_min":0.32,"x_max":0.42,"y_max":0.68}}]}
                """.trimIndent(),
            )

        assertEquals(1, result.size)
        assertEquals("Greek Yogurt", result.single().name)
        assertEquals(0.32f, result.single().boundingBox.ymin, 0.001f)
        assertEquals(0.15f, result.single().boundingBox.xmin, 0.001f)
        assertEquals(0.68f, result.single().boundingBox.ymax, 0.001f)
        assertEquals(0.42f, result.single().boundingBox.xmax, 0.001f)
    }

    @Test
    fun fallsBackToDefaultBoxWhenMissing() {
        val result =
            VisionOutputParser().parse(
                """
                {"detected_items":[{"name":"Salt","category":"SPICES","storage_location":"PANTRY","quantity":1.0,"unit":"COUNT"}]}
                """.trimIndent(),
            )

        assertEquals(1, result.size)
        assertEquals("Salt", result.single().name)
        assertEquals(0f, result.single().boundingBox.ymin, 0.001f)
        assertEquals(1f, result.single().boundingBox.xmax, 0.001f)
    }

    @Test
    fun repairsDanglingClosingDelimitersAndSkipsInvalidItems() {
        val result =
            VisionOutputParser().parse(
                """{"items":[{"name":"Valid","bounding_box":[0,0,1,1]},{"name":"  ","bounding_box":[0,0,1,1]}]""",
            )

        assertEquals(1, result.size)
        assertTrue(result.single().name == "Valid")
    }
}
