package com.jeffers.pantrycraft.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class DatabaseBackupManagerEdgeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        AppDatabase.getDatabase(context)
        Repository.getInstance(context)
    }

    @Test
    fun exportBackupThrowsIOExceptionWhenDestinationCannotBeOpened() {
        val backupDir = File(context.filesDir, "backups").apply { mkdirs() }
        val dummyBackup = File(backupDir, "pantrycraft_backup_999999999.pcbk").apply { writeText("dummy") }
        val invalidUri = Uri.parse("file:///nonexistent_dir_cannot_write/output.bin")

        try {
            assertThrows(IOException::class.java) {
                DatabaseBackupManager.exportBackup(context, dummyBackup.name, invalidUri)
            }
        } finally {
            dummyBackup.delete()
        }
    }

    @Test
    fun restoreEncryptedBackupFromUriThrowsWhenInputStreamNull() {
        val nonExistentUri = Uri.parse("file:///nonexistent/unreadable_backup.bin")
        val pin = "1234".toCharArray()

        assertThrows(Exception::class.java) {
            DatabaseBackupManager.restoreEncryptedBackupFromUri(context, nonExistentUri, pin)
        }
    }

    @Test
    fun exportBackupCreatesRecordWhenNotAlreadyInPreferences() {
        val backupDir = File(context.filesDir, "backups").apply { mkdirs() }
        val timestamp = 1700000000000L
        val backupName = "pantrycraft_backup_$timestamp.pcbk"
        val dummyBackup = File(backupDir, backupName).apply { writeText("data") }
        val targetFile = File(context.cacheDir, "exported_edge_test.bin")
        val targetUri = Uri.fromFile(targetFile)

        try {
            DatabaseBackupManager.exportBackup(context, backupName, targetUri)
            val records = backupRecords(context)
            assertTrue(records.any { it.backupName == backupName })
        } finally {
            dummyBackup.delete()
            targetFile.delete()
        }
    }

    @Test
    fun backupRecordsFiltersSubdirectoriesAndNonPcbkFiles() {
        val backupDir = File(context.filesDir, "backups").apply { mkdirs() }
        val subDir = File(backupDir, "sub_directory_test").apply { mkdirs() }
        val nonPcbkFile = File(backupDir, "ignored_file.txt").apply { writeText("ignored") }
        try {
            val list = listBackupFiles(context)
            assertTrue(list.none { it == "sub_directory_test" || it == "ignored_file.txt" })
        } finally {
            subDir.delete()
            nonPcbkFile.delete()
        }
    }
}
