package com.jeffers.pantrycraft.ui

data class BackupCallbacks(
    val onRequirePin: ((CharArray) -> Unit) -> Unit,
    val onRequireRestore: (suspend () -> Unit) -> Unit,
    val onShowBackupList: () -> Unit,
    val onResetDatabase: () -> Unit,
)
