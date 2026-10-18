package com.jeffers.pantrycraft.ui

import kotlinx.coroutines.CoroutineScope
import com.jeffers.pantrycraft.feature.camera.barcode.CatalogDownloadService

data class FoodFactsProps(
    val repository: CatalogDownloadService,
    val scope: CoroutineScope,
    val catalogReady: Boolean,
    val catalogFileSize: Long?,
    val catalogDownloading: Boolean,
    val catalogDownloadedBytes: Long,
    val catalogTotalBytes: Long,
    val onStartDownload: () -> Unit,
    val onDownloadResult: (Result<java.io.File>) -> Unit,
    val onProgress: (Long, Long) -> Unit,
    val onDelete: () -> Unit,
    val catalogDialogSetter: (Any?) -> Unit,
)
