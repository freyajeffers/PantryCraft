package com.jeffers.pantrycraft.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.util.Arrays

@Database(entities = [PantryItem::class, GroceryItem::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pantryDao(): PantryDao

    abstract fun groceryDao(): GroceryDao

    companion object {
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE pantry_items ADD COLUMN storageLocation TEXT NOT NULL DEFAULT 'PANTRY'")
                    db.execSQL("ALTER TABLE pantry_items ADD COLUMN category TEXT NOT NULL DEFAULT 'OTHER'")
                    db.execSQL("ALTER TABLE pantry_items ADD COLUMN expirationTimestamp INTEGER DEFAULT NULL")
                    db.execSQL("ALTER TABLE pantry_items ADD COLUMN isOpened INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE pantry_items ADD COLUMN openedTimestamp INTEGER DEFAULT NULL")
                    db.execSQL("ALTER TABLE pantry_items ADD COLUMN notes TEXT DEFAULT NULL")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_pantry_items_location ON pantry_items(storageLocation)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_pantry_items_expiration ON pantry_items(expirationTimestamp)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pantry_items_barcode ON pantry_items(barcode)")
                }
            }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                val passphrase = DatabaseKeyProvider.getKey()
                val factory = SupportOpenHelperFactory(passphrase)
                Arrays.fill(passphrase, 0.toByte())

                INSTANCE ?: Room
                    .databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "pantry_grocery_encrypted.db",
                    ).openHelperFactory(factory)
                    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                    .addCallback(
                        object : RoomDatabase.Callback() {
                            override fun onOpen(db: SupportSQLiteDatabase) {
                                db.execSQL("PRAGMA busy_timeout = 5000")
                                db.execSQL("PRAGMA mmap_size = 268435456")
                            }
                        },
                    ).addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
