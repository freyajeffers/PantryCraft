package com.jeffers.pantrycraft.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRecordsTest {
    @Test
    fun backupRecordDataClassHoldsExpectedFields() {
        val record = DatabaseBackupManager.BackupRecord(
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
        val jsonArray = JSONArray().apply {
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

        val records = (0 until jsonArray.length()).map { index ->
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
}
