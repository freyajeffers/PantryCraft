package com.jeffers.pantrycraft.ui

data class AutomaticBackupProps(
    val enabled: Boolean,
    val intervalMinutes: Long,
    val databaseHasUserData: Boolean,
    val onDisable: () -> Unit,
    val onEnableRequested: () -> Unit,
    val onIntervalSelected: (Long) -> Unit,
    val onBackupNow: () -> Unit,
)
