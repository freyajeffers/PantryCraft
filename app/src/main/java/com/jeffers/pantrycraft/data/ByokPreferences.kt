package com.jeffers.pantrycraft.data

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class AiProvider(
    val id: String,
    val label: String,
    val defaultModel: String,
    val defaultEndpoint: String,
    val requiresApiKey: Boolean,
) {
    OFFLINE_MLKIT(
        id = "OFFLINE_MLKIT",
        label = "On-Device Offline (ML Kit / Local)",
        defaultModel = "mlkit-local",
        defaultEndpoint = "",
        requiresApiKey = false,
    ),
    GEMINI(
        id = "GEMINI",
        label = "Google Gemini (BYOKey)",
        defaultModel = "gemini-2.5-flash",
        defaultEndpoint = "https://generativelanguage.googleapis.com/v1beta/models",
        requiresApiKey = true,
    ),
    OPENAI(
        id = "OPENAI",
        label = "OpenAI / OpenRouter (BYOKey)",
        defaultModel = "gpt-4o-mini",
        defaultEndpoint = "https://api.openai.com/v1/chat/completions",
        requiresApiKey = true,
    ),
    ANTHROPIC(
        id = "ANTHROPIC",
        label = "Anthropic Claude (BYOKey)",
        defaultModel = "claude-3-5-sonnet-latest",
        defaultEndpoint = "https://api.anthropic.com/v1/messages",
        requiresApiKey = true,
    ),
    OLLAMA(
        id = "OLLAMA",
        label = "Self-Hosted Ollama (Local LAN)",
        defaultModel = "llava:latest",
        defaultEndpoint = "http://127.0.0.1:11434/api/generate",
        requiresApiKey = false,
    ),
    ;

    companion object {
        fun fromId(id: String?): AiProvider = entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: OFFLINE_MLKIT
    }
}

object ByokPreferences {
    private const val PREFS_NAME = "byok_ai_preferences"
    private const val PROVIDER_KEY = "selected_provider"
    private const val MODEL_NAME_KEY = "selected_model_name"
    private const val ENDPOINT_URL_KEY = "selected_endpoint_url"
    private const val API_KEY_CIPHERTEXT = "api_key_ciphertext"
    private const val API_KEY_IV = "api_key_iv"
    private const val KEY_ALIAS = "pantrycraft_byok_api_key_v1"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    fun selectedProvider(context: Context): AiProvider =
        AiProvider.fromId(preferences(context).getString(PROVIDER_KEY, AiProvider.OFFLINE_MLKIT.id))

    fun selectedModel(context: Context): String {
        val provider = selectedProvider(context)
        return preferences(context)
            .getString(MODEL_NAME_KEY, null)
            ?.takeIf { it.isNotBlank() }
            ?: provider.defaultModel
    }

    fun selectedEndpoint(context: Context): String {
        val provider = selectedProvider(context)
        return preferences(context)
            .getString(ENDPOINT_URL_KEY, null)
            ?.takeIf { it.isNotBlank() }
            ?: provider.defaultEndpoint
    }

    fun saveConfiguration(
        context: Context,
        provider: AiProvider,
        modelName: String,
        endpointUrl: String,
    ) {
        preferences(context)
            .edit()
            .putString(PROVIDER_KEY, provider.id)
            .putString(MODEL_NAME_KEY, modelName.trim().ifEmpty { provider.defaultModel })
            .putString(ENDPOINT_URL_KEY, endpointUrl.trim().ifEmpty { provider.defaultEndpoint })
            .apply()
    }

    fun saveApiKey(
        context: Context,
        apiKey: CharArray,
    ) {
        val trimmed = apiKey.concatToString().trim()
        if (trimmed.isEmpty()) {
            clearApiKey(context)
            apiKey.fill('\u0000')
            return
        }
        val plaintext = trimmed.toByteArray(StandardCharsets.UTF_8)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            preferences(context)
                .edit()
                .putString(
                    API_KEY_CIPHERTEXT,
                    Base64.encodeToString(cipher.doFinal(plaintext), Base64.NO_WRAP),
                ).putString(API_KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .apply()
        } finally {
            plaintext.fill(0)
            apiKey.fill('\u0000')
        }
    }

    fun loadApiKey(context: Context): String? {
        val prefs = preferences(context)
        val encodedCiphertext = prefs.getString(API_KEY_CIPHERTEXT, null)
        val encodedIv = prefs.getString(API_KEY_IV, null)
        if (encodedCiphertext == null || encodedIv == null) return null
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(encodedIv, Base64.NO_WRAP)),
            )
            val bytes = cipher.doFinal(Base64.decode(encodedCiphertext, Base64.NO_WRAP))
            try {
                bytes.toString(StandardCharsets.UTF_8)
            } finally {
                bytes.fill(0)
            }
        }.getOrNull()
    }

    fun hasApiKey(context: Context): Boolean = preferences(context).getString(API_KEY_CIPHERTEXT, null) != null

    fun clearApiKey(context: Context) {
        preferences(context)
            .edit()
            .remove(API_KEY_CIPHERTEXT)
            .remove(API_KEY_IV)
            .apply()
    }

    private fun preferences(context: Context) = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) return existingKey
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val baseBuilder = {
            KeyGenParameterSpec
                .Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(AES_KEY_SIZE_BITS)
        }
        val strongBoxKey =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runCatching {
                    generator.init(baseBuilder().setIsStrongBoxBacked(true).build())
                    generator.generateKey()
                }.getOrNull()
            } else {
                null
            }
        return strongBoxKey ?: generator.run {
            init(baseBuilder().build())
            generateKey()
        }
    }

    private const val AES_KEY_SIZE_BITS = 256
    private const val GCM_TAG_LENGTH_BITS = 128
}
