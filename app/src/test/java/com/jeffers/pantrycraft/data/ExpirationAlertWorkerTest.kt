package com.jeffers.pantrycraft.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ExpirationAlertWorkerTest {
    @Test
    fun initialDelayBeforeNineAmTargetsSameDay() {
        val now = calendarAt(2026, Calendar.JANUARY, 10, 8, 30).timeInMillis
        val delay = ExpirationAlertWorker.calculateInitialDelayToNineAmMillis(now)

        assertEquals(30L * 60L * 1000L, delay)
    }

    @Test
    fun initialDelayAfterNineAmTargetsNextDay() {
        val now = calendarAt(2026, Calendar.JANUARY, 10, 10, 0).timeInMillis
        val delay = ExpirationAlertWorker.calculateInitialDelayToNineAmMillis(now)

        assertEquals(23L * 60L * 60L * 1000L, delay)
    }

    @Test
    fun initialDelayAtExactNineAmTargetsNextDay() {
        val now = calendarAt(2026, Calendar.JANUARY, 10, 9, 0).timeInMillis
        val delay = ExpirationAlertWorker.calculateInitialDelayToNineAmMillis(now)

        assertEquals(24L * 60L * 60L * 1000L, delay)
    }

    @Test
    fun alertWindowIsFortyEightHours() {
        assertEquals(2L * 24L * 60L * 60L * 1000L, ExpirationAlertWorker.ALERT_WINDOW_MILLIS)
        assertTrue(ExpirationAlertWorker.ALERT_WINDOW_MILLIS > 0L)
    }

    @Test
    fun calculateInitialDelayDefaultParameterUsesSystemTime() {
        val delay = ExpirationAlertWorker.calculateInitialDelayToNineAmMillis()
        assertTrue(delay >= 0L)
        assertTrue(delay <= 24L * 60L * 60L * 1000L)
    }

    @Test
    fun isEnabledReadsFromPreferences() {
        val prefsMap = mutableMapOf<String, Any?>("enabled" to true)
        val context = object : ContextWrapper(null) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                createMockPrefs(prefsMap)
        }
        assertTrue(ExpirationAlertWorker.isEnabled(context))

        prefsMap["enabled"] = false
        assertFalse(ExpirationAlertWorker.isEnabled(context))
    }

    @Test
    fun scheduleIfEnabledNoOpsWhenDisabled() {
        val prefsMap = mutableMapOf<String, Any?>("enabled" to false)
        val context = object : ContextWrapper(null) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                createMockPrefs(prefsMap)
        }
        ExpirationAlertWorker.scheduleIfEnabled(context)
        assertFalse(ExpirationAlertWorker.isEnabled(context))
    }

    @Test
    fun expirationAlertSchedulerDelegatesScheduleIfEnabled() {
        val prefsMap = mutableMapOf<String, Any?>("enabled" to false)
        val context = object : ContextWrapper(null) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                createMockPrefs(prefsMap)
        }
        ExpirationAlertScheduler.scheduleIfEnabled(context)
        assertFalse(ExpirationAlertWorker.isEnabled(context))
    }

    private fun createMockPrefs(prefsMap: MutableMap<String, Any?>): SharedPreferences =
        object : SharedPreferences {
            override fun getAll(): Map<String, *> = prefsMap
            override fun getString(key: String, defValue: String?): String? =
                prefsMap[key] as? String ?: defValue
            @Suppress("UNCHECKED_CAST")
            override fun getStringSet(key: String, def: Set<String>?): Set<String>? =
                prefsMap[key] as? Set<String> ?: def
            override fun getInt(key: String, def: Int): Int = prefsMap[key] as? Int ?: def
            override fun getLong(key: String, def: Long): Long = prefsMap[key] as? Long ?: def
            override fun getFloat(key: String, def: Float): Float = prefsMap[key] as? Float ?: def
            override fun getBoolean(key: String, def: Boolean): Boolean = prefsMap[key] as? Boolean ?: def
            override fun contains(key: String): Boolean = key in prefsMap
            override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
                override fun putString(k: String, v: String?): SharedPreferences.Editor {
                    prefsMap[k] = v
                    return this
                }
                override fun putStringSet(k: String, v: Set<String>?): SharedPreferences.Editor {
                    prefsMap[k] = v
                    return this
                }
                override fun putInt(k: String, v: Int): SharedPreferences.Editor {
                    prefsMap[k] = v
                    return this
                }
                override fun putLong(k: String, v: Long): SharedPreferences.Editor {
                    prefsMap[k] = v
                    return this
                }
                override fun putFloat(k: String, v: Float): SharedPreferences.Editor {
                    prefsMap[k] = v
                    return this
                }
                override fun putBoolean(k: String, v: Boolean): SharedPreferences.Editor {
                    prefsMap[k] = v
                    return this
                }
                override fun remove(k: String): SharedPreferences.Editor {
                    prefsMap.remove(k)
                    return this
                }
                override fun clear(): SharedPreferences.Editor {
                    prefsMap.clear()
                    return this
                }
                override fun commit(): Boolean = true
                override fun apply() {
                    // Mock no-op
                }
            }
            override fun registerOnSharedPreferenceChangeListener(
                l: SharedPreferences.OnSharedPreferenceChangeListener?,
            ) {
                // Mock no-op
            }
            override fun unregisterOnSharedPreferenceChangeListener(
                l: SharedPreferences.OnSharedPreferenceChangeListener?,
            ) {
                // Mock no-op
            }
        }

    private fun calendarAt(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }
}
