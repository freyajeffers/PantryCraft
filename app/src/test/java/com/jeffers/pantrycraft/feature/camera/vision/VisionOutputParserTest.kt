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
    fun repairsDanglingClosingDelimitersAndSkipsInvalidItems() {
        val result =
            VisionOutputParser().parse(
                """{"items":[{"name":"Valid","bounding_box":[0,0,1,1]},{"name":"Invalid","bounding_box":[0,0]}]""",
            )

        assertEquals(1, result.size)
        assertTrue(result.single().name == "Valid")
    }
}
