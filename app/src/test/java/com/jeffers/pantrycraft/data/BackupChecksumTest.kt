package com.jeffers.pantrycraft.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BackupChecksumTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun returnsNullWhenFileDoesNotExist() {
        val nonExistent = File(tempFolder.root, "missing_db.db")
        assertNull(fileChecksum(nonExistent))
    }

    @Test
    fun returnsNullWhenFileIsDirectory() {
        val dir = tempFolder.newFolder("sub_directory")
        assertNull(fileChecksum(dir))
    }

    @Test
    fun computesKnownSha256ForEmptyFile() {
        val emptyFile = tempFolder.newFile("empty.db")
        val checksum = fileChecksum(emptyFile)

        assertNotNull(checksum)
        assertEquals(EMPTY_SHA256, checksum)
        assertEquals(SHA256_HEX_LENGTH, checksum?.length)
    }

    @Test
    fun computesValidHexDigestForDataFile() {
        val dataFile = tempFolder.newFile("sample.db")
        dataFile.writeBytes("PantryCraft-Secure-Backup-Payload".toByteArray(Charsets.UTF_8))

        val checksum = fileChecksum(dataFile)
        assertNotNull(checksum)
        assertEquals(SHA256_HEX_LENGTH, checksum?.length)
        assertTrue(checksum!!.all { it in "0123456789abcdef" })
    }

    @Test
    fun checksumChangesWhenContentChanges() {
        val file = tempFolder.newFile("mutating.db")
        file.writeText("initial content")
        val initialChecksum = fileChecksum(file)

        file.writeText("modified content")
        val updatedChecksum = fileChecksum(file)

        assertNotNull(initialChecksum)
        assertNotNull(updatedChecksum)
        assertNotEquals(initialChecksum, updatedChecksum)
    }

    @Test
    fun returnsNullWhenReadingFileFails() {
        val failingFile = object : File(tempFolder.root, "ghost.db") {
            override fun exists() = true
            override fun isFile() = true
        }
        assertNull(fileChecksum(failingFile))

        val unreadableFile = File("/proc/self/mem")
        if (unreadableFile.exists()) {
            assertNull(fileChecksum(unreadableFile))
        }
    }

    @Test
    fun dbChecksumAndLastDbChecksumOperations() {
        val dataFile = tempFolder.newFile("sample_db.db")
        dataFile.writeText("pantrycraft-database-sample")
        val context = createMockContext(dataFile)

        val computed = dbChecksum(context)
        assertNotNull(computed)
        assertEquals(SHA256_HEX_LENGTH, computed?.length)

        assertNull(lastDbChecksum(context))
        saveLastDbChecksum(context, computed)
        assertEquals(computed, lastDbChecksum(context))

        saveLastDbChecksum(context, null)
        assertNull(lastDbChecksum(context))
    }

    private fun createMockContext(dbFile: File): Context {
        val prefsMap = mutableMapOf<String, Any?>()
        val mockPrefs = createMockPrefs(prefsMap)
        return object : ContextWrapper(null) {
            override fun getDatabasePath(name: String): File = dbFile
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = mockPrefs
        }
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

    companion object {
        private const val SHA256_HEX_LENGTH = 64
        private const val EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    }
}
