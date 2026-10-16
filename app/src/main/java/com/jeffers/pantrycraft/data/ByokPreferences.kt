package com.jeffers.pantrycraft.data

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypted, local-only storage for optional model-provider credentials. */
object ByokPreferences {
    enum class Provider { OFFLINE, GEMINI, OPENAI, ANTHROPIC, OLLAMA }

    data class Configuration(
        val provider: Provider,
        val endpoint: String?,
        val apiKey: String?,
    )

    private const val KEY_ALIAS = "pantrycraft-byok-v1"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val PREFS = "byok_preferences"
    private const val PROVIDER = "provider"
    private const val ENDPOINT = "endpoint"
    private const val API_KEY = "api_key"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    fun read(context: Context): Configuration {
        val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val provider =
            runCatching { Provider.valueOf(preferences.getString(PROVIDER, Provider.OFFLINE.name)!!) }
                .getOrDefault(Provider.OFFLINE)
        return Configuration(
            provider = provider,
            endpoint = preferences.getString(ENDPOINT, null)?.let { decrypt(it) },
            apiKey = preferences.getString(API_KEY, null)?.let { decrypt(it) },
        )
    }

    fun save(
        context: Context,
        provider: Provider,
        apiKey: String?,
        endpoint: String?,
    ) {
        require(provider == Provider.OFFLINE || !apiKey.isNullOrBlank()) { "An API key is required for a cloud provider" }
        val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = preferences.edit().putString(PROVIDER, provider.name)
        if (endpoint.isNullOrBlank()) editor.remove(ENDPOINT) else editor.putString(ENDPOINT, encrypt(endpoint))
        if (apiKey.isNullOrBlank()) editor.remove(API_KEY) else editor.putString(API_KEY, encrypt(apiKey))
        check(editor.commit()) { "Unable to persist model configuration" }
    }

    fun clear(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        require(bytes.size > IV_BYTES) { "Encrypted BYOKey value is corrupt" }
        val iv = bytes.copyOfRange(0, IV_BYTES)
        val ciphertext = bytes.copyOfRange(IV_BYTES, bytes.size)
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
            String(doFinal(ciphertext), StandardCharsets.UTF_8)
        }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) return keyStore.getKey(KEY_ALIAS, null) as SecretKey
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && strongBox) builder.setIsStrongBoxBacked(true)
        return builder.build()
    }
}
