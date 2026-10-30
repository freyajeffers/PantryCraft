package com.jeffers.pantrycraft.feature.meals.engine

import android.content.Context
import android.content.ContextWrapper
import com.jeffers.pantrycraft.data.CulinaryScaling
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.feature.meals.model.MAX_PORTIONS
import com.jeffers.pantrycraft.feature.meals.model.MIN_PORTIONS
import com.jeffers.pantrycraft.feature.meals.model.MealMatchMode
import com.jeffers.pantrycraft.feature.meals.model.MealRecipeParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.math.pow

/**
 * Empirical challenger stress-test suite for LiteRtModelEngine, FallbackMealSynthesizer,
 * and portion scaling invariants.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiteRtModelEngineChallengerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var mockContext: Context

    private val sampleItems =
        listOf(
            PantryItem(
                id = 1L,
                name = "Garlic",
                quantity = 50f,
                unit = "g",
                isAromaticOrSeasoning = true,
                expirationTimestamp = System.currentTimeMillis() + 86_400_000L,
            ),
            PantryItem(
                id = 2L,
                name = "Chicken Breast",
                quantity = 500f,
                unit = "g",
                isAromaticOrSeasoning = false,
                expirationTimestamp = null,
            ),
        )

    @Before
    fun setUp() {
        mockContext = ContextWrapper(null)
    }

    // =========================================================================
    // 1. Portion Extraction & Boundary Tests in LiteRtModelEngine
    // =========================================================================

    @Test
    fun testExtractPortions_NegativePortionPrompt_DefaultsSafely() =
        testScope.runTest {
            val engine =
                LiteRtModelEngine(
                    context = mockContext,
                    dispatcher = testDispatcher,
                    hardwareFeasibilityOverride = false,
                )

            // "- Target Portions: -5" does not match \d+, should safely default to 2
            val prompt = "- Match Mode: STRICT\n- Target Portions: -5"
            val result = engine.generateMealRecipes("system", prompt, sampleItems)

            assertTrue("Expected successful fallback result", result.isSuccess)
            val recipe = result.getOrNull()!!.first()
            assertEquals(2, recipe.targetPortions)
        }

    @Test
    fun testExtractPortions_ZeroPortionsPrompt_ClampsToMinPortions() =
        testScope.runTest {
            val engine =
                LiteRtModelEngine(
                    context = mockContext,
                    dispatcher = testDispatcher,
                    hardwareFeasibilityOverride = false,
                )

            // "- Target Portions: 0" matches 0, should clamp to MIN_PORTIONS (1)
            val prompt = "- Match Mode: STRICT\n- Target Portions: 0"
            val result = engine.generateMealRecipes("system", prompt, sampleItems)

            assertTrue(result.isSuccess)
            val recipe = result.getOrNull()!!.first()
            assertEquals(MIN_PORTIONS, recipe.targetPortions)
        }

    @Test
    fun testExtractPortions_ExcessivePortionsPrompt_ClampsToMaxPortions() =
        testScope.runTest {
            val engine =
                LiteRtModelEngine(
                    context = mockContext,
                    dispatcher = testDispatcher,
                    hardwareFeasibilityOverride = false,
                )

            // "- Target Portions: 15" should clamp to MAX_PORTIONS (12)
            val prompt = "- Match Mode: STRICT\n- Target Portions: 15"
            val result = engine.generateMealRecipes("system", prompt, sampleItems)

            assertTrue(result.isSuccess)
            val recipe = result.getOrNull()!!.first()
            assertEquals(MAX_PORTIONS, recipe.targetPortions)
        }

    @Test
    fun testExtractPortions_IntegerOverflowPortions_DefaultsSafely() =
        testScope.runTest {
            val engine =
                LiteRtModelEngine(
                    context = mockContext,
                    dispatcher = testDispatcher,
                    hardwareFeasibilityOverride = false,
                )

            val prompt = "- Match Mode: STRICT\n- Target Portions: 9999999999999999999999999999"
            val result = engine.generateMealRecipes("system", prompt, sampleItems)

            assertTrue(result.isSuccess)
            val recipe = result.getOrNull()!!.first()
            assertEquals(2, recipe.targetPortions)
        }

    // =========================================================================
    // 2. Non-Standard and Adversarial Prompts
    // =========================================================================

    @Test
    fun testExtractMatchMode_CompletelyBlankPrompt_DefaultsToStrict() =
        testScope.runTest {
            val engine =
                LiteRtModelEngine(
                    context = mockContext,
                    dispatcher = testDispatcher,
                    hardwareFeasibilityOverride = false,
                )

            val result = engine.generateMealRecipes("", "", sampleItems)
            assertTrue(result.isSuccess)
            val recipe = result.getOrNull()!!.first()
            assertEquals(MealMatchMode.STRICT, recipe.matchMode)
            assertEquals(2, recipe.targetPortions)
        }

    @Test
    fun testExtractMatchMode_UnknownOrLowercaseMode_DefaultsToStrict() =
        testScope.runTest {
            val engine =
                LiteRtModelEngine(
                    context = mockContext,
                    dispatcher = testDispatcher,
                    hardwareFeasibilityOverride = false,
                )

            val prompt = "- Match Mode: mostly\n- Target Portions: 3"
            val result = engine.generateMealRecipes("sys", prompt, sampleItems)

            assertTrue(result.isSuccess)
            val recipe = result.getOrNull()!!.first()
            // Lowercase mode doesn't match id uppercase, so defaults to STRICT
            assertEquals(MealMatchMode.STRICT, recipe.matchMode)
            assertEquals(3, recipe.targetPortions)
        }

    // =========================================================================
    // 3. Blank, Malformed, and Hallucinated Model Outputs
    // =========================================================================

    @Test
    fun testMalformedOutput_WhitespaceAndHtml_FallsBackGracefully() =
        testScope.runTest {
            val whitespaceEngine =
                LiteRtModelEngine(
                    context = mockContext,
                    dispatcher = testDispatcher,
                    hardwareFeasibilityOverride = true,
                    mockInferenceExecutor = { _, _ -> "   \n\t   " },
                )

            val wsResult = whitespaceEngine.generateMealRecipes("sys", "- Match Mode: MOSTLY", sampleItems)
            assertTrue(wsResult.isSuccess)
            assertTrue(
                wsResult
                    .getOrNull()!!
                    .first()
                    .recipeId
                    .startsWith("local_"),
            )

            val htmlEngine =
                LiteRtModelEngine(
                    context = mockContext,
                    dispatcher = testDispatcher,
                    hardwareFeasibilityOverride = true,
                    mockInferenceExecutor = { _, _ -> "<html>502 Bad Gateway</html>" },
                )

            val htmlResult = htmlEngine.generateMealRecipes("sys", "- Match Mode: MOSTLY", sampleItems)
            assertTrue(htmlResult.isSuccess)
            assertTrue(
                htmlResult
                    .getOrNull()!!
                    .first()
                    .recipeId
                    .startsWith("local_"),
            )
        }

    @Test
    fun testMalformedOutput_EmptyJsonAndIncompleteArrays_FallsBackGracefully() =
        testScope.runTest {
            val emptyObjEngine =
                LiteRtModelEngine(
                    context = mockContext,
                    dispatcher = testDispatcher,
                    hardwareFeasibilityOverride = true,
                    mockInferenceExecutor = { _, _ -> "{}" },
                )

            val objResult = emptyObjEngine.generateMealRecipes("sys", "- Match Mode: STRICT", sampleItems)
            assertTrue(objResult.isSuccess)
            assertTrue(
                objResult
                    .getOrNull()!!
                    .first()
                    .recipeId
                    .startsWith("local_"),
            )

            val emptyArrEngine =
                LiteRtModelEngine(
                    context = mockContext,
                    dispatcher = testDispatcher,
                    hardwareFeasibilityOverride = true,
                    mockInferenceExecutor = { _, _ -> "[]" },
                )

            val arrResult = emptyArrEngine.generateMealRecipes("sys", "- Match Mode: STRICT", sampleItems)
            assertTrue(arrResult.isSuccess)
            assertTrue(
                arrResult
                    .getOrNull()!!
                    .first()
                    .recipeId
                    .startsWith("local_"),
            )
        }

    @Test
    fun testModelOutput_MarkdownCodeFencesExtractedCorrectly() =
        testScope.runTest {
            val markdownJson =
                """
                Here is the requested recipe:
                ```json
                [
                  {
                    "recipe_id": "fenced_101",
                    "title": "Fenced Garlic Chicken",
                    "match_mode": "STRICT",
                    "target_portions": 4,
                    "prep_time_minutes": 10,
                    "cook_time_minutes": 20,
                    "urgency_score": 0.85
                  }
                ]
                ```
                Hope you like it!
                """.trimIndent()

            val engine =
                LiteRtModelEngine(
                    context = mockContext,
                    dispatcher = testDispatcher,
                    hardwareFeasibilityOverride = true,
                    mockInferenceExecutor = { _, _ -> markdownJson },
                )

            val result = engine.generateMealRecipes("sys", "- Match Mode: STRICT", sampleItems)
            assertTrue(result.isSuccess)
            val recipe = result.getOrNull()!!.first()
            assertEquals("fenced_101", recipe.recipeId)
            assertEquals("Fenced Garlic Chicken", recipe.title)
        }

    @Test
    fun testModelOutput_ExtremePortionsInModelJson_ClampedByParser() =
        testScope.runTest {
            val extremeJson =
                """
                [
                  {
                    "recipe_id": "extreme_99",
                    "title": "Huge Portion Feast",
                    "target_portions": 50
                  }
                ]
                """.trimIndent()

            val engine =
                LiteRtModelEngine(
                    context = mockContext,
                    dispatcher = testDispatcher,
                    hardwareFeasibilityOverride = true,
                    mockInferenceExecutor = { _, _ -> extremeJson },
                )

            val result = engine.generateMealRecipes("sys", "- Match Mode: STRICT", sampleItems)
            assertTrue(result.isSuccess)
            val recipe = result.getOrNull()!!.first()
            assertEquals(MAX_PORTIONS, recipe.targetPortions)
        }

    // =========================================================================
    // 4. Cancellation & Error Handling
    // =========================================================================

    @Test(expected = CancellationException::class)
    fun testCancellationException_RethrownWithoutSwallowing() =
        testScope.runTest {
            val engine =
                LiteRtModelEngine(
                    context = mockContext,
                    dispatcher = testDispatcher,
                    hardwareFeasibilityOverride = true,
                    mockInferenceExecutor = { _, _ -> throw CancellationException("Coroutine cancelled") },
                )

            engine.generateMealRecipes("sys", "user", sampleItems)
        }

    // =========================================================================
    // 5. Ephemeral Buffer Hygiene (Byte Zeroing)
    // =========================================================================

    @Test
    fun testBufferHygiene_ScrubbingInvokedForOutputOnParseFailure() =
        testScope.runTest {
            val scrubbed = mutableListOf<ByteArray>()
            val engine =
                LiteRtModelEngine(
                    context = mockContext,
                    dispatcher = testDispatcher,
                    hardwareFeasibilityOverride = true,
                    mockInferenceExecutor = { _, _ -> "{not valid json at all" },
                    onBufferScrubbed = { scrubbed.add(it.copyOf()) },
                )

            val result = engine.generateMealRecipes("sys", "user", sampleItems)
            assertTrue(result.isSuccess)
            assertTrue("Expected prompt and output buffers scrubbed", scrubbed.size >= 2)
            for (buf in scrubbed) {
                assertTrue("Scrubbed buffer must be all zeros", buf.all { it == 0.toByte() })
            }
        }

    // =========================================================================
    // 6. Seasoning Power Curve (AGENTS.md Invariant 7)
    // =========================================================================

    @Test
    fun testSeasoningPowerCurve_SubLinearExponentStrictlyEnforced() {
        val baseQuantity = 10f
        val basePortions = 2
        val targetPortions = 4

        // Aromatic/Seasoning ingredient: portions^0.75
        val scaledSeasoning =
            CulinaryScaling.scaleQuantity(
                baseQuantity = baseQuantity,
                basePortions = basePortions,
                targetPortions = targetPortions,
                isAromaticOrSeasoning = true,
            )

        // Bulk ingredient: linear scaling (target / base)
        val scaledBulk =
            CulinaryScaling.scaleQuantity(
                baseQuantity = baseQuantity,
                basePortions = basePortions,
                targetPortions = targetPortions,
                isAromaticOrSeasoning = false,
            )

        val ratio = targetPortions.toDouble() / basePortions.toDouble()
        val expectedSeasoning = (baseQuantity.toDouble() * ratio.pow(0.75)).toFloat()
        val expectedBulk = 20.0f

        assertEquals(expectedBulk, scaledBulk, 0.01f)
        assertEquals(expectedSeasoning, scaledSeasoning, 0.01f)
        assertTrue(
            "Seasoning ($scaledSeasoning) must scale sub-linearly compared to bulk ($scaledBulk)",
            scaledSeasoning < scaledBulk,
        )
    }

    // =========================================================================
    // 7. Direct Stress Test on FallbackMealSynthesizer Extreme Portions
    // =========================================================================

    @Test
    fun testFallbackMealSynthesizer_ExtremePortionInputs() {
        // Test standard valid range
        val validResult = FallbackMealSynthesizer.synthesize(sampleItems, MealMatchMode.STRICT, 4)
        assertEquals(1, validResult.size)
        assertEquals(4, validResult.first().targetPortions)

        // Test boundary 1
        val minResult = FallbackMealSynthesizer.synthesize(sampleItems, MealMatchMode.STRICT, 1)
        assertEquals(1, minResult.size)
        assertEquals(1, minResult.first().targetPortions)

        // Test boundary 12
        val maxResult = FallbackMealSynthesizer.synthesize(sampleItems, MealMatchMode.STRICT, 12)
        assertEquals(1, maxResult.size)
        assertEquals(12, maxResult.first().targetPortions)

        // Test extreme portion inputs: -5, 0, 15
        // Let's verify whether FallbackMealSynthesizer throws or handles gracefully:
        try {
            FallbackMealSynthesizer.synthesize(sampleItems, MealMatchMode.STRICT, 0)
            fail("Expected FallbackMealSynthesizer to throw or clamp on 0 portions")
        } catch (e: IllegalArgumentException) {
            // Expected: FallbackMealSynthesizer calls scaleToPortions(0) which throws require(newPortions in 1..12)
            assertTrue(e.message?.contains("Target portions must be between 1 and 12") == true)
        }

        try {
            FallbackMealSynthesizer.synthesize(sampleItems, MealMatchMode.STRICT, -5)
            fail("Expected FallbackMealSynthesizer to throw or clamp on -5 portions")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Target portions must be between 1 and 12") == true)
        }

        try {
            FallbackMealSynthesizer.synthesize(sampleItems, MealMatchMode.STRICT, 15)
            fail("Expected FallbackMealSynthesizer to throw or clamp on 15 portions")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Target portions must be between 1 and 12") == true)
        }
    }

    // =========================================================================
    // 8. Model Hallucination: Empty Recipe Object in Array
    // =========================================================================

    @Test
    fun testMealRecipeParser_EmptyRecipeObjectHandling() {
        val hallucinatedJson =
            """
            [
              {
                "foo": "bar"
              }
            ]
            """.trimIndent()

        val parsed = MealRecipeParser.parseRecipes(hallucinatedJson)
        assertEquals(1, parsed.size)
        val recipe = parsed.first()
        assertEquals("Untitled Recipe", recipe.title)
        assertTrue(recipe.itemsUsedFromStock.isEmpty())
        assertTrue(recipe.stepByStepInstructions.isEmpty())
    }

    // =========================================================================
    // 9. Stress Testing with Large Datasets & Unicode / Emojis
    // =========================================================================

    @Test
    fun testLargeDataset_10000InventoryItems_FallsBackFastWithoutOOM() =
        testScope.runTest {
            val largeList =
                (1..10000).map { i ->
                    PantryItem(
                        id = i.toLong(),
                        name = "Item $i",
                        quantity = (i % 50 + 1).toFloat(),
                        unit = "g",
                        isAromaticOrSeasoning = (i % 5 == 0),
                        expirationTimestamp = if (i % 10 == 0) System.currentTimeMillis() + 3600000L else null,
                    )
                }

            val engine =
                LiteRtModelEngine(
                    context = mockContext,
                    dispatcher = testDispatcher,
                    hardwareFeasibilityOverride = false,
                )

            val startTime = System.currentTimeMillis()
            val result = engine.generateMealRecipes("system", "- Match Mode: STRICT\n- Target Portions: 4", largeList)
            val elapsed = System.currentTimeMillis() - startTime

            assertTrue(result.isSuccess)
            val recipes = result.getOrNull().orEmpty()
            assertEquals(1, recipes.size)
            assertTrue("Execution across 10,000 items must be sub-second", elapsed < 1000)
        }

    @Test
    fun testUnicodeAndEmojisInPrompt_ScrubbingAndHandlingSucceeds() =
        testScope.runTest {
            val unicodePrompt =
                """
                - Match Mode: STRICT
                - Target Portions: 3
                - Notes: 🥬 🥩 🌶️ 🧄 👨‍🍳 いただきます! Special chars: \u0000\n\t
                """.trimIndent()

            val scrubbed = mutableListOf<ByteArray>()
            val engine =
                LiteRtModelEngine(
                    context = mockContext,
                    dispatcher = testDispatcher,
                    hardwareFeasibilityOverride = true,
                    mockInferenceExecutor = { _, _ -> null },
                    onBufferScrubbed = { scrubbed.add(it.copyOf()) },
                )

            val result = engine.generateMealRecipes("system", unicodePrompt, sampleItems)
            assertTrue(result.isSuccess)
            assertEquals(3, result.getOrNull()!!.first().targetPortions)
            assertTrue(scrubbed.isNotEmpty())
            assertTrue("Scrubbed unicode buffer must be zeroed", scrubbed.first().all { it == 0.toByte() })
        }

    @Test
    fun testSpecialFloatQuantities_NaNAndNegativeFilteredOut() =
        testScope.runTest {
            val irregularItems =
                listOf(
                    PantryItem(id = 10L, name = "Bad Item 1", quantity = Float.NaN),
                    PantryItem(id = 11L, name = "Bad Item 2", quantity = -10f),
                    PantryItem(id = 12L, name = "Bad Item 3", quantity = 0f),
                    PantryItem(id = 13L, name = "Valid Item", quantity = 100f, unit = "g"),
                )

            val engine =
                LiteRtModelEngine(
                    context = mockContext,
                    dispatcher = testDispatcher,
                    hardwareFeasibilityOverride = false,
                )

            val result = engine.generateMealRecipes("system", "- Match Mode: STRICT", irregularItems)
            assertTrue(result.isSuccess)
            val recipe = result.getOrNull()!!.first()
            // Only Valid Item should be included
            assertEquals(1, recipe.itemsUsedFromStock.size)
            assertEquals("Valid Item", recipe.itemsUsedFromStock.first().name)
        }
}
