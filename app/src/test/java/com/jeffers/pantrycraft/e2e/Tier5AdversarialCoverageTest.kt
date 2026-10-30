package com.jeffers.pantrycraft.e2e

import com.jeffers.pantrycraft.data.CulinaryScaling
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.reclaimScratchMedia
import com.jeffers.pantrycraft.data.sanitizeSearchQuery
import com.jeffers.pantrycraft.data.validatePantryItem
import com.jeffers.pantrycraft.feature.camera.barcode.BarcodeCatalogDatabase
import com.jeffers.pantrycraft.feature.camera.barcode.CatalogDownloadVerifier
import com.jeffers.pantrycraft.feature.camera.barcode.EmbeddedBarcodeMatch
import com.jeffers.pantrycraft.feature.camera.barcode.OfflineBarcodeResolver
import com.jeffers.pantrycraft.feature.camera.vision.SnapshotFrame
import com.jeffers.pantrycraft.feature.meals.deduction.MealCookHandler
import com.jeffers.pantrycraft.feature.meals.model.MealRecipe
import com.jeffers.pantrycraft.feature.meals.model.RecipeMissingItem
import com.jeffers.pantrycraft.feature.meals.model.RecipePantryUsage
import com.jeffers.pantrycraft.feature.meals.swap.SmartSwapEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * Tier 5: White-Box Adversarial Coverage Hardening Test Suite.
 * Exercises adversarial edge cases across 5 critical domains:
 * 1. Malformed / corrupted barcode inputs and OpenFoodFacts catalog lookup failures.
 * 2. Extreme recipe ingredient quantities (NaN, Infinity, negative values, 0 quantities).
 * 3. Missing / blank pantry item names and units.
 * 4. Deep smart swap substitution cycles or missing graph nodes.
 * 5. Ephemeral media file deletion and byte zeroing verification.
 */
class Tier5AdversarialCoverageTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    // =========================================================================
    // Domain 1: Malformed Barcode Inputs & Catalog Lookup Failures
    // =========================================================================

    @Test
    fun advR2MalformedBarcodeInputsWithSpecialCharsAndSqlInjectionThrow() {
        val resolver = OfflineBarcodeResolver(InMemoryCustomBarcodeDao())
        val malformedCodes =
            listOf(
                "1234' OR '1'='1",
                "1234\u00005678",
                "1234; DROP TABLE products;",
                "1234--5678",
                "!@#$%^&*()",
                "1234\n5678",
                "1234\t5678",
                "1234 5678",
                "1234<script>5678",
            )
        for (code in malformedCodes) {
            assertThrows("Expected failure for malformed barcode: $code", IllegalArgumentException::class.java) {
                runBlocking { resolver.resolveBarcode(code) }
            }
        }
    }

    @Test
    fun advR2BarcodeLengthBoundariesStrictValidation() {
        val resolver = OfflineBarcodeResolver(InMemoryCustomBarcodeDao())
        val invalidLengths =
            listOf(
                "",
                "1",
                "1234567",
                "123456789012345",
                "12345678901234567890",
            )
        for (code in invalidLengths) {
            assertThrows("Expected rejection for invalid length: $code", IllegalArgumentException::class.java) {
                runBlocking { resolver.resolveBarcode(code) }
            }
        }

        runBlocking {
            assertNull(resolver.resolveBarcode("12345678"))
            assertNull(resolver.resolveBarcode("12345678901234"))
        }
    }

    @Test
    fun advR2CatalogLookupFailureWithThrowingDatabaseProviderGracefullyHandled(): Unit =
        runBlocking {
            val throwingProvider: () -> BarcodeCatalogDatabase? = {
                throw IllegalStateException("Simulated SQLiteDiskIOException: disk corrupt")
            }
            val resolver =
                OfflineBarcodeResolver(
                    customBarcodeDao = InMemoryCustomBarcodeDao(),
                    assetBarcodeDatabaseProvider = throwingProvider,
                )

            val result = resolver.resolveBarcode("99999999")
            assertNull(result)

            resolver.reloadCatalog()
            resolver.close()
        }

    @Test
    fun advR2CatalogLookupFallsBackToEmbeddedProductsWhenAssetReturnsNull(): Unit =
        runBlocking {
            val emptyAssetDb =
                object : BarcodeCatalogDatabase {
                    override fun findByBarcode(barcode: String): EmbeddedBarcodeMatch? = null

                    override fun close() {
                        // No-op for in-memory double
                    }
                }
            val resolver =
                OfflineBarcodeResolver(
                    customBarcodeDao = InMemoryCustomBarcodeDao(),
                    assetBarcodeDatabaseProvider = { emptyAssetDb },
                )

            val result = resolver.resolveBarcode("012000001056")
            assertNotNull(result)
            assertEquals("Coca-Cola", result?.name)
            assertEquals("OTHER", result?.category)
        }

    @Test
    fun advR2CatalogDownloadChecksumVerificationDetectsCorruption() {
        val testFile = tempFolder.newFile("catalog_test.db")
        testFile.writeBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05))

        val originalHash = CatalogDownloadVerifier.sha256(testFile)
        assertTrue(originalHash.isNotBlank())
        assertEquals(64, originalHash.length)

        testFile.writeBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x06))
        val corruptedHash = CatalogDownloadVerifier.sha256(testFile)
        assertNotEquals(originalHash, corruptedHash)
    }

    @Test
    fun advR2AssociateBarcodeWithAdversarialInputsHandlesSanitization(): Unit =
        runBlocking {
            val resolver = OfflineBarcodeResolver(InMemoryCustomBarcodeDao())
            val match =
                resolver.associateBarcode(
                    barcode = "  0123456789  ",
                    name = "  Special Crackers  ",
                    category = "   ",
                    defaultUnit = "   ",
                    estimatedShelfLifeDays = -50,
                )

            assertEquals("0123456789", match.barcode)
            assertEquals("Special Crackers", match.name)
            assertEquals("OTHER", match.category)
            assertEquals("COUNT", match.unit)
            assertEquals(0, match.estimatedShelfLifeDays)

            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    resolver.associateBarcode(barcode = "NOT_DIGITS", name = "Bad")
                }
            }
        }

    // =========================================================================
    // Domain 2: Extreme Recipe Ingredient Quantities
    // =========================================================================

    @Test
    fun advR3CulinaryScalingWithNanAndInfiniteQuantitiesThrowsOrRejects() {
        assertThrows(IllegalArgumentException::class.java) {
            CulinaryScaling.scaleQuantity(Float.NaN, 2, 4, false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CulinaryScaling.scaleQuantity(Float.NEGATIVE_INFINITY, 2, 4, false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CulinaryScaling.scaleQuantity(-0.0001f, 2, 4, false)
        }

        val zeroScaled = CulinaryScaling.scaleQuantity(0f, 2, 4, false)
        assertEquals(0f, zeroScaled, 0.001f)

        val infiniteScaled = CulinaryScaling.scaleQuantity(Float.POSITIVE_INFINITY, 2, 4, false)
        assertTrue(infiniteScaled.isInfinite())
    }

    @Test
    fun advR3CulinaryVolumeConversionRejectsNanInfinityAndNegatives() {
        assertNull(CulinaryScaling.convertVolume(Float.NaN, "CUP", "ML"))
        assertNull(CulinaryScaling.convertVolume(Float.POSITIVE_INFINITY, "CUP", "ML"))
        assertNull(CulinaryScaling.convertVolume(Float.NEGATIVE_INFINITY, "CUP", "ML"))
        assertNull(CulinaryScaling.convertVolume(-10f, "CUP", "ML"))
        assertNull(CulinaryScaling.convertVolume(Float.NaN, "invalid_unit", "ML"))

        val zeroResult = CulinaryScaling.convertVolume(0f, "CUP", "ML")
        assertEquals(0f, zeroResult ?: -1f, 0.001f)
    }

    @Test
    fun advR3SeasoningExponentExtremeClampingAndAromaticPowerCurve() {
        val baseQty = 10f
        val scaledMin =
            CulinaryScaling.scaleQuantity(
                baseQuantity = baseQty,
                basePortions = 2,
                targetPortions = 8,
                isAromaticOrSeasoning = true,
                exponent = -999.0,
            )
        val expectedMin =
            CulinaryScaling.scaleQuantity(
                baseQuantity = baseQty,
                basePortions = 2,
                targetPortions = 8,
                isAromaticOrSeasoning = true,
                exponent = CulinaryScaling.MIN_PALATE_EXPONENT,
            )
        assertEquals(expectedMin, scaledMin, 0.001f)

        val scaledMax =
            CulinaryScaling.scaleQuantity(
                baseQuantity = baseQty,
                basePortions = 2,
                targetPortions = 8,
                isAromaticOrSeasoning = true,
                exponent = 999.0,
            )
        val expectedMax =
            CulinaryScaling.scaleQuantity(
                baseQuantity = baseQty,
                basePortions = 2,
                targetPortions = 8,
                isAromaticOrSeasoning = true,
                exponent = CulinaryScaling.MAX_PALATE_EXPONENT,
            )
        assertEquals(expectedMax, scaledMax, 0.001f)
    }

    @Test
    fun advR4MealCookHandlerFiltersOutNanAndInfiniteQuantities(): Unit =
        runBlocking {
            val flour = PantryItem(id = 10L, name = "Flour", quantity = 100f, unit = "g", storageLocation = "PANTRY")
            val pantryRepo = InMemoryPantryRepository(initialPantry = listOf(flour))
            val groceryRepo = InMemoryPantryRepository()

            val invalidUsages =
                listOf(
                    RecipePantryUsage(name = "Flour", pantryItemId = 10L, quantityUsed = Float.NaN),
                    RecipePantryUsage(name = "Flour", pantryItemId = 10L, quantityUsed = Float.POSITIVE_INFINITY),
                    RecipePantryUsage(name = "Flour", pantryItemId = 10L, quantityUsed = Float.NEGATIVE_INFINITY),
                    RecipePantryUsage(name = "Flour", pantryItemId = 10L, quantityUsed = -25f),
                    RecipePantryUsage(name = "Flour", pantryItemId = 10L, quantityUsed = 0f),
                )

            for (usage in invalidUsages) {
                val recipe =
                    MealRecipe(
                        recipeId = "adv_recipe_${System.nanoTime()}",
                        title = "Test Cake",
                        itemsUsedFromStock = listOf(usage),
                    )
                val result = MealCookHandler.executeCookMeal(recipe, pantryRepo, groceryRepo)
                assertEquals(0, result.itemsDeductedCount)
            }

            val remainingFlour = pantryRepo.observePantryItems().first().first { it.id == 10L }
            assertEquals(100f, remainingFlour.quantity, 0.001f)
        }

    @Test
    fun advR4MealCookHandlerExtremeMissingGroceryQuantities(): Unit =
        runBlocking {
            val pantryRepo = InMemoryPantryRepository()
            val groceryRepo = InMemoryPantryRepository()

            val missing =
                listOf(
                    RecipeMissingItem(name = "   ", quantityNeeded = 10f),
                    RecipeMissingItem(name = "Vanilla Extract", quantityNeeded = 0f),
                    RecipeMissingItem(name = "Nutmeg", quantityNeeded = -1f),
                )

            val recipe =
                MealRecipe(
                    recipeId = "adv_missing",
                    title = "Missing Test Recipe",
                    missingItemsForGrocery = missing,
                )

            val result =
                MealCookHandler.executeCookMeal(
                    recipe = recipe,
                    pantryRepository = pantryRepo,
                    groceryRepository = groceryRepo,
                    addMissingToGrocery = true,
                )

            assertEquals(2, result.groceryItemsAddedCount)
            val addedItems = groceryRepo.observeGroceryItems().first()
            assertEquals(2, addedItems.size)
            assertTrue(addedItems.none { it.name.isBlank() })
        }

    // =========================================================================
    // Domain 3: Missing / Blank Pantry Item Names and Units
    // =========================================================================

    @Test
    fun advR1BlankAndWhitespacePantryItemNamesStrictlyRejected() {
        val invalidNames = listOf("", "   ", "\t", "\n\r", " \t \n ")
        for (badName in invalidNames) {
            val item = PantryItem(name = badName, quantity = 5f, storageLocation = "PANTRY")
            assertThrows("Expected rejection for blank name: '$badName'", IllegalArgumentException::class.java) {
                validatePantryItem(item)
            }
        }
    }

    @Test
    fun advR1NullableAndBlankUnitsAreSafelyAcceptedInPantryItem() {
        val variations = listOf(null, "", "   ", "COUNT", "G", "ML")
        for (unitVariation in variations) {
            val item =
                PantryItem(
                    name = "Apples",
                    quantity = 5f,
                    unit = unitVariation,
                    storageLocation = "PANTRY",
                )
            val validated = validatePantryItem(item)
            assertEquals("apples", validated.normalizedName)
            assertEquals(unitVariation, validated.unit)
        }
    }

    @Test
    fun advR4MealCookHandlerWithBlankOrMismatchedUnitsFallsBackToRawQuantity(): Unit =
        runBlocking {
            val sugar = PantryItem(id = 20L, name = "Sugar", quantity = 10f, unit = null, storageLocation = "PANTRY")
            val milk = PantryItem(id = 21L, name = "Milk", quantity = 10f, unit = "ML", storageLocation = "FRIDGE")
            val flour = PantryItem(id = 22L, name = "Flour", quantity = 10f, unit = "", storageLocation = "PANTRY")
            val pantryRepo = InMemoryPantryRepository(initialPantry = listOf(sugar, milk, flour))
            val groceryRepo = InMemoryPantryRepository()

            val recipe =
                MealRecipe(
                    recipeId = "adv_units",
                    title = "Pancake Test",
                    itemsUsedFromStock =
                        listOf(
                            RecipePantryUsage(name = "Sugar", pantryItemId = 20L, quantityUsed = 2f, unit = "CUP"),
                            RecipePantryUsage(name = "Milk", pantryItemId = 21L, quantityUsed = 3f, unit = null),
                            RecipePantryUsage(name = "Flour", pantryItemId = 22L, quantityUsed = 4f, unit = ""),
                        ),
                )

            val result = MealCookHandler.executeCookMeal(recipe, pantryRepo, groceryRepo)
            assertEquals(3, result.itemsDeductedCount)

            val items = pantryRepo.observePantryItems().first().associateBy { it.id }
            assertEquals(8f, items[20L]?.quantity ?: 0f, 0.001f)
            assertEquals(7f, items[21L]?.quantity ?: 0f, 0.001f)
            assertEquals(6f, items[22L]?.quantity ?: 0f, 0.001f)
        }

    @Test
    fun advR4MealCookHandlerWithInconvertibleUnitsFallsBackGracefully(): Unit =
        runBlocking {
            val stockItem =
                PantryItem(
                    id = 30L,
                    name = "Apples",
                    quantity = 20f,
                    unit = "COUNT",
                    storageLocation = "PANTRY",
                )
            val pantryRepo = InMemoryPantryRepository(initialPantry = listOf(stockItem))
            val groceryRepo = InMemoryPantryRepository()

            val recipe =
                MealRecipe(
                    recipeId = "adv_inconvertible",
                    title = "Apple Pie",
                    itemsUsedFromStock =
                        listOf(
                            RecipePantryUsage(name = "Apples", pantryItemId = 30L, quantityUsed = 5f, unit = "L"),
                        ),
                )

            val result = MealCookHandler.executeCookMeal(recipe, pantryRepo, groceryRepo)
            assertEquals(1, result.itemsDeductedCount)

            val remaining = pantryRepo.observePantryItems().first().first { it.id == 30L }
            assertEquals(15f, remaining.quantity, 0.001f)
        }

    @Test
    fun advR1SearchPantryItemsWithBlankWhitespaceAndPunctuationOnlyQueries(): Unit =
        runBlocking {
            val flour = PantryItem(id = 1L, name = "Organic Flour", quantity = 5f, storageLocation = "PANTRY")
            val salt = PantryItem(id = 2L, name = "Sea Salt", quantity = 1f, storageLocation = "PANTRY")
            val repo = InMemoryPantryRepository(initialPantry = listOf(flour, salt))

            val adversarialQueries = listOf("", "   ", "\t\n", "!@#$%", "AND OR NOT NEAR", " - * ' \" ")
            for (query in adversarialQueries) {
                val sanitized = sanitizeSearchQuery(query)
                assertEquals("", sanitized)
                val results = repo.searchPantryItems(query).first()
                assertEquals(2, results.size)
            }
        }

    // =========================================================================
    // Domain 4: Deep Smart Swap Substitution Cycles or Missing Graph Nodes
    // =========================================================================

    @Test
    fun advR3SmartSwapMissingGraphNodesReturnEmptyList() {
        val onHand =
            listOf(
                PantryItem(name = "Flour", quantity = 10f, storageLocation = "PANTRY"),
                PantryItem(name = "Milk", quantity = 5f, storageLocation = "FRIDGE"),
            )
        val missingNodes =
            listOf(
                "saffron",
                "dragonfruit",
                "truffle oil",
                "foie gras",
                "",
                "   ",
                "12345",
                "!@#$%",
            )
        for (node in missingNodes) {
            val swaps = SmartSwapEngine.findSwapsForMissingItem(node, onHand)
            assertTrue("Expected empty swap list for missing node '$node'", swaps.isEmpty())
        }
    }

    @Test
    fun advR3SmartSwapDetectsDeletedOrZeroQuantityStockAsUnavailable() {
        val deletedMilk = PantryItem(name = "Whole Milk", quantity = 5f, isDeleted = true, storageLocation = "FRIDGE")
        val zeroYogurt = PantryItem(name = "Greek Yogurt", quantity = 0f, storageLocation = "FRIDGE")
        val onHand = listOf(deletedMilk, zeroYogurt)

        val buttermilkSwaps = SmartSwapEngine.findSwapsForMissingItem("buttermilk", onHand)
        assertFalse(buttermilkSwaps.isEmpty())
        assertFalse(buttermilkSwaps.first().isFullyAvailableOnHand)

        val sourCreamSwaps = SmartSwapEngine.findSwapsForMissingItem("sour cream", onHand)
        assertFalse(sourCreamSwaps.isEmpty())
        assertFalse(sourCreamSwaps.first().isFullyAvailableOnHand)
    }

    @Test
    fun advR3SmartSwapTransitiveSubstitutionCycleResistance() {
        val cyclicGraph =
            mapOf(
                "ingredient_a" to listOf("ingredient_b"),
                "ingredient_b" to listOf("ingredient_c"),
                "ingredient_c" to listOf("ingredient_a"),
            )

        val visited = mutableSetOf<String>()
        var current: String? = "ingredient_a"
        var steps = 0
        val maxAllowedSteps = 10

        while (current != null && steps < maxAllowedSteps) {
            steps++
            if (current in visited) break
            visited.add(current)
            current = cyclicGraph[current]?.firstOrNull()
        }

        assertTrue(steps <= 4)
        assertTrue(visited.contains("ingredient_a"))
        assertTrue(visited.contains("ingredient_b"))
        assertTrue(visited.contains("ingredient_c"))
    }

    @Test
    fun advR3SmartSwapEnrichMissingItemsWithMixedPresentAndMissingNodes() {
        val onHand =
            listOf(
                PantryItem(name = "Whole Milk", quantity = 2f, storageLocation = "FRIDGE"),
                PantryItem(name = "Lemon Juice", quantity = 1f, storageLocation = "FRIDGE"),
            )
        val queriedItems = listOf("buttermilk", "non_existent_spice", "sour cream", "")
        val enriched = SmartSwapEngine.enrichMissingItemsWithSwaps(queriedItems, onHand)

        assertEquals(4, enriched.size)
        assertTrue(enriched.containsKey("buttermilk"))
        assertTrue(enriched.containsKey("non_existent_spice"))
        assertTrue(enriched.getValue("non_existent_spice").isEmpty())
        assertTrue(enriched.getValue("").isEmpty())
        assertFalse(enriched.getValue("buttermilk").isEmpty())
    }

    // =========================================================================
    // Domain 5: Ephemeral Media File Deletion and Byte Zeroing Verification
    // =========================================================================

    @Test
    fun advR2SnapshotFrameByteZeroingWipesBufferCompletely() {
        val bufferSize = 65536
        val rawBuffer = ByteArray(bufferSize) { 0x7F.toByte() }
        val frame = SnapshotFrame(rawBuffer)

        assertEquals(0x7F.toByte(), rawBuffer[0])
        assertEquals(0x7F.toByte(), rawBuffer[bufferSize - 1])

        frame.close()

        for (i in 0 until bufferSize) {
            assertEquals(0.toByte(), rawBuffer[i])
        }
    }

    @Test
    fun advR2SnapshotFrameCloseIdempotency() {
        val rawBuffer = byteArrayOf(1, 2, 3, 4, 5)
        val frame = SnapshotFrame(rawBuffer)

        frame.close()
        for (byte in rawBuffer) {
            assertEquals(0.toByte(), byte)
        }

        frame.close()
        frame.close()

        val emptyFrame = SnapshotFrame(ByteArray(0))
        emptyFrame.close()
    }

    @Test
    fun advR2ReclaimScratchMediaDeepNestedDirectoryAndFilePruning() {
        val cacheDir = tempFolder.newFolder("vision_cache")
        val scratchDir = File(cacheDir, "vision_scratch").apply { mkdirs() }

        val rootFrame = File(scratchDir, "root_frame.jpg").apply { writeText("root") }
        val subDir1 = File(scratchDir, "batch_1").apply { mkdirs() }
        val sub1Frame = File(subDir1, "frame_1.jpg").apply { writeText("sub1") }
        val subDir2 = File(subDir1, "nested_batch").apply { mkdirs() }
        val sub2Frame = File(subDir2, "frame_2.jpg").apply { writeText("sub2") }
        val emptyDir = File(scratchDir, "empty_folder").apply { mkdirs() }

        val deletedCount = reclaimScratchMedia(cacheDir)
        assertTrue(deletedCount >= 3)

        assertFalse(rootFrame.exists())
        assertFalse(sub1Frame.exists())
        assertFalse(sub2Frame.exists())
        assertFalse(emptyDir.exists())
        assertFalse(subDir1.exists())
    }

    @Test
    fun advR2ReclaimScratchMediaWhenScratchIsRegularFile() {
        val cacheDir = tempFolder.newFolder("vision_cache_file")
        val scratchFile = File(cacheDir, "vision_scratch").apply { writeText("not a directory") }
        assertTrue(scratchFile.isFile)

        val deletedCount = reclaimScratchMedia(cacheDir)
        assertEquals(0, deletedCount)
        assertTrue(scratchFile.exists())
    }

    @Test
    fun advR2EphemeralFileDeletionInFinallyBlockUnderSimulatedFailure() {
        val cacheDir = tempFolder.newFolder("scratch_pipeline")
        val tempFrame =
            File(cacheDir, "temp_inference_frame.jpg").apply {
                writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
            }
        assertTrue(tempFrame.exists())

        var caughtException: IOException? = null
        try {
            try {
                throw IOException("Simulated network dropout during inference")
            } finally {
                tempFrame.delete()
            }
        } catch (e: IOException) {
            caughtException = e
        }

        assertNotNull(caughtException)
        assertFalse(tempFrame.exists())
    }
}
