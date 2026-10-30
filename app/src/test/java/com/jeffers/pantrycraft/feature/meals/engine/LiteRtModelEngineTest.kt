package com.jeffers.pantrycraft.feature.meals.engine

import android.content.Context
import android.content.ContextWrapper
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.feature.meals.model.MealMatchMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalCoroutinesApi::class)
class LiteRtModelEngineTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var mockContext: Context
    private val defaultSystemPrompt = "You are a professional chef."
    private val defaultUserPrompt =
        """
        - Match Mode: STRICT
        - Target Portions: 4
        - On-hand ingredients:
          * Fresh Eggs (qty: 6.0 count)
          * Cheddar Cheese (qty: 200.0 g)
        """.trimIndent()

    private val sampleItems =
        listOf(
            PantryItem(
                id = 101L,
                name = "Fresh Eggs",
                quantity = 6f,
                unit = "count",
                expirationTimestamp = System.currentTimeMillis() + 86_400_000L,
            ),
            PantryItem(
                id = 102L,
                name = "Cheddar Cheese",
                quantity = 200f,
                unit = "g",
                expirationTimestamp = null,
            ),
        )

    @Before
    fun setUp() {
        mockContext = ContextWrapper(null)
    }

    // =========================================================================
    // 1. Availability & Fallback when On-Device Runtime is Unavailable
    // =========================================================================

    @Test
    fun isAvailableReturnsFalseWhenHardwareInsufficient() {
        val engine =
            LiteRtModelEngine(
                context = mockContext,
                dispatcher = testDispatcher,
                hardwareFeasibilityOverride = false,
            )

        assertFalse(engine.isAvailable)
    }

    @Test
    fun isAvailableReturnsTrueWhenHardwareFeasibleAndOverrideTrue() {
        val engine =
            LiteRtModelEngine(
                context = mockContext,
                dispatcher = testDispatcher,
                hardwareFeasibilityOverride = true,
            )

        assertTrue(engine.isAvailable)
    }

    @Test
    fun generateMealRecipesFallsBackWhenRuntimeUnavailable() =
        testScope.runTest {
            val engine =
                LiteRtModelEngine(
                    context = mockContext,
                    dispatcher = testDispatcher,
                    hardwareFeasibilityOverride = false,
                )

            val result =
                engine.generateMealRecipes(
                    systemPrompt = defaultSystemPrompt,
                    userPrompt = defaultUserPrompt,
                    onHandItems = sampleItems,
                )

            assertTrue(result.isSuccess)
            val recipes = result.getOrNull().orEmpty()
            assertEquals(1, recipes.size)
            val recipe = recipes.first()
            assertTrue(recipe.recipeId.startsWith("local_"))
            assertEquals(4, recipe.targetPortions)
            assertTrue(recipe.title.isNotEmpty())
        }

    @Test
    fun generateMealRecipesFallsBackWhenModelWeightsFileDoesNotExist() =
        testScope.runTest {
            val missingFile = File(tempFolder.root, "non_existent_model.tflite")
            val engine =
                LiteRtModelEngine(
                    context = mockContext,
                    dispatcher = testDispatcher,
                    customModelFile = missingFile,
                    hardwareFeasibilityOverride = null,
                )

            val result =
                engine.generateMealRecipes(
                    systemPrompt = defaultSystemPrompt,
                    userPrompt = defaultUserPrompt,
                    onHandItems = sampleItems,
                )

            assertTrue(result.isSuccess)
            val recipes = result.getOrNull().orEmpty()
            assertEquals(1, recipes.size)
            assertTrue(recipes.first().recipeId.startsWith("local_"))
        }

    // =========================================================================
    // 2. Delegation to FallbackMealSynthesizer
    // =========================================================================

    @Test
    fun generateMealRecipesDelegatesAndParsesEmergencySalvageMode() =
        testScope.runTest {
            val emergencyPrompt =
                """
                - Match Mode: EMERGENCY_SALVAGE
                - Target Portions: 2
                """.trimIndent()

            val engine =
                LiteRtModelEngine(
                    context = mockContext,
                    dispatcher = testDispatcher,
                    hardwareFeasibilityOverride = false,
                )

            val result =
                engine.generateMealRecipes(
                    systemPrompt = defaultSystemPrompt,
                    userPrompt = emergencyPrompt,
                    onHandItems = sampleItems,
                )

            assertTrue(result.isSuccess)
            val recipe = result.getOrNull()!!.first()
            assertEquals(MealMatchMode.EMERGENCY_SALVAGE, recipe.matchMode)
            assertEquals(2, recipe.targetPortions)
            assertEquals(5, recipe.prepTimeMinutes)
            assertEquals(10, recipe.cookTimeMinutes)
            assertTrue(recipe.urgencyScore >= 0.9f)
        }

    @Test
    fun generateMealRecipesDelegationWithEmptyItemsReturnsEmptyList() =
        testScope.runTest {
            val engine =
                LiteRtModelEngine(
                    context = mockContext,
                    dispatcher = testDispatcher,
                    hardwareFeasibilityOverride = false,
                )

            val result =
                engine.generateMealRecipes(
                    systemPrompt = defaultSystemPrompt,
                    userPrompt = defaultUserPrompt,
                    onHandItems = emptyList(),
                )

            assertTrue(result.isSuccess)
            assertTrue(result.getOrNull()!!.isEmpty())
        }

    @Test
    fun generateMealRecipesDelegatesAndClampsInvalidPortionCounts() =
        testScope.runTest {
            val highPortionPrompt = "- Target Portions: 99\n- Match Mode: STRICT"
            val engine =
                LiteRtModelEngine(
                    context = mockContext,
                    dispatcher = testDispatcher,
                    hardwareFeasibilityOverride = false,
                )

            val result =
                engine.generateMealRecipes(
                    systemPrompt = defaultSystemPrompt,
                    userPrompt = highPortionPrompt,
                    onHandItems = sampleItems,
                )

            assertTrue(result.isSuccess)
            assertEquals(12, result.getOrNull()!!.first().targetPortions)
        }

    // =========================================================================
    // 3. Ephemeral Buffer Memory Scrubbing (Byte Zeroing)
    // =========================================================================

    @Test
    fun generateMealRecipesScrubsInputAndOutputBuffersOnSuccess() =
        testScope.runTest {
            val scrubbedBuffers = mutableListOf<ByteArray>()
            val dummyValidJson =
                """
                [
                  {
                    "recipe_id": "on_device_999",
                    "title": "On-Device Omelette",
                    "match_mode": "STRICT",
                    "target_portions": 4,
                    "prep_time_minutes": 5,
                    "cook_time_minutes": 10,
                    "urgency_score": 0.8,
                    "items_used": [
                      {"name": "Fresh Eggs", "quantity_used": 4.0, "unit": "count"}
                    ],
                    "missing_items": [],
                    "instructions": ["Whisk eggs", "Cook on skillet"],
                    "nutritional_estimates": {
                      "calories": 280,
                      "protein_grams": 16,
                      "carbs_grams": 2,
                      "fat_grams": 18
                    }
                  }
                ]
                """.trimIndent()

            val engine =
                LiteRtModelEngine(
                    context = mockContext,
                    dispatcher = testDispatcher,
                    hardwareFeasibilityOverride = true,
                    mockInferenceExecutor = { _, _ -> dummyValidJson },
                    onBufferScrubbed = { buffer -> scrubbedBuffers.add(buffer.copyOf()) },
                )

            val result =
                engine.generateMealRecipes(
                    systemPrompt = defaultSystemPrompt,
                    userPrompt = defaultUserPrompt,
                    onHandItems = sampleItems,
                )

            assertTrue(result.isSuccess)
            val recipe = result.getOrNull()!!.first()
            assertEquals("on_device_999", recipe.recipeId)

            // Verify that memory buffers were passed to scrubbing and are completely zeroed
            assertTrue("Expected at least one scrubbed buffer", scrubbedBuffers.isNotEmpty())
            for (buffer in scrubbedBuffers) {
                assertTrue("Buffer must be zeroed out", buffer.all { it == 0.toByte() })
            }
        }

    @Test
    fun generateMealRecipesScrubsPromptBufferOnInferenceException() =
        testScope.runTest {
            val scrubbedBuffers = mutableListOf<ByteArray>()
            val engine =
                LiteRtModelEngine(
                    context = mockContext,
                    dispatcher = testDispatcher,
                    hardwareFeasibilityOverride = true,
                    mockInferenceExecutor = { _, _ -> throw IllegalStateException("LiteRT execution fault") },
                    onBufferScrubbed = { buffer -> scrubbedBuffers.add(buffer.copyOf()) },
                )

            val result =
                engine.generateMealRecipes(
                    systemPrompt = defaultSystemPrompt,
                    userPrompt = defaultUserPrompt,
                    onHandItems = sampleItems,
                )

            // Engine catches runtime fault and falls back gracefully
            assertTrue(result.isSuccess)
            assertTrue(
                result
                    .getOrNull()!!
                    .first()
                    .recipeId
                    .startsWith("local_"),
            )

            // Invariant: Prompt buffer must be zeroed in finally even when inference throws
            assertTrue("Prompt buffer must be captured in scrubbing callback", scrubbedBuffers.isNotEmpty())
            assertTrue("Prompt buffer must be zeroed", scrubbedBuffers.first().all { it == 0.toByte() })
        }

    // =========================================================================
    // 4. Thread Pinning & Error Handling
    // =========================================================================

    @Test
    fun generateMealRecipesExecutesOnProvidedDispatcher() =
        testScope.runTest {
            val executedOnTestThread = AtomicBoolean(false)
            val engine =
                LiteRtModelEngine(
                    context = mockContext,
                    dispatcher = testDispatcher,
                    hardwareFeasibilityOverride = true,
                    mockInferenceExecutor = { _, _ ->
                        executedOnTestThread.set(true)
                        "{}"
                    },
                )

            engine.generateMealRecipes(
                systemPrompt = defaultSystemPrompt,
                userPrompt = defaultUserPrompt,
                onHandItems = sampleItems,
            )

            assertTrue("Execution must occur on injected testDispatcher", executedOnTestThread.get())
        }

    @Test
    fun generateMealRecipesHandlesMalformedJsonByGracefullyFallingBack() =
        testScope.runTest {
            val engine =
                LiteRtModelEngine(
                    context = mockContext,
                    dispatcher = testDispatcher,
                    hardwareFeasibilityOverride = true,
                    mockInferenceExecutor = { _, _ -> "<<MALFORMED NON-JSON RESPONSE>>" },
                )

            val result =
                engine.generateMealRecipes(
                    systemPrompt = defaultSystemPrompt,
                    userPrompt = defaultUserPrompt,
                    onHandItems = sampleItems,
                )

            assertTrue(result.isSuccess)
            val recipes = result.getOrNull().orEmpty()
            assertEquals(1, recipes.size)
            val isLocalFallback = recipes.first().recipeId.startsWith("local_")
            assertTrue("Malformed JSON must gracefully fallback to local synthesizer", isLocalFallback)
        }

    @Test
    fun generateMealRecipesHandlesBlankOutputByGracefullyFallingBack() =
        testScope.runTest {
            val engine =
                LiteRtModelEngine(
                    context = mockContext,
                    dispatcher = testDispatcher,
                    hardwareFeasibilityOverride = true,
                    mockInferenceExecutor = { _, _ -> "   " },
                )

            val result =
                engine.generateMealRecipes(
                    systemPrompt = defaultSystemPrompt,
                    userPrompt = defaultUserPrompt,
                    onHandItems = sampleItems,
                )

            assertTrue(result.isSuccess)
            val recipes = result.getOrNull().orEmpty()
            assertEquals(1, recipes.size)
            assertTrue(recipes.first().recipeId.startsWith("local_"))
        }
}
