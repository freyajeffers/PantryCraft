package com.jeffers.pantrycraft.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class BackupHeaderOperationsTest {
    @Test
    fun readBackupHeaderValidHeaderReturnsSaltAndIv() {
        val magic = "PCBK".toByteArray(Charsets.UTF_8)
        val version = byteArrayOf(1)
        val salt = ByteArray(16) { (it + 1).toByte() }
        val iv = ByteArray(12) { (it + 10).toByte() }
        val stream = ByteArrayInputStream(magic + version + salt + iv)

        val (parsedSalt, parsedIv) = readBackupHeader(stream)

        assertArrayEquals(salt, parsedSalt)
        assertArrayEquals(iv, parsedIv)
    }

    @Test
    fun readBackupHeaderInvalidMagicThrows() {
        val badMagic = "BADM".toByteArray(Charsets.UTF_8)
        val rest = ByteArray(29)
        val stream = ByteArrayInputStream(badMagic + rest)

        val ex =
            assertThrows(IOException::class.java) {
                readBackupHeader(stream)
            }
        assertEquals("Invalid backup magic", ex.message)
    }

    @Test
    fun readBackupHeaderUnsupportedVersionThrows() {
        val magic = "PCBK".toByteArray(Charsets.UTF_8)
        val unsupportedVersion = byteArrayOf(99)
        val rest = ByteArray(28)
        val stream = ByteArrayInputStream(magic + unsupportedVersion + rest)

        val ex =
            assertThrows(IOException::class.java) {
                readBackupHeader(stream)
            }
        assertEquals("Unsupported backup version: 99", ex.message)
    }

    @Test
    fun readBackupHeaderTruncatedMagicThrows() {
        val truncatedMagic = "PC".toByteArray(Charsets.UTF_8)
        val stream = ByteArrayInputStream(truncatedMagic)

        val ex =
            assertThrows(IOException::class.java) {
                readBackupHeader(stream)
            }
        assertEquals("Truncated backup", ex.message)
    }

    @Test
    fun readBackupHeaderTruncatedSaltThrows() {
        val magic = "PCBK".toByteArray(Charsets.UTF_8)
        val version = byteArrayOf(1)
        val truncatedSalt = ByteArray(8)
        val stream = ByteArrayInputStream(magic + version + truncatedSalt)

        val ex =
            assertThrows(IOException::class.java) {
                readBackupHeader(stream)
            }
        assertEquals("Truncated backup salt", ex.message)
    }

    @Test
    fun readBackupHeaderTruncatedIvThrows() {
        val magic = "PCBK".toByteArray(Charsets.UTF_8)
        val version = byteArrayOf(1)
        val salt = ByteArray(16)
        val truncatedIv = ByteArray(5)
        val stream = ByteArrayInputStream(magic + version + salt + truncatedIv)

        val ex =
            assertThrows(IOException::class.java) {
                readBackupHeader(stream)
            }
        assertEquals("Truncated backup IV", ex.message)
    }

    @Test
    fun requirePassphraseReturnsNonNullAndThrowsOnNull() {
        val validPassphrase = "passphrase-bytes".toByteArray()
        val result = requirePassphrase(validPassphrase)
        assertArrayEquals(validPassphrase, result)

        val ex =
            assertThrows(IOException::class.java) {
                requirePassphrase(null)
            }
        assertEquals("Backup omitted database passphrase", ex.message)
    }

    @Test
    fun throwRestoreFailureWrapsIOException() {
        val rootError = IOException("Corrupted payload")
        val ex =
            assertThrows(IOException::class.java) {
                throwRestoreFailure(rootError)
            }
        assertEquals("Failed to restore backup: Corrupted payload", ex.message)
        assertEquals(rootError, ex.cause)
    }
}
