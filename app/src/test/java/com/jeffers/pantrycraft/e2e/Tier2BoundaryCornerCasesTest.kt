package com.jeffers.pantrycraft.e2e

import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.objects.ObjectDetector
import com.jeffers.pantrycraft.data.CulinaryScaling
import com.jeffers.pantrycraft.data.CustomBarcodeItem
import com.jeffers.pantrycraft.data.DatabaseKeyInvalidatedException
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.readBackupHeader
import com.jeffers.pantrycraft.data.reclaimScratchMedia
import com.jeffers.pantrycraft.data.validatePantryItem
import com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionAnalyzer
import com.jeffers.pantrycraft.feature.camera.barcode.OfflineBarcodeResolver
import com.jeffers.pantrycraft.feature.meals.deduction.MealCookHandler
import com.jeffers.pantrycraft.feature.meals.engine.FallbackMealSynthesizer
import com.jeffers.pantrycraft.feature.meals.model.MAX_PORTIONS
import com.jeffers.pantrycraft.feature.meals.model.MIN_PORTIONS
import com.jeffers.pantrycraft.feature.meals.model.MealMatchMode
import com.jeffers.pantrycraft.feature.meals.model.MealRecipe
import com.jeffers.pantrycraft.feature.meals.model.RecipePantryUsage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tier 2: Boundary & Corner Cases Test Suite.
 * Covers extremes, negative/zero values, boundary clamps, corrupted inputs, and error states across R1-R4.
 */
class Tier2BoundaryCornerCasesTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    // -------------------------------------------------------------------------
    // R1: Storage & Cryptographic Boundaries
    // -------------------------------------------------------------------------

    @Test
    fun r1NegativePantryQuantityThrows() {
        val item = PantryItem(name = "Flour", quantity = -5f, storageLocation = "PANTRY")
        assertThrows(IllegalArgumentException::class.java) {
            validatePantryItem(item)
        }
    }

    @Test
    fun r1BlankPantryItemNameThrows() {
        val emptyItem = PantryItem(name = "   ", quantity = 10f, storageLocation = "PANTRY")
        assertThrows(IllegalArgumentException::class.java) {
            validatePantryItem(emptyItem)
        }
    }

    @Test
    fun r1NegativeThresholdThrows() {
        val item = PantryItem(name = "Milk", quantity = 1f, threshold = -1f, storageLocation = "FRIDGE")
        assertThrows(IllegalArgumentException::class.java) {
            validatePantryItem(item)
        }
    }

    @Test
    fun r1InvalidStorageLocationThrows() {
        val item = PantryItem(name = "Apples", quantity = 5f, storageLocation = "BACKYARD_SHED")
        assertThrows(IllegalArgumentException::class.java) {
            validatePantryItem(item)
        }
    }

    @Test
    fun r1DatabaseKeyInvalidatedExceptionCause() {
        val cause = IllegalStateException("Hardware KeyStore wiped after biometric enrollment change")
        val exception = DatabaseKeyInvalidatedException(cause)

        assertEquals("The hardware-backed database key is no longer valid", exception.message)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun r1CorruptAndTruncatedBackupHeaders() {
        val badMagicStream = ByteArrayInputStream("WRONG_MAGIC_HEADER_BYTES".toByteArray())
        assertThrows(IOException::class.java) {
            readBackupHeader(badMagicStream)
        }

        val truncatedStream = ByteArrayInputStream("PC".toByteArray())
        assertThrows(IOException::class.java) {
            readBackupHeader(truncatedStream)
        }

        val badVersion = "PCBK".toByteArray() + byteArrayOf(42) + ByteArray(28)
        assertThrows(IOException::class.java) {
            readBackupHeader(ByteArrayInputStream(badVersion))
        }
    }

    // -------------------------------------------------------------------------
    // R2: Vision & Barcode Boundaries
    // -------------------------------------------------------------------------

    @Test
    fun r2InvalidBarcodeFormatThrows() {
        val resolver = OfflineBarcodeResolver(customBarcodeDao = InMemoryCustomBarcodeDao())

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { resolver.resolveBarcode("12345") } // too short (<8)
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { resolver.resolveBarcode("12345678901234567") } // too long (>14)
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { resolver.resolveBarcode("ABCD1234EFGH") } // non-numeric
        }
    }

    @Test
    fun r2EmptyAndBlankBarcodeThrows() {
        val resolver = OfflineBarcodeResolver(customBarcodeDao = InMemoryCustomBarcodeDao())

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { resolver.resolveBarcode("") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { resolver.resolveBarcode("     ") }
        }
    }

    @Test
    fun r2AssociateBarcodeClampsNegativeShelfLife() =
        runBlocking {
            val customDao = InMemoryCustomBarcodeDao()
            val resolver = OfflineBarcodeResolver(customBarcodeDao = customDao)

            val match =
                resolver.associateBarcode(
                    barcode = "123456789012",
                    name = "Frozen Berries",
                    category = "FROZEN",
                    estimatedShelfLifeDays = -45, // negative input
                )

            assertEquals(0, match.estimatedShelfLifeDays)
            val persisted = customDao.findByBarcode("123456789012")
            assertEquals(0, persisted?.estimatedShelfLifeDays)
        }

    @Test
    fun r2ReclaimScratchMediaOnNonExistentOrEmptyDir() {
        val nonExistent = File(tempFolder.root, "does_not_exist")
        val reclaimedMissing = reclaimScratchMedia(nonExistent)
        assertEquals(0, reclaimedMissing)

        val regularFile = tempFolder.newFile("not_a_dir.tmp")
        val reclaimedFile = reclaimScratchMedia(regularFile)
        assertEquals(0, reclaimedFile)
    }

    @Test
    fun r2RapidBarcodeFlappingUnderDebounce() =
        runBlocking {
            val customDao = InMemoryCustomBarcodeDao()
            val resolver = OfflineBarcodeResolver(customBarcodeDao = customDao)
            val barcodeA = "1111222233334"
            val barcodeB = "5555666677778"

            customDao.upsert(CustomBarcodeItem(barcode = barcodeA, name = "Product A"))
            customDao.upsert(CustomBarcodeItem(barcode = barcodeB, name = "Product B"))

            val scanA1 = resolver.resolveBarcode(barcodeA)
            assertNotNull(scanA1)

            // Switching to different barcode bypasses same-barcode debounce
            val scanB1 = resolver.resolveBarcode(barcodeB)
            assertNotNull(scanB1)

            // Switching back to A is treated as new scan since last code was B
            val scanA2 = resolver.resolveBarcode(barcodeA)
            assertNotNull(scanA2)

            // Immediate repeated scan of A is debounced
            val scanA3 = resolver.resolveBarcode(barcodeA)
            assertNull(scanA3)
        }

    @Test
    fun r2LiveVisionAnalyzerHighRateFrameDrop() {
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

        val closeCounter = AtomicInteger(0)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val analyzer =
                LiveVisionAnalyzer(
                    barcodeScanner = barcodeScanner,
                    objectDetector = objectDetector,
                    executor = executor,
                    onResult = {},
                )

            // Feed 5 frames back-to-back with zero delay
            repeat(5) {
                val proxy =
                    Proxy.newProxyInstance(
                        ImageProxy::class.java.classLoader,
                        arrayOf(ImageProxy::class.java),
                    ) { _, method, _ ->
                        if (method.name == "close") closeCounter.incrementAndGet()
                        null
                    } as ImageProxy
                analyzer.analyze(proxy)
            }

            // Every submitted proxy must be closed even when throttled
            assertEquals(5, closeCounter.get())
        } finally {
            executor.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // R3: Culinary Scaling & Synthesis Boundaries
    // -------------------------------------------------------------------------

    @Test
    fun r3ZeroBaseQuantityReturnsZero() {
        val scaled =
            CulinaryScaling.scaleQuantity(
                baseQuantity = 0f,
                basePortions = 2,
                targetPortions = 8,
                isAromaticOrSeasoning = true,
            )
        assertEquals(0f, scaled, 0.0001f)
    }

    @Test
    fun r3NegativeBaseQuantityThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            CulinaryScaling.scaleQuantity(
                baseQuantity = -10f,
                basePortions = 2,
                targetPortions = 4,
                isAromaticOrSeasoning = false,
            )
        }
    }

    @Test
    fun r3ZeroOrNegativePortionsThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            CulinaryScaling.scaleQuantity(100f, 0, 4, false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CulinaryScaling.scaleQuantity(100f, 2, -1, false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CulinaryScaling.scaleQuantity(100f, -2, 4, false)
        }
    }

    @Test
    fun r3PalateExponentExtremeClamping() {
        // Extreme negative exponent clamped to MIN_PALATE_EXPONENT (0.65)
        val extremeNegative =
            CulinaryScaling.scaleQuantity(
                baseQuantity = 1f,
                basePortions = 1,
                targetPortions = 4,
                isAromaticOrSeasoning = true,
                exponent = -100.0,
            )
        val expectedMin =
            CulinaryScaling.scaleQuantity(
                baseQuantity = 1f,
                basePortions = 1,
                targetPortions = 4,
                isAromaticOrSeasoning = true,
                exponent = 0.65,
            )
        assertEquals(expectedMin, extremeNegative, 0.001f)

        // Extreme positive exponent clamped to MAX_PALATE_EXPONENT (0.85)
        val extremePositive =
            CulinaryScaling.scaleQuantity(
                baseQuantity = 1f,
                basePortions = 1,
                targetPortions = 4,
                isAromaticOrSeasoning = true,
                exponent = 100.0,
            )
        val expectedMax =
            CulinaryScaling.scaleQuantity(
                baseQuantity = 1f,
                basePortions = 1,
                targetPortions = 4,
                isAromaticOrSeasoning = true,
                exponent = 0.85,
            )
        assertEquals(expectedMax, extremePositive, 0.001f)
    }

    @Test
    fun r3InvalidCulinaryVolumeUnitConversions() {
        assertNull(CulinaryScaling.convertVolume(10f, "GRAMS", "TSP"))
        assertNull(CulinaryScaling.convertVolume(5f, "CUP", "OUNCE"))
        assertNull(CulinaryScaling.convertVolume(1f, "UNKNOWN_UNIT", "ML"))
    }

    @Test
    fun r3EmptyInventorySynthesisProducesEmptyRecipes() {
        val emptyList = emptyList<PantryItem>()
        val result =
            FallbackMealSynthesizer.synthesize(
                items = emptyList,
                mode = MealMatchMode.STRICT,
                targetPortions = 2,
            )
        assertTrue(result.isEmpty())

        val deletedOnly =
            listOf(
                PantryItem(name = "Old Apple", quantity = 1f, isDeleted = true),
            )
        val deletedResult =
            FallbackMealSynthesizer.synthesize(
                items = deletedOnly,
                mode = MealMatchMode.STRICT,
                targetPortions = 2,
            )
        assertTrue(deletedResult.isEmpty())
    }

    // -------------------------------------------------------------------------
    // R4: Deduction & Replenishment Boundaries
    // -------------------------------------------------------------------------

    @Test
    fun r4PortionClampingLowerBoundViolation() {
        val recipe = MealRecipe(recipeId = "rec_test", title = "Soup", targetPortions = 2)

        assertThrows(IllegalArgumentException::class.java) {
            recipe.scaleToPortions(0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            recipe.scaleToPortions(-3)
        }
    }

    @Test
    fun r4PortionClampingUpperBoundViolation() {
        val recipe = MealRecipe(recipeId = "rec_test", title = "Soup", targetPortions = 2)

        assertThrows(IllegalArgumentException::class.java) {
            recipe.scaleToPortions(13)
        }
        assertThrows(IllegalArgumentException::class.java) {
            recipe.scaleToPortions(100)
        }
    }

    @Test
    fun r4PortionsBoundaryValuesOneAndTwelve() {
        val recipe =
            MealRecipe(
                recipeId = "rec_boundary",
                title = "Pancake Stack",
                targetPortions = 4,
                itemsUsedFromStock =
                    listOf(
                        RecipePantryUsage(name = "Flour", quantityUsed = 400f, unit = "G"),
                    ),
            )

        val minScaled = recipe.scaleToPortions(MIN_PORTIONS)
        assertEquals(1, minScaled.targetPortions)
        assertEquals(100f, minScaled.itemsUsedFromStock.first().quantityUsed, 0.01f)

        val maxScaled = recipe.scaleToPortions(MAX_PORTIONS)
        assertEquals(12, maxScaled.targetPortions)
        assertEquals(1200f, maxScaled.itemsUsedFromStock.first().quantityUsed, 0.01f)
    }

    @Test
    fun r4CookMealWithZeroOrNegativeQuantityUsed() =
        runBlocking {
            val pantryItem = PantryItem(id = 501L, name = "Salt", quantity = 100f, unit = "G")
            val pantryRepo = InMemoryPantryRepository(listOf(pantryItem))
            val groceryRepo = InMemoryPantryRepository()

            val recipe =
                MealRecipe(
                    recipeId = "rec_zero_qty",
                    title = "Zero Qty Recipe",
                    itemsUsedFromStock =
                        listOf(
                            RecipePantryUsage(name = "Salt", pantryItemId = 501L, quantityUsed = 0f),
                            RecipePantryUsage(name = "Salt", pantryItemId = 501L, quantityUsed = -5f),
                        ),
                )

            val result =
                MealCookHandler.executeCookMeal(
                    recipe = recipe,
                    pantryRepository = pantryRepo,
                    groceryRepository = groceryRepo,
                )

            assertEquals(0, result.itemsDeductedCount)
            val remaining = pantryRepo.observePantryItems().first().first { it.id == 501L }
            assertEquals(100f, remaining.quantity, 0.001f)
        }

    @Test
    fun r4StockDecrementFiniteCheck() {
        val item = PantryItem(id = 601L, name = "Sugar", quantity = 50f)
        val repo = InMemoryPantryRepository(listOf(item))

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.decrementQuantity(item, -10f) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.decrementQuantity(item, Float.NaN) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.decrementQuantity(item, Float.POSITIVE_INFINITY) }
        }
    }

    @Test
    fun r4ExpiredPantryItemUrgencyScoring() {
        val now = 2_000_000L
        val expiredItem =
            PantryItem(
                id = 701L,
                name = "Yogurt",
                quantity = 250f,
                unit = "G",
                expirationTimestamp = now - 3600_000L, // expired 1 hour ago
            )
        val freshItem =
            PantryItem(
                id = 702L,
                name = "Dry Beans",
                quantity = 500f,
                unit = "G",
                expirationTimestamp = now + (365L * 86_400_000L), // fresh
            )

        val recipes =
            FallbackMealSynthesizer.synthesize(
                items = listOf(expiredItem, freshItem),
                mode = MealMatchMode.EMERGENCY_SALVAGE,
                targetPortions = 2,
                nowMillis = now,
            )

        assertEquals(1, recipes.size)
        val recipe = recipes.first()
        // Expired item prioritized as main item
        assertTrue(recipe.title.contains("Yogurt"))
        assertEquals(0.95f, recipe.urgencyScore, 0.01f)
    }
}
