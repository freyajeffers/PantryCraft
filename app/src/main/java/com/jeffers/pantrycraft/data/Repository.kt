package com.jeffers.pantrycraft.data

import android.content.Context
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.jeffers.pantrycraft.feature.camera.barcode.AssetBarcodeDatabase
import com.jeffers.pantrycraft.feature.camera.barcode.CatalogDownloadService
import com.jeffers.pantrycraft.feature.camera.barcode.OfflineBarcodeResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

class Repository private constructor(
    private val db: AppDatabase,
    private val appContext: Context,
) {
    private val pantryDao = db.pantryDao()
    internal val groceryDao = db.groceryDao()

    fun observePantryItems(): Flow<List<PantryItem>> = pantryDao.observeAllPantryItems()

    fun offlineBarcodeResolver(): OfflineBarcodeResolver =
        OfflineBarcodeResolver(
            customBarcodeDao = db.customBarcodeDao(),
            assetBarcodeDatabase = runCatching { AssetBarcodeDatabase(appContext) }.getOrNull(),
            assetBarcodeDatabaseProvider = { runCatching { AssetBarcodeDatabase(appContext) }.getOrNull() },
        )

    suspend fun downloadOfflineCatalog(onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }) =
        CatalogDownloadService(appContext).downloadIfNeeded(onProgress = onProgress)

    fun searchPantryItems(query: String): Flow<List<PantryItem>> {
        val sanitized = sanitizeSearchQuery(query)
        val ftsQuery =
            if (sanitized.isEmpty()) {
                ""
            } else {
                sanitized
                    .split(Regex("\\s+"))
                    .filter { it.isNotBlank() }
                    .joinToString(" ") { term ->
                        "\"${term.replace("\"", "\"\"")}\"*"
                    }
            }
        return if (ftsQuery.isBlank()) {
            observePantryItems()
        } else {
            pantryDao.search(
                SimpleSQLiteQuery(
                    "SELECT pantry_items.* FROM pantry_items " +
                        "JOIN pantry_items_fts ON pantry_items.rowid = pantry_items_fts.rowid " +
                        "WHERE pantry_items.isDeleted = 0 AND pantry_items_fts MATCH ? " +
                        "ORDER BY pantry_items.name COLLATE NOCASE",
                    arrayOf(ftsQuery),
                ),
            )
        }
    }

    fun observeLowStock(): Flow<List<PantryItem>> = pantryDao.observeLowStock()

    suspend fun findPantryItemByBarcode(barcode: String): PantryItem? = pantryDao.findByBarcode(barcode)

    suspend fun findAnyPantryItemByBarcode(barcode: String): PantryItem? = pantryDao.findAnyByBarcode(barcode)

    suspend fun decrementQuantity(
        item: PantryItem,
        delta: Float,
    ) = require(delta > 0f && delta.isFinite()) {
        "Quantity decrement must be finite and positive"
    }.let {
        pantryDao.decrementQuantity(item.id, delta)
    }

    suspend fun getAvailableIngredientsForMeals(): List<PantryItem> = pantryDao.getAvailableIngredients()

    suspend fun insertPantry(item: PantryItem): Long =
        db.withTransaction {
            handlePantryInsertOrUpdate(validate(item))
        }

    suspend fun insertPantryBatch(items: List<PantryItem>): List<Long> =
        db.withTransaction {
            items.map { item ->
                handlePantryInsertOrUpdate(validate(item))
            }
        }

    private suspend fun handlePantryInsertOrUpdate(validated: PantryItem): Long {
        val barcode = validated.barcode?.trim()?.takeIf { it.isNotEmpty() }
        if (barcode != null) {
            val existing = pantryDao.findAnyByBarcode(barcode)
            if (existing != null) {
                return if (existing.isDeleted) {
                    val restored =
                        validated.copy(
                            id = existing.id,
                            isDeleted = false,
                            quantity = if (validated.quantity > 0f) validated.quantity else existing.quantity,
                            updatedAt = System.currentTimeMillis(),
                        )
                    pantryDao.update(restored)
                    existing.id
                } else {
                    val combinedQuantity = existing.quantity + validated.quantity
                    val updated =
                        existing.copy(
                            quantity = combinedQuantity,
                            name = validated.name.ifBlank { existing.name },
                            category = if (validated.category != "OTHER") validated.category else existing.category,
                            storageLocation = validated.storageLocation,
                            expirationTimestamp = validated.expirationTimestamp ?: existing.expirationTimestamp,
                            updatedAt = System.currentTimeMillis(),
                        )
                    pantryDao.update(updated)
                    existing.id
                }
            }
        }
        return pantryDao.insert(validated)
    }

    suspend fun updatePantry(item: PantryItem) =
        pantryDao.update(validate(item).copy(updatedAt = System.currentTimeMillis()))

    suspend fun deletePantry(item: PantryItem) = pantryDao.softDelete(item.id)

    suspend fun restorePantry(item: PantryItem) = pantryDao.restore(item.id)

    suspend fun importInventoryFromJson(jsonString: String): Int {
        db.withTransaction {
            val json = JSONObject(jsonString)
            val pantryArray = json.optJSONArray("pantry") ?: JSONArray()
            val groceryArray = json.optJSONArray("grocery") ?: JSONArray()
            var importedCount = 0

            for (i in 0 until pantryArray.length()) {
                val obj = pantryArray.getJSONObject(i)
                val name = obj.optString("name").trim()
                if (name.isNotEmpty()) {
                    val exp = if (obj.isNull("expirationTimestamp")) null else obj.optLong("expirationTimestamp")
                    val openedTs = if (obj.isNull("openedTimestamp")) null else obj.optLong("openedTimestamp")
                    val customUnit =
                        if (obj.isNull("customUnitName")) {
                            null
                        } else {
                            obj.optString("customUnitName").trim().takeIf { it.isNotEmpty() }
                        }
                    val item =
                        PantryItem(
                            name = name,
                            quantity = obj.optDouble("quantity", 1.0).toFloat().coerceAtLeast(0f),
                            unit = obj.optString("unit", "COUNT").takeIf { it.isNotBlank() },
                            customUnitName = customUnit,
                            threshold = obj.optDouble("threshold", 0.0).toFloat().coerceAtLeast(0f),
                            barcode = obj.optString("barcode").trim().takeIf { it.isNotEmpty() },
                            storageLocation = obj.optString("storageLocation", "PANTRY").uppercase(),
                            category = obj.optString("category", "OTHER").uppercase(),
                            expirationTimestamp = exp,
                            isOpened = obj.optBoolean("isOpened", false),
                            openedTimestamp = openedTs,
                            isAromaticOrSeasoning = obj.optBoolean("isAromaticOrSeasoning", false),
                            notes = obj.optString("notes").trim().takeIf { it.isNotEmpty() },
                        )
                    insertPantry(item)
                    importedCount++
                }
            }

            for (i in 0 until groceryArray.length()) {
                val obj = groceryArray.getJSONObject(i)
                val name = obj.optString("name").trim()
                if (name.isNotEmpty()) {
                    val grocery =
                        GroceryItem(
                            name = name,
                            quantity = obj.optDouble("quantity", 1.0).toFloat().coerceAtLeast(0f),
                            unit = obj.optString("unit", "COUNT").takeIf { it.isNotBlank() },
                            checked = obj.optBoolean("checked", false),
                        )
                    insertGrocery(grocery)
                    importedCount++
                }
            }
            importedCount
        }
    }

    suspend fun exportPantryToCsv(): String {
        val pantry = pantryDao.observeAllPantryItems().first()
        return buildString {
            append(CSV_HEADER)
            pantry.forEach { item ->
                val expDate =
                    item.expirationTimestamp?.let {
                        java.text
                            .SimpleDateFormat(DATE_PATTERN, java.util.Locale.US)
                            .format(java.util.Date(it))
                    } ?: ""
                val escapedName = "\"${item.name.replace("\"", "\"\"")}\""
                val escapedCustomUnit = "\"${(item.customUnitName ?: "").replace("\"", "\"\"")}\""
                val escapedNotes = "\"${(item.notes ?: "").replace("\"", "\"\"")}\""
                append(
                    "$escapedName,${item.category},${item.storageLocation},${item.quantity}," +
                        "${item.unit.orEmpty()},$escapedCustomUnit,${item.threshold}," +
                        "$expDate,${item.isOpened},$escapedNotes\n",
                )
            }
        }
    }

    internal suspend fun <T> withTransaction(block: suspend () -> T): T = db.withTransaction(block)

    fun reclaimTemporaryMedia(): Int {
        val scratch = appContext.cacheDir.resolve("vision_scratch")
        if (!scratch.isDirectory) return 0
        return scratch.listFiles().orEmpty().count { it.deleteRecursively() }
    }

    private fun validate(item: PantryItem): PantryItem {
        require(item.name.isNotBlank()) { "Pantry item name must not be blank" }
        require(item.quantity >= 0f) { "Pantry item quantity must not be negative" }
        require(item.threshold >= 0f) { "Pantry item threshold must not be negative" }
        require(item.storageLocation in PantryTaxonomy.storageLocations) {
            "Unsupported pantry storage location"
        }
        val isAromatic =
            item.isAromaticOrSeasoning || PantryTaxonomy.isAromaticOrSeasoningCategory(item.category)
        return item.copy(
            normalizedName = item.name.trim().lowercase(),
            isAromaticOrSeasoning = isAromatic,
        )
    }

    companion object {
        private const val CSV_HEADER =
            "Name,Category,StorageLocation,Quantity,Unit,CustomUnitName," +
                "Threshold,ExpirationDate,IsOpened,Notes\n"

        @Volatile
        private var INSTANCE: Repository? = null

        fun resetInstance() {
            synchronized(this) {
                INSTANCE = null
            }
        }

        fun getInstance(context: Context): Repository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Repository(
                    AppDatabase.getDatabase(context),
                    context.applicationContext,
                ).also { INSTANCE = it }
            }
    }
}
