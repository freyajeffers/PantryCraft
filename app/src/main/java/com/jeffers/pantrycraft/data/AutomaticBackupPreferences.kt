package com.jeffers.pantrycraft.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object AutomaticBackupPreferences {
    private const val PREFS_NAME = "backup_preferences"
    private const val ENABLED_KEY = "automatic_backup_enabled"
    private const val INTERVAL_MINUTES_KEY = "automatic_backup_interval_minutes"
    private const val PIN_CIPHERTEXT_KEY = "automatic_backup_pin_ciphertext"
    private const val PIN_IV_KEY = "automatic_backup_pin_iv"
    private const val KEY_ALIAS = "pantrycraft_automatic_backup_pin"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    fun isEnabled(context: Context): Boolean = preferences(context).getBoolean(ENABLED_KEY, false)

    fun intervalMinutes(context: Context): Long = preferences(context).getLong(INTERVAL_MINUTES_KEY, 24 * 60L)

    fun setSchedule(
        context: Context,
        enabled: Boolean,
        intervalMinutes: Long,
    ) {
        preferences(context)
            .edit()
            .putBoolean(ENABLED_KEY, enabled)
            .putLong(INTERVAL_MINUTES_KEY, intervalMinutes)
            .apply()
    }

    fun savePin(
        context: Context,
        pin: CharArray,
    ) {
        val plaintext = pin.concatToString().toByteArray(StandardCharsets.UTF_8)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val preferences = preferences(context)
            preferences
                .edit()
                .putString(PIN_CIPHERTEXT_KEY, Base64.encodeToString(cipher.doFinal(plaintext), Base64.NO_WRAP))
                .putString(PIN_IV_KEY, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .apply()
        } finally {
            plaintext.fill(0)
            pin.fill('\u0000')
        }
    }

    fun loadPin(context: Context): CharArray? {
        val preferences = preferences(context)
        val encodedCiphertext = preferences.getString(PIN_CIPHERTEXT_KEY, null) ?: return null
        val encodedIv = preferences.getString(PIN_IV_KEY, null) ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, Base64.decode(encodedIv, Base64.NO_WRAP)),
        )
        val plaintext = cipher.doFinal(Base64.decode(encodedCiphertext, Base64.NO_WRAP))
        return try {
            plaintext.toString(StandardCharsets.UTF_8).toCharArray()
        } finally {
            plaintext.fill(0)
        }
    }

    private fun preferences(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val baseBuilder = {
            KeyGenParameterSpec
                .Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            runCatching {
                generator.init(baseBuilder().setIsStrongBoxBacked(true).build())
                return generator.generateKey()
            }
        }
        generator.init(baseBuilder().build())
        return generator.generateKey()
    }
}
