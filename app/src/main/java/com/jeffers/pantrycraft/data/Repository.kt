package com.jeffers.pantrycraft.data

import android.content.Context
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.jeffers.pantrycraft.feature.camera.barcode.AssetBarcodeDatabase
import com.jeffers.pantrycraft.feature.camera.barcode.CatalogDownloadService
import com.jeffers.pantrycraft.feature.camera.barcode.OfflineBarcodeResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

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
        )

    suspend fun downloadOfflineCatalog(onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }) =
        CatalogDownloadService(appContext).downloadIfNeeded(onProgress = onProgress)

    fun searchPantryItems(query: String): Flow<List<PantryItem>> {
        val searchTerm = query.trim()
        if (searchTerm.isEmpty()) return observePantryItems()
        val ftsQuery =
            searchTerm
                .split(Regex("\\s+"))
                .joinToString(" ") { term ->
                    "\"${term.replace("\"", "\"\"")}\"*"
                }
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
