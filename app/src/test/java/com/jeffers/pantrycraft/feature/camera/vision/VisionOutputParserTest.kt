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

    @Test
    fun parsesAlternateBoundingBoxObjectKeyNames() {
        val json = """
            {"detected_items":[{"name":"Pasta","bounding_box":{"ymin":0.1,"xmin":0.2,"ymax":0.8,"xmax":0.9}}]}
        """.trimIndent()

        val item = VisionOutputParser().parse(json).single()
        assertEquals(0.1f, item.boundingBox.ymin, 0.001f)
        assertEquals(0.2f, item.boundingBox.xmin, 0.001f)
        assertEquals(0.8f, item.boundingBox.ymax, 0.001f)
        assertEquals(0.9f, item.boundingBox.xmax, 0.001f)
    }

    @Test
    fun handlesMalformedBoundingBoxGracefully() {
        // Less than 4 values -> falls back to default 0,0,1,1
        val jsonShortArray = """
            {"detected_items":[{"name":"Tea","bounding_box":[0.1, 0.2]}]}
        """.trimIndent()
        val itemShort = VisionOutputParser().parse(jsonShortArray).single()
        assertEquals(0f, itemShort.boundingBox.ymin, 0.001f)
        assertEquals(1f, itemShort.boundingBox.ymax, 0.001f)

        // Non-numeric/NaN in array -> falls back to default 0,0,1,1
        val jsonNan = """
            {"detected_items":[{"name":"Coffee","bounding_box":["nan", 0.2, 0.8, 0.9]}]}
        """.trimIndent()
        val itemNan = VisionOutputParser().parse(jsonNan).single()
        assertEquals(0f, itemNan.boundingBox.ymin, 0.001f)
        assertEquals(1f, itemNan.boundingBox.ymax, 0.001f)

        val jsonObjNan = """
            {"detected_items":[{"name":"Tea","bounding_box":{"y_min":1e999,"x_min":0.1,"y_max":0.9,"x_max":0.8}}]}
        """.trimIndent()
        val itemObjNan = VisionOutputParser().parse(jsonObjNan).single()
        assertEquals(0f, itemObjNan.boundingBox.ymin, 0.001f)
        assertEquals(1f, itemObjNan.boundingBox.ymax, 0.001f)
    }

    @Test
    fun stripsControlCharactersAndAutoClosesUnclosedJson() {
        // Contains control character \u0007 (BEL) and missing final closing brace '}'
        val raw = "```json\n{\"detected_items\":[{\"name\":\"Paprika\u0007\"}]"
        val items = VisionOutputParser().parse(raw)
        assertEquals(1, items.size)
        assertEquals("Paprika", items.single().name)
    }

    @Test
    fun clampsNegativeEstimatedShelfLifeDaysToZero() {
        val json = """
            {"detected_items":[{"name":"Bread","estimated_shelf_life_days":-10}]}
        """.trimIndent()
        val item = VisionOutputParser().parse(json).single()
        assertEquals(0, item.estimatedShelfLifeDays)
    }

    @Test
    fun parsesItemsKeyFallbackAndEmptyJson() {
        val fallbackJson = """
            {"items":[{"name":"Almonds","quantity":2}]}
        """.trimIndent()
        val fallbackResult = VisionOutputParser().parse(fallbackJson)
        assertEquals(1, fallbackResult.size)
        assertEquals("Almonds", fallbackResult.single().name)

        val emptyJson = "{}"
        val emptyResult = VisionOutputParser().parse(emptyJson)
        assertTrue(emptyResult.isEmpty())
    }

    @Test
    fun handlesEscapedQuotesAndBackslashesInUnclosedJson() {
        // Unclosed json with escaped quotes and backslashes in value
        val raw = "{\"detected_items\":[{\"name\":\"Special \\\"Herb\\\" \\\\ Blend\"}"
        val items = VisionOutputParser().parse(raw)
        assertEquals(1, items.size)
        assertEquals("Special \"Herb\" \\ Blend", items.single().name)
    }

    @Test
    fun skipsNonObjectEntriesInDetectedItems() {
        val json = """
            {"detected_items":["invalid-string-entry", null, {"name":"Walnuts"}]}
        """.trimIndent()
        val items = VisionOutputParser().parse(json)
        assertEquals(1, items.size)
        assertEquals("Walnuts", items.single().name)
    }
}
