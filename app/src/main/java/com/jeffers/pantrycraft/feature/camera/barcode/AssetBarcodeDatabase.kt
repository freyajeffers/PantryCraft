package com.jeffers.pantrycraft.feature.camera.barcode

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

interface BarcodeCatalogDatabase : AutoCloseable {
    fun findByBarcode(barcode: String): EmbeddedBarcodeMatch?
}

class AssetBarcodeDatabase(
    context: Context,
) : BarcodeCatalogDatabase {
    private val database: SQLiteDatabase

    init {
        val target = File(context.noBackupFilesDir, ASSET_NAME)
        if (
            !target.exists() &&
            !context.assets
                .list("")
                .orEmpty()
                .contains(ASSET_NAME)
        ) {
            error("Offline food catalog is not installed")
        }
        if (!target.exists()) {
            context.assets.open(ASSET_NAME).use { input -> target.outputStream().use(input::copyTo) }
        }
        database = SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    override fun findByBarcode(barcode: String): EmbeddedBarcodeMatch? =
        database
            .query(
                "products",
                arrayOf("barcode", "name", "brand", "category", "shelf_life_days"),
                "barcode = ?",
                arrayOf(barcode),
                null,
                null,
                null,
                QUERY_LIMIT,
            ).use { cursor ->
                if (!cursor.moveToFirst()) return null
                EmbeddedBarcodeMatch(
                    barcode = cursor.getString(0),
                    name = cursor.getString(1),
                    brand = cursor.getString(BRAND_COLUMN),
                    category = cursor.getString(CATEGORY_COLUMN),
                    shelfLifeDays = cursor.getInt(SHELF_LIFE_COLUMN),
                )
            }

    override fun close() = database.close()

    companion object {
        private const val ASSET_NAME = "foodfacts.db"
        private const val QUERY_LIMIT = "1"
        private const val BRAND_COLUMN = 2
        private const val CATEGORY_COLUMN = 3
        private const val SHELF_LIFE_COLUMN = 4
    }
}

data class EmbeddedBarcodeMatch(
    val barcode: String,
    val name: String,
    val brand: String,
    val category: String,
    val shelfLifeDays: Int,
)
