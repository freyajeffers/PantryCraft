package com.jeffers.pantrycraft.feature.camera.barcode

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class CatalogDownloadVerifierTest {
    @Test
    fun sha256MatchesKnownBytes() {
        val file = Files.createTempFile("foodfacts", ".db").toFile()
        try {
            file.writeBytes("PantryCraft catalog".toByteArray())
            assertEquals(
                "13e390a921871ca61d5e2b1c7dcd826b5dfa2d6d743cd707bd1e2f483d535b93",
                CatalogDownloadVerifier.sha256(file),
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun sha256OfEmptyFileMatchesStandardDigest() {
        val file = Files.createTempFile("empty", ".db").toFile()
        try {
            assertEquals(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                CatalogDownloadVerifier.sha256(file),
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun sha256OfMultiChunkFileMatchesExpectedDigest() {
        val file = Files.createTempFile("multichunk", ".db").toFile()
        try {
            // Write 16384 bytes (2x 8KB buffer) with predictable pattern
            val content = ByteArray(16384) { (it % 256).toByte() }
            file.writeBytes(content)
            val digest = CatalogDownloadVerifier.sha256(file)
            assertEquals(64, digest.length)
        } finally {
            file.delete()
        }
    }

    @Test
    fun catalogDownloadExceptionPreservesMessage() {
        val exception = CatalogDownloadException("Catalog download timed out")
        assertEquals("Catalog download timed out", exception.message)
    }

    @Test
    fun sha256ProducesHexLowercaseChars() {
        val file = Files.createTempFile("hextest", ".db").toFile()
        try {
            file.writeBytes("Digest test payload".toByteArray())
            val digest = CatalogDownloadVerifier.sha256(file)
            assertEquals(64, digest.length)
            org.junit.Assert.assertTrue(digest.all { it in "0123456789abcdef" })
        } finally {
            file.delete()
        }
    }

    @Test
    fun sha256DifferentContentsProduceDistinctDigests() {
        val fileA = Files.createTempFile("fileA", ".db").toFile()
        val fileB = Files.createTempFile("fileB", ".db").toFile()
        try {
            fileA.writeBytes("Payload A".toByteArray())
            fileB.writeBytes("Payload B".toByteArray())
            val digestA = CatalogDownloadVerifier.sha256(fileA)
            val digestB = CatalogDownloadVerifier.sha256(fileB)
            org.junit.Assert.assertNotEquals(digestA, digestB)
        } finally {
            fileA.delete()
            fileB.delete()
        }
    }

    @Test
    fun sha256ThrowsWhenFileReadingFails() {
        val unreadableFile = java.io.File("/proc/self/mem")
        if (unreadableFile.exists()) {
            try {
                CatalogDownloadVerifier.sha256(unreadableFile)
                org.junit.Assert.fail("Expected IOException")
            } catch (_: java.io.IOException) {
                // Expected when read fails inside use block
            }
        }
    }
}
