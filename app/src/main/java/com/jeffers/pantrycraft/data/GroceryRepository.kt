package com.jeffers.pantrycraft.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

interface GroceryRepository {
    fun observeGroceryItems(): Flow<List<GroceryItem>>
    suspend fun insertGrocery(item: GroceryItem): Long
    suspend fun updateGrocery(item: GroceryItem)
    suspend fun deleteGrocery(item: GroceryItem)
    suspend fun clearCheckedGrocery()
    suspend fun restockCheckedGroceryToPantry(): Int
    fun reclaimTemporaryMedia(): Int
}

internal class DefaultGroceryRepository(
    private val db: AppDatabase,
    private val appContext: Context,
) : GroceryRepository {
    private val groceryDao = db.groceryDao()
    private val pantryDao = db.pantryDao()

    override fun observeGroceryItems(): Flow<List<GroceryItem>> = groceryDao.observeAllGroceryItems()

    override suspend fun insertGrocery(item: GroceryItem): Long = groceryDao.insert(item)

    override suspend fun updateGrocery(item: GroceryItem) = groceryDao.update(item)

    override suspend fun deleteGrocery(item: GroceryItem) = groceryDao.delete(item)

    override suspend fun clearCheckedGrocery() = groceryDao.deleteChecked()

    override suspend fun restockCheckedGroceryToPantry(): Int =
        db.withTransaction {
            val allChecked = groceryDao.observeAllGroceryItems().first().filter { it.checked }
            allChecked.forEach { grocery ->
                val existing =
                    grocery.fromPantryId?.let { pantryId ->
                        pantryDao.observeAllPantryItems().first().firstOrNull { it.id == pantryId }
                    }
                if (existing != null) {
                    pantryDao.update(
                        existing.copy(
                            quantity = existing.quantity + grocery.quantity,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                } else {
                    pantryDao.insert(
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

    override fun reclaimTemporaryMedia(): Int {
        val scratch = appContext.cacheDir.resolve("vision_scratch")
        if (!scratch.isDirectory) return 0
        return scratch.listFiles().orEmpty().count { it.deleteRecursively() }
    }
}
