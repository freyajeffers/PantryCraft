package com.jeffers.pantrycraft.e2e

import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.objects.ObjectDetector
import com.jeffers.pantrycraft.data.CulinaryScaling
import com.jeffers.pantrycraft.data.CustomBarcodeItem
import com.jeffers.pantrycraft.data.GroceryItem
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.markOpened
import com.jeffers.pantrycraft.data.readBackupHeader
import com.jeffers.pantrycraft.data.reclaimScratchMedia
import com.jeffers.pantrycraft.data.sanitizeSearchQuery
import com.jeffers.pantrycraft.data.validatePantryItem
import com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionAnalyzer
import com.jeffers.pantrycraft.feature.camera.barcode.OfflineBarcodeResolver
import com.jeffers.pantrycraft.feature.meals.deduction.MealCookHandler
import com.jeffers.pantrycraft.feature.meals.engine.FallbackMealSynthesizer
import com.jeffers.pantrycraft.feature.meals.model.MealMatchMode
import com.jeffers.pantrycraft.feature.meals.model.MealRecipe
import com.jeffers.pantrycraft.feature.meals.model.RecipeMissingItem
import com.jeffers.pantrycraft.feature.meals.model.RecipePantryUsage
import com.jeffers.pantrycraft.feature.meals.swap.SmartSwapEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tier 1: Primary Feature Coverage Test Suite.
 * Covers happy paths and domain contracts across R1, R2, R3, and R4 (>=5 per feature).
 */
class Tier1FeatureCoverageTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    // -------------------------------------------------------------------------
    // R1: Encrypted Storage & Inventory Architecture
    // -------------------------------------------------------------------------

    @Test
    fun r1PantryItemCreationAndTaxonomyValidation() {
        val rawItem =
            PantryItem(
                name = "  Extra Virgin Olive Oil  ",
                quantity = 500f,
                unit = "ML",
                storageLocation = "PANTRY",
                category = "PANTRY_STAPLE",
            )
        val validated = validatePantryItem(rawItem)

        assertEquals("extra virgin olive oil", validated.normalizedName)
        assertEquals("PANTRY", validated.storageLocation)
        assertTrue(validated.quantity > 0f)
        assertFalse(validated.isDeleted)
    }

    @Test
    fun r1PantryTaxonomyAromaticAutoDetection() {
        val spiceItem =
            PantryItem(
                name = "Cinnamon Bark",
                category = "SPICES",
                storageLocation = "PANTRY",
                isAromaticOrSeasoning = false,
            )
        val condimentItem =
            PantryItem(
                name = "Dijon Mustard",
                category = "CONDIMENTS",
                storageLocation = "FRIDGE",
                isAromaticOrSeasoning = false,
            )

        val validatedSpice = validatePantryItem(spiceItem)
        val validatedCondiment = validatePantryItem(condimentItem)

        assertTrue("SPICES category must be marked as aromatic", validatedSpice.isAromaticOrSeasoning)
        assertTrue("CONDIMENTS category must be marked as aromatic", validatedCondiment.isAromaticOrSeasoning)
    }

    @Test
    fun r1PantryItemMarkOpenedLifecycle() {
        val now = 1_000_000L
        val originalExpiration = now + (30L * 86_400_000L) // 30 days
        val dairyItem =
            PantryItem(
                name = "Whole Milk",
                category = "DAIRY",
                expirationTimestamp = originalExpiration,
                isOpened = false,
            )

        val opened = dairyItem.markOpened(now)

        assertTrue(opened.isOpened)
        assertEquals(now, opened.openedTimestamp)
        // DAIRY shelf life when opened is 7 days
        val expectedOpenedExp = now + (7L * 86_400_000L)
        assertEquals(expectedOpenedExp, opened.expirationTimestamp)
    }

    @Test
    fun r1TombstoneSoftDeleteAndRestore() =
        runBlocking {
            val repo = InMemoryPantryRepository()
            val id =
                repo.insertPantry(
                    PantryItem(name = "Organic Honey", quantity = 250f, unit = "G", storageLocation = "PANTRY"),
                )

            var activeItems = repo.observePantryItems().first()
            assertEquals(1, activeItems.size)

            val inserted = activeItems.first()
            repo.deletePantry(inserted)

            activeItems = repo.observePantryItems().first()
            assertTrue("Soft-deleted item must not appear in active pantry flow", activeItems.isEmpty())

            val rawAll = repo.getAllRawPantry()
            assertEquals(1, rawAll.size)
            assertTrue(rawAll.first().isDeleted)

            repo.restorePantry(rawAll.first())
            activeItems = repo.observePantryItems().first()
            assertEquals(1, activeItems.size)
            assertFalse(activeItems.first().isDeleted)
        }

    @Test
    fun r1CryptographicBackupHeaderParsing() {
        val magic = "PCBK".toByteArray(Charsets.UTF_8)
        val version = byteArrayOf(1)
        val salt = ByteArray(16) { 0x42.toByte() }
        val iv = ByteArray(12) { 0x07.toByte() }
        val stream = ByteArrayInputStream(magic + version + salt + iv)

        val (parsedSalt, parsedIv) = readBackupHeader(stream)

        assertArrayEquals(salt, parsedSalt)
        assertArrayEquals(iv, parsedIv)
    }

    @Test
    fun r1FtsQuerySanitization() {
        val rawSearch = "  organic * milk & AND cheese? NOT OR NEAR   "
        val sanitized = sanitizeSearchQuery(rawSearch)

        // Non-alphanumeric removed, reserved FTS words (AND, NOT, OR, NEAR) stripped
        assertEquals("organic milk cheese", sanitized)
    }

    // -------------------------------------------------------------------------
    // R2: CameraX Dual-Mode Vision Pipeline & Visual Ingestion
    // -------------------------------------------------------------------------

    @Test
    fun r2OfflineBarcodeResolverStandardEan13() =
        runBlocking {
            val customDao = InMemoryCustomBarcodeDao()
            val resolver = OfflineBarcodeResolver(customBarcodeDao = customDao)

            // Remember custom item simulating catalog or previous resolution
            val item =
                CustomBarcodeItem(
                    barcode = "0123456789012",
                    name = "Rolled Oats",
                    category = "GRAINS",
                    defaultUnit = "G",
                    estimatedShelfLifeDays = 180,
                )
            customDao.upsert(item)

            val match = resolver.resolveBarcode("0123456789012")

            assertNotNull(match)
            assertEquals("0123456789012", match?.barcode)
            assertEquals("Rolled Oats", match?.name)
            assertEquals("GRAINS", match?.category)
            assertEquals(180, match?.estimatedShelfLifeDays)
        }

    @Test
    fun r2OfflineBarcodeResolverDebounceThrottling() =
        runBlocking {
            val customDao = InMemoryCustomBarcodeDao()
            val resolver = OfflineBarcodeResolver(customBarcodeDao = customDao)
            val barcode = "0987654321098"

            customDao.upsert(
                CustomBarcodeItem(barcode = barcode, name = "Cheddar Cheese", category = "DAIRY"),
            )

            val firstScan = resolver.resolveBarcode(barcode)
            assertNotNull(firstScan)

            // Rapid immediate second scan within debounce threshold returns null
            val secondScan = resolver.resolveBarcode(barcode)
            assertNull("Subsequent scan within debounce window must be suppressed", secondScan)
        }

    @Test
    fun r2OfflineBarcodeResolverCustomRemember() =
        runBlocking {
            val customDao = InMemoryCustomBarcodeDao()
            val resolver = OfflineBarcodeResolver(customBarcodeDao = customDao)

            val match =
                resolver.associateBarcode(
                    barcode = "5000123456789",
                    name = "Local Artisan Sourdough",
                    category = "BAKERY",
                    defaultUnit = "EACH",
                    estimatedShelfLifeDays = 4,
                )

            assertEquals("5000123456789", match.barcode)
            assertEquals("Local Artisan Sourdough", match.name)
            val stored = customDao.findByBarcode("5000123456789")
            assertNotNull(stored)
            assertEquals("BAKERY", stored?.category)
        }

    @Test
    fun r2LiveVisionAnalyzerGuaranteedProxyClosureOnNullMedia() {
        val barcodeScanner =
            Proxy.newProxyInstance(
                BarcodeScanner::class.java.classLoader,
                arrayOf(BarcodeScanner::class.java),
            ) { _, _, _ -> null } as BarcodeScanner

        val objectDetector =
            Proxy.newProxyInstance(
                ObjectDetector::class.java.classLoader,
                arrayOf(ObjectDetector::class.java),
            ) { _, _, _ -> null } as ObjectDetector

        val imageProxyClosed = AtomicBoolean(false)
        val imageProxy =
            Proxy.newProxyInstance(
                ImageProxy::class.java.classLoader,
                arrayOf(ImageProxy::class.java),
            ) { _, method, _ ->
                if (method.name == "close") imageProxyClosed.set(true)
                null
            } as ImageProxy

        val executor = Executors.newSingleThreadExecutor()
        try {
            val analyzer =
                LiveVisionAnalyzer(
                    barcodeScanner = barcodeScanner,
                    objectDetector = objectDetector,
                    executor = executor,
                    onResult = {},
                )
            analyzer.analyze(imageProxy)
            assertTrue("imageProxy.close() must be called when media image is null", imageProxyClosed.get())
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun r2LiveVisionAnalyzerCloseClosesScanners() {
        val barcodeClosed = AtomicBoolean(false)
        val barcodeScanner =
            Proxy.newProxyInstance(
                BarcodeScanner::class.java.classLoader,
                arrayOf(BarcodeScanner::class.java),
            ) { _, method, _ ->
                if (method.name == "close") barcodeClosed.set(true)
                null
            } as BarcodeScanner

        val objectDetectorClosed = AtomicBoolean(false)
        val objectDetector =
            Proxy.newProxyInstance(
                ObjectDetector::class.java.classLoader,
                arrayOf(ObjectDetector::class.java),
            ) { _, method, _ ->
                if (method.name == "close") objectDetectorClosed.set(true)
                null
            } as ObjectDetector

        val executor = Executors.newSingleThreadExecutor()
        try {
            val analyzer =
                LiveVisionAnalyzer(
                    barcodeScanner = barcodeScanner,
                    objectDetector = objectDetector,
                    executor = executor,
                    onResult = {},
                )
            analyzer.close()
            assertTrue(barcodeClosed.get())
            assertTrue(objectDetectorClosed.get())
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun r2EphemeralMediaScratchReclamation() {
        val cacheDir = tempFolder.newFolder("cache")
        val scratchDir = File(cacheDir, "vision_scratch")
        scratchDir.mkdirs()

        val tempFrame = File(scratchDir, "frame_001.jpg")
        tempFrame.writeText("binary-frame-data")
        val tempThumb = File(scratchDir, "thumb_001.webp")
        tempThumb.writeText("binary-thumb-data")

        val deletedCount = reclaimScratchMedia(cacheDir)
        assertEquals(2, deletedCount)
        assertFalse(tempFrame.exists())
        assertFalse(tempThumb.exists())
    }

    // -------------------------------------------------------------------------
    // R3: Culinary Intelligence & Meal Synthesis
    // -------------------------------------------------------------------------

    @Test
    fun r3LinearBulkIngredientScaling() {
        // 200g of pasta for 2 portions scaled to 6 portions (3x) -> 600g
        val scaled =
            CulinaryScaling.scaleQuantity(
                baseQuantity = 200f,
                basePortions = 2,
                targetPortions = 6,
                isAromaticOrSeasoning = false,
            )
        assertEquals(600f, scaled, 0.01f)
    }

    @Test
    fun r3NonLinearSeasoningPowerCurveScaling() {
        // 2 tsp salt for 2 portions scaled to 8 portions (4x ratio)
        // Non-linear: 2.0 * (4.0 ^ 0.75) = 2.0 * 2.8284 = 5.66 tsp
        val scaled =
            CulinaryScaling.scaleQuantity(
                baseQuantity = 2f,
                basePortions = 2,
                targetPortions = 8,
                isAromaticOrSeasoning = true,
                exponent = 0.75,
            )
        assertEquals(5.66f, scaled, 0.02f)
    }

    @Test
    fun r3PalateExponentClamping() {
        // Below MIN_PALATE_EXPONENT (0.65) must be clamped to 0.65
        val scaledLow =
            CulinaryScaling.scaleQuantity(
                baseQuantity = 2f,
                basePortions = 2,
                targetPortions = 8,
                isAromaticOrSeasoning = true,
                exponent = 0.50, // lower than min
            )
        // 2.0 * (4.0 ^ 0.65) = 2.0 * 2.4623 = 4.92
        assertEquals(4.92f, scaledLow, 0.02f)

        // Above MAX_PALATE_EXPONENT (0.85) must be clamped to 0.85
        val scaledHigh =
            CulinaryScaling.scaleQuantity(
                baseQuantity = 2f,
                basePortions = 2,
                targetPortions = 8,
                isAromaticOrSeasoning = true,
                exponent = 0.95, // higher than max
            )
        // 2.0 * (4.0 ^ 0.85) = 2.0 * 3.2490 = 6.50
        assertEquals(6.50f, scaledHigh, 0.02f)
    }

    @Test
    fun r3CulinaryVolumeUnitConversions() {
        // 1 TBSP = 3 TSP (15 ml vs 5 ml)
        val tspFromTbsp = CulinaryScaling.convertVolume(1f, "TBSP", "TSP")
        assertEquals(3f, tspFromTbsp ?: 0f, 0.01f)

        // 1 CUP = 16 TBSP (240 ml vs 15 ml)
        val tbspFromCup = CulinaryScaling.convertVolume(1f, "CUP", "TBSP")
        assertEquals(16f, tbspFromCup ?: 0f, 0.01f)

        // 1 L = 1000 ML
        val mlFromL = CulinaryScaling.convertVolume(1f, "L", "ML")
        assertEquals(1000f, mlFromL ?: 0f, 0.01f)
    }

    @Test
    fun r3SmartSwapKnowledgeGraphSubstitution() {
        val onHand =
            listOf(
                PantryItem(id = 1L, name = "Greek Yogurt", quantity = 200f, unit = "G"),
            )

        // Sour cream can be swapped for Greek Yogurt (1:1 ratio)
        val swaps = SmartSwapEngine.findSwapsForMissingItem("Sour Cream", onHand)

        assertEquals(1, swaps.size)
        val swap = swaps.first()
        assertEquals("Greek Yogurt", swap.substituteName)
        assertTrue(swap.isFullyAvailableOnHand)
    }

    @Test
    fun r3FallbackDeterministicMealSynthesis() {
        val now = 1_000_000L
        val items =
            listOf(
                PantryItem(
                    id = 10L,
                    name = "Fresh Spinach",
                    quantity = 200f,
                    unit = "G",
                    expirationTimestamp = now + (24L * 3600_000L), // expiring in 1 day
                ),
                PantryItem(id = 20L, name = "Eggs", quantity = 6f, unit = "COUNT"),
            )

        val recipes =
            FallbackMealSynthesizer.synthesize(
                items = items,
                mode = MealMatchMode.EMERGENCY_SALVAGE,
                targetPortions = 2,
                nowMillis = now,
            )

        assertEquals(1, recipes.size)
        val recipe = recipes.first()
        assertEquals(MealMatchMode.EMERGENCY_SALVAGE, recipe.matchMode)
        assertTrue("Urgency score must reflect expiring ingredients", recipe.urgencyScore >= 0.9f)
        assertTrue(recipe.totalTimeMinutes <= 15)
    }

    // -------------------------------------------------------------------------
    // R4: End-to-End User Journey & Deduction Workflows
    // -------------------------------------------------------------------------

    @Test
    fun r4MealRecipePortionScaling() {
        val recipe =
            MealRecipe(
                recipeId = "rec_pasta",
                title = "Tomato Garlic Pasta",
                targetPortions = 2,
                itemsUsedFromStock =
                    listOf(
                        RecipePantryUsage(name = "Pasta", quantityUsed = 200f, unit = "G", isAromaticOrSeasoning = false),
                        RecipePantryUsage(name = "Garlic", quantityUsed = 2f, unit = "CLOVE", isAromaticOrSeasoning = true),
                    ),
                missingItemsForGrocery =
                    listOf(
                        RecipeMissingItem(name = "Parmesan", quantityNeeded = 50f, unit = "G"),
                    ),
            )

        val scaled = recipe.scaleToPortions(4)

        assertEquals(4, scaled.targetPortions)
        val pasta = scaled.itemsUsedFromStock.first { it.name == "Pasta" }
        val garlic = scaled.itemsUsedFromStock.first { it.name == "Garlic" }
        val cheese = scaled.missingItemsForGrocery.first { it.name == "Parmesan" }

        // Bulk linearly: 200 * 2 = 400g
        assertEquals(400f, pasta.quantityUsed, 0.01f)
        // Seasoning non-linearly: 2 * (2 ^ 0.75) = 2 * 1.6818 = 3.36
        assertEquals(3.36f, garlic.quantityUsed, 0.02f)
        // Missing bulk item: 50 * 2 = 100g
        assertEquals(100f, cheese.quantityNeeded, 0.01f)
    }

    @Test
    fun r4MealCookHandlerPantryDeduction() =
        runBlocking {
            val pantryItem = PantryItem(id = 100L, name = "Basmati Rice", quantity = 500f, unit = "G")
            val pantryRepo = InMemoryPantryRepository(listOf(pantryItem))
            val groceryRepo = InMemoryPantryRepository()

            val recipe =
                MealRecipe(
                    recipeId = "rec_rice",
                    title = "Steamed Rice",
                    itemsUsedFromStock =
                        listOf(
                            RecipePantryUsage(
                                name = "Basmati Rice",
                                pantryItemId = 100L,
                                quantityUsed = 150f,
                                preventsWaste = true,
                            ),
                        ),
                )

            val result =
                MealCookHandler.executeCookMeal(
                    recipe = recipe,
                    pantryRepository = pantryRepo,
                    groceryRepository = groceryRepo,
                    addMissingToGrocery = true,
                )

            assertEquals(1, result.itemsDeductedCount)
            assertEquals(1, result.foodWastePreventedCount)
            assertEquals(0, result.groceryItemsAddedCount)

            val remaining = pantryRepo.observePantryItems().first().first { it.id == 100L }
            assertEquals(350f, remaining.quantity, 0.01f)
        }

    @Test
    fun r4MealCookHandlerMissingItemsToGrocery() =
        runBlocking {
            val pantryRepo = InMemoryPantryRepository()
            val groceryRepo = InMemoryPantryRepository()

            val recipe =
                MealRecipe(
                    recipeId = "rec_curry",
                    title = "Vegetable Curry",
                    itemsUsedFromStock = emptyList(),
                    missingItemsForGrocery =
                        listOf(
                            RecipeMissingItem(name = "Coconut Milk", quantityNeeded = 1f, unit = "CAN"),
                            RecipeMissingItem(name = "Curry Paste", quantityNeeded = 2f, unit = "TBSP"),
                        ),
                )

            val result =
                MealCookHandler.executeCookMeal(
                    recipe = recipe,
                    pantryRepository = pantryRepo,
                    groceryRepository = groceryRepo,
                    addMissingToGrocery = true,
                )

            assertEquals(2, result.groceryItemsAddedCount)
            val groceries = groceryRepo.observeGroceryItems().first()
            assertEquals(2, groceries.size)
            assertTrue(groceries.any { it.name == "Coconut Milk" && it.quantity == 1f })
            assertTrue(groceries.any { it.name == "Curry Paste" && it.quantity == 2f })
        }

    @Test
    fun r4MealCookHandlerDisableGroceryInsertion() =
        runBlocking {
            val pantryRepo = InMemoryPantryRepository()
            val groceryRepo = InMemoryPantryRepository()

            val recipe =
                MealRecipe(
                    recipeId = "rec_simple",
                    title = "Quick Snack",
                    missingItemsForGrocery =
                        listOf(
                            RecipeMissingItem(name = "Crackers", quantityNeeded = 1f, unit = "BOX"),
                        ),
                )

            val result =
                MealCookHandler.executeCookMeal(
                    recipe = recipe,
                    pantryRepository = pantryRepo,
                    groceryRepository = groceryRepo,
                    addMissingToGrocery = false,
                )

            assertEquals(0, result.groceryItemsAddedCount)
            val groceries = groceryRepo.observeGroceryItems().first()
            assertTrue(groceries.isEmpty())
        }

    @Test
    fun r4GroceryCheckedRestockReplenishment() =
        runBlocking {
            val initialPantry = PantryItem(id = 101L, name = "Flour", quantity = 1000f, unit = "G")
            val repo = InMemoryPantryRepository(initialPantry = listOf(initialPantry))

            // Add 2 grocery items: 1 linked to existing flour, 1 brand new
            repo.insertGrocery(
                GroceryItem(id = 201L, name = "Flour", quantity = 500f, unit = "G", checked = true, fromPantryId = 101L),
            )
            repo.insertGrocery(
                GroceryItem(id = 202L, name = "Yeast", quantity = 3f, unit = "PACKET", checked = true),
            )

            val restockedCount = repo.restockCheckedGroceryToPantry()
            assertEquals(2, restockedCount)

            val activePantry = repo.observePantryItems().first()
            val updatedFlour = activePantry.first { it.id == 101L }
            assertEquals(1500f, updatedFlour.quantity, 0.01f)

            val newYeast = activePantry.firstOrNull { it.name == "Yeast" }
            assertNotNull(newYeast)
            assertEquals(3f, newYeast?.quantity ?: 0f, 0.01f)

            // Checked grocery items should be cleared
            val remainingGrocery = repo.observeGroceryItems().first()
            assertTrue(remainingGrocery.isEmpty())
        }

    @Test
    fun r4ZeroFloorStockProtection() =
        runBlocking {
            val pantryItem = PantryItem(id = 301L, name = "Butter", quantity = 50f, unit = "G")
            val repo = InMemoryPantryRepository(listOf(pantryItem))

            // Attempt to deduct 80g when only 50g are in stock
            repo.decrementQuantity(pantryItem, 80f)

            val updated = repo.observePantryItems().first().first { it.id == 301L }
            assertEquals(0f, updated.quantity, 0.001f)
        }
}
