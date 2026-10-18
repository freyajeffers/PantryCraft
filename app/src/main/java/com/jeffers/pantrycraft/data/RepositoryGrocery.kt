package com.jeffers.pantrycraft.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

fun Repository.observeGroceryItems(): Flow<List<GroceryItem>> = groceryDao.observeAllGroceryItems()

suspend fun Repository.insertGrocery(item: GroceryItem) = groceryDao.insert(item)

suspend fun Repository.updateGrocery(item: GroceryItem) = groceryDao.update(item)

suspend fun Repository.deleteGrocery(item: GroceryItem) = groceryDao.delete(item)

suspend fun Repository.clearCheckedGrocery() = groceryDao.deleteChecked()

suspend fun Repository.restockCheckedGroceryToPantry(): Int =
    withTransaction {
        val allChecked = groceryDao.observeAllGroceryItems().first().filter { it.checked }
        allChecked.forEach { grocery ->
            val existing = grocery.fromPantryId?.let { pantryId ->
                observePantryItems().first().firstOrNull { it.id == pantryId }
            }
            if (existing != null) {
                updatePantry(
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
        }
        groceryDao.deleteChecked()
        allChecked.size
    }
