package com.jeffers.pantrycraft.feature.camera.vision

import org.junit.Assert.assertEquals
import org.junit.Test

class MlKitSnapshotModelUnitTest {
    private val companionClass = MlKitSnapshotModel.Companion::class.java
    private val companionInstance = MlKitSnapshotModel.Companion

    @Test
    fun mapLabelToCategoryClassifiesKeywords() {
        val method =
            companionClass.getDeclaredMethod("mapLabelToCategory", String::class.java).apply {
                isAccessible = true
            }

        @Suppress("UNCHECKED_CAST")
        fun invoke(label: String): Pair<String, String> = method.invoke(companionInstance, label) as Pair<String, String>

        assertEquals("Fresh Fruit" to "PRODUCE", invoke("Fresh Fruit"))
        assertEquals("Whole Milk" to "DAIRY", invoke("Whole Milk"))
        assertEquals("Ground Beef" to "MEAT", invoke("Ground Beef"))
        assertEquals("Sourdough Bread" to "BAKERY", invoke("Sourdough Bread"))
        assertEquals("Orange Juice" to "BEVERAGES", invoke("Orange Juice"))
        assertEquals("Potato Chip" to "SNACKS", invoke("Potato Chip"))
        assertEquals("Canned Beans" to "CANNED", invoke("Canned Beans"))
        assertEquals("Frozen Peas" to "FROZEN", invoke("Frozen Peas"))
        assertEquals("White Rice" to "GRAINS", invoke("White Rice"))
        assertEquals("Food item" to "OTHER", invoke("Packaged Food"))
        assertEquals("Widget" to "OTHER", invoke("Widget"))
    }

    @Test
    fun defaultStorageForCategoryReturnsExpectedLocations() {
        val method =
            companionClass.getDeclaredMethod("defaultStorageForCategory", String::class.java).apply {
                isAccessible = true
            }

        fun invoke(cat: String): String = method.invoke(companionInstance, cat) as String

        assertEquals("FRIDGE", invoke("PRODUCE"))
        assertEquals("FRIDGE", invoke("DAIRY"))
        assertEquals("FRIDGE", invoke("MEAT"))
        assertEquals("FRIDGE", invoke("SEAFOOD"))
        assertEquals("FREEZER", invoke("FROZEN"))
        assertEquals("COUNTER", invoke("BAKERY"))
        assertEquals("PANTRY", invoke("GRAINS"))
        assertEquals("PANTRY", invoke("OTHER"))
    }

    @Test
    fun defaultShelfLifeForCategoryReturnsExpectedDurations() {
        val method =
            companionClass.getDeclaredMethod("defaultShelfLifeForCategory", String::class.java).apply {
                isAccessible = true
            }

        fun invoke(cat: String): Int = method.invoke(companionInstance, cat) as Int

        assertEquals(7, invoke("PRODUCE"))
        assertEquals(10, invoke("DAIRY"))
        assertEquals(3, invoke("MEAT"))
        assertEquals(3, invoke("SEAFOOD"))
        assertEquals(5, invoke("BAKERY"))
        assertEquals(90, invoke("FROZEN"))
        assertEquals(30, invoke("GRAINS"))
        assertEquals(30, invoke("OTHER"))
    }
}
