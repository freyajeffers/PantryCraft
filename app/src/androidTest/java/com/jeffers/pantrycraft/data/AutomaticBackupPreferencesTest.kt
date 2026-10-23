package com.jeffers.pantrycraft.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.GeneralSecurityException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutomaticBackupPreferencesTest {
    private lateinit var context: Context
    private var originalEnabled: Boolean = false
    private var originalInterval: Long = 1440L
    private var originalPin: CharArray? = null
    private var originalTreeUri: Uri? = null
    private var originalExportedUri: Uri? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        originalEnabled = AutomaticBackupPreferences.isEnabled(context)
        originalInterval = AutomaticBackupPreferences.intervalMinutes(context)
        originalPin = AutomaticBackupPreferences.loadPin(context)
        originalTreeUri = persistedAutoBackupTreeUri(context)
        originalExportedUri = persistedBackupUri(context)
    }

    @After
    fun tearDown() {
        AutomaticBackupPreferences.setSchedule(context, originalEnabled, originalInterval)
        if (originalPin != null) {
            AutomaticBackupPreferences.savePin(context, originalPin!!)
        } else {
            clearPinPreferences()
        }
        persistAutoBackupTreeUri(context, originalTreeUri)
        persistBackupUri(context, originalExportedUri)
    }

    private fun clearPinPreferences() {
        context
            .getSharedPreferences("backup_preferences", Context.MODE_PRIVATE)
            .edit()
            .remove("automatic_backup_pin_ciphertext")
            .remove("automatic_backup_pin_iv")
            .apply()
    }

    @Test
    fun schedulePreferencesRoundTrip() {
        assertFalse(AutomaticBackupPreferences.isEnabled(context))
        assertEquals(1440L, AutomaticBackupPreferences.intervalMinutes(context))

        AutomaticBackupPreferences.setSchedule(context, enabled = true, intervalMinutes = 720L)
        assertTrue(AutomaticBackupPreferences.isEnabled(context))
        assertEquals(720L, AutomaticBackupPreferences.intervalMinutes(context))
    }

    @Test
    fun saveAndLoadEncryptedBackupPinWithMemoryScrubbing() {
        val originalPin = "987654"
        val pinChars = originalPin.toCharArray()

        AutomaticBackupPreferences.savePin(context, pinChars)

        // Verifies pinChars buffer was scrubbed
        assertTrue(pinChars.all { it == '\u0000' })

        val loaded = AutomaticBackupPreferences.loadPin(context)
        assertNotNull(loaded)
        assertEquals(originalPin, loaded!!.concatToString())

        // Scrub loaded pin
        loaded.fill('\u0000')
    }

    @Test
    fun loadPinReturnsNullWhenNotConfigured() {
        clearPinPreferences()
        assertNull(AutomaticBackupPreferences.loadPin(context))
    }

    @Test
    fun loadPinReturnsNullWhenPartiallyConfigured() {
        clearPinPreferences()
        val prefs = context.getSharedPreferences("backup_preferences", Context.MODE_PRIVATE)

        // Only ciphertext, missing IV
        prefs.edit().putString("automatic_backup_pin_ciphertext", "VGhpcyBJcyBDaXBoZXI=").apply()
        assertNull(AutomaticBackupPreferences.loadPin(context))

        // Only IV, missing ciphertext
        prefs.edit()
            .remove("automatic_backup_pin_ciphertext")
            .putString("automatic_backup_pin_iv", "QUJDREVGR0hJSktMTU5PUA==")
            .apply()
        assertNull(AutomaticBackupPreferences.loadPin(context))
    }

    @Test
    fun loadPinFailsWithSecurityExceptionWhenCiphertextCorrupted() {
        val originalPin = "123456"
        AutomaticBackupPreferences.savePin(context, originalPin.toCharArray())

        val prefs = context.getSharedPreferences("backup_preferences", Context.MODE_PRIVATE)
        // Corrupt ciphertext with invalid payload
        prefs.edit().putString("automatic_backup_pin_ciphertext", "VGhpcyBJcyBDb3JydXB0ZWREYXRh").apply()

        assertThrows(GeneralSecurityException::class.java) {
            AutomaticBackupPreferences.loadPin(context)
        }
    }

    @Test
    fun autoBackupTreeUriRoundTripsAndClears() {
        val testUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ABackups")
        persistAutoBackupTreeUri(context, testUri)
        assertEquals(testUri, persistedAutoBackupTreeUri(context))

        persistAutoBackupTreeUri(context, null)
        assertNull(persistedAutoBackupTreeUri(context))
    }

    @Test
    fun backupUriRoundTripsAndClears() {
        val testUri =
            Uri.parse("content://com.android.externalstorage.documents/document/primary%3Abackup.pcbk")
        persistBackupUri(context, testUri)
        assertEquals(testUri, persistedBackupUri(context))

        persistBackupUri(context, null)
        assertNull(persistedBackupUri(context))
    }
}
