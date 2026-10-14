package com.jeffers.pantrycraft.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Entity(tableName = "custom_barcode_cache")
data class CustomBarcodeItem(
    @androidx.room.PrimaryKey val barcode: String,
    val name: String,
    val category: String = "OTHER",
    val defaultUnit: String = "COUNT",
    val estimatedShelfLifeDays: Int = 0,
    val lastScanned: Long = System.currentTimeMillis(),
)

@Dao
interface CustomBarcodeDao {
    @Query("SELECT * FROM custom_barcode_cache WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): CustomBarcodeItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CustomBarcodeItem)
}
