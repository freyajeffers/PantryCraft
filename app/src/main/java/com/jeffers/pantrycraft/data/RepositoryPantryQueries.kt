package com.jeffers.pantrycraft.data

import com.jeffers.pantrycraft.feature.camera.barcode.AssetBarcodeDatabase
import com.jeffers.pantrycraft.feature.camera.barcode.CatalogDownloadService
import com.jeffers.pantrycraft.feature.camera.barcode.OfflineBarcodeResolver
import kotlinx.coroutines.flow.Flow

private typealias CatalogProgress = (Long, Long) -> Unit

fun Repository.offlineBarcodeResolver(): OfflineBarcodeResolver =
    OfflineBarcodeResolver(
        customBarcodeDao = db.customBarcodeDao(),
        assetBarcodeDatabase = runCatching { AssetBarcodeDatabase(appContext) }.getOrNull(),
        assetBarcodeDatabaseProvider = { runCatching { AssetBarcodeDatabase(appContext) }.getOrNull() },
    )

suspend fun Repository.downloadOfflineCatalog(onProgress: CatalogProgress): java.io.File =
    CatalogDownloadService(appContext).downloadIfNeeded(onProgress = onProgress)

suspend fun Repository.downloadOfflineCatalog(): java.io.File = downloadOfflineCatalog { _, _ -> }

fun Repository.observeLowStock(): Flow<List<PantryItem>> = pantryDao.observeLowStock()

suspend fun Repository.findPantryItemByBarcode(barcode: String): PantryItem? = pantryDao.findByBarcode(barcode)

suspend fun Repository.findAnyPantryItemByBarcode(barcode: String): PantryItem? = pantryDao.findAnyByBarcode(barcode)
