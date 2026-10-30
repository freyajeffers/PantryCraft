package com.jeffers.pantrycraft.feature.camera.ui

import com.jeffers.pantrycraft.feature.camera.vision.NormalizedBox
import com.jeffers.pantrycraft.feature.camera.vision.ParsedVisionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewComponentsModelTest {
    private fun createParsedVisionItem(name: String = "Apples"): ParsedVisionItem =
        ParsedVisionItem(
            name = name,
            category = "PRODUCE",
            storageLocation = "PANTRY",
            quantity = 2f,
            unit = "COUNT",
            confidence = 0.95f,
            estimatedShelfLifeDays = 7,
            isAromaticOrSeasoning = false,
            boundingBox = NormalizedBox(0f, 0f, 1f, 1f),
            conditionNotes = null,
        )

    @Test
    fun reviewEditorPropsDefaultValuesAndCallbacks() {
        var nameChanged = ""
        var categoryChanged = ""
        var unitChanged = ""
        var storageChanged = ""
        var shelfLifeChanged = 0
        var notesChanged = ""
        var aromaticChanged = false

        val props =
            ReviewEditorProps(
                name = "Apples",
                onNameChange = { nameChanged = it },
                category = "PRODUCE",
                onCategoryChange = { categoryChanged = it },
                unit = "COUNT",
                onUnitChange = { unitChanged = it },
                storageLocation = "PANTRY",
                onStorageLocationChange = { storageChanged = it },
                shelfLifeDays = 7,
                onShelfLifeChange = { shelfLifeChanged = it },
                notes = "Crisp",
                onNotesChange = { notesChanged = it },
                isAromatic = false,
                onAromaticChange = { aromaticChanged = it },
            )

        assertEquals("Apples", props.name)
        assertEquals("PRODUCE", props.category)
        assertEquals("COUNT", props.unit)
        assertEquals("PANTRY", props.storageLocation)
        assertEquals(7, props.shelfLifeDays)
        assertEquals("Crisp", props.notes)
        assertFalse(props.isAromatic)
        assertEquals("", props.customUnitName)
        assertEquals("0", props.thresholdText)
        assertFalse(props.isOpened)
        assertEquals(null, props.barcode)

        props.onCustomUnitNameChange("")
        props.onThresholdTextChange("")
        props.onOpenedChange(false)
        props.onScanBarcodeClick()

        props.onNameChange("Pears")
        props.onCategoryChange("FRUIT")
        props.onUnitChange("KG")
        props.onStorageLocationChange("FRIDGE")
        props.onShelfLifeChange(14)
        props.onNotesChange("Juicy")
        props.onAromaticChange(true)

        assertEquals("Pears", nameChanged)
        assertEquals("FRUIT", categoryChanged)
        assertEquals("KG", unitChanged)
        assertEquals("FRIDGE", storageChanged)
        assertEquals(14, shelfLifeChanged)
        assertEquals("Juicy", notesChanged)
        assertTrue(aromaticChanged)
    }

    @Test
    fun reviewEditorPropsFullCopyAndCallbacks() {
        var customUnitChanged = ""
        var thresholdChanged = ""
        var openedChanged = false
        var scanBarcodeClicked = false

        val props =
            ReviewEditorProps(
                name = "Apples",
                onNameChange = {},
                category = "PRODUCE",
                onCategoryChange = {},
                unit = "COUNT",
                onUnitChange = {},
                storageLocation = "PANTRY",
                onStorageLocationChange = {},
                shelfLifeDays = 7,
                onShelfLifeChange = {},
                notes = "Crisp",
                onNotesChange = {},
                isAromatic = false,
                onAromaticChange = {},
            )

        val fullProps =
            props.copy(
                customUnitName = "crate",
                onCustomUnitNameChange = { customUnitChanged = it },
                thresholdText = "2",
                onThresholdTextChange = { thresholdChanged = it },
                isOpened = true,
                onOpenedChange = { openedChanged = it },
                barcode = "012345678901",
                onScanBarcodeClick = { scanBarcodeClicked = true },
            )
        fullProps.onCustomUnitNameChange("box")
        fullProps.onThresholdTextChange("5")
        fullProps.onOpenedChange(false)
        fullProps.onScanBarcodeClick()

        assertEquals("box", customUnitChanged)
        assertEquals("5", thresholdChanged)
        assertFalse(openedChanged)
        assertTrue(scanBarcodeClicked)
        assertEquals("012345678901", fullProps.barcode)
    }

    @Test
    fun reviewItemCardStatePropertiesAndActions() {
        val parsed = createParsedVisionItem()
        val reviewItem = ReviewItem(item = parsed, included = true)
        var edited = false
        var toggled = false
        var decreased = false
        var increased = false
        var quantitySet = ""

        val cardState =
            ReviewItemCardState(
                index = 0,
                itemsCount = 1,
                reviewItem = reviewItem,
                displayedName = "Honeycrisp Apples",
                displayedCategory = "PRODUCE",
                displayedUnit = "COUNT",
                displayedStorage = "PANTRY",
                displayedShelfLife = 7,
                rawUnit = "COUNT",
                quantityText = "2",
                onEdit = { edited = true },
                onToggleIncluded = { toggled = true },
                onDecrease = { decreased = true },
                onIncrease = { increased = true },
                onSetQuantityText = { quantitySet = it },
            )

        assertEquals(0, cardState.index)
        assertEquals(1, cardState.itemsCount)
        assertEquals(reviewItem, cardState.reviewItem)
        assertEquals("Honeycrisp Apples", cardState.displayedName)
        assertEquals("PRODUCE", cardState.displayedCategory)
        assertEquals("COUNT", cardState.displayedUnit)
        assertEquals("PANTRY", cardState.displayedStorage)
        assertEquals(7, cardState.displayedShelfLife)
        assertEquals("COUNT", cardState.rawUnit)
        assertEquals("2", cardState.quantityText)

        cardState.onEdit()
        cardState.onToggleIncluded()
        cardState.onDecrease()
        cardState.onIncrease()
        cardState.onSetQuantityText("3")

        assertTrue(edited)
        assertTrue(toggled)
        assertTrue(decreased)
        assertTrue(increased)
        assertEquals("3", quantitySet)
    }

    @Test
    fun reviewListStatePropertiesAndActions() {
        val parsed = createParsedVisionItem()
        val reviewItems = listOf(ReviewItem(item = parsed, included = true))
        var editedIndex = -1
        var toggledIndex = -1
        var decreasedIndex = -1
        var increasedIndex = -1
        var setIndex = -1
        var setText = ""

        val listState =
            ReviewListState(
                reviewItems = reviewItems,
                itemsSize = 1,
                quantityTextByIndex = mapOf(0 to "2"),
                singleName = "Apples",
                singleCategory = "PRODUCE",
                singleUnit = "COUNT",
                singleCustomUnitName = "",
                singleStorage = "PANTRY",
                singleShelfLifeDays = 7,
                onEdit = { editedIndex = it },
                onToggleIncluded = { toggledIndex = it },
                onDecrease = { decreasedIndex = it },
                onIncrease = { increasedIndex = it },
                onSetQuantityText = { idx, text ->
                    setIndex = idx
                    setText = text
                },
            )

        assertEquals(reviewItems, listState.reviewItems)
        assertEquals(1, listState.itemsSize)
        assertEquals("2", listState.quantityTextByIndex[0])
        assertEquals("Apples", listState.singleName)
        assertEquals("PRODUCE", listState.singleCategory)
        assertEquals("COUNT", listState.singleUnit)
        assertEquals("", listState.singleCustomUnitName)
        assertEquals("PANTRY", listState.singleStorage)
        assertEquals(7, listState.singleShelfLifeDays)

        listState.onEdit(2)
        listState.onToggleIncluded(3)
        listState.onDecrease(4)
        listState.onIncrease(5)
        listState.onSetQuantityText(6, "10")

        assertEquals(2, editedIndex)
        assertEquals(3, toggledIndex)
        assertEquals(4, decreasedIndex)
        assertEquals(5, increasedIndex)
        assertEquals(6, setIndex)
        assertEquals("10", setText)
    }
}
