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
    private val groceryDao = db.groceryDao()

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

    fun observeGroceryItems(): Flow<List<GroceryItem>> = groceryDao.observeAllGroceryItems()

    suspend fun insertGrocery(item: GroceryItem) = groceryDao.insert(item)

    suspend fun updateGrocery(item: GroceryItem) = groceryDao.update(item)

    suspend fun deleteGrocery(item: GroceryItem) = groceryDao.delete(item)

    suspend fun clearCheckedGrocery() = groceryDao.deleteChecked()

    suspend fun restockCheckedGroceryToPantry(): Int =
        db.withTransaction {
            val allChecked = groceryDao.observeAllGroceryItems().first().filter { it.checked }
            allChecked.forEach { grocery ->
                if (grocery.fromPantryId != null) {
                    val existing =
                        pantryDao.observeAllPantryItems().first().firstOrNull { it.id == grocery.fromPantryId }
                    if (existing != null) {
                        pantryDao.update(
                            existing.copy(
                                quantity = existing.quantity + grocery.quantity,
                                updatedAt = System.currentTimeMillis(),
                            ),
                        )
                    } else {
                        insertPantry(
                            PantryItem(
                                name = grocery.name,
                                quantity = grocery.quantity,
                                unit = grocery.unit,
                            ),
                        )
                    }
                } else {
                    insertPantry(
                        PantryItem(
                            name = grocery.name,
                            quantity = grocery.quantity,
                            unit = grocery.unit,
                        ),
                    )
                }
            }
            groceryDao.deleteChecked()
            allChecked.size
        }

    suspend fun importInventoryFromJson(jsonString: String): Int =
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

    suspend fun importPantryFromCsv(csvString: String): Int =
        db.withTransaction {
            val lines = csvString.lines().filter { it.isNotBlank() }
            if (lines.size <= 1) return@withTransaction 0
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            var importedCount = 0
            lines.drop(1).forEach { line ->
                parsePantryCsvItem(parseCsvLine(line), dateFormat)?.let { item ->
                    insertPantry(item)
                    importedCount++
                }
            }
            importedCount
        }

    private fun parsePantryCsvItem(
        cols: List<String>,
        dateFormat: java.text.SimpleDateFormat,
    ): PantryItem? {
        val name = cols.getOrNull(0)?.trim().orEmpty()
        if (name.isEmpty()) return null
        val category = cols.getOrNull(1)?.trim()?.uppercase()?.takeIf(String::isNotEmpty) ?: "OTHER"
        val location =
            cols.getOrNull(2)?.trim()?.uppercase()?.takeIf { it in PantryTaxonomy.storageLocations } ?: "PANTRY"
        val quantity = cols.getOrNull(QUANTITY_COLUMN)?.trim()?.toFloatOrNull()?.coerceAtLeast(0f) ?: 1f
        val unit = cols.getOrNull(UNIT_COLUMN)?.trim()?.uppercase()?.takeIf(String::isNotEmpty) ?: "COUNT"
        val extended = cols.size >= EXTENDED_CSV_COLUMN_COUNT
        val customUnit = optionalCsvValue(cols, CUSTOM_UNIT_COLUMN, extended)
        val threshold =
            if (extended) {
                cols.getOrNull(THRESHOLD_COLUMN)?.trim()?.toFloatOrNull()?.coerceAtLeast(0f) ?: 0f
            } else {
                0f
            }
        val expirationColumn = if (extended) EXPIRATION_COLUMN else LEGACY_EXPIRATION_COLUMN
        val expiration = cols.getOrNull(expirationColumn)?.trim().orEmpty().takeIf(String::isNotEmpty)?.let {
            runCatching { dateFormat.parse(it)?.time }.getOrNull()
        }
        val openedColumn = if (extended) IS_OPENED_COLUMN else LEGACY_IS_OPENED_COLUMN
        val isOpened = cols.getOrNull(openedColumn)?.trim()?.toBoolean() ?: false
        val notesColumn = if (extended) NOTES_COLUMN else LEGACY_NOTES_COLUMN
        val notes = cols.getOrNull(notesColumn)?.trim()?.takeIf(String::isNotEmpty)
        return PantryItem(
            name = name,
            category = category,
            storageLocation = location,
            quantity = quantity,
            unit = unit,
            customUnitName = customUnit,
            threshold = threshold,
            expirationTimestamp = expiration,
            isOpened = isOpened,
            openedTimestamp = if (isOpened) System.currentTimeMillis() else null,
            notes = notes,
        )
    }

    private fun optionalCsvValue(cols: List<String>, index: Int, available: Boolean): String? =
        if (available) cols.getOrNull(index)?.trim()?.takeIf(String::isNotEmpty) else null

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            val ch = line[index]
            if (ch == '"') {
                if (inQuotes && index + 1 < line.length && line[index + 1] == '"') {
                    current.append('"')
                    index++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (ch == ',' && !inQuotes) {
                result.add(current.toString())
                current.clear()
            } else {
                current.append(ch)
            }
            index++
        }
        result.add(current.toString())
        return result
    }

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
        private const val EXTENDED_CSV_COLUMN_COUNT = 10
        private const val DATE_PATTERN = "yyyy-MM-dd"
        private const val CUSTOM_UNIT_COLUMN = 5
        private const val QUANTITY_COLUMN = 3
        private const val UNIT_COLUMN = 4
        private const val THRESHOLD_COLUMN = 6
        private const val EXPIRATION_COLUMN = 7
        private const val LEGACY_EXPIRATION_COLUMN = 5
        private const val IS_OPENED_COLUMN = 8
        private const val LEGACY_IS_OPENED_COLUMN = 6
        private const val NOTES_COLUMN = 9
        private const val LEGACY_NOTES_COLUMN = 7

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
