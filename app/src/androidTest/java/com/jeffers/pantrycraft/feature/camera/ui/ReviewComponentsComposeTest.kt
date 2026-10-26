package com.jeffers.pantrycraft.feature.camera.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.pantrycraft.feature.camera.vision.NormalizedBox
import com.jeffers.pantrycraft.feature.camera.vision.ParsedVisionItem
import com.jeffers.pantrycraft.ui.PantryCraftTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewComponentsComposeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun sampleItem(
        name: String = "Apples",
        unit: String = "COUNT",
        qty: Float = 2f,
    ) = ParsedVisionItem(
        name = name,
        category = "PRODUCE",
        storageLocation = "PANTRY",
        quantity = qty,
        unit = unit,
        confidence = 0.95f,
        estimatedShelfLifeDays = 7,
        isAromaticOrSeasoning = false,
        boundingBox = NormalizedBox(0f, 0f, 1f, 1f),
        conditionNotes = null,
    )

    @Test
    fun reviewListContentRendersSingleItemAndSteppers() {
        var decreased = false
        var increased = false
        val item = sampleItem()
        val listState = ReviewListState(
            reviewItems = listOf(ReviewItem(item, true)),
            itemsSize = 1,
            quantityTextByIndex = emptyMap(),
            singleName = "Crisp Apple",
            singleCategory = "FRUIT",
            singleUnit = "COUNT",
            singleCustomUnitName = "",
            singleStorage = "PANTRY",
            singleShelfLifeDays = 5,
            onEdit = {},
            onToggleIncluded = {},
            onDecrease = { decreased = true },
            onIncrease = { increased = true },
            onSetQuantityText = { _, _ -> },
        )

        composeTestRule.setContent {
            PantryCraftTheme {
                ReviewListContent(state = listState)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Crisp Apple").assertIsDisplayed()
        composeTestRule.onNodeWithText("Category: FRUIT · Unit: COUNT").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Increase quantity").performClick()
        assertTrue(increased)
        composeTestRule.onNodeWithContentDescription("Decrease quantity").performClick()
        assertTrue(decreased)
    }

    @Test
    fun reviewItemCardMultipleItemsAndDecimalInput() {
        var editClicked = false
        var toggleClicked = false
        var quantityInput = ""
        val item = sampleItem(name = "Flour", unit = "KG", qty = 1.5f)
        val cardState = ReviewItemCardState(
            index = 0,
            itemsCount = 2,
            reviewItem = ReviewItem(item, included = true),
            displayedName = "Flour",
            displayedCategory = "BAKING",
            displayedUnit = "KG",
            displayedStorage = "PANTRY",
            displayedShelfLife = 180,
            rawUnit = "KG",
            quantityText = null,
            onEdit = { editClicked = true },
            onToggleIncluded = { toggleClicked = true },
            onDecrease = {},
            onIncrease = {},
            onSetQuantityText = { quantityInput = it },
        )

        composeTestRule.setContent {
            PantryCraftTheme {
                ReviewItemCard(state = cardState)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Edit").performClick()
        assertTrue(editClicked)
        composeTestRule.onNodeWithText("Remove").performClick()
        assertTrue(toggleClicked)
        composeTestRule.onNodeWithText("Quantity (KG)").performTextInput("2.5")
        assertEquals("2.5", quantityInput)
    }

    @Test
    fun reviewEditorFieldsRendersAndHandlesCheckboxes() {
        var openedChanged = false
        var aromaticChanged = false
        val props = ReviewEditorProps(
            name = "Olive Oil",
            onNameChange = {},
            category = "OILS",
            onCategoryChange = {},
            unit = "BOTTLE",
            onUnitChange = {},
            storageLocation = "PANTRY",
            onStorageLocationChange = {},
            shelfLifeDays = 365,
            onShelfLifeChange = {},
            notes = "Extra virgin",
            onNotesChange = {},
            isAromatic = false,
            onAromaticChange = { aromaticChanged = it },
            isOpened = false,
            onOpenedChange = { openedChanged = it },
        )

        composeTestRule.setContent {
            PantryCraftTheme {
                ReviewEditorFields(props = props)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Already opened (accelerates perishability)").performClick()
        assertTrue(openedChanged)
        composeTestRule.onNodeWithText("Aromatic / Seasoning (spices, herbs, oils)").performClick()
        assertTrue(aromaticChanged)
    }

    @Test
    fun reviewIdentityFieldsWithCustomUnitAndBarcode() {
        var scanBarcodeClicked = false
        var customUnitVal = ""
        val props = ReviewEditorProps(
            name = "Cilantro",
            onNameChange = {},
            category = "PRODUCE",
            onCategoryChange = {},
            unit = "CUSTOM",
            onUnitChange = {},
            customUnitName = "bunch",
            onCustomUnitNameChange = { customUnitVal = it },
            storageLocation = "FRIDGE",
            onStorageLocationChange = {},
            shelfLifeDays = 7,
            onShelfLifeChange = {},
            notes = "",
            onNotesChange = {},
            isAromatic = true,
            onAromaticChange = {},
            barcode = "1234567890",
            onScanBarcodeClick = { scanBarcodeClicked = true },
        )

        composeTestRule.setContent {
            PantryCraftTheme {
                ReviewIdentityFields(props = props)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Barcode: 1234567890").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Scan barcode to fill item").performClick()
        assertTrue(scanBarcodeClicked)
        composeTestRule.onNodeWithText("Custom unit (e.g. bunch, cloves, stalks)").performTextInput("es")
        assertEquals("bunches", customUnitVal)
    }

    @Test
    fun reviewShelfLifeEditorIncrementsDecrementsAndSelectsPreset() {
        var shelfLifeVal = 7
        val presets = listOf(3, 7, 14, 30)

        composeTestRule.setContent {
            PantryCraftTheme {
                ReviewShelfLifeEditor(
                    label = "Shelf life",
                    value = shelfLifeVal,
                    presets = presets,
                    onValueChange = { shelfLifeVal = it },
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Increase Shelf life").performClick()
        assertEquals(8, shelfLifeVal)
        composeTestRule.onNodeWithContentDescription("Decrease Shelf life").performClick()
        assertEquals(6, shelfLifeVal)
        composeTestRule.onNodeWithText("14 days").performClick()
        assertEquals(14, shelfLifeVal)
    }

    @Test
    fun reviewDropdownExpandsAndSelectsOption() {
        var selectedValue = "PANTRY"
        val options = listOf("PANTRY", "FRIDGE", "FREEZER")

        composeTestRule.setContent {
            PantryCraftTheme {
                ReviewDropdown(
                    label = "Storage location",
                    value = selectedValue,
                    options = options,
                    onSelected = { selectedValue = it },
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("PANTRY").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("FREEZER").performClick()
        assertEquals("FREEZER", selectedValue)
    }
}
