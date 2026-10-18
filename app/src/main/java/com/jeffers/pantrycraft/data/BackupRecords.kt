package com.jeffers.pantrycraft.data

import android.content.Context
import java.io.File
import org.json.JSONArray

internal fun backupRecords(context: Context): List<DatabaseBackupManager.BackupRecord> {
    val preferences = context.getSharedPreferences("backup_preferences", Context.MODE_PRIVATE)
    val stored =
        runCatching {
            val json = JSONArray(preferences.getString("backup_records", "[]"))
            (0 until json.length()).map { index ->
                val item = json.getJSONObject(index)
                DatabaseBackupManager.BackupRecord(
                    backupName = item.getString("name"),
                    createdAt = item.getLong("createdAt"),
                    createdAtLabel = item.optString("createdAtLabel", item.getString("name")),
                    exportedUri = item.optString("uri").takeIf { it.isNotBlank() },
                )
            }
        }.getOrDefault(emptyList())
    val known = stored.mapTo(mutableSetOf()) { it.backupName }
    val missing = listBackupFiles(context).filterNot(known::contains).map { name ->
        DatabaseBackupManager.BackupRecord(name, backupRecordTimestamp(name), name, null)
    }
    return (stored + missing).sortedByDescending { it.createdAt }
}

private fun listBackupFiles(context: Context): List<String> {
    val dir = File(context.filesDir, "backups")
    if (!dir.exists() || !dir.isDirectory) return emptyList()
    return dir.listFiles()?.filter { it.isFile && it.name.endsWith(".pcbk") }
        ?.sortedByDescending { it.lastModified() }?.map { it.name }.orEmpty()
}

private fun backupRecordTimestamp(backupName: String): Long =
    backupName.substringBeforeLast(".pcbk").substringAfterLast('.').toLongOrNull()
        ?: System.currentTimeMillis()
