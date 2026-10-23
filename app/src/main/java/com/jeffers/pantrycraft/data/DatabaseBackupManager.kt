package com.jeffers.pantrycraft.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.SecureRandom
import java.text.DateFormat
import java.util.Arrays
import java.util.Date
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Creates and restores encrypted backups that bundle the SQLCipher passphrase together with the
 * database file. The bundle is encrypted with a key derived from a user-supplied PIN so the user
 * can restore even after the hardware KeyStore key was invalidated.
 *
 * Format:
 *  - ASCII magic: 4 bytes "PCBK"
 *  - version: 1 byte (currently 1)
 *  - salt: 16 bytes
 *  - iv: 12 bytes
 *  - ciphertext: AES/GCM over (passphrase-length(2 bytes BE) || passphrase || database-bytes)
 */
private const val BACKUP_DIR = "backups"
private const val DB_NAME = "pantry_grocery_encrypted.db"
private const val MAGIC = "PCBK"
private const val VERSION: Byte = 1
private const val SALT_LEN = 16
private const val IV_LEN = 12
private const val ITERATIONS = 200_000
private const val KEY_LEN_BITS = 256
private const val GCM_TAG_LENGTH_BITS = 128
private const val STREAM_BUFFER_BYTES = 16 * 1024

private const val BYTE_MASK = 0xFF
private const val BYTE_SHIFT = 8
private const val PREFS_NAME = "backup_preferences"
private const val BACKUP_RECORDS = "backup_records"

object DatabaseBackupManager {
    private data class EncryptedBackupPayload(
        val passphrase: ByteArray?,
        val secretKey: SecretKeySpec,
        val salt: ByteArray,
        val iv: ByteArray,
    )

    data class BackupRecord(
        val backupName: String,
        val createdAt: Long,
        val createdAtLabel: String,
        val exportedUri: String?,
    )

    fun exportBackup(
        context: Context,
        backupName: String,
        destination: Uri,
    ) {
        val source = backupFile(context, backupName)
        if (!source.isFile) throw IOException("Backup not found: $backupName")
        val output =
            context.contentResolver.openOutputStream(destination)
                ?: throw IOException("Unable to open selected backup location")
        source.inputStream().use { input -> output.use { input.copyTo(it) } }
        persistBackupUri(context, destination)
        val record = backupRecords(context).firstOrNull { it.backupName == backupName }
        val baseRecord =
            record ?: run {
                val timestamp = backupTimestamp(backupName)
                BackupRecord(backupName, timestamp, formatBackupTimestamp(timestamp), null)
            }
        saveBackupRecord(
            context,
            baseRecord.copy(
                exportedUri = destination.toString(),
            ),
        )
    }

    fun restoreEncryptedBackupFromUri(
        context: Context,
        uri: Uri,
        pin: CharArray,
    ) {
        val backupDir = File(context.filesDir, BACKUP_DIR).apply { mkdirs() }
        val imported = File.createTempFile(".pantry_import_", ".pcbk", backupDir)
        try {
            val input =
                context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Unable to open selected backup")
            input.use { source -> imported.outputStream().use { source.copyTo(it) } }
            restoreEncryptedBackup(context, imported.name, pin)
        } finally {
            imported.delete()
        }
    }

    @Throws(IOException::class)
    fun createEncryptedBackup(
        context: Context,
        pin: CharArray,
    ): String {
        val dbFile = context.getDatabasePath(DB_NAME)
        ensureDatabasePresent(dbFile)

        val dir = File(context.filesDir, BACKUP_DIR).apply { if (!exists()) mkdirs() }
        val stamp = System.currentTimeMillis()
        val dest = File(dir, "${DB_NAME}.$stamp.pcbk")
        val temp = File(dir, ".$stamp.pcbk.tmp")

        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }

        var derived: ByteArray? = null
        var passphrase: ByteArray? = null
        try {
            derived = deriveKeyBytes(pin, salt)
            val secretKey = SecretKeySpec(derived, "AES")

            // Obtain the SQLCipher passphrase from the KeyProvider. Caller must have unlocked KeyStore.
            passphrase = DatabaseKeyProvider.getKey(context)

            AppDatabase.checkpointForBackup(context)
            // Close Room before copying the database file so no open connection can
            // continue writing after the WAL checkpoint and during the snapshot.
            AppDatabase.closeDatabase()

            writeEncryptedBackup(
                temp,
                dbFile,
                EncryptedBackupPayload(passphrase, secretKey, salt, iv),
            )

            if (!temp.renameTo(dest)) {
                temp.delete()
                throw IOException("Unable to finalize encrypted backup")
            }
        } finally {
            passphrase?.fill(0)
            derived?.fill(0)
            Arrays.fill(pin, '\u0000')
        }

        val timestamp = backupTimestamp(dest.name)
        saveBackupRecord(
            context,
            BackupRecord(dest.name, timestamp, formatBackupTimestamp(timestamp), null),
        )
        return dest.name
    }

    private fun writeEncryptedBackup(
        temp: File,
        dbFile: File,
        payload: EncryptedBackupPayload,
    ) {
        FileOutputStream(temp).use { fos ->
            // header
            fos.write(MAGIC.toByteArray(Charsets.UTF_8))
            fos.write(byteArrayOf(VERSION))
            fos.write(payload.salt)
            fos.write(payload.iv)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                payload.secretKey,
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, payload.iv),
            )

            CipherOutputStream(fos, cipher).use { cos ->
                // write passphrase length (2 bytes big-endian) then passphrase
                val len = payload.passphrase?.size ?: 0
                cos.write((len ushr BYTE_SHIFT) and BYTE_MASK)
                cos.write(len and BYTE_MASK)
                if (payload.passphrase != null) cos.write(payload.passphrase)

                writeEncryptedDatabase(cos, dbFile)
                cos.flush()
            }
        }
    }

    private fun writeEncryptedDatabase(
        output: CipherOutputStream,
        dbFile: File,
    ) {
        FileInputStream(dbFile).use { fis ->
            val buffer = ByteArray(STREAM_BUFFER_BYTES)
            var read: Int
            while (fis.read(buffer).also { read = it } > 0) {
                output.write(buffer, 0, read)
            }
        }
    }

    private fun saveBackupRecord(
        context: Context,
        record: BackupRecord,
    ) {
        val records = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val merged = backupRecords(context).filterNot { it.backupName == record.backupName } + record
        val json = JSONArray()
        merged.sortedByDescending { it.createdAt }.forEach {
            json.put(
                JSONObject().apply {
                    put("name", it.backupName)
                    put("createdAt", it.createdAt)
                    put("createdAtLabel", formatBackupTimestamp(it.createdAt))
                    put("uri", it.exportedUri ?: "")
                },
            )
        }
        records.edit().putString(BACKUP_RECORDS, json.toString()).apply()
    }

    private fun backupTimestamp(backupName: String): Long =
        backupName
            .substringBeforeLast(".pcbk")
            .substringAfterLast('.')
            .toLongOrNull() ?: System.currentTimeMillis()

    private fun formatBackupTimestamp(timestamp: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))

    @Throws(IOException::class)
    fun restoreEncryptedBackup(
        context: Context,
        backupName: String,
        pin: CharArray,
    ) {
        val src = backupFile(context, backupName)
        if (!src.exists()) {
            throw IOException("Backup not found: $backupName")
        }

        FileInputStream(src).use { fis ->
            val (passphrase, temp) = decryptBackupToTemp(fis, pin, context)

            try {
                AppDatabase.closeDatabase()
                Repository.resetInstance()
                DatabaseKeyProvider.reset(context)
                val dbPass = requirePassphrase(passphrase)
                DatabaseKeyProvider.restorePassphrase(context, dbPass)
                val dbFile = context.getDatabasePath(DB_NAME)
                if (!temp.renameTo(dbFile)) {
                    replaceDatabaseFile(temp, dbFile)
                }
            } catch (e: IOException) {
                temp.delete()
                throwRestoreFailure(e)
            } finally {
                passphrase?.fill(0)
                Arrays.fill(pin, '\u0000')
            }
        }
    }

    private fun decryptBackupToTemp(
        fis: FileInputStream,
        pin: CharArray,
        context: Context,
    ): Pair<ByteArray?, File> {
        val (salt, iv) = readBackupHeader(fis)

        var derived: ByteArray? = null
        var passphrase: ByteArray? = null
        val temp = File.createTempFile("pantry_backup_", ".db.tmp", context.filesDir)

        try {
            derived = deriveKeyBytes(pin, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(derived, "AES"), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

            CipherInputStream(fis, cipher).use { cis ->
                passphrase = readBackupPassphrase(cis)
                streamDecryptedDatabase(cis, temp)
            }

            return Pair(passphrase, temp)
        } finally {
            derived?.fill(0)
        }
    }
}

private data class EncryptedBackupPayload(
    val passphrase: ByteArray?,
    val secretKey: SecretKeySpec,
    val salt: ByteArray,
    val iv: ByteArray,
)

private fun writeEncryptedBackup(
    temp: File,
    dbFile: File,
    payload: EncryptedBackupPayload,
) {
    FileOutputStream(temp).use { fos ->
        // header
        fos.write(MAGIC.toByteArray(Charsets.UTF_8))
        fos.write(byteArrayOf(VERSION))
        fos.write(payload.salt)
        fos.write(payload.iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            payload.secretKey,
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, payload.iv),
        )

        CipherOutputStream(fos, cipher).use { cos ->
            // write passphrase length (2 bytes big-endian) then passphrase
            val len = payload.passphrase?.size ?: 0
            cos.write((len ushr BYTE_SHIFT) and BYTE_MASK)
            cos.write(len and BYTE_MASK)
            if (payload.passphrase != null) cos.write(payload.passphrase)

            writeEncryptedDatabase(cos, dbFile)
            cos.flush()
        }
    }
}

private fun writeEncryptedDatabase(
    output: CipherOutputStream,
    dbFile: File,
) {
    FileInputStream(dbFile).use { fis ->
        val buffer = ByteArray(STREAM_BUFFER_BYTES)
        var read: Int
        while (fis.read(buffer).also { read = it } > 0) {
            output.write(buffer, 0, read)
        }
    }
}

private fun decryptBackupToTemp(
    fis: FileInputStream,
    pin: CharArray,
    context: Context,
): Pair<ByteArray?, File> {
    val (salt, iv) = readBackupHeader(fis)

    var derived: ByteArray? = null
    var passphrase: ByteArray? = null
    val temp = File.createTempFile("pantry_backup_", ".db.tmp", context.filesDir)

    try {
        derived = deriveKeyBytes(pin, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(derived, "AES"), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        CipherInputStream(fis, cipher).use { cis ->
            passphrase = readBackupPassphrase(cis)
            streamDecryptedDatabase(cis, temp)
        }

        return Pair(passphrase, temp)
    } finally {
        derived?.fill(0)
    }
}

private fun readBackupHeader(fis: FileInputStream): Pair<ByteArray, ByteArray> {
    val magicBytes = ByteArray(MAGIC.length)
    readExact(fis, magicBytes, "Truncated backup")
    if (!magicBytes.contentEquals(MAGIC.toByteArray(Charsets.UTF_8))) {
        throw IOException("Invalid backup magic")
    }
    val version = fis.read()
    if (version != VERSION.toInt()) throw IOException("Unsupported backup version: $version")
    val salt = ByteArray(SALT_LEN)
    readExact(fis, salt, "Truncated backup salt")
    val iv = ByteArray(IV_LEN)
    readExact(fis, iv, "Truncated backup IV")
    return salt to iv
}

private fun readExact(
    input: FileInputStream,
    buffer: ByteArray,
    message: String,
) {
    if (input.read(buffer) != buffer.size) throw IOException(message)
}

private fun requirePassphrase(passphrase: ByteArray?): ByteArray =
    passphrase
        ?: throw IOException("Backup omitted database passphrase")

private fun throwRestoreFailure(error: IOException): Nothing =
    throw IOException(
        "Failed to restore backup: ${error.message}",
        error,
    )

private fun readBackupPassphrase(input: CipherInputStream): ByteArray {
    val lengthHigh = input.read()
    val lengthLow = input.read()
    if (lengthHigh < 0 || lengthLow < 0) throw IOException("Truncated backup payload")
    val passphraseLength = (lengthHigh shl BYTE_SHIFT) or lengthLow
    val passphrase = ByteArray(passphraseLength)
    var offset = 0
    while (offset < passphraseLength) {
        val read = input.read(passphrase, offset, passphraseLength - offset)
        if (read < 0) throw IOException("Truncated backup passphrase")
        offset += read
    }
    return passphrase
}

private fun deriveKeyBytes(
    pin: CharArray,
    salt: ByteArray,
): ByteArray {
    val spec = PBEKeySpec(pin, salt, ITERATIONS, KEY_LEN_BITS)
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val key = factory.generateSecret(spec).encoded
    spec.clearPassword()
    return key
}

private fun backupFile(
    context: Context,
    backupName: String,
): File {
    if (backupName != File(backupName).name || !backupName.endsWith(".pcbk")) {
        throw IOException("Invalid backup name")
    }
    return File(context.filesDir, "$BACKUP_DIR/$backupName")
}
