package com.jeffers.pantrycraft.data

import android.content.Context
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.flow.Flow

class Repository private constructor(
    private val db: AppDatabase,
) {
    private val pantryDao = db.pantryDao()
    private val groceryDao = db.groceryDao()

    fun observePantryItems(): Flow<List<PantryItem>> = pantryDao.observeAllPantryItems()

    fun searchPantryItems(query: String): Flow<List<PantryItem>> {
        val searchTerm = query.trim()
        if (searchTerm.isEmpty()) return observePantryItems()
        val ftsQuery =
            searchTerm
                .split(Regex("\\s+"))
                .filter(String::isNotBlank)
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

    suspend fun decrementQuantity(
        item: PantryItem,
        delta: Float,
    ) = pantryDao.decrementQuantity(item.id, delta)

    suspend fun getAvailableIngredientsForMeals(): List<PantryItem> = pantryDao.getAvailableIngredients()

    suspend fun insertPantry(item: PantryItem) = pantryDao.insert(validate(item))

    suspend fun updatePantry(item: PantryItem) = pantryDao.update(validate(item).copy(updatedAt = System.currentTimeMillis()))

    suspend fun deletePantry(item: PantryItem) = pantryDao.softDelete(item.id)

    suspend fun restorePantry(item: PantryItem) = pantryDao.restore(item.id)

    fun observeGroceryItems(): Flow<List<GroceryItem>> = groceryDao.observeAllGroceryItems()

    suspend fun insertGrocery(item: GroceryItem) = groceryDao.insert(item)

    suspend fun updateGrocery(item: GroceryItem) = groceryDao.update(item)

    suspend fun deleteGrocery(item: GroceryItem) = groceryDao.delete(item)

    suspend fun clearCheckedGrocery() = groceryDao.deleteChecked()

    private fun validate(item: PantryItem): PantryItem {
        require(item.name.isNotBlank()) { "Pantry item name must not be blank" }
        require(item.quantity >= 0f) { "Pantry item quantity must not be negative" }
        require(item.threshold >= 0f) { "Pantry item threshold must not be negative" }
        require(item.storageLocation in setOf("PANTRY", "FRIDGE", "FREEZER", "COUNTER")) {
            "Unsupported pantry storage location"
        }
        return item.copy(normalizedName = item.name.trim().lowercase())
    }

    companion object {
        @Volatile
        private var INSTANCE: Repository? = null

        fun getInstance(context: Context): Repository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Repository(AppDatabase.getDatabase(context)).also { INSTANCE = it }
            }
    }
}
