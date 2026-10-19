package com.jeffers.pantrycraft.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BackupChecksumTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun returnsNullWhenFileDoesNotExist() {
        val nonExistent = File(tempFolder.root, "missing_db.db")
        assertNull(fileChecksum(nonExistent))
    }

    @Test
    fun returnsNullWhenFileIsDirectory() {
        val dir = tempFolder.newFolder("sub_directory")
        assertNull(fileChecksum(dir))
    }

    @Test
    fun computesKnownSha256ForEmptyFile() {
        val emptyFile = tempFolder.newFile("empty.db")
        val checksum = fileChecksum(emptyFile)

        assertNotNull(checksum)
        assertEquals(EMPTY_SHA256, checksum)
        assertEquals(SHA256_HEX_LENGTH, checksum?.length)
    }

    @Test
    fun computesValidHexDigestForDataFile() {
        val dataFile = tempFolder.newFile("sample.db")
        dataFile.writeBytes("PantryCraft-Secure-Backup-Payload".toByteArray(Charsets.UTF_8))

        val checksum = fileChecksum(dataFile)
        assertNotNull(checksum)
        assertEquals(SHA256_HEX_LENGTH, checksum?.length)
        assertTrue(checksum!!.all { it in "0123456789abcdef" })
    }

    @Test
    fun checksumChangesWhenContentChanges() {
        val file = tempFolder.newFile("mutating.db")
        file.writeText("initial content")
        val initialChecksum = fileChecksum(file)

        file.writeText("modified content")
        val updatedChecksum = fileChecksum(file)

        assertNotNull(initialChecksum)
        assertNotNull(updatedChecksum)
        assertNotEquals(initialChecksum, updatedChecksum)
    }

    companion object {
        private const val SHA256_HEX_LENGTH = 64
        private const val EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    }
}
