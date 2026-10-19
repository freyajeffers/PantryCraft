package com.jeffers.pantrycraft.feature.camera.barcode

import com.jeffers.pantrycraft.data.CustomBarcodeDao
import com.jeffers.pantrycraft.data.CustomBarcodeItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BarcodeMatch(
    val barcode: String,
    val name: String,
    val category: String,
    val unit: String,
    val estimatedShelfLifeDays: Int,
)

class OfflineBarcodeResolver(
    private val customBarcodeDao: CustomBarcodeDao,
    private var assetBarcodeDatabase: AssetBarcodeDatabase? = null,
    private val assetBarcodeDatabaseProvider: () -> AssetBarcodeDatabase? = { null },
) : AutoCloseable {
    private var lastScannedBarcode: String? = null
    private var lastScannedAt = 0L

    suspend fun resolveBarcode(rawCode: String): BarcodeMatch? {
        val barcode = rawCode.trim()
        require(barcode.matches(Regex("\\d{8,14}"))) { "Unsupported barcode format" }
        val now = System.currentTimeMillis()
        if (barcode == lastScannedBarcode && now - lastScannedAt < DEBOUNCE_MS) return null
        lastScannedBarcode = barcode
        lastScannedAt = now
        return withContext(Dispatchers.IO) {
            customBarcodeDao.findByBarcode(barcode)?.toMatch(barcode)
                ?: activeAssetDatabase()?.findByBarcode(barcode)?.toMatch()
                ?: EmbeddedBarcodeCatalog.products[barcode]?.toMatch(barcode)
        }
    }

    suspend fun remember(item: CustomBarcodeItem) =
        withContext(Dispatchers.IO) {
            require(item.barcode.matches(Regex("\\d{8,14}"))) { "Unsupported barcode format" }
            customBarcodeDao.upsert(item)
        }

    suspend fun associateBarcode(
        barcode: String,
        name: String,
        category: String = "OTHER",
        defaultUnit: String = "COUNT",
        estimatedShelfLifeDays: Int = 0,
    ): BarcodeMatch {
        val cleanBarcode = barcode.trim()
        val item =
            CustomBarcodeItem(
                barcode = cleanBarcode,
                name = name.trim(),
                category = category.trim().ifEmpty { "OTHER" },
                defaultUnit = defaultUnit.trim().ifEmpty { "COUNT" },
                estimatedShelfLifeDays = estimatedShelfLifeDays.coerceAtLeast(0),
                lastScanned = System.currentTimeMillis(),
            )
        remember(item)
        return item.toMatch(cleanBarcode)
    }

    @Synchronized
    fun reloadCatalog() {
        runCatching { assetBarcodeDatabase?.close() }
        assetBarcodeDatabase = runCatching { assetBarcodeDatabaseProvider() }.getOrNull()
    }

    @Synchronized
    private fun activeAssetDatabase(): AssetBarcodeDatabase? {
        if (assetBarcodeDatabase == null) {
            assetBarcodeDatabase = runCatching { assetBarcodeDatabaseProvider() }.getOrNull()
        }
        return assetBarcodeDatabase
    }

    @Synchronized
    override fun close() {
        runCatching { assetBarcodeDatabase?.close() }
        assetBarcodeDatabase = null
    }

    private fun CustomBarcodeItem.toMatch(barcode: String) =
        BarcodeMatch(
            barcode,
            name,
            category,
            defaultUnit,
            estimatedShelfLifeDays,
        )

    private fun EmbeddedProduct.toMatch(barcode: String) = BarcodeMatch(barcode, name, category, unit, shelfLifeDays)

    private fun EmbeddedBarcodeMatch.toMatch() =
        BarcodeMatch(
            barcode = barcode,
            name = if (brand.isNotBlank() && !name.contains(brand, ignoreCase = true)) "$brand $name" else name,
            category = category,
            unit = "COUNT",
            estimatedShelfLifeDays = shelfLifeDays,
        )

    companion object {
        private const val DEBOUNCE_MS = 2_500L
    }
}

private data class EmbeddedProduct(
    val name: String,
    val category: String,
    val unit: String,
    val shelfLifeDays: Int,
)

/** Small deterministic seed catalog; the full OpenFoodFacts asset can be added without API changes. */
private object EmbeddedBarcodeCatalog {
    val products =
        mapOf(
            "012000001056" to EmbeddedProduct("Coca-Cola", "OTHER", "COUNT", SHELF_LIFE_YEAR_DAYS),
            "011110038364" to EmbeddedProduct("Great Value Milk", "DAIRY", "COUNT", SHELF_LIFE_TWO_WEEKS_DAYS),
            "004100007012" to EmbeddedProduct("Cheerios", "PANTRY_STAPLE", "COUNT", SHELF_LIFE_YEAR_DAYS),
        )

    private const val SHELF_LIFE_YEAR_DAYS = 365
    private const val SHELF_LIFE_TWO_WEEKS_DAYS = 14
}
