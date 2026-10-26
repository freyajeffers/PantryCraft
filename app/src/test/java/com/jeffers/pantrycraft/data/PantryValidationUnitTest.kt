package com.jeffers.pantrycraft.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PantryValidationUnitTest {

    @Test
    fun validatePantryItemTrimsAndNormalizesName() {
        val item = PantryItem(
            name = "   Organic Basmati Rice   ",
            quantity = 5f,
            threshold = 1f,
            storageLocation = "PANTRY",
            category = "GRAINS",
        )
        val validated = validatePantryItem(item)
        assertEquals("organic basmati rice", validated.normalizedName)
        assertFalse(validated.isAromaticOrSeasoning)
    }

    @Test
    fun validatePantryItemEnforcesAromaticFlagAndCategory() {
        val spice = PantryItem(
            name = "Coriander Seeds",
            quantity = 50f,
            threshold = 10f,
            storageLocation = "PANTRY",
            category = "SPICES",
            isAromaticOrSeasoning = false,
        )
        val validatedSpice = validatePantryItem(spice)
        assertTrue(validatedSpice.isAromaticOrSeasoning)

        val customAromatic = PantryItem(
            name = "Truffle Oil",
            quantity = 1f,
            threshold = 0f,
            storageLocation = "PANTRY",
            category = "OILS",
            isAromaticOrSeasoning = true,
        )
        val validatedCustom = validatePantryItem(customAromatic)
        assertTrue(validatedCustom.isAromaticOrSeasoning)
    }

    @Test
    fun validatePantryItemThrowsOnBlankName() {
        val blankItem = PantryItem(
            name = "   ",
            quantity = 1f,
            storageLocation = "PANTRY",
        )
        assertThrows(IllegalArgumentException::class.java) {
            validatePantryItem(blankItem)
        }
    }

    @Test
    fun validatePantryItemThrowsOnNegativeQuantity() {
        val negativeQty = PantryItem(
            name = "Milk",
            quantity = -1f,
            storageLocation = "FRIDGE",
        )
        assertThrows(IllegalArgumentException::class.java) {
            validatePantryItem(negativeQty)
        }
    }

    @Test
    fun validatePantryItemThrowsOnNegativeThreshold() {
        val negativeThreshold = PantryItem(
            name = "Eggs",
            quantity = 12f,
            threshold = -0.5f,
            storageLocation = "FRIDGE",
        )
        assertThrows(IllegalArgumentException::class.java) {
            validatePantryItem(negativeThreshold)
        }
    }

    @Test
    fun validatePantryItemThrowsOnUnsupportedStorageLocation() {
        val invalidStorage = PantryItem(
            name = "Ice Cream",
            quantity = 1f,
            storageLocation = "BASEMENT_VAULT",
        )
        assertThrows(IllegalArgumentException::class.java) {
            validatePantryItem(invalidStorage)
        }
    }
}
