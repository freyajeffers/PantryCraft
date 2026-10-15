package com.jeffers.pantrycraft.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DatabaseBackupManagerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AppDatabase.closeDatabase()
    }

    @After
    fun tearDown() {
        AppDatabase.closeDatabase()
        File(context.filesDir, "backups").deleteRecursively()
    }

    @Test
    fun encryptedBackupRoundTripsDatabaseAndRejectsWrongPin() =
        runBlocking {
            val database = AppDatabase.getDatabase(context)
            val marker = "backup-round-trip-${System.nanoTime()}"
            database.pantryDao().insert(PantryItem(name = marker, quantity = 3f))

            val backupName =
                DatabaseBackupManager.createEncryptedBackup(
                    context,
                    "correct-pin".toCharArray(),
                )
            assertTrue(DatabaseBackupManager.listBackups(context).contains(backupName))

            var wrongPinRejected = false
            try {
                DatabaseBackupManager.restoreEncryptedBackup(
                    context,
                    backupName,
                    "wrong-pin".toCharArray(),
                )
            } catch (_: Exception) {
                wrongPinRejected = true
            }
            assertTrue(wrongPinRejected)

            DatabaseBackupManager.restoreEncryptedBackup(
                context,
                backupName,
                "correct-pin".toCharArray(),
            )
            val restored =
                AppDatabase
                    .getDatabase(context)
                    .pantryDao()
                    .observeAllPantryItems()
                    .first()
                    .single { it.name == marker }
            assertEquals(marker, restored.name)
        }

    @Test
    fun restoreRejectsPathTraversalBackupNames() {
        var rejected = false
        try {
            DatabaseBackupManager.restoreEncryptedBackup(
                context,
                "../not-a-backup.pcbk",
                "pin".toCharArray(),
            )
        } catch (_: Exception) {
            rejected = true
        }
        assertTrue(rejected)
    }
}
