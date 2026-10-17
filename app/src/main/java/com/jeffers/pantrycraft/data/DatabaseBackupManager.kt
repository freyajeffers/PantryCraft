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
object DatabaseBackupManager {
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

    private data class EncryptedBackupPayload(
        val passphrase: ByteArray?,
        val secretKey: SecretKeySpec,
        val salt: ByteArray,
        val iv: ByteArray,
    )

    private const val BYTE_MASK = 0xFF
    private const val BYTE_SHIFT = 8
    private const val PREFS_NAME = "backup_preferences"
    private const val LAST_EXPORTED_URI = "last_exported_uri"
    private const val LAST_DB_CHECKSUM = "last_db_checksum"
    private const val BACKUP_RECORDS = "backup_records"
    private const val AUTO_BACKUP_TREE_URI = "auto_backup_tree_uri"

    data class BackupRecord(
        val backupName: String,
        val createdAt: Long,
        val createdAtLabel: String,
        val exportedUri: String?,
    )

    fun backupRecords(context: Context): List<BackupRecord> {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored =
            runCatching {
                val json = JSONArray(preferences.getString(BACKUP_RECORDS, "[]"))
                (0 until json.length()).map { index ->
                    val item = json.getJSONObject(index)
                    BackupRecord(
                        backupName = item.getString("name"),
                        createdAt = item.getLong("createdAt"),
                        createdAtLabel = item.optString("createdAtLabel", item.getString("name")),
                        exportedUri = item.optString("uri").takeIf { it.isNotBlank() },
                    )
                }
            }.getOrDefault(emptyList())
        val known = stored.mapTo(mutableSetOf()) { it.backupName }
        val missing =
            listBackups(context)
                .filterNot(known::contains)
                .map { name -> BackupRecord(name, backupTimestamp(name), name, null) }
        return (stored + missing).sortedByDescending { it.createdAt }
    }

    fun recordCreatedBackup(
        context: Context,
        backupName: String,
    ) {
        val timestamp = backupTimestamp(backupName)
        saveBackupRecord(
            context,
            BackupRecord(backupName, timestamp, formatBackupTimestamp(timestamp), null),
        )
    }

    fun persistedBackupUri(context: Context): Uri? {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val value = preferences.getString(LAST_EXPORTED_URI, null)
        return value?.let(Uri::parse)
    }

    fun persistedAutoBackupTreeUri(context: Context): Uri? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(AUTO_BACKUP_TREE_URI, null)?.let { Uri.parse(it) }
    }

    fun persistAutoBackupTreeUri(
        context: Context,
        uri: Uri?,
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(AUTO_BACKUP_TREE_URI, uri?.toString()).apply()
    }

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
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        preferences
            .edit()
            .putString(LAST_EXPORTED_URI, destination.toString())
            .apply()
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

    fun dbChecksum(context: Context): String? {
        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.exists() || !dbFile.isFile) return null
        return runCatching {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            dbFile.inputStream().use { input ->
                val buffer = ByteArray(STREAM_BUFFER_BYTES)
                var read: Int
                while (input.read(buffer).also { read = it } > 0) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }

    fun lastDbChecksum(context: Context): String? =
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(LAST_DB_CHECKSUM, null)

    fun saveLastDbChecksum(
        context: Context,
        checksum: String?,
    ) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(LAST_DB_CHECKSUM, checksum)
            .apply()
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

    fun listBackups(context: Context): List<String> {
        val dir = File(context.filesDir, BACKUP_DIR)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir
            .listFiles()
            ?.filter { it.isFile && it.name.endsWith(".pcbk") }
            ?.sortedByDescending { it.lastModified() }
            ?.map { it.name }
            ?: emptyList()
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

        recordCreatedBackup(context, dest.name)
        return dest.name
    }

    private fun ensureDatabasePresent(dbFile: File) {
        if (!dbFile.exists()) {
            throw IOException("Database file not found")
        }
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

                // stream DB into cipher
                FileInputStream(dbFile).use { fis ->
                    val buffer = ByteArray(STREAM_BUFFER_BYTES)
                    var read: Int
                    while (fis.read(buffer).also { read = it } > 0) {
                        cos.write(buffer, 0, read)
                    }
                }
                cos.flush()
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
                DatabaseKeyProvider.restorePassphrase(
                    context,
                    passphrase ?: throw IOException("Backup omitted database passphrase"),
                )
                val dbFile = context.getDatabasePath(DB_NAME)
                if (!temp.renameTo(dbFile)) {
                    FileInputStream(temp).use { input ->
                        FileOutputStream(dbFile).use { output ->
                            input.copyTo(output, STREAM_BUFFER_BYTES)
                            output.fd.sync()
                        }
                    }
                    check(temp.delete()) { "Unable to remove temporary restored database" }
                }
            } catch (e: IOException) {
                temp.delete()
                throw IOException("Failed to restore backup: ${e.message}", e)
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
        val magicBytes = ByteArray(MAGIC.length)
        if (fis.read(magicBytes) != magicBytes.size) throw IOException("Truncated backup")
        if (!magicBytes.contentEquals(MAGIC.toByteArray(Charsets.UTF_8))) {
            throw IOException("Invalid backup magic")
        }
        val version = fis.read()
        if (version != VERSION.toInt()) throw IOException("Unsupported backup version: $version")
        val salt = ByteArray(SALT_LEN)
        if (fis.read(salt) != SALT_LEN) throw IOException("Truncated backup salt")
        val iv = ByteArray(IV_LEN)
        if (fis.read(iv) != IV_LEN) throw IOException("Truncated backup IV")

        var derived: ByteArray? = null
        var passphrase: ByteArray? = null
        val temp = File.createTempFile("pantry_backup_", ".db.tmp", context.filesDir)

        try {
            derived = deriveKeyBytes(pin, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(derived, "AES"), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

            CipherInputStream(fis, cipher).use { cis ->
                val lengthHigh = cis.read()
                val lengthLow = cis.read()
                if (lengthHigh < 0 || lengthLow < 0) throw IOException("Truncated backup payload")
                val passphraseLength = (lengthHigh shl 8) or lengthLow
                passphrase = ByteArray(passphraseLength)
                var offset = 0
                while (offset < passphraseLength) {
                    val read = cis.read(passphrase, offset, passphraseLength - offset)
                    if (read < 0) throw IOException("Truncated backup passphrase")
                    offset += read
                }
                FileOutputStream(temp).use { fos ->
                    val buffer = ByteArray(STREAM_BUFFER_BYTES)
                    var read: Int
                    while (cis.read(buffer).also { read = it } > 0) {
                        fos.write(buffer, 0, read)
                    }
                    fos.fd.sync()
                }
            }

            return Pair(passphrase, temp)
        } finally {
            derived?.fill(0)
        }
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
}
