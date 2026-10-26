package com.jeffers.pantrycraft.feature.camera.vision

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test

class ByokCloudVisionModelUnitTest {

    @Test
    fun byokCloudVisionModelOfflineProviderThrowsIllegalState() = runBlocking {
        val prefsMap = mutableMapOf<String, Any?>()
        val mockPrefs = createMockPrefs(prefsMap)
        val context = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = mockPrefs
        }

        val model = ByokCloudVisionModel(context)
        assertNotNull(model)

        try {
            model.analyze(ByteArray(0), "Identify pantry items")
            fail("Expected IllegalStateException for OFFLINE_MLKIT routing")
        } catch (expected: IllegalStateException) {
            assertNotNull(expected.message)
        }
    }

    @Test
    fun byokCloudVisionModelCompanionProperties() {
        val companionField = ByokCloudVisionModel::class.java.getDeclaredField("Companion")
            .apply { isAccessible = true }
            .get(null)
        val getter = companionField.javaClass.getDeclaredMethod("getHTTP_SUCCESS_RANGE")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val range = getter.invoke(companionField) as IntRange
        org.junit.Assert.assertEquals(200..299, range)
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
}
