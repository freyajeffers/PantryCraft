package com.jeffers.pantrycraft.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenStateTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var holder: SettingsDialogStateHolder

    @Before
    fun setUp() {
        holder = SettingsDialogStateHolder(context)
    }

    @Test
    fun defaultStateIsDismissed() {
        assertFalse(holder.showPinDialog)
        assertEquals("", holder.pinText)
        assertFalse(holder.showBackupList)
        assertFalse(holder.confirmDatabaseReset)
        assertFalse(holder.confirmCatalogDelete)
        assertNull(holder.catalogDialog)
    }

    @Test
    fun openPinSetsFlagAndResetsPinText() {
        holder.pinText = "previous-pin"
        var actionExecuted = false

        holder.openPin {
            actionExecuted = true
        }

        assertTrue(holder.showPinDialog)
        assertEquals("", holder.pinText)
        assertFalse(actionExecuted)
    }

    @Test
    fun closePinResetsDialogAndClearsPinText() {
        holder.openPin {}
        holder.pinText = "1234"

        holder.closePin()

        assertFalse(holder.showPinDialog)
        assertEquals("", holder.pinText)
    }

    @Test
    fun confirmPinClosesDialogAndDispatchesEnteredChars() {
        var dispatchedPin: String? = null
        holder.openPin { chars ->
            dispatchedPin = chars.concatToString()
        }
        holder.pinText = "5678"

        holder.confirmPin()

        assertFalse(holder.showPinDialog)
        assertEquals("", holder.pinText)
        assertEquals("5678", dispatchedPin)
    }

    @Test
    fun openBackupListSetsVisibilityFlag() {
        holder.openBackupList()
        assertTrue(holder.showBackupList)
    }

    @Test
    fun dialogFlagsCanBeToggled() {
        holder.confirmDatabaseReset = true
        assertTrue(holder.confirmDatabaseReset)
        holder.confirmDatabaseReset = false
        assertFalse(holder.confirmDatabaseReset)

        holder.confirmCatalogDelete = true
        assertTrue(holder.confirmCatalogDelete)
        holder.confirmCatalogDelete = false
        assertFalse(holder.confirmCatalogDelete)
    }
}
