package com.jeffers.pantrycraft.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TemporaryMediaStressTest {
    @Test
    fun fiftyScratchSessionsLeaveNoOrphans() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val repository = Repository.getInstance(context)
            val scratch = context.cacheDir.resolve("vision_scratch")
            scratch.deleteRecursively()

            repeat(50) { session ->
                scratch.mkdirs()
                scratch.resolve("session-$session.jpg").writeBytes(ByteArray(32))
                assertEquals(1, repository.reclaimTemporaryMedia())
                assertEquals(0, scratch.listFiles().orEmpty().size)
            }

            assertEquals(0, repository.reclaimTemporaryMedia())
        }
}
