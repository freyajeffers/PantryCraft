package com.jeffers.pantrycraft.ui

data class DataSovereigntyProps(
    val onExportJson: () -> Unit,
    val onExportCsv: () -> Unit,
    val onImportJson: () -> Unit,
    val onImportCsv: () -> Unit,
)
