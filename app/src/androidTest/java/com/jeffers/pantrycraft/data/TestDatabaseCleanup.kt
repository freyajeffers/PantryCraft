package com.jeffers.pantrycraft.data

import android.content.Context
import kotlinx.coroutines.runBlocking

/** Removes only unmistakable synthetic records created by instrumentation tests. */
internal object TestDatabaseCleanup {
    private val syntheticPrefixes =
        listOf(
            "repository-",
            "concurrency-wal-",
            "export-import-int-",
            "grocery-int-",
            "grocery-test-",
            "pantry-tx-test-",
            "vm-test-",
            "home-test-",
            "scaffold-test-",
            "settings-direct-test-",
            "batch-rev-",
            "assoc-test-",
        )

    fun cleanup(context: Context) {
        val databaseFile = context.getDatabasePath("pantry_grocery_encrypted.db")
        if (!databaseFile.exists()) return

        val database = AppDatabase.getDatabase(context)
        runBlocking {
            syntheticPrefixes.forEach { prefix ->
                database.pantryDao().deleteByNamePrefix(prefix)
                database.groceryDao().deleteByNamePrefix(prefix)
            }
            database.pantryDao().deleteByBarcodePrefix("barcode-repository-")
            database.customBarcodeDao().deleteByBarcodePrefix("barcode-repository-")
            database.customBarcodeDao().deleteByBarcodePrefix("9988776655")
        }
    }
}
