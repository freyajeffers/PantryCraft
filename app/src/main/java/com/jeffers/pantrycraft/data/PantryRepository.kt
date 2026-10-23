package com.jeffers.pantrycraft.data

import android.content.Context
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.flow.Flow

interface PantryRepository : GroceryRepository {
    fun observePantryItems(): Flow<List<PantryItem>>
    fun searchPantryItems(query: String): Flow<List<PantryItem>>
    suspend fun insertPantry(item: PantryItem): Long
    suspend fun updatePantry(item: PantryItem)
    suspend fun decrementQuantity(item: PantryItem, delta: Float)
    suspend fun deletePantry(item: PantryItem)
    suspend fun restorePantry(item: PantryItem)
}

internal class DefaultPantryRepository(
    private val db: AppDatabase,
    private val appContext: Context,
    private val groceryRepo: GroceryRepository = DefaultGroceryRepository(db, appContext),
) : PantryRepository, GroceryRepository by groceryRepo {
    private val pantryDao = db.pantryDao()

    override fun observePantryItems(): Flow<List<PantryItem>> = pantryDao.observeAllPantryItems()

    override fun searchPantryItems(query: String): Flow<List<PantryItem>> {
        val sanitized = sanitizeSearchQuery(query)
        val ftsQuery =
            if (sanitized.isEmpty()) {
                ""
            } else {
                sanitized
                    .split(Regex("\\s+"))
                    .filter { it.isNotBlank() }
                    .joinToString(" ") { term -> "\"${term.replace("\"", "\"\"")}\"*" }
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

    override suspend fun insertPantry(item: PantryItem): Long =
        db.withTransaction {
            handlePantryInsertOrUpdate(validate(item))
        }

    override suspend fun updatePantry(item: PantryItem) =
        pantryDao.update(
            validate(item).copy(updatedAt = System.currentTimeMillis()),
        )

    override suspend fun decrementQuantity(item: PantryItem, delta: Float) =
        require(delta > 0f && delta.isFinite()) {
            "Quantity decrement must be finite and positive"
        }.let {
            pantryDao.decrementQuantity(item.id, delta)
        }

    override suspend fun deletePantry(item: PantryItem) = pantryDao.softDelete(item.id)

    override suspend fun restorePantry(item: PantryItem) = pantryDao.restore(item.id)

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
}
