package com.jeffers.pantrycraft.data

import android.content.Context
import android.net.Uri

internal fun persistAutoBackupTreeUri(
    context: Context,
    uri: Uri?,
) {
    context
        .getSharedPreferences("backup_preferences", Context.MODE_PRIVATE)
        .edit()
        .putString("auto_backup_tree_uri", uri?.toString())
        .apply()
}

internal fun persistBackupUri(
    context: Context,
    uri: Uri?,
) {
    context
        .getSharedPreferences("backup_preferences", Context.MODE_PRIVATE)
        .edit()
        .putString("last_exported_uri", uri?.toString())
        .apply()
}

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
