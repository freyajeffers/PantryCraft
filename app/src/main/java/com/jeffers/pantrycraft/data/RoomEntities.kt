package com.jeffers.pantrycraft.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pantry_items",
    indices = [
        Index(value = ["storageLocation"]),
        Index(value = ["normalized_name"]),
        Index(value = ["expirationTimestamp"]),
        Index(value = ["barcode"], unique = true),
        Index(value = ["isDeleted"]),
    ],
)
data class PantryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val quantity: Float = 0f,
    val unit: String? = null,
    val threshold: Float = 0f,
    val barcode: String? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "normalized_name") val normalizedName: String = name.trim().lowercase(),
    @ColumnInfo(name = "custom_unit_name") val customUnitName: String? = null,
    @ColumnInfo(name = "purchase_timestamp") val purchaseTimestamp: Long = lastUpdated,
    val storageLocation: String = "PANTRY",
    val category: String = "OTHER",
    val expirationTimestamp: Long? = null,
    val isOpened: Boolean = false,
    val openedTimestamp: Long? = null,
    val isAromaticOrSeasoning: Boolean = false,
    val isDeleted: Boolean = false,
    val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = lastUpdated,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = lastUpdated,
)

@Entity(tableName = "grocery_items")
data class GroceryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val quantity: Float = 1f,
    val unit: String? = null,
    val checked: Boolean = false,
    val fromPantryId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
