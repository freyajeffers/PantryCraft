package com.jeffers.pantrycraft.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupRecordsInstrumentationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val backupDir = File(context.filesDir, "backups")
    private val createdTestFiles = mutableListOf<File>()
    private var originalRecordsJson: String? = null

    @Before
    fun setUp() {
        backupDir.mkdirs()
        originalRecordsJson = context
            .getSharedPreferences("backup_preferences", Context.MODE_PRIVATE)
            .getString("backup_records", null)
    }

    @After
    fun tearDown() {
        createdTestFiles.forEach { it.delete() }
        createdTestFiles.clear()
        context
            .getSharedPreferences("backup_preferences", Context.MODE_PRIVATE)
            .edit()
            .putString("backup_records", originalRecordsJson)
            .apply()
    }

    @Test
    fun listBackupFilesReturnsOnlyPcbkFilesSortedByLastModified() {
        val nonPcbk = File(backupDir, "sample.txt").apply { writeText("ignore") }
        val olderPcbk = File(backupDir, "backup.1000.pcbk").apply { writeText("data1") }
        olderPcbk.setLastModified(1_000_000L)
        val newerPcbk = File(backupDir, "backup.2000.pcbk").apply { writeText("data2") }
        newerPcbk.setLastModified(2_000_000L)

        createdTestFiles.addAll(listOf(nonPcbk, olderPcbk, newerPcbk))

        val listed = listBackupFiles(context)
        assertTrue(listed.contains("backup.1000.pcbk"))
        assertTrue(listed.contains("backup.2000.pcbk"))
        assertTrue(!listed.contains("sample.txt"))

        val indexOlder = listed.indexOf("backup.1000.pcbk")
        val indexNewer = listed.indexOf("backup.2000.pcbk")
        assertTrue("Newer modified backup file must appear before older one", indexNewer < indexOlder)
    }

    @Test
    fun backupRecordsMergesStoredPreferencesWithFilesystemBackups() {
        val unlistedFile = File(backupDir, "untracked.3000.pcbk").apply { writeText("untracked") }
        createdTestFiles.add(unlistedFile)

        val storedJson = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("name", "tracked.4000.pcbk")
                    put("createdAt", 4000L)
                    put("createdAtLabel", "Tracked Backup")
                    put("uri", "content://test/uri/1")
                },
            )
        }.toString()

        context
            .getSharedPreferences("backup_preferences", Context.MODE_PRIVATE)
            .edit()
            .putString("backup_records", storedJson)
            .apply()

        val records = backupRecords(context)
        val tracked = records.firstOrNull { it.backupName == "tracked.4000.pcbk" }
        val untracked = records.firstOrNull { it.backupName == "untracked.3000.pcbk" }

        assertNotNull(tracked)
        assertEquals("Tracked Backup", tracked?.createdAtLabel)
        assertEquals("content://test/uri/1", tracked?.exportedUri)

        assertNotNull(untracked)
        assertEquals(3000L, untracked?.createdAt)
        assertEquals("untracked.3000.pcbk", untracked?.createdAtLabel)
    }

    @Test
    fun backupRecordsRecoversGracefullyFromCorruptPreferences() {
        context
            .getSharedPreferences("backup_preferences", Context.MODE_PRIVATE)
            .edit()
            .putString("backup_records", "{ invalid json array }")
            .apply()

        val records = backupRecords(context)
        assertNotNull(records)
    }
}
