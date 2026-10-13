package com.jeffers.pantrycraft.data

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object DatabaseKeyProvider {
    private const val KEY_ALIAS = "pantrycraft-db-wrap-v1"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val PREFERENCES = "pantrycraft_database_key"
    private const val WRAPPED_KEY = "wrapped_passphrase"
    private const val PASSPHRASE_BYTES = 32
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private val lock = Any()

    fun getKey(context: Context): ByteArray {
        return synchronized(lock) {
            val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            val encoded = preferences.getString(WRAPPED_KEY, null)
            if (encoded == null) {
                val passphrase = ByteArray(PASSPHRASE_BYTES).also(SecureRandom()::nextBytes)
                return@synchronized wrapAndPersist(preferences, passphrase)
            }
            decrypt(Base64.decode(encoded, Base64.NO_WRAP))
        }
    }

    @Suppress("ktlint:standard:function-expression-body")
    private fun wrapAndPersist(
        preferences: android.content.SharedPreferences,
        passphrase: ByteArray,
    ): ByteArray {
        return try {
            val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
            val cipher = cipher(Cipher.ENCRYPT_MODE, getOrCreateWrappingKey(), iv)
            val ciphertext = cipher.doFinal(passphrase)
            val wrapped = ByteArray(iv.size + ciphertext.size)
            iv.copyInto(wrapped)
            ciphertext.copyInto(wrapped, destinationOffset = iv.size)
            check(
                preferences
                    .edit()
                    .putString(WRAPPED_KEY, Base64.encodeToString(wrapped, Base64.NO_WRAP))
                    .commit(),
            ) { "Unable to persist the encrypted database passphrase" }
            wrapped.fill(0)
            iv.fill(0)
            ciphertext.fill(0)
            passphrase
        } catch (error: KeyPermanentlyInvalidatedException) {
            passphrase.fill(0)
            throw DatabaseKeyInvalidatedException(error)
        }
    }

    private fun decrypt(wrapped: ByteArray): ByteArray {
        require(wrapped.size > IV_BYTES) { "Encrypted database passphrase is corrupt" }
        val iv = wrapped.copyOfRange(0, IV_BYTES)
        val ciphertext = wrapped.copyOfRange(IV_BYTES, wrapped.size)
        return try {
            cipher(Cipher.DECRYPT_MODE, getOrCreateWrappingKey(), iv).doFinal(ciphertext)
        } catch (error: KeyPermanentlyInvalidatedException) {
            throw DatabaseKeyInvalidatedException(error)
        } catch (error: AEADBadTagException) {
            throw IllegalStateException("Encrypted database passphrase cannot be authenticated", error)
        } finally {
            wrapped.fill(0)
            iv.fill(0)
            ciphertext.fill(0)
        }
    }

    private fun cipher(
        mode: Int,
        key: SecretKey,
        iv: ByteArray,
    ): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        }

    private fun getOrCreateWrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return try {
                keyStore.getKey(KEY_ALIAS, null) as SecretKey
            } catch (error: KeyPermanentlyInvalidatedException) {
                throw DatabaseKeyInvalidatedException(error)
            }
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        return try {
            generator.init(keySpec(strongBox = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P))
            generator.generateKey()
        } catch (_: StrongBoxUnavailableException) {
            generator.init(keySpec(strongBox = false))
            generator.generateKey()
        }
    }

    private fun keySpec(strongBox: Boolean): KeyGenParameterSpec {
        val builder =
            KeyGenParameterSpec
                .Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && strongBox) {
            builder.setIsStrongBoxBacked(true)
        }
        return builder.build()
    }
}

class DatabaseKeyInvalidatedException(
    cause: Throwable,
) : IllegalStateException("The hardware-backed database key is no longer valid", cause)
