package com.jeffers.pantrycraft.data

import androidx.sqlite.db.SimpleSQLiteQuery
import com.jeffers.pantrycraft.feature.camera.barcode.AssetBarcodeDatabase
import com.jeffers.pantrycraft.feature.camera.barcode.CatalogDownloadService
import com.jeffers.pantrycraft.feature.camera.barcode.OfflineBarcodeResolver
import kotlinx.coroutines.flow.Flow

private typealias CatalogProgress = (Long, Long) -> Unit

fun Repository.observePantryItems(): Flow<List<PantryItem>> = pantryDao.observeAllPantryItems()

fun Repository.offlineBarcodeResolver(): OfflineBarcodeResolver =
    OfflineBarcodeResolver(
        customBarcodeDao = db.customBarcodeDao(),
        assetBarcodeDatabase = runCatching { AssetBarcodeDatabase(appContext) }.getOrNull(),
        assetBarcodeDatabaseProvider = { runCatching { AssetBarcodeDatabase(appContext) }.getOrNull() },
    )

suspend fun Repository.downloadOfflineCatalog(onProgress: CatalogProgress): java.io.File =
    CatalogDownloadService(appContext).downloadIfNeeded(onProgress = onProgress)

suspend fun Repository.downloadOfflineCatalog(): java.io.File = downloadOfflineCatalog { _, _ -> }

fun Repository.searchPantryItems(query: String): Flow<List<PantryItem>> {
    val sanitized = sanitizeSearchQuery(query)
    val ftsQuery =
        if (sanitized.isEmpty()) {
            ""
        } else {
            sanitized
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .joinToString(" ") { term -> "\"${term.replace("\"", "\"\"")}\"*" }
        }
    return if (ftsQuery.isBlank()) {
        observePantryItems()
    } else {
        pantryDao.search(
            SimpleSQLiteQuery(
                "SELECT pantry_items.* FROM pantry_items " +
                    "JOIN pantry_items_fts ON pantry_items.rowid = pantry_items_fts.rowid " +
                    "WHERE pantry_items.isDeleted = 0 AND pantry_items_fts MATCH ? " +
                    "ORDER BY pantry_items.name COLLATE NOCASE",
                arrayOf(ftsQuery),
            ),
        )
    }
}

fun Repository.observeLowStock(): Flow<List<PantryItem>> = pantryDao.observeLowStock()

suspend fun Repository.findPantryItemByBarcode(barcode: String): PantryItem? = pantryDao.findByBarcode(barcode)

suspend fun Repository.findAnyPantryItemByBarcode(barcode: String): PantryItem? = pantryDao.findAnyByBarcode(barcode)
