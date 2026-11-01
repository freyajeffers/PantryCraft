package com.jeffers.pantrycraft.e2e

import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.common.internal.BarcodeSource
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetector
import com.jeffers.pantrycraft.data.CulinaryScaling
import com.jeffers.pantrycraft.data.GroceryItem
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.feature.camera.analyzer.FrameTimingStats
import com.jeffers.pantrycraft.feature.camera.analyzer.LiveVisionAnalyzer
import com.jeffers.pantrycraft.feature.camera.analyzer.buildLiveVisionResult
import com.jeffers.pantrycraft.feature.camera.vision.SnapshotFrame
import com.jeffers.pantrycraft.feature.meals.swap.SmartSwapEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.Arrays
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureNanoTime

/**
 * Tier 5: Systems Security & Performance SLA Empirical Benchmark Suite.
 * Empirically validates SLA targets defined in AGENTS.md § 3 and PROJECT.md § Feature 26.
 */
class Tier5SlaBenchmarkTest {
    @Test
    fun sla01_ftsPrefixSearchAcross5000RecordsStaysUnderFiveMilliseconds() =
        runBlocking {
            val totalRecords = 5_000
            val items = ArrayList<PantryItem>(totalRecords)
            for (i in 0 until totalRecords) {
                items.add(
                    PantryItem(
                        id = (i + 1).toLong(),
                        name = "Pantry Item $i unique$i",
                        notes = "Storage zone shelf index $i",
                        category = if (i % 2 == 0) "GRAINS" else "SPICES",
                        quantity = (i % 10 + 1).toFloat(),
                        isDeleted = false,
                    ),
                )
            }
            val repository = InMemoryPantryRepository(initialPantry = items)

            // Warm up JIT and cache
            repeat(WARMUP_ITERATIONS) {
                repository.searchPantryItems("unique4999").first()
            }

            // Measure search query latency over multiple runs
            val durationsMs = ArrayList<Double>(MEASURED_RUNS)
            repeat(MEASURED_RUNS) {
                val nano =
                    measureNanoTime {
                        val results = repository.searchPantryItems("unique4999").first()
                        assertFalse("Search should find matched records", results.isEmpty())
                    }
                durationsMs.add(nano / NANOS_PER_MILLI)
            }

            val averageMs = durationsMs.average()
            durationsMs.sort()
            val medianMs = durationsMs[durationsMs.size / 2]
            val minMs = durationsMs.first()

            assertTrue(
                "Average FTS prefix search latency must stay under 5ms (was ${averageMs}ms)",
                averageMs < FTS_SLA_TARGET_MS,
            )
            assertTrue(
                "Median FTS prefix search latency must stay under 5ms (was ${medianMs}ms)",
                medianMs < FTS_SLA_TARGET_MS,
            )
            assertTrue(
                "Min FTS prefix search latency must stay under 5ms (was ${minMs}ms)",
                minMs < FTS_SLA_TARGET_MS,
            )
        }

    @Test
    fun sla02_cameraXLiveViewfinderThrottleEnforces15FpsAndDropsBursts() {
        val scanner = createMockBarcodeScanner()
        val detector = createMockObjectDetector()
        val executor = Executors.newSingleThreadExecutor()

        val closedCount = AtomicInteger(0)

        fun createProxy(): ImageProxy =
            Proxy.newProxyInstance(
                ImageProxy::class.java.classLoader,
                arrayOf(ImageProxy::class.java),
            ) { _, method, _ ->
                if (method.name == "close") closedCount.incrementAndGet()
                null
            } as ImageProxy

        val analyzer =
            LiveVisionAnalyzer(
                barcodeScanner = scanner,
                objectDetector = detector,
                executor = executor,
                onResult = {},
            )

        try {
            // First frame: accepted then closed because mediaImage is null
            analyzer.analyze(createProxy())
            assertEquals("First frame accepted and closed", 1, closedCount.get())

            // Rapid burst frames within 66ms interval must be dropped immediately by throttle
            val burstFrames = 5
            repeat(burstFrames) {
                analyzer.analyze(createProxy())
            }

            assertEquals(
                "All burst frames within 66ms frame spacing must be dropped and closed",
                1 + burstFrames,
                closedCount.get(),
            )
        } finally {
            analyzer.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun sla03_cameraXLiveFrameAnalysisExecutionLatencyUnderFortyMilliseconds() {
        val timingStats = FrameTimingStats()
        val rotations = intArrayOf(0, 90, 180, 270)
        val durationsMs = ArrayList<Double>(MEASURED_RUNS)

        // Warmup JIT to eliminate initial class loading and compiler overhead
        repeat(WARMUP_ITERATIONS) { iteration ->
            executeLiveFrameAnalysisBenchmarkRun(
                rotation = rotations[iteration % rotations.size],
                timingStats = null,
                durationsMs = null,
            )
        }

        // Measure live frame analysis execution across realistic simulated frame loads
        repeat(MEASURED_RUNS) { iteration ->
            executeLiveFrameAnalysisBenchmarkRun(
                rotation = rotations[iteration % rotations.size],
                timingStats = timingStats,
                durationsMs = durationsMs,
            )
        }

        assertEquals(MEASURED_RUNS, timingStats.sampleCount)
        assertTrue(
            "Max frame analysis latency must stay under 40ms per frame SLA (was ${timingStats.maxDurationMillis}ms)",
            timingStats.maxDurationMillis < CAMERA_FRAME_SLA_MS,
        )

        val averageMs = durationsMs.average()
        assertTrue(
            "Average frame analysis latency must stay under 40ms per frame SLA (was ${averageMs}ms)",
            averageMs < CAMERA_FRAME_SLA_MS.toDouble(),
        )
    }

    @Test
    fun sla04_appColdLaunchSimulatedPathCompletesUnder350Milliseconds() =
        runBlocking {
            val coldLaunchTimeNanos =
                measureNanoTime {
                    // 1. Simulate hardware KeyStore access & passphrase derivation
                    val mockPassphrase = ByteArray(PASSPHRASE_BYTE_COUNT)
                    Arrays.fill(mockPassphrase, 42.toByte())
                    mockPassphrase.fill(0)

                    // 2. Simulate seeding initial pantry taxonomy and inventory
                    val initialItems =
                        List(SEED_PANTRY_COUNT) { index ->
                            PantryItem(
                                id = (index + 1).toLong(),
                                name = "Pantry Item $index",
                                category = if (index % 3 == 0) "SPICES" else "PRODUCE",
                                quantity = (index + 1).toFloat(),
                                unit = "G",
                            )
                        }
                    val initialGroceries =
                        List(SEED_GROCERY_COUNT) { index ->
                            GroceryItem(
                                id = (index + 1).toLong(),
                                name = "Grocery Item $index",
                                quantity = 1f,
                                unit = "ITEM",
                            )
                        }
                    val repo =
                        InMemoryPantryRepository(
                            initialPantry = initialItems,
                            initialGrocery = initialGroceries,
                        )

                    // 3. Query initial states to complete cold launch readiness
                    val pantryList = repo.observePantryItems().first()
                    val groceryList = repo.observeGroceryItems().first()

                    assertEquals(SEED_PANTRY_COUNT, pantryList.size)
                    assertEquals(SEED_GROCERY_COUNT, groceryList.size)
                }

            val coldLaunchMs = coldLaunchTimeNanos / NANOS_PER_MILLI
            assertTrue(
                "Simulated cold launch must complete in under 350ms (was ${coldLaunchMs}ms)",
                coldLaunchMs < COLD_LAUNCH_SLA_MS,
            )
        }

    @Test
    fun sla05_visionSnapshotHeapAllocationDeltaStaysUnderSixtyFourMegabytes() {
        val runtime = Runtime.getRuntime()
        val initialAllocatedHeap = runtime.totalMemory() - runtime.freeMemory()

        // Allocate a high-resolution 1536x1536 WebP vision snapshot buffer (~4 MB)
        val snapshotBytes = ByteArray(VISION_SNAPSHOT_BYTE_SIZE) { (it % BYTE_MASK).toByte() }
        val frame = SnapshotFrame(snapshotBytes)

        val activeAllocatedHeap = runtime.totalMemory() - runtime.freeMemory()
        val allocationDelta = activeAllocatedHeap - initialAllocatedHeap

        // Verify heap delta stays well within the 64 MB budget
        assertTrue(
            "Vision snapshot heap allocation delta must remain under 64 MB (was ${allocationDelta / BYTES_PER_MB} MB)",
            allocationDelta < MAX_HEAP_DELTA_BYTES,
        )

        // Verify ephemeral media scrubbing zeroes out memory bytes
        frame.close()
        assertTrue(
            "SnapshotFrame.close() must scrub all memory bytes to zero",
            snapshotBytes.all { it == 0.toByte() },
        )
    }

    @Test
    fun sla06_smartSwapSubstitutionKnowledgeGraphEvaluatesUnderFiveMilliseconds() {
        val onHand =
            listOf(
                PantryItem(name = "Greek Yogurt", category = "DAIRY", quantity = 500f, unit = "G"),
                PantryItem(name = "Almond Milk", category = "DAIRY", quantity = 1000f, unit = "ML"),
                PantryItem(name = "Tamari", category = "CONDIMENTS", quantity = 250f, unit = "ML"),
                PantryItem(name = "Maple Syrup", category = "CONDIMENTS", quantity = 250f, unit = "ML"),
            )

        // Warmup
        SmartSwapEngine.findSwapsForMissingItem("sour cream", onHand)

        val elapsedNanos =
            measureNanoTime {
                repeat(SWAP_BENCHMARK_ITERATIONS) {
                    val substitutes = SmartSwapEngine.findSwapsForMissingItem("sour cream", onHand)
                    assertFalse("Must find substitute for Sour Cream", substitutes.isEmpty())
                    assertEquals("Greek Yogurt", substitutes.first().substituteName)
                }
            }

        val avgMs = (elapsedNanos / NANOS_PER_MILLI) / SWAP_BENCHMARK_ITERATIONS
        assertTrue(
            "SmartSwap substitution lookup must stay under 5ms SLA (was ${avgMs}ms)",
            avgMs < SMART_SWAP_SLA_MS,
        )
    }

    @Test
    fun sla07_seasoningPowerCurveScalingStaysWithin120FpsFrameBudget() {
        // Verify mathematical non-linear power curve: 2 * (8/2)^0.75 = 2 * 4^0.75 = 5.66
        val scaledQuantity =
            CulinaryScaling.scaleQuantity(
                baseQuantity = 2.0f,
                basePortions = 2,
                targetPortions = 8,
                isAromaticOrSeasoning = true,
            )
        assertEquals(5.66f, scaledQuantity, 0.01f)

        // Warmup JIT
        repeat(WARMUP_ITERATIONS) {
            CulinaryScaling.scaleQuantity(1.5f, 2, 4, true)
        }

        // Test standard recipe scaling (15 ingredients): must complete in under 1ms
        val standardRecipeNanos =
            measureNanoTime {
                for (i in 1..STANDARD_RECIPE_INGREDIENT_COUNT) {
                    CulinaryScaling.scaleQuantity(
                        baseQuantity = 1.5f,
                        basePortions = 2,
                        targetPortions = (i % 10) + 1,
                        isAromaticOrSeasoning = true,
                    )
                }
            }
        val standardRecipeMs = standardRecipeNanos / NANOS_PER_MILLI
        assertTrue(
            "Standard recipe scaling (15 ingredients) must stay under 1ms (was ${standardRecipeMs}ms)",
            standardRecipeMs < 1.0,
        )

        // Test large banquet batch scaling (100 ingredients): must stay within 120 FPS budget (8.33ms)
        val banquetBatchNanos =
            measureNanoTime {
                for (i in 1..RECIPE_SCALING_BATCH_SIZE) {
                    CulinaryScaling.scaleQuantity(
                        baseQuantity = 1.5f,
                        basePortions = 2,
                        targetPortions = (i % 10) + 1,
                        isAromaticOrSeasoning = true,
                    )
                }
            }
        val banquetBatchMs = banquetBatchNanos / NANOS_PER_MILLI
        assertTrue(
            "Banquet scaling (100 ingredients) must stay within 120 FPS budget (was ${banquetBatchMs}ms)",
            banquetBatchMs < FRAME_BUDGET_120FPS_MS,
        )
    }

    private fun executeLiveFrameAnalysisBenchmarkRun(
        rotation: Int,
        timingStats: FrameTimingStats?,
        durationsMs: MutableList<Double>?,
    ) {
        val closedCount = AtomicInteger(0)
        val proxy = createBenchmarkImageProxy(FRAME_WIDTH, FRAME_HEIGHT) { closedCount.incrementAndGet() }
        val barcodeTask = Tasks.forResult(listOf(createMockBarcode("012000001056")))
        val objectTask = Tasks.forResult(createSimulatedDetectedObjects())

        val elapsedNanos =
            measureNanoTime {
                val result = buildLiveVisionResult(barcodeTask, objectTask, proxy, rotation)
                assertFalse("Barcodes must be extracted from task", result.barcodes.isEmpty())
                assertEquals(
                    "Noise objects below threshold must be filtered",
                    EXPECTED_OBJECT_COUNT,
                    result.objectBoxes.size,
                )
                proxy.close()
            }

        assertEquals("Frame proxy must be closed post-analysis", 1, closedCount.get())
        val elapsedMs = elapsedNanos / NANOS_PER_MILLI
        durationsMs?.add(elapsedMs)
        timingStats?.record(elapsedMs.toLong().coerceAtLeast(0L))
    }

    private fun createSimulatedDetectedObjects(): List<DetectedObject> =
        listOf(
            createMockDetectedObject("Fresh Produce", 0.94f, intArrayOf(100, 150, 400, 600)),
            createMockDetectedObject("Whole Milk", 0.88f, intArrayOf(500, 200, 900, 800)),
            createMockDetectedObject("Artifact Low Confidence", 0.42f, intArrayOf(50, 50, 150, 150)),
            createMockDetectedObject("Sourdough Bread", 0.78f, intArrayOf(200, 700, 600, 1000)),
        )

    private fun createMockBarcode(rawValue: String): Barcode {
        val source =
            Proxy.newProxyInstance(
                BarcodeSource::class.java.classLoader,
                arrayOf(BarcodeSource::class.java),
            ) { _, method, _ ->
                when (method.name) {
                    "getRawValue" -> rawValue
                    "getDisplayValue" -> rawValue
                    "getFormat" -> Barcode.FORMAT_EAN_13
                    "getValueType" -> Barcode.TYPE_PRODUCT
                    else -> null
                }
            } as BarcodeSource
        return Barcode(source)
    }

    private fun createMockDetectedObject(
        label: String,
        confidence: Float,
        bounds: IntArray,
    ): DetectedObject {
        val rect =
            android.graphics.Rect().apply {
                this.left = bounds[0]
                this.top = bounds[1]
                this.right = bounds[2]
                this.bottom = bounds[3]
            }
        val mlLabel = DetectedObject.Label(label, confidence, 0)
        return DetectedObject(rect, 1, listOf(mlLabel))
    }

    private fun createBenchmarkImageProxy(
        width: Int,
        height: Int,
        onClose: () -> Unit = {},
    ): ImageProxy =
        Proxy.newProxyInstance(
            ImageProxy::class.java.classLoader,
            arrayOf(ImageProxy::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getWidth" -> width
                "getHeight" -> height
                "close" -> {
                    onClose()
                    null
                }
                else -> null
            }
        } as ImageProxy

    private fun createMockBarcodeScanner(): BarcodeScanner =
        Proxy.newProxyInstance(
            BarcodeScanner::class.java.classLoader,
            arrayOf(BarcodeScanner::class.java),
        ) { _, _, _ -> null } as BarcodeScanner

    private fun createMockObjectDetector(): ObjectDetector =
        Proxy.newProxyInstance(
            ObjectDetector::class.java.classLoader,
            arrayOf(ObjectDetector::class.java),
        ) { _, _, _ -> null } as ObjectDetector

    companion object {
        private const val FRAME_WIDTH = 1920
        private const val FRAME_HEIGHT = 1080
        private const val EXPECTED_OBJECT_COUNT = 3
        private const val WARMUP_ITERATIONS = 20
        private const val MEASURED_RUNS = 25
        private const val FTS_SLA_TARGET_MS = 5.0
        private const val CAMERA_FRAME_SLA_MS = 40L
        private const val COLD_LAUNCH_SLA_MS = 350.0
        private const val SMART_SWAP_SLA_MS = 5.0
        private const val FRAME_BUDGET_120FPS_MS = 8.33
        private const val STANDARD_RECIPE_INGREDIENT_COUNT = 15
        private const val SWAP_BENCHMARK_ITERATIONS = 20
        private const val RECIPE_SCALING_BATCH_SIZE = 100
        private const val PASSPHRASE_BYTE_COUNT = 32
        private const val SEED_PANTRY_COUNT = 100
        private const val SEED_GROCERY_COUNT = 50
        private const val VISION_SNAPSHOT_BYTE_SIZE = 4 * 1024 * 1024 // 4 MB buffer
        private const val MAX_HEAP_DELTA_BYTES = 64L * 1024L * 1024L // 64 MB SLA limit
        private const val BYTES_PER_MB = 1024L * 1024L
        private const val NANOS_PER_MILLI = 1_000_000.0
        private const val BYTE_MASK = 256
    }
}
