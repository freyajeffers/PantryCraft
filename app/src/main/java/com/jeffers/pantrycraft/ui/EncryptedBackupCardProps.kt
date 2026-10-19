package com.jeffers.pantrycraft.ui

import android.net.Uri

data class EncryptedBackupCardProps(
    val autoBackupProps: AutomaticBackupProps,
    val lastExportedUri: Uri?,
    val hasBackupRecords: Boolean,
    val autoBackupTreeUri: Uri?,
    val backupResult: String?,
    val onShareSavedBackup: () -> Unit,
    val onRestoreSavedBackup: () -> Unit,
    val onSelectAutoBackupLocation: () -> Unit,
    val onOpenBackupFile: () -> Unit,
    val onResetDatabase: () -> Unit,
)
