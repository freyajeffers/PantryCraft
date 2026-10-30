package com.jeffers.pantrycraft.feature.camera.ui

import com.jeffers.pantrycraft.feature.camera.vision.NormalizedBox
import com.jeffers.pantrycraft.feature.camera.vision.ParsedVisionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchReviewEditorStateTest {
    @Test
    fun loadFromPopulatesAllFieldsAndDetectsAromaticCategory() {
        val item =
            ParsedVisionItem(
                name = "Cinnamon Sticks",
                category = "SPICES",
                storageLocation = "PANTRY",
                quantity = 2f,
                unit = "BOTTLE",
                confidence = 0.95f,
                estimatedShelfLifeDays = 730,
                isAromaticOrSeasoning = false, // Category SPICES will auto-detect as aromatic
                boundingBox = NormalizedBox(0f, 0f, 1f, 1f),
                conditionNotes = "Sealed glass jar",
                customUnitName = null,
                barcode = "012345678901",
                threshold = 1.5f,
                isOpened = true,
            )

        val editor = BatchReviewEditorState()
        editor.loadFrom(item)

        assertEquals("Cinnamon Sticks", editor.name)
        assertEquals("SPICES", editor.category)
        assertEquals("PANTRY", editor.storageLocation)
        assertEquals("BOTTLE", editor.unit)
        assertEquals("", editor.customUnitName)
        assertEquals(730, editor.shelfLifeDays)
        assertEquals("Sealed glass jar", editor.notes)
        assertTrue(editor.isAromatic)
        assertTrue(editor.isOpened)
        assertEquals("1.5", editor.thresholdText)
        assertEquals("012345678901", editor.barcode)
    }

    @Test
    fun applyToTrimsFieldsAndAppliesSensibleFallbacks() {
        val baseItem =
            ParsedVisionItem(
                name = "Raw",
                category = "OTHER",
                storageLocation = "PANTRY",
                quantity = 1f,
                unit = "COUNT",
                confidence = 1f,
                estimatedShelfLifeDays = 0,
                isAromaticOrSeasoning = false,
                boundingBox = NormalizedBox(0f, 0f, 1f, 1f),
                conditionNotes = null,
            )

        val editor = BatchReviewEditorState()
        editor.name = "  Organic Strawberries  "
        editor.category = "   " // Should fall back to "OTHER"
        editor.unit = "   " // Should fall back to "COUNT"
        editor.storageLocation = "   " // Should fall back to first storage location ("PANTRY")
        editor.notes = "   " // Blank notes should become null
        editor.thresholdText = "-5.0" // Negative threshold clamped to 0f
        editor.shelfLifeDays = 5
        editor.isOpened = false
        editor.isAromatic = false

        val updated = editor.applyTo(baseItem)

        assertEquals("Organic Strawberries", updated.name)
        assertEquals("OTHER", updated.category)
        assertEquals("COUNT", updated.unit)
        assertEquals("PANTRY", updated.storageLocation)
        assertNull(updated.conditionNotes)
        assertEquals(0f, updated.threshold, 0.001f)
        assertEquals(5, updated.estimatedShelfLifeDays)
        assertFalse(updated.isOpened)

        editor.notes = "  Freshly picked  "
        val withNotes = editor.applyTo(baseItem)
        assertEquals("Freshly picked", withNotes.conditionNotes)
    }

    @Test
    fun applyToHandlesCustomUnitNameCorrectly() {
        val baseItem =
            ParsedVisionItem(
                name = "Rosemary",
                category = "SPICES",
                storageLocation = "PANTRY",
                quantity = 3f,
                unit = "CUSTOM",
                confidence = 1f,
                estimatedShelfLifeDays = 14,
                isAromaticOrSeasoning = true,
                boundingBox = NormalizedBox(0f, 0f, 1f, 1f),
                conditionNotes = null,
            )

        val editor = BatchReviewEditorState()
        editor.name = "Rosemary"
        editor.category = "SPICES"
        editor.storageLocation = "PANTRY"

        // Unit is CUSTOM and customUnitName is provided
        editor.unit = "CUSTOM"
        editor.customUnitName = "  sprigs  "
        val customUpdated = editor.applyTo(baseItem)
        assertEquals("CUSTOM", customUpdated.unit)
        assertEquals("sprigs", customUpdated.customUnitName)

        // When unit is changed to BUNCH, customUnitName is disregarded and set to null
        editor.unit = "BUNCH"
        editor.customUnitName = "sprigs"
        val nonCustomUpdated = editor.applyTo(baseItem)
        assertEquals("BUNCH", nonCustomUpdated.unit)
        assertNull(nonCustomUpdated.customUnitName)
    }

    @Test
    fun applyToHandlesNonNumericAndInvalidThresholdText() {
        val baseItem =
            ParsedVisionItem(
                name = "Flour",
                category = "BAKING",
                storageLocation = "PANTRY",
                quantity = 5f,
                unit = "BAG",
                confidence = 1f,
                estimatedShelfLifeDays = 180,
                isAromaticOrSeasoning = false,
                boundingBox = NormalizedBox(0f, 0f, 1f, 1f),
                conditionNotes = null,
            )

        val editor = BatchReviewEditorState()
        editor.loadFrom(baseItem)

        editor.thresholdText = "not-a-number"
        val invalidUpdated = editor.applyTo(baseItem)
        assertEquals(0f, invalidUpdated.threshold, 0.001f)

        editor.thresholdText = ""
        val emptyUpdated = editor.applyTo(baseItem)
        assertEquals(0f, emptyUpdated.threshold, 0.001f)

        editor.thresholdText = "2.75"
        val validUpdated = editor.applyTo(baseItem)
        assertEquals(2.75f, validUpdated.threshold, 0.001f)
    }

    @Test
    fun applyToWithCustomUnitAndBlankCustomUnitNameSetsNull() {
        val baseItem =
            ParsedVisionItem(
                name = "Thyme",
                category = "SPICES",
                storageLocation = "PANTRY",
                quantity = 1f,
                unit = "CUSTOM",
                confidence = 1f,
                estimatedShelfLifeDays = 7,
                isAromaticOrSeasoning = true,
                boundingBox = NormalizedBox(0f, 0f, 1f, 1f),
                conditionNotes = null,
            )

        val editor = BatchReviewEditorState()
        editor.loadFrom(baseItem)
        editor.unit = "CUSTOM"
        editor.customUnitName = "   "

        val updated = editor.applyTo(baseItem)
        assertEquals("CUSTOM", updated.unit)
        assertNull(updated.customUnitName)
    }

    @Test
    fun applyToPreservesOrUpdatesBarcode() {
        val baseItem =
            ParsedVisionItem(
                name = "Olive Oil",
                category = "OILS",
                storageLocation = "PANTRY",
                quantity = 1f,
                unit = "BOTTLE",
                confidence = 1f,
                estimatedShelfLifeDays = 365,
                isAromaticOrSeasoning = false,
                boundingBox = NormalizedBox(0f, 0f, 1f, 1f),
                conditionNotes = null,
                barcode = "111222333444",
            )

        val editor = BatchReviewEditorState()
        editor.loadFrom(baseItem)
        assertEquals("111222333444", editor.barcode)

        editor.barcode = "999888777666"
        val updated = editor.applyTo(baseItem)
        assertEquals("999888777666", updated.barcode)

        editor.barcode = null
        val cleared = editor.applyTo(baseItem)
        assertNull(cleared.barcode)
    }
}
