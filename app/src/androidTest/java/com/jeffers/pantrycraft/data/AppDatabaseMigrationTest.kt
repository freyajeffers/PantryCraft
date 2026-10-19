package com.jeffers.pantrycraft.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import android.content.Context
import org.junit.After
import org.junit.Before
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

    @Before
    @After
    fun cleanupDatabases() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("migration-test")
        context.deleteDatabase("migration-test-2-3")
        context.deleteDatabase("migration-test-1-3")
    }


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

    @Test
    fun migrate2To3_createsCustomBarcodeCacheTable() {
        helper.createDatabase("migration-test-2-3", 2).apply {
            close()
        }

        val db =
            helper.runMigrationsAndValidate(
                "migration-test-2-3",
                3,
                true,
                AppDatabase.MIGRATION_2_3,
            )
        db.execSQL(
            "INSERT INTO custom_barcode_cache " +
                "(barcode, name, category, defaultUnit, estimatedShelfLifeDays, lastScanned) " +
                "VALUES ('012345678901', 'Test Oats', 'GRAINS', 'COUNT', 180, 1000)",
        )
        db.query("SELECT * FROM custom_barcode_cache WHERE barcode = '012345678901'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Test Oats", cursor.getString(cursor.getColumnIndexOrThrow("name")))
            assertEquals("GRAINS", cursor.getString(cursor.getColumnIndexOrThrow("category")))
            assertEquals(180, cursor.getInt(cursor.getColumnIndexOrThrow("estimatedShelfLifeDays")))
        }
        db.close()
    }

    @Test
    fun migrate1To3_preservesLegacyDataAndCreatesCustomBarcodeCache() {
        helper.createDatabase("migration-test-1-3", 1).apply {
            execSQL(
                "INSERT INTO pantry_items " +
                    "(id, name, quantity, unit, threshold, barcode, lastUpdated) " +
                    "VALUES (7, 'Spinach', 2.0, 'COUNT', 1.0, '987654321012', 2000)",
            )
            close()
        }

        val db =
            helper.runMigrationsAndValidate(
                "migration-test-1-3",
                3,
                true,
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
            )
        db.query("SELECT * FROM pantry_items WHERE id = 7").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Spinach", cursor.getString(cursor.getColumnIndexOrThrow("name")))
            assertEquals("spinach", cursor.getString(cursor.getColumnIndexOrThrow("normalized_name")))
        }
        db.execSQL(
            "INSERT INTO custom_barcode_cache " +
                "(barcode, name, category, defaultUnit, estimatedShelfLifeDays, lastScanned) " +
                "VALUES ('987654321012', 'Baby Spinach', 'PRODUCE', 'COUNT', 5, 3000)",
        )
        db.query("SELECT * FROM custom_barcode_cache WHERE barcode = '987654321012'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Baby Spinach", cursor.getString(cursor.getColumnIndexOrThrow("name")))
        }
        db.close()
    }
}
