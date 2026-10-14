package com.jeffers.pantrycraft.feature.camera.barcode

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

class AssetBarcodeDatabase(
    context: Context,
) : AutoCloseable {
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
            throw IllegalStateException("Offline food catalog is not installed")
        }
        if (!target.exists()) {
            context.assets.open(ASSET_NAME).use { input -> target.outputStream().use(input::copyTo) }
        }
        database = SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    fun findByBarcode(barcode: String): EmbeddedBarcodeMatch? =
        database
            .query(
                "products",
                arrayOf("barcode", "name", "brand", "category", "shelf_life_days"),
                "barcode = ?",
                arrayOf(barcode),
                null,
                null,
                null,
                "1",
            ).use { cursor ->
                if (!cursor.moveToFirst()) return null
                EmbeddedBarcodeMatch(
                    barcode = cursor.getString(0),
                    name = cursor.getString(1),
                    brand = cursor.getString(2),
                    category = cursor.getString(3),
                    shelfLifeDays = cursor.getInt(4),
                )
            }

    override fun close() = database.close()

    companion object {
        private const val ASSET_NAME = "foodfacts.db"
    }
}

data class EmbeddedBarcodeMatch(
    val barcode: String,
    val name: String,
    val brand: String,
    val category: String,
    val shelfLifeDays: Int,
)
