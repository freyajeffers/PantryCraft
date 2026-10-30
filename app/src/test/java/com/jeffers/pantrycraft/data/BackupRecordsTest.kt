package com.jeffers.pantrycraft.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.lang.reflect.Proxy

class BackupRecordsTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun backupRecordDataClassHoldsExpectedFields() {
        val record =
            DatabaseBackupManager.BackupRecord(
                backupName = "pantrycraft_backup.1700000000.pcbk",
                createdAt = 1700000000L,
                createdAtLabel = "Nov 14, 2023",
                exportedUri = "content://com.android.providers.downloads/1",
            )

        assertEquals("pantrycraft_backup.1700000000.pcbk", record.backupName)
        assertEquals(1700000000L, record.createdAt)
        assertEquals("Nov 14, 2023", record.createdAtLabel)
        assertEquals("content://com.android.providers.downloads/1", record.exportedUri)
    }

    @Test
    fun backupRecordJsonDeserializationExtractsFields() {
        val jsonArray =
            JSONArray().apply {
                put(
                    JSONObject().apply {
                        put("name", "backup_1.pcbk")
                        put("createdAt", 1600000000L)
                        put("createdAtLabel", "Backup 1")
                        put("uri", "content://media/external/123")
                    },
                )
                put(
                    JSONObject().apply {
                        put("name", "backup_2.pcbk")
                        put("createdAt", 1700000000L)
                    },
                )
            }

        val records =
            (0 until jsonArray.length()).map { index ->
                val item = jsonArray.getJSONObject(index)
                DatabaseBackupManager.BackupRecord(
                    backupName = item.getString("name"),
                    createdAt = item.getLong("createdAt"),
                    createdAtLabel = item.optString("createdAtLabel", item.getString("name")),
                    exportedUri = item.optString("uri").takeIf { it.isNotBlank() },
                )
            }

        assertEquals(2, records.size)
        assertEquals("backup_1.pcbk", records[0].backupName)
        assertEquals("content://media/external/123", records[0].exportedUri)
        assertEquals("backup_2.pcbk", records[1].backupName)
        assertEquals("backup_2.pcbk", records[1].createdAtLabel)
        assertNull(records[1].exportedUri)
    }

    @Test
    fun timestampParsingFromFilenameExtractsEpoch() {
        val filename = "pantrycraft_backup.1729000000.pcbk"
        val timestamp = filename.substringBeforeLast(".pcbk").substringAfterLast('.').toLongOrNull()

        assertEquals(1729000000L, timestamp)
    }

    @Test
    fun timestampParsingFromArbitraryNameReturnsNull() {
        val filename = "custom_named_backup.pcbk"
        val timestamp = filename.substringBeforeLast(".pcbk").substringAfterLast('.').toLongOrNull()

        assertNull(timestamp)
    }

    @Test
    fun backupFileValidatesExtensionAndRejectsPathTraversal() {
        val mockContext = DummyContext(tempFolder.root, createMockPrefs(emptyMap()))
        org.junit.Assert.assertThrows(java.io.IOException::class.java) {
            backupFile(mockContext, "../escape.pcbk")
        }
        org.junit.Assert.assertThrows(java.io.IOException::class.java) {
            backupFile(mockContext, "invalid_extension.db")
        }
        org.junit.Assert.assertThrows(java.io.IOException::class.java) {
            backupFile(mockContext, "/absolute/path/backup.pcbk")
        }
    }

    @Test
    fun listBackupFilesReturnsEmptyWhenDirectoryDoesNotExist() {
        val context = DummyContext(tempFolder.root, createMockPrefs(emptyMap()))
        assertTrue(listBackupFiles(context).isEmpty())
    }

    @Test
    fun listBackupFilesReturnsSortedPcbkFilesOnly() {
        val backupsDir = File(tempFolder.root, "backups").apply { mkdirs() }
        val older =
            File(backupsDir, "backup_old.pcbk").apply {
                writeText("data")
                setLastModified(1000L)
            }
        val newer =
            File(backupsDir, "backup_new.pcbk").apply {
                writeText("data")
                setLastModified(2000L)
            }
        File(backupsDir, "unrelated.txt").writeText("text")

        val context = DummyContext(tempFolder.root, createMockPrefs(emptyMap()))
        val files = listBackupFiles(context)

        assertEquals(listOf(newer.name, older.name), files)
    }

    @Test
    fun backupRecordsMergesPreferencesAndFilesystem() {
        val backupsDir = File(tempFolder.root, "backups").apply { mkdirs() }
        File(backupsDir, "pantrycraft_backup.1700000000.pcbk").writeText("db")
        File(backupsDir, "unindexed_backup.1705000000.pcbk").writeText("db")

        val storedJson =
            """
            [
              {
                "name": "pantrycraft_backup.1700000000.pcbk",
                "createdAt": 1700000000,
                "createdAtLabel": "First Backup"
              }
            ]
            """.trimIndent()
        val prefs = createMockPrefs(mapOf("backup_records" to storedJson))
        val context = DummyContext(tempFolder.root, prefs)

        val records = backupRecords(context)
        assertEquals(2, records.size)
        assertEquals("unindexed_backup.1705000000.pcbk", records[0].backupName)
        assertEquals(1705000000L, records[0].createdAt)
        assertEquals("pantrycraft_backup.1700000000.pcbk", records[1].backupName)
        assertEquals("First Backup", records[1].createdAtLabel)
    }

    @Test
    fun backupRecordsHandlesCorruptedPreferencesJsonGracefully() {
        val backupsDir = File(tempFolder.root, "backups").apply { mkdirs() }
        File(backupsDir, "fallback.1650000000.pcbk").writeText("db")

        val prefs = createMockPrefs(mapOf("backup_records" to "{ corrupt json"))
        val context = DummyContext(tempFolder.root, prefs)

        val records = backupRecords(context)
        assertEquals(1, records.size)
        assertEquals("fallback.1650000000.pcbk", records[0].backupName)
    }

    private fun createMockPrefs(map: Map<String, Any?>): SharedPreferences =
        Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getString" -> map[args[0]] as? String ?: args[1]
                else -> null
            }
        } as SharedPreferences

    private class DummyContext(
        private val baseFilesDir: File,
        private val prefs: SharedPreferences,
    ) : ContextWrapper(null) {
        override fun getFilesDir(): File = baseFilesDir

        override fun getSharedPreferences(
            name: String,
            mode: Int,
        ): SharedPreferences = prefs
    }
}
