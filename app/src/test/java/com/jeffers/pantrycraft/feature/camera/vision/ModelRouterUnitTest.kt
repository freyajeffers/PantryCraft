package com.jeffers.pantrycraft.feature.camera.vision

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.jeffers.pantrycraft.data.AiProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ModelRouterUnitTest {
    private class CloseableVisionModel(
        val result: () -> String,
    ) : LocalVisionModel,
        AutoCloseable {
        var closed = false

        override suspend fun analyze(
            webpBytes: ByteArray,
            prompt: String,
        ): String = result()

        override fun close() {
            closed = true
        }
    }

    private fun createMockContext(
        providerId: String? = null,
        hasApiKey: Boolean = false,
    ): Context {
        val prefsMap = mutableMapOf<String, Any?>()
        if (providerId != null) {
            prefsMap["selected_provider"] = providerId
        }
        if (hasApiKey) {
            prefsMap["api_key_ciphertext"] = "dummy"
            prefsMap["api_key_iv"] = "dummy"
        }
        val mockPrefs =
            object : SharedPreferences {
                override fun getAll(): Map<String, *> = prefsMap

                override fun getString(
                    key: String,
                    defValue: String?,
                ): String? = prefsMap[key] as? String ?: defValue

                @Suppress("UNCHECKED_CAST")
                override fun getStringSet(
                    key: String,
                    def: Set<String>?,
                ): Set<String>? = prefsMap[key] as? Set<String> ?: def

                override fun getInt(
                    key: String,
                    def: Int,
                ): Int = prefsMap[key] as? Int ?: def

                override fun getLong(
                    key: String,
                    def: Long,
                ): Long = prefsMap[key] as? Long ?: def

                override fun getFloat(
                    key: String,
                    def: Float,
                ): Float = prefsMap[key] as? Float ?: def

                override fun getBoolean(
                    key: String,
                    def: Boolean,
                ): Boolean = prefsMap[key] as? Boolean ?: def

                override fun contains(key: String): Boolean = key in prefsMap

                override fun edit(): SharedPreferences.Editor = throw UnsupportedOperationException()

                override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

                override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) =
                    Unit
            }
        return object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this

            override fun getSharedPreferences(
                name: String,
                mode: Int,
            ): SharedPreferences = mockPrefs
        }
    }

    @Test
    fun modelRouterCloseClosesBothModels() {
        val local = CloseableVisionModel { "local" }
        val cloud = CloseableVisionModel { "cloud" }
        val context = createMockContext()
        val router = ModelRouter(context, local, cloud)

        router.close()

        assertTrue(local.closed)
        assertTrue(cloud.closed)

        val nonCloseableLocal = LocalVisionModel { _, _ -> "plain" }
        val nonCloseableCloud = LocalVisionModel { _, _ -> "plain" }
        val routerNonCloseable = ModelRouter(context, nonCloseableLocal, nonCloseableCloud)
        routerNonCloseable.close()
    }

    @Test
    fun modelRouterFallbackWhenOfflineProvider() =
        runBlocking {
            val local = CloseableVisionModel { "local_result" }
            val cloud = CloseableVisionModel { "cloud_result" }
            val context = createMockContext(AiProvider.OFFLINE_MLKIT.id)
            val router = ModelRouter(context, local, cloud)

            val result = router.analyze(ByteArray(0), "test")
            assertEquals("local_result", result)
        }

    @Test
    fun modelRouterCloudWhenOnlineAndConfigured() =
        runBlocking {
            val local = CloseableVisionModel { "local_result" }
            val cloud = CloseableVisionModel { "cloud_result" }
            val context = createMockContext(AiProvider.GEMINI.id, hasApiKey = true)
            val router = ModelRouter(context, local, cloud)

            val result = router.analyze(ByteArray(0), "test")
            assertEquals("cloud_result", result)
        }

    @Test
    fun modelRouterFallbackWhenCloudFails() =
        runBlocking {
            val local = CloseableVisionModel { "local_result" }
            val cloud = CloseableVisionModel { throw IOException("Network error") }
            val context = createMockContext(AiProvider.GEMINI.id, hasApiKey = true)
            val router = ModelRouter(context, local, cloud)

            val result = router.analyze(ByteArray(0), "test")
            assertEquals("local_result", result)
        }
}
