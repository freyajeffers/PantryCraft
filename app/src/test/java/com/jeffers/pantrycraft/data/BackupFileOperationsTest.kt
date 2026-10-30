package com.jeffers.pantrycraft.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.SecretKeySpec

class BackupFileOperationsTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun ensureDatabasePresentThrowsWhenFileNotFound() {
        val nonExistent = File(tempFolder.root, "does_not_exist.db")
        assertThrows(IOException::class.java) {
            ensureDatabasePresent(nonExistent)
        }
    }

    @Test
    fun ensureDatabasePresentSucceedsWhenFileExists() {
        val existing = tempFolder.newFile("existing.db")
        existing.writeText("database-content")
        ensureDatabasePresent(existing)
        assertTrue(existing.exists())
    }

    @Test
    fun replaceDatabaseFileOverwritesTargetAndDeletesTemporarySource() {
        val targetDb = tempFolder.newFile("target.db")
        targetDb.writeText("old-database-content")

        val tempRestored = tempFolder.newFile("temp_restore.db")
        tempRestored.writeText("restored-database-content")

        replaceDatabaseFile(temp = tempRestored, dbFile = targetDb)

        assertEquals("restored-database-content", targetDb.readText())
        assertFalse(tempRestored.exists())
    }

    @Test
    fun streamDecryptedDatabaseWritesBytesFromCipherStream() {
        val key = SecretKeySpec(ByteArray(16) { 1 }, "AES")
        val cipherEncrypt = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipherEncrypt.init(Cipher.ENCRYPT_MODE, key)
        val plaintext = "Streamed decrypted database payload content".toByteArray(Charsets.UTF_8)
        val ciphertext = cipherEncrypt.doFinal(plaintext)

        val cipherDecrypt = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipherDecrypt.init(Cipher.DECRYPT_MODE, key)
        val cis = CipherInputStream(java.io.ByteArrayInputStream(ciphertext), cipherDecrypt)

        val targetFile = tempFolder.newFile("decrypted_output.db")
        streamDecryptedDatabase(cis, targetFile)

        assertEquals("Streamed decrypted database payload content", targetFile.readText())
    }

    @Test
    fun replaceDatabaseFileThrowsWhenTemporaryFileCannotBeDeleted() {
        val targetDb = tempFolder.newFile("target_fail.db")
        val realTemp = tempFolder.newFile("temp_fail.db")
        realTemp.writeText("data")

        val undeletableTemp =
            object : File(realTemp.absolutePath) {
                override fun delete(): Boolean = false
            }

        assertThrows(IOException::class.java) {
            replaceDatabaseFile(temp = undeletableTemp, dbFile = targetDb)
        }
    }

    @Test
    fun streamDecryptedDatabasePropagatesExceptionWhenStreamThrows() {
        val key = SecretKeySpec(ByteArray(16) { 1 }, "AES")
        val cipherDecrypt = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipherDecrypt.init(Cipher.DECRYPT_MODE, key)
        val bais = java.io.ByteArrayInputStream(ByteArray(10))
        val failingInputStream =
            object : CipherInputStream(bais, cipherDecrypt) {
                override fun read(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                ): Int = throw IOException("Read error")
            }
        val targetFile = tempFolder.newFile("failing_output.db")
        assertThrows(IOException::class.java) {
            streamDecryptedDatabase(failingInputStream, targetFile)
        }
    }

    @Test
    fun replaceDatabaseFileThrowsWhenTargetIsDirectory() {
        val tempRestored = tempFolder.newFile("temp_valid.db")
        tempRestored.writeText("data")
        val targetDir = tempFolder.newFolder("target_dir")

        assertThrows(IOException::class.java) {
            replaceDatabaseFile(temp = tempRestored, dbFile = targetDir)
        }
    }

    @Test
    fun replaceDatabaseFileThrowsWhenWritingToFullDevice() {
        val tempRestored = tempFolder.newFile("temp_full.db")
        tempRestored.writeText("data-to-write-to-full-device")
        val devFull = File("/dev/full")

        if (devFull.exists()) {
            assertThrows(IOException::class.java) {
                replaceDatabaseFile(temp = tempRestored, dbFile = devFull)
            }
        }
    }

    @Test
    fun deriveKeyBytesDerivesDeterministicKeyAndScrubsPin() {
        val pin = charArrayOf('5', '6', '7', '8')
        val salt = ByteArray(16) { (it + 1).toByte() }
        val key1 = deriveKeyBytes(pin, salt)
        assertEquals(32, key1.size)

        val pinCopy = charArrayOf('5', '6', '7', '8')
        val key2 = deriveKeyBytes(pinCopy, salt)
        assertTrue(key1.contentEquals(key2))

        val diffPin = charArrayOf('5', '6', '7', '9')
        val keyDiff = deriveKeyBytes(diffPin, salt)
        assertFalse(key1.contentEquals(keyDiff))
    }

    @Test
    fun readBackupPassphraseParsesPayloadAndThrowsOnTruncation() {
        val samplePassphrase = "test-passphrase-bytes-1234".toByteArray(Charsets.UTF_8)
        val len = samplePassphrase.size
        val high = (len shr 8) and 0xFF
        val low = len and 0xFF
        val streamBytes = byteArrayOf(high.toByte(), low.toByte()) + samplePassphrase

        val stream = java.io.ByteArrayInputStream(streamBytes)
        val parsed = readBackupPassphrase(stream)
        assertEquals("test-passphrase-bytes-1234", parsed.toString(Charsets.UTF_8))

        val truncatedStream = java.io.ByteArrayInputStream(byteArrayOf(0.toByte(), 10.toByte(), 1, 2, 3))
        assertThrows(IOException::class.java) {
            readBackupPassphrase(truncatedStream)
        }
    }
}
