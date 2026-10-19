package com.jeffers.pantrycraft.data

import android.content.Context
import androidx.room.withTransaction

class Repository private constructor(
    internal val db: AppDatabase,
    internal val appContext: Context,
) {
    internal val pantryDao = db.pantryDao()
    internal val groceryDao = db.groceryDao()

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
        pantryDao.update(
            validate(item).copy(updatedAt = System.currentTimeMillis()),
        )

    suspend fun deletePantry(item: PantryItem) = pantryDao.softDelete(item.id)

    suspend fun restorePantry(item: PantryItem) = pantryDao.restore(item.id)

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
