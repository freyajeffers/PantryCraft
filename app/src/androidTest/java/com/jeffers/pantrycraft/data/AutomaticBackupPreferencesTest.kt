package com.jeffers.pantrycraft.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        originalEnabled = AutomaticBackupPreferences.isEnabled(context)
        originalInterval = AutomaticBackupPreferences.intervalMinutes(context)
        originalPin = AutomaticBackupPreferences.loadPin(context)
    }

    @After
    fun tearDown() {
        AutomaticBackupPreferences.setSchedule(context, originalEnabled, originalInterval)
        if (originalPin != null) {
            AutomaticBackupPreferences.savePin(context, originalPin!!)
        } else {
            context
                .getSharedPreferences("backup_preferences", Context.MODE_PRIVATE)
                .edit()
                .remove("automatic_backup_pin_ciphertext")
                .remove("automatic_backup_pin_iv")
                .apply()
        }
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
}
