package com.jeffers.pantrycraft.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PantryDao {
    @Query("SELECT * FROM pantry_items ORDER BY name COLLATE NOCASE")
    fun observeAllPantryItems(): Flow<List<PantryItem>>

    @Query("SELECT * FROM pantry_items WHERE quantity <= threshold ORDER BY name COLLATE NOCASE")
    fun observeLowStock(): Flow<List<PantryItem>>

    @Query("SELECT * FROM pantry_items WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): PantryItem?

    @Query("SELECT * FROM pantry_items ORDER BY expirationTimestamp ASC")
    suspend fun getAvailableIngredients(): List<PantryItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PantryItem): Long

    @Update
    suspend fun update(item: PantryItem)

    @Delete
    suspend fun delete(item: PantryItem)
}

@Dao
interface GroceryDao {
    @Query("SELECT * FROM grocery_items ORDER BY createdAt DESC")
    fun observeAllGroceryItems(): Flow<List<GroceryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: GroceryItem): Long

    @Update
    suspend fun update(item: GroceryItem)

    @Delete
    suspend fun delete(item: GroceryItem)

    @Query("DELETE FROM grocery_items WHERE checked = 1")
    suspend fun deleteChecked()
}
