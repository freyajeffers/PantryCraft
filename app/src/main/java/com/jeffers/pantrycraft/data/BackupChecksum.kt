package com.jeffers.pantrycraft.data

import android.content.Context
import java.security.MessageDigest

private const val CHECKSUM_BUFFER_BYTES = 16 * 1024

internal fun fileChecksum(file: java.io.File): String? {
    if (!file.exists() || !file.isFile) return null
    return runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(CHECKSUM_BUFFER_BYTES)
            var read: Int
            while (input.read(buffer).also { read = it } > 0) digest.update(buffer, 0, read)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()
}

internal fun dbChecksum(context: Context): String? =
    fileChecksum(context.getDatabasePath("pantry_grocery_encrypted.db"))


internal fun lastDbChecksum(context: Context): String? =
    context
        .getSharedPreferences("backup_preferences", Context.MODE_PRIVATE)
        .getString("last_db_checksum", null)

internal fun saveLastDbChecksum(
    context: Context,
    checksum: String?,
) {
    context
        .getSharedPreferences("backup_preferences", Context.MODE_PRIVATE)
        .edit()
        .putString("last_db_checksum", checksum)
        .apply()
}
