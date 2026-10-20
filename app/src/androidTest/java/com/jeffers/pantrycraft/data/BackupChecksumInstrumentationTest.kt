package com.jeffers.pantrycraft.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupChecksumInstrumentationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var originalChecksum: String? = null

    @Before
    fun setUp() {
        originalChecksum = lastDbChecksum(context)
        saveLastDbChecksum(context, null)
    }

    @After
    fun tearDown() {
        saveLastDbChecksum(context, originalChecksum)
    }

    @Test
    fun lastChecksumDefaultsToNull() {
        assertNull(lastDbChecksum(context))
    }

    @Test
    fun savesAndRetrievesLastDbChecksum() {
        val testChecksum = "a".repeat(64)
        saveLastDbChecksum(context, testChecksum)

        assertEquals(testChecksum, lastDbChecksum(context))

        saveLastDbChecksum(context, null)
        assertNull(lastDbChecksum(context))
    }

    @Test
    fun dbChecksumReturnsValidHexDigestForActiveEncryptedDatabase() {
        val database = AppDatabase.getDatabase(context)
        assertNotNull(database)
        val checksum = dbChecksum(context)
        assertNotNull(checksum)
        assertEquals(64, checksum?.length)
        assertTrue(checksum!!.all { it in "0123456789abcdef" })
    }
}
