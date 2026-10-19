package com.jeffers.pantrycraft.ui

import java.io.File

data class FoodFactsProps(
    val catalogReady: Boolean,
    val catalogFileSize: Long?,
    val catalogDownloading: Boolean,
    val catalogDownloadedBytes: Long,
    val catalogTotalBytes: Long,
    val onStartDownload: () -> Unit,
    val onDownloadResult: (Result<File>) -> Unit,
    val onProgress: (Long, Long) -> Unit,
    val onDelete: () -> Unit,
)
