package com.jeffers.pantrycraft.data

import android.content.Context

class Repository private constructor(
    internal val db: AppDatabase,
    internal val appContext: Context,
    pantryRepo: PantryRepository =
        DefaultPantryRepository(
            db,
            appContext,
            DefaultGroceryRepository(db, appContext),
        ),
) : PantryRepository by pantryRepo {
    internal val pantryDao = db.pantryDao()
    internal val groceryDao = db.groceryDao()

    suspend fun getAvailableIngredientsForMeals(): List<PantryItem> = pantryDao.getAvailableIngredients()

    suspend fun insertPantryBatch(items: List<PantryItem>): List<Long> = items.map { insertPantry(it) }

    companion object {
        @Volatile
        private var INSTANCE: Repository? = null

        fun resetInstance() {
            synchronized(this) {
                INSTANCE = null
            }
        }

        fun getInstance(context: Context): Repository =
            INSTANCE?.takeIf { it.db.isOpen } ?: synchronized(this) {
                INSTANCE?.takeIf { it.db.isOpen } ?: Repository(
                    AppDatabase.getDatabase(context),
                    context.applicationContext,
                ).also { INSTANCE = it }
            }
    }
}
