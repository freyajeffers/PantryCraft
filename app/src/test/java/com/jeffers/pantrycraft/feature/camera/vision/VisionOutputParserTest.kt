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

    @Test
    fun parsesExtendedFieldsAndNormalizesCasing() {
        val json = """
            {
                "detected_items": [
                    {
                        "name": "Organic Honey",
                        "category": "condiments",
                        "storage_location": "pantry",
                        "quantity": 1.5,
                        "unit": "jar",
                        "confidence": 0.88,
                        "estimated_shelf_life_days": 365,
                        "is_aromatic_or_seasoning": true,
                        "is_opened": true,
                        "threshold": 0.5,
                        "barcode": "123456789012",
                        "custom_unit_name": "mason jar",
                        "condition_notes": "Unopened seal intact"
                    }
                ]
            }
        """.trimIndent()

        val item = VisionOutputParser().parse(json).single()
        assertEquals("Organic Honey", item.name)
        assertEquals("CONDIMENTS", item.category)
        assertEquals("PANTRY", item.storageLocation)
        assertEquals(1.5f, item.quantity, 0.001f)
        assertEquals("JAR", item.unit)
        assertEquals(0.88f, item.confidence, 0.001f)
        assertEquals(365, item.estimatedShelfLifeDays)
        assertTrue(item.isAromaticOrSeasoning)
        assertTrue(item.isOpened)
        assertEquals(0.5f, item.threshold, 0.001f)
        assertEquals("123456789012", item.barcode)
        assertEquals("mason jar", item.customUnitName)
        assertEquals("Unopened seal intact", item.conditionNotes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun throwsIllegalArgumentExceptionWhenNoJsonPresent() {
        VisionOutputParser().parse("No json object here at all")
    }

    @Test
    fun clampsNegativeQuantitiesAndThresholds() {
        val json = """
            {"detected_items":[{"name":"Flour","quantity":-3.0,"threshold":-1.0,"confidence":-0.5}]}
        """.trimIndent()

        val item = VisionOutputParser().parse(json).single()
        assertEquals(0f, item.quantity, 0.001f)
        assertEquals(0f, item.threshold, 0.001f)
        assertEquals(0f, item.confidence, 0.001f)
    }
}
