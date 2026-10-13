package com.jeffers.pantrycraft.data

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

object DatabaseKeyProvider {
    private const val KEY_ALIAS = "pantrycraft-db-hmac-v1"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private val purpose = "PantryCraft SQLCipher database key v1".toByteArray(StandardCharsets.UTF_8)

    fun getKey(): ByteArray {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val key =
            if (keyStore.containsAlias(KEY_ALIAS)) {
                try {
                    keyStore.getKey(KEY_ALIAS, null) as SecretKey
                } catch (error: KeyPermanentlyInvalidatedException) {
                    throw DatabaseKeyInvalidatedException(error)
                }
            } else {
                generateKey()
            }
        return try {
            Mac.getInstance("HmacSHA256").run {
                init(key)
                doFinal(purpose)
            }
        } catch (error: KeyPermanentlyInvalidatedException) {
            throw DatabaseKeyInvalidatedException(error)
        }
    }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE)
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
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                ).setDigests(KeyProperties.DIGEST_SHA256)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && strongBox) {
            builder.setIsStrongBoxBacked(true)
        }
        return builder.build()
    }
}

class DatabaseKeyInvalidatedException(
    cause: Throwable,
) : IllegalStateException("The hardware-backed database key is no longer valid", cause)
