package com.jeffers.pantrycraft.data

import android.content.Context

class Repository private constructor(private val db: AppDatabase) {

    private val pantryDao = db.pantryDao()
    private val groceryDao = db.groceryDao()

    suspend fun getAllPantryItems() = pantryDao.getAllPantryItems()
    suspend fun insertPantry(item: PantryItem) = pantryDao.insert(item)
    suspend fun updatePantry(item: PantryItem) = pantryDao.update(item)
    suspend fun deletePantry(item: PantryItem) = pantryDao.delete(item)
    suspend fun getLowStock() = pantryDao.getLowStock()

    suspend fun getAllGroceryItems() = groceryDao.getAllGroceryItems()
    suspend fun insertGrocery(item: GroceryItem) = groceryDao.insert(item)
    suspend fun updateGrocery(item: GroceryItem) = groceryDao.update(item)
    suspend fun deleteGrocery(item: GroceryItem) = groceryDao.delete(item)
    suspend fun clearCheckedGrocery() = groceryDao.deleteChecked()

    companion object {
        @Volatile
        private var INSTANCE: Repository? = null

        fun getInstance(context: Context): Repository {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getDatabase(context)
                val repo = Repository(db)
                INSTANCE = repo
                repo
            }
        }
    }
}
