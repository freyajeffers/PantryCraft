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

        val undeletableTemp = object : File(realTemp.absolutePath) {
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
        val failingInputStream = object : CipherInputStream(bais, cipherDecrypt) {
            override fun read(b: ByteArray, off: Int, len: Int): Int = throw IOException("Read error")
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
}
