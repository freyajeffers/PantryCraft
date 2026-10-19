package com.jeffers.pantrycraft.data

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import javax.crypto.CipherInputStream

private const val RESTORE_STREAM_BUFFER_BYTES = 16 * 1024

internal fun ensureDatabasePresent(dbFile: File) {
    if (!dbFile.exists()) throw IOException("Database file not found")
}

internal fun replaceDatabaseFile(
    temp: File,
    dbFile: File,
) {
    FileInputStream(temp).use { input ->
        FileOutputStream(dbFile).use { output ->
            input.copyTo(output, RESTORE_STREAM_BUFFER_BYTES)
            output.fd.sync()
        }
    }
    if (!temp.delete()) throw IOException("Unable to remove temporary restored database")
}

internal fun streamDecryptedDatabase(
    input: CipherInputStream,
    temp: File,
) {
    FileOutputStream(temp).use { output ->
        val buffer = ByteArray(RESTORE_STREAM_BUFFER_BYTES)
        var read: Int
        while (input.read(buffer).also { read = it } > 0) {
            output.write(buffer, 0, read)
        }
        output.fd.sync()
    }
}
