package com.jeffers.pantrycraft.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val helper
        get() =
            MigrationTestHelper(
                InstrumentationRegistry.getInstrumentation(),
                AppDatabase::class.java,
                emptyList(),
                SupportOpenHelperFactory(
                    DatabaseKeyProvider.getKey(ApplicationProvider.getApplicationContext()),
                ),
            )

    @Test
    fun migrate1To2_preservesExistingDataAndAddsInventoryFields() {
        helper.createDatabase("migration-test", 1).apply {
            execSQL(
                "INSERT INTO pantry_items " +
                    "(id, name, quantity, unit, threshold, barcode, lastUpdated) " +
                    "VALUES (1, 'Milk', 1.0, 'GALLON', 0.2, '12345', 1000)",
            )
            close()
        }

        val db =
            helper.runMigrationsAndValidate(
                "migration-test",
                2,
                true,
                AppDatabase.MIGRATION_1_2,
            )
        db.query("SELECT * FROM pantry_items WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Milk", cursor.getString(cursor.getColumnIndexOrThrow("name")))
            assertEquals("PANTRY", cursor.getString(cursor.getColumnIndexOrThrow("storageLocation")))
            assertEquals("OTHER", cursor.getString(cursor.getColumnIndexOrThrow("category")))
            assertEquals("milk", cursor.getString(cursor.getColumnIndexOrThrow("normalized_name")))
            assertEquals(1000, cursor.getLong(cursor.getColumnIndexOrThrow("purchase_timestamp")))
            assertEquals(1000, cursor.getLong(cursor.getColumnIndexOrThrow("created_at")))
            assertEquals(1000, cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("isOpened")))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("isAromaticOrSeasoning")))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("isDeleted")))
        }
        db.close()
    }
}
