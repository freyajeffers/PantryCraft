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
        if (sanitized.isEmpty()) return observePantryItems()
        val ftsQuery =
            sanitized
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .joinToString(" ") { term ->
                    "\"${term.replace("\"", "\"\"")}\"*"
                }
        if (ftsQuery.isBlank()) return observePantryItems()
        return pantryDao.search(
            SimpleSQLiteQuery(
                "SELECT pantry_items.* FROM pantry_items " +
                    "JOIN pantry_items_fts ON pantry_items.rowid = pantry_items_fts.rowid " +
                    "WHERE pantry_items.isDeleted = 0 AND pantry_items_fts MATCH ? " +
                    "ORDER BY pantry_items.name COLLATE NOCASE",
                arrayOf(ftsQuery),
            ),
        )
    }

    private fun sanitizeSearchQuery(raw: String): String =
        raw
            .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() && !isFtsReservedWord(it) }
            .joinToString(" ")

    private fun isFtsReservedWord(word: String): Boolean =
        word.equals("AND", ignoreCase = true) ||
            word.equals("OR", ignoreCase = true) ||
            word.equals("NOT", ignoreCase = true) ||
            word.equals("NEAR", ignoreCase = true)

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

    suspend fun updatePantry(item: PantryItem) = pantryDao.update(validate(item).copy(updatedAt = System.currentTimeMillis()))

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
                    val existing = pantryDao.observeAllPantryItems().first().firstOrNull { it.id == grocery.fromPantryId }
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

    suspend fun exportInventoryToJson(): String {
        val pantry = pantryDao.observeAllPantryItems().first()
        val grocery = groceryDao.observeAllGroceryItems().first()

        val json = JSONObject()
        json.put("version", 1)
        json.put("exportedAt", System.currentTimeMillis())

        val pantryArray = JSONArray()
        pantry.forEach { item ->
            val obj = JSONObject()
            obj.put("name", item.name)
            obj.put("quantity", item.quantity.toDouble())
            obj.put("unit", item.unit ?: "COUNT")
            obj.put("customUnitName", item.customUnitName ?: JSONObject.NULL)
            obj.put("threshold", item.threshold.toDouble())
            obj.put("barcode", item.barcode ?: "")
            obj.put("storageLocation", item.storageLocation)
            obj.put("category", item.category)
            obj.put("expirationTimestamp", item.expirationTimestamp ?: JSONObject.NULL)
            obj.put("isOpened", item.isOpened)
            obj.put("openedTimestamp", item.openedTimestamp ?: JSONObject.NULL)
            obj.put("isAromaticOrSeasoning", item.isAromaticOrSeasoning)
            obj.put("notes", item.notes ?: "")
            pantryArray.put(obj)
        }
        json.put("pantry", pantryArray)

        val groceryArray = JSONArray()
        grocery.forEach { item ->
            val obj = JSONObject()
            obj.put("name", item.name)
            obj.put("quantity", item.quantity.toDouble())
            obj.put("unit", item.unit ?: "COUNT")
            obj.put("checked", item.checked)
            groceryArray.put(obj)
        }
        json.put("grocery", groceryArray)

        return json.toString(2)
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
            append("Name,Category,StorageLocation,Quantity,Unit,CustomUnitName,Threshold,ExpirationDate,IsOpened,Notes\n")
            pantry.forEach { item ->
                val expDate =
                    item.expirationTimestamp?.let {
                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(it))
                    } ?: ""
                val escapedName = "\"${item.name.replace("\"", "\"\"")}\""
                val escapedCustomUnit = "\"${(item.customUnitName ?: "").replace("\"", "\"\"")}\""
                val escapedNotes = "\"${(item.notes ?: "").replace("\"", "\"\"")}\""
                append(
                    "$escapedName,${item.category},${item.storageLocation},${item.quantity}," +
                        "${item.unit.orEmpty()},$escapedCustomUnit,${item.threshold},$expDate,${item.isOpened},$escapedNotes\n",
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
                val cols = parseCsvLine(line)
                val name = cols.getOrNull(0)?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    val category =
                        cols
                            .getOrNull(1)
                            ?.trim()
                            ?.uppercase()
                            ?.takeIf { it.isNotEmpty() } ?: "OTHER"
                    val location =
                        cols
                            .getOrNull(2)
                            ?.trim()
                            ?.uppercase()
                            ?.takeIf { it in PantryTaxonomy.storageLocations }
                            ?: "PANTRY"
                    val quantity =
                        cols
                            .getOrNull(3)
                            ?.trim()
                            ?.toFloatOrNull()
                            ?.coerceAtLeast(0f) ?: 1f
                    val unit =
                        cols
                            .getOrNull(4)
                            ?.trim()
                            ?.uppercase()
                            ?.takeIf { it.isNotEmpty() } ?: "COUNT"
                    val hasExtendedCols = cols.size >= 10
                    val customUnit = if (hasExtendedCols) cols.getOrNull(5)?.trim()?.takeIf { it.isNotEmpty() } else null
                    val threshold =
                        if (hasExtendedCols) {
                            cols
                                .getOrNull(6)
                                ?.trim()
                                ?.toFloatOrNull()
                                ?.coerceAtLeast(0f) ?: 0f
                        } else {
                            0f
                        }
                    val expDateStr = (if (hasExtendedCols) cols.getOrNull(7) else cols.getOrNull(5))?.trim().orEmpty()
                    val expTimestamp =
                        expDateStr.takeIf { it.isNotEmpty() }?.let {
                            runCatching { dateFormat.parse(it)?.time }.getOrNull()
                        }
                    val isOpened = (if (hasExtendedCols) cols.getOrNull(8) else cols.getOrNull(6))?.trim()?.toBoolean() ?: false
                    val notes = (if (hasExtendedCols) cols.getOrNull(9) else cols.getOrNull(7))?.trim()?.takeIf { it.isNotEmpty() }
                    insertPantry(
                        PantryItem(
                            name = name,
                            category = category,
                            storageLocation = location,
                            quantity = quantity,
                            unit = unit,
                            customUnitName = customUnit,
                            threshold = threshold,
                            expirationTimestamp = expTimestamp,
                            isOpened = isOpened,
                            openedTimestamp = if (isOpened) System.currentTimeMillis() else null,
                            notes = notes,
                        ),
                    )
                    importedCount++
                }
            }
            importedCount
        }

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
        val isAromatic = item.isAromaticOrSeasoning || PantryTaxonomy.isAromaticOrSeasoningCategory(item.category)
        return item.copy(
            normalizedName = item.name.trim().lowercase(),
            isAromaticOrSeasoning = isAromatic,
        )
    }

    companion object {
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
