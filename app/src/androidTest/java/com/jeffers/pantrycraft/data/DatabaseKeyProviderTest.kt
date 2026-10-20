package com.jeffers.pantrycraft.data

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseKeyProviderTest {
    private lateinit var context: Context
    private var originalKey: ByteArray? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        originalKey = DatabaseKeyProvider.getKey(context).copyOf()
    }

    @After
    fun tearDown() {
        if (originalKey != null) {
            DatabaseKeyProvider.restorePassphrase(context, originalKey!!)
            originalKey!!.fill(0)
        }
    }

    @Test
    fun keyMaterialIsStableAnd256Bit() {
        val first = DatabaseKeyProvider.getKey(context)
        val second = DatabaseKeyProvider.getKey(context)

        assertEquals(32, first.size)
        assertArrayEquals(first, second)
    }

    @Test
    fun restorePassphraseReplacesKeyAndDecryptsCorrectly() {
        val customPassphrase = ByteArray(32) { (it + 42).toByte() }
        DatabaseKeyProvider.restorePassphrase(context, customPassphrase)

        val retrievedKey = DatabaseKeyProvider.getKey(context)
        assertEquals(32, retrievedKey.size)
        assertArrayEquals(customPassphrase, retrievedKey)
    }

    @Test
    fun shortCorruptedWrappedPassphraseThrowsIllegalArgumentException() {
        val prefs = context.getSharedPreferences("pantrycraft_database_key", Context.MODE_PRIVATE)
        val shortPayload = Base64.encodeToString(byteArrayOf(1, 2, 3), Base64.NO_WRAP)
        prefs.edit().putString("wrapped_passphrase", shortPayload).commit()

        val exception = assertThrows(IllegalArgumentException::class.java) {
            DatabaseKeyProvider.getKey(context)
        }
        assertEquals("Encrypted database passphrase is corrupt", exception.message)
    }

    @Test
    fun tamperedCiphertextThrowsAuthenticationFailure() {
        val prefs = context.getSharedPreferences("pantrycraft_database_key", Context.MODE_PRIVATE)
        val currentWrapped = Base64.decode(prefs.getString("wrapped_passphrase", "")!!, Base64.NO_WRAP)
        // Flip a bit in the ciphertext portion (after 12 byte IV)
        currentWrapped[15] = (currentWrapped[15].toInt() xor 0xFF).toByte()
        prefs.edit().putString("wrapped_passphrase", Base64.encodeToString(currentWrapped, Base64.NO_WRAP)).commit()

        val exception = assertThrows(IllegalStateException::class.java) {
            DatabaseKeyProvider.getKey(context)
        }
        assertEquals("Encrypted database passphrase cannot be authenticated", exception.message)
    }
}
