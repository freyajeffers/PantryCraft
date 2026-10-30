package com.jeffers.pantrycraft.feature.meals.swap

import com.jeffers.pantrycraft.data.PantryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartSwapEngineTest {
    @Test
    fun findSwapsForMissingItemIdentifiesOnHandStock() {
        val onHand =
            listOf(
                PantryItem(name = "Whole Milk", quantity = 1f, unit = "L"),
                PantryItem(name = "Greek Yogurt", quantity = 500f, unit = "G"),
            )

        val buttermilkSwaps = SmartSwapEngine.findSwapsForMissingItem("buttermilk", onHand)
        assertEquals(1, buttermilkSwaps.size)
        val swap = buttermilkSwaps.first()
        assertEquals("Milk + Acid (Lemon/Vinegar)", swap.substituteName)
        assertTrue(swap.isFullyAvailableOnHand)

        val sourCreamSwaps = SmartSwapEngine.findSwapsForMissingItem("sour cream", onHand)
        assertEquals(1, sourCreamSwaps.size)
        assertTrue(sourCreamSwaps.first().isFullyAvailableOnHand)
    }

    @Test
    fun findSwapsForMissingItemFlagsMissingStock() {
        val onHand =
            listOf(
                PantryItem(name = "Olive Oil", quantity = 500f, unit = "ML"),
            )

        val creamSwaps = SmartSwapEngine.findSwapsForMissingItem("heavy cream", onHand)
        assertEquals(1, creamSwaps.size)
        assertFalse(creamSwaps.first().isFullyAvailableOnHand)
    }

    @Test
    fun enrichMissingItemsWithSwapsAssociatesAllItems() {
        val onHand =
            listOf(
                PantryItem(name = "Applesauce", quantity = 1f, unit = "CAN"),
            )
        val missing = listOf("egg", "breadcrumbs")
        val enriched = SmartSwapEngine.enrichMissingItemsWithSwaps(missing, onHand)

        assertEquals(2, enriched.size)
        assertTrue(enriched.containsKey("egg"))
        assertTrue(enriched.containsKey("breadcrumbs"))
        val eggSwaps = checkNotNull(enriched["egg"])
        val breadcrumbSwaps = checkNotNull(enriched["breadcrumbs"])
        assertTrue(eggSwaps.first().isFullyAvailableOnHand)
        assertFalse(breadcrumbSwaps.first().isFullyAvailableOnHand)
    }
}
