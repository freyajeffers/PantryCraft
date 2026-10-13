package com.jeffers.pantrycraft.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class Repository private constructor(
    private val db: AppDatabase,
) {
    private val pantryDao = db.pantryDao()
    private val groceryDao = db.groceryDao()

    fun observePantryItems(): Flow<List<PantryItem>> = pantryDao.observeAllPantryItems()

    fun observeLowStock(): Flow<List<PantryItem>> = pantryDao.observeLowStock()

    suspend fun findPantryItemByBarcode(barcode: String): PantryItem? = pantryDao.findByBarcode(barcode)

    suspend fun getAvailableIngredientsForMeals(): List<PantryItem> = pantryDao.getAvailableIngredients()

    suspend fun insertPantry(item: PantryItem) = pantryDao.insert(item)

    suspend fun updatePantry(item: PantryItem) = pantryDao.update(item)

    suspend fun deletePantry(item: PantryItem) = pantryDao.delete(item)

    fun observeGroceryItems(): Flow<List<GroceryItem>> = groceryDao.observeAllGroceryItems()

    suspend fun insertGrocery(item: GroceryItem) = groceryDao.insert(item)

    suspend fun updateGrocery(item: GroceryItem) = groceryDao.update(item)

    suspend fun deleteGrocery(item: GroceryItem) = groceryDao.delete(item)

    suspend fun clearCheckedGrocery() = groceryDao.deleteChecked()

    companion object {
        @Volatile
        private var INSTANCE: Repository? = null

        fun getInstance(context: Context): Repository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Repository(AppDatabase.getDatabase(context)).also { INSTANCE = it }
            }
    }
}
