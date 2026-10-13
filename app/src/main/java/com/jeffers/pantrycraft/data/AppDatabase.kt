package com.jeffers.pantrycraft.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PantryItem::class, GroceryItem::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pantryDao(): PantryDao
    abstract fun groceryDao(): GroceryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pantry_grocery_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
