package com.jeffers.pantrycraft.data

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface PantryDao {
    @Query("SELECT * FROM pantry_items WHERE isDeleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeAllPantryItems(): Flow<List<PantryItem>>

    @RawQuery(observedEntities = [PantryItem::class])
    fun search(query: SupportSQLiteQuery): Flow<List<PantryItem>>

    @Query("SELECT * FROM pantry_items WHERE quantity <= threshold AND isDeleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeLowStock(): Flow<List<PantryItem>>

    @Query("SELECT * FROM pantry_items WHERE barcode = :barcode AND isDeleted = 0 LIMIT 1")
    suspend fun findByBarcode(barcode: String): PantryItem?

    @Query("SELECT * FROM pantry_items WHERE isDeleted = 0 ORDER BY expirationTimestamp ASC")
    suspend fun getAvailableIngredients(): List<PantryItem>

    @Query("UPDATE pantry_items SET isDeleted = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDelete(
        id: Long,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query("UPDATE pantry_items SET isDeleted = 0, updated_at = :updatedAt WHERE id = :id")
    suspend fun restore(
        id: Long,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PantryItem): Long

    @Update
    suspend fun update(item: PantryItem)

    @Query(
        "UPDATE pantry_items SET quantity = CASE WHEN quantity - :delta < 0 THEN 0 " +
            "ELSE quantity - :delta END, updated_at = :updatedAt " +
            "WHERE id = :id AND isDeleted = 0",
    )
    suspend fun decrementQuantity(
        id: Long,
        delta: Float,
        updatedAt: Long = System.currentTimeMillis(),
    )

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
