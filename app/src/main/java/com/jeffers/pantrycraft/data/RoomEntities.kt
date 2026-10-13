package com.jeffers.pantrycraft.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pantry_items")
data class PantryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val quantity: Float = 0f,
    val unit: String? = null,
    val threshold: Float = 0f,
    val barcode: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "grocery_items")
data class GroceryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val quantity: Float = 1f,
    val unit: String? = null,
    val checked: Boolean = false,
    val fromPantryId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
