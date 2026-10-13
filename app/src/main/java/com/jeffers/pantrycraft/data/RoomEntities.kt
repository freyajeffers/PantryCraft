package com.jeffers.pantrycraft.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pantry_items",
    indices = [
        Index(value = ["storageLocation"]),
        Index(value = ["expirationTimestamp"]),
        Index(value = ["barcode"], unique = true),
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
    val storageLocation: String = "PANTRY",
    val category: String = "OTHER",
    val expirationTimestamp: Long? = null,
    val isOpened: Boolean = false,
    val openedTimestamp: Long? = null,
    val notes: String? = null,
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
