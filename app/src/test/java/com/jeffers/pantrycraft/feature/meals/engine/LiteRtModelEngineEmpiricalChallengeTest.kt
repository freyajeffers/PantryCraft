package com.jeffers.pantrycraft.feature.meals.engine

import android.content.Context
import android.content.ContextWrapper
import com.jeffers.pantrycraft.data.PantryItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class LiteRtModelEngineEmpiricalChallengeTest {
    private lateinit var mockContext: Context
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val sampleItems =
        listOf(
            PantryItem(
                id = 1L,
                name = "Organic Spinach",
                quantity = 250f,
                unit = "g",
                expirationTimestamp = System.currentTimeMillis() + 86_400_000L,
            ),
            PantryItem(
                id = 2L,
                name = "Tofu",
                quantity = 400f,
                unit = "g",
                expirationTimestamp = null,
            ),
        )

    private val validJson =
        """
        [
          {
            "recipe_id": "test_recipe_stress_1",
            "title": "Crispy Tofu Bowl",
            "match_mode": "STRICT",
            "target_portions": 2,
            "prep_time_minutes": 10,
            "cook_time_minutes": 15,
            "urgency_score": 0.9,
            "items_used": [
              {"name": "Tofu", "quantity_used": 400.0, "unit": "g"},
              {"name": "Organic Spinach", "quantity_used": 200.0, "unit": "g"}
            ],
            "missing_items": [],
            "instructions": ["Press tofu", "Saute spinach"],
            "nutritional_estimates": {
              "calories": 320,
              "protein_grams": 24,
              "carbs_grams": 8,
              "fat_grams": 16
            }
          }
        ]
        """.trimIndent()

    @Before
    fun setUp() {
        mockContext = ContextWrapper(null)
    }

    @Test
    fun challengePromptBufferZeroedUnderCoroutineCancellation() =
        runBlocking {
            val capturedBuffers = ConcurrentLinkedQueue<ByteArray>()
            val latch = CountDownLatch(1)
            val inferenceStarted = AtomicBoolean(false)

            val engine =
                LiteRtModelEngine(
                    context = mockContext,
                    dispatcher = Dispatchers.Default,
                    hardwareFeasibilityOverride = true,
                    mockInferenceExecutor = { _, _ ->
                        inferenceStarted.set(true)
                        latch.countDown()
                        // Simulate long-running inference that gets cancelled
                        try {
                            Thread.sleep(2000)
                        } catch (_: InterruptedException) {
                            throw CancellationException("Simulated thread interrupt / cancellation")
                        }
                        throw CancellationException("Coroutine cancelled during inference")
                    },
                    onBufferScrubbed = { buffer ->
                        capturedBuffers.add(buffer.copyOf())
                    },
                )

            val job =
                launch(Dispatchers.Default) {
                    engine.generateMealRecipes(
                        systemPrompt = "System instructions",
                        userPrompt = "Top secret user ingredients and health constraints",
                        onHandItems = sampleItems,
                    )
                }

            assertTrue("Inference should start", latch.await(2, TimeUnit.SECONDS))
            job.cancelAndJoin()

            assertTrue("Prompt buffer must have been scrubbed", capturedBuffers.isNotEmpty())
            for (buffer in capturedBuffers) {
                assertTrue("Buffer must be zeroed", buffer.all { it == 0.toByte() })
            }
        }

    @Test
    fun challengePromptBufferZeroedUnderCancellationExceptionInInference() =
        testScope.runTest {
            val capturedBuffers = mutableListOf<ByteArray>()
            val engine =
                LiteRtModelEngine(
                    context = mockContext,
                    dispatcher = testDispatcher,
                    hardwareFeasibilityOverride = true,
                    mockInferenceExecutor = { _, _ ->
                        throw CancellationException("Explicit cancellation")
                    },
                    onBufferScrubbed = { buffer ->
                        capturedBuffers.add(buffer.copyOf())
                    },
                )

            try {
                engine.generateMealRecipes(
                    systemPrompt = "System instructions",
                    userPrompt = "Confidential prompt content",
                    onHandItems = sampleItems,
                )
                fail("Should rethrow CancellationException")
            } catch (_: CancellationException) {
                // Expected
            }

            assertEquals("Prompt buffer must be captured exactly once", 1, capturedBuffers.size)
            assertTrue("Prompt buffer must be zeroed", capturedBuffers.first().all { it == 0.toByte() })
        }

    @Test
    fun challengeBothBuffersZeroedUnderParsingFailure() =
        testScope.runTest {
            val capturedBuffers = mutableListOf<ByteArray>()
            val invalidJsonOutput = """{"invalid": "this is not an array of recipes"}"""

            val engine =
                LiteRtModelEngine(
                    context = mockContext,
                    dispatcher = testDispatcher,
                    hardwareFeasibilityOverride = true,
                    mockInferenceExecutor = { _, _ -> invalidJsonOutput },
                    onBufferScrubbed = { buffer ->
                        capturedBuffers.add(buffer.copyOf())
                    },
                )

            val result =
                engine.generateMealRecipes(
                    systemPrompt = "System instructions",
                    userPrompt = "Prompt for recipe",
                    onHandItems = sampleItems,
                )

            assertTrue(result.isSuccess)
            // Should have scrubbed both prompt buffer and output buffer
            assertEquals("Both prompt and output buffers must be scrubbed", 2, capturedBuffers.size)
            for (buf in capturedBuffers) {
                assertTrue("Every scrubbed byte in buffer must be 0", buf.all { it == 0.toByte() })
            }
        }

    @Test
    fun challengeHighConcurrencyBufferIsolationAndScrubbing() =
        runBlocking {
            val concurrencyLevel = 50
            val capturedBuffers = ConcurrentLinkedQueue<ByteArray>()
            val completionCounter = AtomicInteger(0)

            val engine =
                LiteRtModelEngine(
                    context = mockContext,
                    dispatcher = Dispatchers.Default,
                    hardwareFeasibilityOverride = true,
                    mockInferenceExecutor = { _, userPrompt ->
                        // Return valid JSON containing the prompt ID
                        validJson.replace("test_recipe_stress_1", "recipe_${userPrompt.hashCode()}")
                    },
                    onBufferScrubbed = { buffer ->
                        capturedBuffers.add(buffer.copyOf())
                    },
                )

            val jobs =
                (1..concurrencyLevel).map { index ->
                    async(Dispatchers.Default) {
                        val prompt = "User prompt iteration #$index with unique data ${System.nanoTime()}"
                        val result =
                            engine.generateMealRecipes(
                                systemPrompt = "System chef prompt",
                                userPrompt = prompt,
                                onHandItems = sampleItems,
                            )
                        if (result.isSuccess) {
                            completionCounter.incrementAndGet()
                        }
                    }
                }

            jobs.awaitAll()

            assertEquals("All concurrent requests should succeed", concurrencyLevel, completionCounter.get())
            assertEquals(
                "Exactly 2 buffers (prompt + output) per request must be scrubbed",
                concurrencyLevel * 2,
                capturedBuffers.size,
            )

            var totalScrubbedBytes = 0L
            for (buffer in capturedBuffers) {
                totalScrubbedBytes += buffer.size
                assertTrue("Every byte in captured buffer must be 0", buffer.all { it == 0.toByte() })
            }
            assertTrue("Substantial byte volume was wiped", totalScrubbedBytes > 1000)
        }

    @Test
    fun challengeLargePayloadBufferScrubbing() =
        testScope.runTest {
            val capturedBuffers = mutableListOf<ByteArray>()
            val largePrompt = "LargePromptData: " + "A".repeat(100_000)
            val largeOutput = validJson.replace("Crispy Tofu Bowl", "B".repeat(100_000))

            val engine =
                LiteRtModelEngine(
                    context = mockContext,
                    dispatcher = testDispatcher,
                    hardwareFeasibilityOverride = true,
                    mockInferenceExecutor = { _, _ -> largeOutput },
                    onBufferScrubbed = { buffer ->
                        capturedBuffers.add(buffer.copyOf())
                    },
                )

            val result =
                engine.generateMealRecipes(
                    systemPrompt = "System prompt",
                    userPrompt = largePrompt,
                    onHandItems = sampleItems,
                )

            assertTrue(result.isSuccess)
            assertEquals("Both buffers must be scrubbed", 2, capturedBuffers.size)
            val promptBuffer = capturedBuffers[0]
            val outputBuffer = capturedBuffers[1]

            assertTrue("Prompt buffer >= 100KB", promptBuffer.size >= 100_000)
            assertTrue("Prompt buffer is all zeroes", promptBuffer.all { it == 0.toByte() })

            assertTrue("Output buffer >= 100KB", outputBuffer.size >= 100_000)
            assertTrue("Output buffer is all zeroes", outputBuffer.all { it == 0.toByte() })
        }
}
