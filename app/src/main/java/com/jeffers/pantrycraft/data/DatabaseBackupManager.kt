package com.jeffers.pantrycraft.data

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.SecureRandom
import java.util.Arrays
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
        if (!dbFile.exists()) {
            throw IOException("Database file not found")
        }

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

            AppDatabase.closeDatabase()
            FileOutputStream(temp).use { fos ->
                // header
                fos.write(MAGIC.toByteArray(Charsets.UTF_8))
                fos.write(byteArrayOf(VERSION))
                fos.write(salt)
                fos.write(iv)

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))

                CipherOutputStream(fos, cipher).use { cos ->
                    // write passphrase length (2 bytes big-endian) then passphrase
                    val len = passphrase.size
                    cos.write((len ushr 8) and 0xFF)
                    cos.write(len and 0xFF)
                    cos.write(passphrase)

                    // stream DB into cipher
                    FileInputStream(dbFile).use { fis ->
                        val buffer = ByteArray(16 * 1024)
                        var read: Int
                        while (fis.read(buffer).also { read = it } > 0) {
                            cos.write(buffer, 0, read)
                        }
                    }
                    cos.flush()
                }
            }
            if (!temp.renameTo(dest)) throw IOException("Unable to finalize encrypted backup")
        } catch (error: Exception) {
            temp.delete()
            throw error
        } finally {
            passphrase?.fill(0)
            derived?.fill(0)
            Arrays.fill(pin, '\u0000')
        }
        return dest.name
    }

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
            val temp =
                File.createTempFile("pantry_backup_", ".db.tmp", context.filesDir)
            try {
                derived = deriveKeyBytes(pin, salt)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    SecretKeySpec(derived, "AES"),
                    GCMParameterSpec(128, iv),
                )
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
                        val buffer = ByteArray(16 * 1024)
                        var read: Int
                        while (cis.read(buffer).also { read = it } > 0) {
                            fos.write(buffer, 0, read)
                        }
                        fos.fd.sync()
                    }
                }

                AppDatabase.closeDatabase()
                DatabaseKeyProvider.reset(context)
                DatabaseKeyProvider.restorePassphrase(
                    context,
                    passphrase ?: throw IOException("Backup omitted database passphrase"),
                )
                val dbFile = context.getDatabasePath(DB_NAME)
                if (!temp.renameTo(dbFile)) {
                    FileInputStream(temp).use { input ->
                        FileOutputStream(dbFile).use { output ->
                            input.copyTo(output, 16 * 1024)
                            output.fd.sync()
                        }
                    }
                    check(temp.delete()) { "Unable to remove temporary restored database" }
                }
            } catch (e: Exception) {
                temp.delete()
                throw IOException("Failed to restore backup: ${e.message}", e)
            } finally {
                passphrase?.fill(0)
                derived?.fill(0)
                Arrays.fill(pin, '\u0000')
            }
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
