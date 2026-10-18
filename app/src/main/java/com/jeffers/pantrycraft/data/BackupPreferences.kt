package com.jeffers.pantrycraft.data

import android.content.Context
import android.net.Uri

internal fun persistedBackupUri(context: Context): Uri? =
    context
        .getSharedPreferences("backup_preferences", Context.MODE_PRIVATE)
        .getString("last_exported_uri", null)
        ?.let(Uri::parse)

internal fun persistedAutoBackupTreeUri(context: Context): Uri? =
    context
        .getSharedPreferences("backup_preferences", Context.MODE_PRIVATE)
        .getString("auto_backup_tree_uri", null)
        ?.let(Uri::parse)
