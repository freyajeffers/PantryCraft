package com.jeffers.pantrycraft.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
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
                keyStore.getKey(KEY_ALIAS, null) as SecretKey
            } else {
                KeyGenerator
                    .getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE)
                    .apply {
                        init(
                            KeyGenParameterSpec
                                .Builder(
                                    KEY_ALIAS,
                                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                                ).setDigests(KeyProperties.DIGEST_SHA256)
                                .build(),
                        )
                    }.generateKey()
            }
        return Mac.getInstance("HmacSHA256").run {
            init(key)
            doFinal(purpose)
        }
    }
}
