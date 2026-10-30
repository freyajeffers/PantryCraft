package com.jeffers.pantrycraft.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomDestinationTest {
    @Test
    fun bottomDestinationEnumEntriesAndProperties() {
        assertEquals(5, BottomDestination.entries.size)

        val home = BottomDestination.HOME
        assertEquals("home", home.route)
        assertEquals("Dashboard", home.label)
        assertNotNull(home.icon)
        assertNotNull(home.selectedIcon)

        val pantry = BottomDestination.PANTRY
        assertEquals("pantry", pantry.route)
        assertEquals("Pantry", pantry.label)

        val grocery = BottomDestination.GROCERY
        assertEquals("grocery", grocery.route)
        assertEquals("Grocery", grocery.label)

        val camera = BottomDestination.CAMERA
        assertEquals("camera", camera.route)
        assertEquals("Scan", camera.label)

        val settings = BottomDestination.SETTINGS
        assertEquals("settings", settings.route)
        assertEquals("Settings", settings.label)
    }

    @Test
    fun bottomDestinationStandardEnumMethods() {
        assertEquals(BottomDestination.HOME, BottomDestination.valueOf("HOME"))
        assertEquals(BottomDestination.PANTRY, BottomDestination.valueOf("PANTRY"))
        assertEquals(BottomDestination.GROCERY, BottomDestination.valueOf("GROCERY"))
        assertEquals(BottomDestination.CAMERA, BottomDestination.valueOf("CAMERA"))
        assertEquals(BottomDestination.SETTINGS, BottomDestination.valueOf("SETTINGS"))

        val values = BottomDestination.values()
        assertEquals(5, values.size)
        assertTrue(values.contains(BottomDestination.HOME))
    }
}
