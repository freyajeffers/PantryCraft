package com.jeffers.pantrycraft.feature.camera.barcode

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AssetBarcodeDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val targetDbFile = File(context.noBackupFilesDir, "foodfacts.db")
    private val backupFile = File(context.noBackupFilesDir, "foodfacts.db.bak")

    @Before
    fun setUp() {
        if (targetDbFile.exists()) {
            targetDbFile.renameTo(backupFile)
        }
    }

    @After
    fun tearDown() {
        if (targetDbFile.exists()) {
            targetDbFile.delete()
        }
        if (backupFile.exists()) {
            backupFile.renameTo(targetDbFile)
        }
    }

    @Test
    fun throwsWhenCatalogNotInstalled() {
        val error =
            assertThrows(IllegalStateException::class.java) {
                AssetBarcodeDatabase(context)
            }
        assertTrue(error.message?.contains("Offline food catalog is not installed") == true)
    }

    @Test
    fun queriesProductWhenDatabaseInstalled() {
        setupTestFoodFactsDatabase(targetDbFile)

        AssetBarcodeDatabase(context).use { db ->
            val match = db.findByBarcode("012345678901")
            assertNotNull(match)
            assertEquals("012345678901", match?.barcode)
            assertEquals("Steel Cut Oats", match?.name)
            assertEquals("Pantry Best", match?.brand)
            assertEquals("Grains", match?.category)
            assertEquals(365, match?.shelfLifeDays)

            val unknown = db.findByBarcode("000000000000")
            assertNull(unknown)
        }
    }

    private fun setupTestFoodFactsDatabase(file: File) {
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db.execSQL(
            """
            CREATE TABLE products (
                barcode TEXT PRIMARY KEY,
                name TEXT,
                brand TEXT,
                category TEXT,
                shelf_life_days INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO products (barcode, name, brand, category, shelf_life_days)
            VALUES ('012345678901', 'Steel Cut Oats', 'Pantry Best', 'Grains', 365)
            """.trimIndent(),
        )
        db.close()
    }
}
