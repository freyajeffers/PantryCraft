package com.jeffers.pantrycraft.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TemporaryMediaUnitTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun reclaimScratchMediaReturnsZeroWhenDirectoryDoesNotExist() {
        val nonExistentCache = File(tempFolder.root, "empty_cache")
        assertEquals(0, reclaimScratchMedia(nonExistentCache))
    }

    @Test
    fun reclaimScratchMediaDeletesAllTemporaryInferenceMedia() {
        val cacheDir = tempFolder.newFolder("app_cache")
        val scratchDir = File(cacheDir, "vision_scratch").apply { mkdirs() }

        val frame1 = File(scratchDir, "snapshot-101.jpg").apply { writeText("frame1") }
        val frame2 = File(scratchDir, "snapshot-102.jpg").apply { writeText("frame2") }
        val subDir = File(scratchDir, "sub_batch").apply {
            mkdirs()
            File(this, "nested.jpg").writeText("nested")
        }

        val deletedCount = reclaimScratchMedia(cacheDir)
        assertEquals(3, deletedCount)

        assertFalse(frame1.exists())
        assertFalse(frame2.exists())
        assertFalse(subDir.exists())
    }
}
