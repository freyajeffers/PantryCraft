package com.jeffers.pantrycraft.feature.camera.vision

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.pantrycraft.data.AiProvider
import com.jeffers.pantrycraft.data.ByokPreferences
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class ModelRouterIntegrationTest {
    private lateinit var context: Context
    private var originalProvider: AiProvider = AiProvider.OFFLINE_MLKIT
    private var originalModel: String = ""
    private var originalEndpoint: String = ""
    private var originalApiKey: String? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        originalProvider = ByokPreferences.selectedProvider(context)
        originalModel = ByokPreferences.selectedModel(context)
        originalEndpoint = ByokPreferences.selectedEndpoint(context)
        originalApiKey = ByokPreferences.loadApiKey(context)

        ByokPreferences.clearApiKey(context)
        ByokPreferences.saveConfiguration(context, AiProvider.OFFLINE_MLKIT, "", "")
    }

    @After
    fun tearDown() {
        if (originalApiKey != null) {
            val keyChars = originalApiKey!!.toCharArray()
            ByokPreferences.saveApiKey(context, keyChars)
            keyChars.fill('\u0000')
        } else {
            ByokPreferences.clearApiKey(context)
        }
        ByokPreferences.saveConfiguration(context, originalProvider, originalModel, originalEndpoint)
    }

    @Test
    fun offlineMlKitDispatchesToLocalFallback() = runBlocking {
        ByokPreferences.saveConfiguration(context, AiProvider.OFFLINE_MLKIT, "", "")

        val localModel = LocalVisionModel { _, _ -> "local-fallback-output" }
        val cloudModel = LocalVisionModel { _, _ -> "cloud-output" }
        val router = ModelRouter(context, localModel, cloudModel)

        val result = router.analyze(ByteArray(0), "test-prompt")
        assertEquals("local-fallback-output", result)
    }

    @Test
    fun cloudProviderWithoutApiKeyFallsBackToLocalModel() = runBlocking {
        ByokPreferences.saveConfiguration(context, AiProvider.OPENAI, "gpt-4o-mini", "")
        ByokPreferences.clearApiKey(context)

        val localModel = LocalVisionModel { _, _ -> "local-fallback-output" }
        val cloudModel = LocalVisionModel { _, _ -> "cloud-output" }
        val router = ModelRouter(context, localModel, cloudModel)

        val result = router.analyze(ByteArray(0), "test-prompt")
        assertEquals("local-fallback-output", result)
    }

    @Test
    fun failingCloudModelCatchesAndFallsBackToLocalModel() = runBlocking {
        ByokPreferences.saveConfiguration(context, AiProvider.OLLAMA, "llava:latest", "http://127.0.0.1:11434")

        val localModel = LocalVisionModel { _, _ -> "local-fallback-output" }
        val cloudModel = LocalVisionModel { _, _ -> throw IOException("Network unreachable") }
        val router = ModelRouter(context, localModel, cloudModel)

        val result = router.analyze(ByteArray(0), "test-prompt")
        assertEquals("local-fallback-output", result)
    }

    @Test
    fun successfulCloudModelReturnsCloudOutput() = runBlocking {
        ByokPreferences.saveConfiguration(context, AiProvider.OLLAMA, "llava:latest", "http://127.0.0.1:11434")

        val localModel = LocalVisionModel { _, _ -> "local-fallback-output" }
        val cloudModel = LocalVisionModel { _, _ -> "cloud-output" }
        val router = ModelRouter(context, localModel, cloudModel)

        val result = router.analyze(ByteArray(0), "test-prompt")
        assertEquals("cloud-output", result)
    }
}
