package com.jeffers.pantrycraft.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate1To2_preservesExistingDataAndAddsInventoryFields() {
        helper.createDatabase("migration-test", 1).apply {
            execSQL(
                "INSERT INTO pantry_items " +
                    "(id, name, quantity, unit, threshold, barcode, lastUpdated) " +
                    "VALUES (1, 'Milk', 1.0, 'GALLON', 0.2, '12345', 1000)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate("migration-test", 2, true, AppDatabase.MIGRATION_1_2)
        db.query("SELECT * FROM pantry_items WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Milk", cursor.getString(cursor.getColumnIndexOrThrow("name")))
            assertEquals("PANTRY", cursor.getString(cursor.getColumnIndexOrThrow("storageLocation")))
            assertEquals("OTHER", cursor.getString(cursor.getColumnIndexOrThrow("category")))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("isOpened")))
        }
        db.close()
    }
}