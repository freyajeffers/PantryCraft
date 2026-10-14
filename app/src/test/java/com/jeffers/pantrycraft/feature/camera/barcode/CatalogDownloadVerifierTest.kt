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
}
