package com.jeffers.pantrycraft.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DatabaseBackupManagerTest {
    private lateinit var context: Context
    private var preservedDb: File? = null
    private var preservedKey: ByteArray? = null
    private var createdBackupName: String? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AppDatabase.checkpointForBackup(context)
        AppDatabase.closeDatabase()
        Repository.resetInstance()

        val dbFile = context.getDatabasePath("pantry_grocery_encrypted.db")
        if (dbFile.exists()) {
            preservedDb = File.createTempFile("preserved_pantry_", ".db", context.cacheDir)
            dbFile.copyTo(preservedDb!!, overwrite = true)
            preservedKey = DatabaseKeyProvider.getKey(context).copyOf()
        }
    }

    @After
    fun tearDown() {
        AppDatabase.closeDatabase()
        Repository.resetInstance()

        createdBackupName?.let {
            File(context.filesDir, "backups/$it").delete()
        }

        val dbFile = context.getDatabasePath("pantry_grocery_encrypted.db")
        if (preservedDb != null && preservedDb!!.exists() && preservedKey != null) {
            preservedDb!!.copyTo(dbFile, overwrite = true)
            preservedDb!!.delete()
            DatabaseKeyProvider.restorePassphrase(context, preservedKey!!)
            preservedKey!!.fill(0)
        } else {
            removeRoundTripEntries()
        }
        AppDatabase.closeDatabase()
        Repository.resetInstance()
        AppDatabase.getDatabase(context)
        Repository.getInstance(context)
    }

    private fun removeRoundTripEntries() {
        runBlocking {
            AppDatabase
                .getDatabase(context)
                .pantryDao()
                .deleteByNamePrefix("backup-round-trip-")
        }
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
            createdBackupName = backupName
            assertTrue(listBackupFiles(context).contains(backupName))


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
            var restored: PantryItem? = null
            try {
                val restoredItem =
                    AppDatabase
                        .getDatabase(context)
                        .pantryDao()
                        .observeAllPantryItems()
                        .first()
                        .single { it.name == marker }
                restored = restoredItem
                assertEquals(marker, restoredItem.name)
            } finally {
                restored?.let { AppDatabase.getDatabase(context).pantryDao().delete(it) }
            }
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

    @Test
    fun restoreRejectsInvalidFileExtension() {
        var rejected = false
        try {
            DatabaseBackupManager.restoreEncryptedBackup(
                context,
                "backup.db",
                "pin".toCharArray(),
            )
        } catch (_: Exception) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun restoreRejectsNonExistentBackupFile() {
        var rejected = false
        try {
            DatabaseBackupManager.restoreEncryptedBackup(
                context,
                "does_not_exist_9999.pcbk",
                "pin".toCharArray(),
            )
        } catch (_: Exception) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun restoreRejectsCorruptedMagicHeader() {
        val backupDir = File(context.filesDir, "backups").apply { mkdirs() }
        val corruptFile = File(backupDir, "corrupted_magic_test.pcbk")
        try {
            corruptFile.writeBytes("CORRUPTED_HEADER_DATA_NOT_MAGIC".toByteArray())
            var rejected = false
            try {
                DatabaseBackupManager.restoreEncryptedBackup(
                    context,
                    corruptFile.name,
                    "pin".toCharArray(),
                )
            } catch (_: Exception) {
                rejected = true
            }
            assertTrue(rejected)
        } finally {
            corruptFile.delete()
        }
    }

    @Test
    fun backupRecordsIncludesNewlyCreatedBackup() {
        val pin = "test-record-pin".toCharArray()
        val backupName = DatabaseBackupManager.createEncryptedBackup(context, pin)
        createdBackupName = backupName

        val records = backupRecords(context)
        val matchingRecord = records.firstOrNull { it.backupName == backupName }

        assertNotNull(matchingRecord)
        assertEquals(backupName, matchingRecord?.backupName)
        assertTrue((matchingRecord?.createdAt ?: 0L) > 0L)
        assertNotNull(matchingRecord?.createdAtLabel)
        assertTrue(listBackupFiles(context).contains(backupName))
    }

    @Test
    fun exportBackupAndRestoreFromUriRoundTripsDatabase() = runBlocking {
        val database = AppDatabase.getDatabase(context)
        val marker = "backup-uri-round-trip-${System.nanoTime()}"
        database.pantryDao().insert(PantryItem(name = marker, quantity = 4f))

        val pin = "uri-round-trip-pin".toCharArray()
        val backupName = DatabaseBackupManager.createEncryptedBackup(context, pin)
        createdBackupName = backupName

        val exportFile = File(context.cacheDir, "exported_backup_${System.nanoTime()}.pcbk")
        val exportUri = android.net.Uri.fromFile(exportFile)

        try {
            DatabaseBackupManager.exportBackup(context, backupName, exportUri)
            assertTrue(exportFile.exists())
            assertTrue(exportFile.length() > 0)

            // Modify database to simulate loss
            AppDatabase.getDatabase(context).pantryDao().deleteByNamePrefix("backup-uri-round-trip-")

            // Restore from URI
            DatabaseBackupManager.restoreEncryptedBackupFromUri(
                context,
                exportUri,
                "uri-round-trip-pin".toCharArray(),
            )

            val restoredItem =
                AppDatabase
                    .getDatabase(context)
                    .pantryDao()
                    .observeAllPantryItems()
                    .first()
                    .firstOrNull { it.name == marker }

            assertNotNull(restoredItem)
            assertEquals(marker, restoredItem?.name)
        } finally {
            exportFile.delete()
        }
    }

    @Test(expected = java.io.IOException::class)
    fun exportBackupThrowsIOExceptionForNonExistentBackup() {
        val nonExistentUri = android.net.Uri.fromFile(File(context.cacheDir, "nowhere.pcbk"))
        DatabaseBackupManager.exportBackup(
            context,
            "does_not_exist_file.pcbk",
            nonExistentUri,
        )
    }

    @Test
    fun restoreRejectsUnsupportedBackupVersion() {
        val backupDir = File(context.filesDir, "backups").apply { mkdirs() }
        val corruptVersionFile = File(backupDir, "unsupported_version_test.pcbk")
        try {
            // Write magic "PCBK" followed by version 99
            val bytes = "PCBK".toByteArray(Charsets.UTF_8) + byteArrayOf(99)
            corruptVersionFile.writeBytes(bytes)

            var rejected = false
            try {
                DatabaseBackupManager.restoreEncryptedBackup(
                    context,
                    corruptVersionFile.name,
                    "pin".toCharArray(),
                )
            } catch (_: Exception) {
                rejected = true
            }
            assertTrue(rejected)
        } finally {
            corruptVersionFile.delete()
        }
    }
}
