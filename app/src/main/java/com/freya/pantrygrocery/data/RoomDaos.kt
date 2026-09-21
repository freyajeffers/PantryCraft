package com.freya.pantrygrocery.data

import androidx.room.*

@Dao
interface PantryDao {
    @Query("SELECT * FROM pantry_items ORDER BY name COLLATE NOCASE")
    suspend fun getAllPantryItems(): List<PantryItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PantryItem): Long

    @Update
    suspend fun update(item: PantryItem)

    @Delete
    suspend fun delete(item: PantryItem)

    @Query("SELECT * FROM pantry_items WHERE quantity <= threshold")
    suspend fun getLowStock(): List<PantryItem>
}

@Dao
interface GroceryDao {
    @Query("SELECT * FROM grocery_items ORDER BY createdAt DESC")
    suspend fun getAllGroceryItems(): List<GroceryItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: GroceryItem): Long

    @Update
    suspend fun update(item: GroceryItem)

    @Delete
    suspend fun delete(item: GroceryItem)

    @Query("DELETE FROM grocery_items WHERE checked = 1")
    suspend fun deleteChecked()
}
