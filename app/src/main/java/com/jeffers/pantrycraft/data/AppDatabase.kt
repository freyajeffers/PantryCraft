package com.jeffers.pantrycraft.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.util.Arrays

@Database(
    entities = [PantryItem::class, GroceryItem::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pantryDao(): PantryDao

    abstract fun groceryDao(): GroceryDao

    companion object {
        init {
            System.loadLibrary("sqlcipher")
        }

        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE pantry_items ADD COLUMN storageLocation TEXT NOT NULL DEFAULT 'PANTRY'")
                    db.execSQL("ALTER TABLE pantry_items ADD COLUMN category TEXT NOT NULL DEFAULT 'OTHER'")
                    db.execSQL("ALTER TABLE pantry_items ADD COLUMN expirationTimestamp INTEGER DEFAULT NULL")
                    db.execSQL("ALTER TABLE pantry_items ADD COLUMN isOpened INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE pantry_items ADD COLUMN openedTimestamp INTEGER DEFAULT NULL")
                    db.execSQL("ALTER TABLE pantry_items ADD COLUMN isAromaticOrSeasoning INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE pantry_items ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE pantry_items ADD COLUMN notes TEXT DEFAULT NULL")
                    db.execSQL("ALTER TABLE pantry_items ADD COLUMN normalized_name TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE pantry_items ADD COLUMN custom_unit_name TEXT DEFAULT NULL")
                    db.execSQL("ALTER TABLE pantry_items ADD COLUMN purchase_timestamp INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE pantry_items ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE pantry_items ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
                    db.execSQL(
                        "UPDATE pantry_items SET normalized_name = lower(trim(name)), " +
                            "purchase_timestamp = lastUpdated, created_at = lastUpdated, " +
                            "updated_at = lastUpdated",
                    )
                    db.execSQL(
                        "CREATE TABLE pantry_items_new (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "name TEXT NOT NULL, quantity REAL NOT NULL, unit TEXT, " +
                            "threshold REAL NOT NULL, barcode TEXT, lastUpdated INTEGER NOT NULL, " +
                            "normalized_name TEXT NOT NULL, custom_unit_name TEXT, " +
                            "purchase_timestamp INTEGER NOT NULL, storageLocation TEXT NOT NULL, " +
                            "category TEXT NOT NULL, expirationTimestamp INTEGER, " +
                            "isOpened INTEGER NOT NULL, openedTimestamp INTEGER, " +
                            "isAromaticOrSeasoning INTEGER NOT NULL, isDeleted INTEGER NOT NULL, " +
                            "notes TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)",
                    )
                    db.execSQL(
                        "INSERT INTO pantry_items_new SELECT id, name, quantity, unit, threshold, barcode, " +
                            "lastUpdated, normalized_name, custom_unit_name, purchase_timestamp, " +
                            "storageLocation, category, expirationTimestamp, isOpened, openedTimestamp, " +
                            "isAromaticOrSeasoning, isDeleted, notes, created_at, updated_at " +
                            "FROM pantry_items",
                    )
                    db.execSQL("DROP TABLE pantry_items")
                    db.execSQL("ALTER TABLE pantry_items_new RENAME TO pantry_items")
                    db.execSQL("CREATE INDEX index_pantry_items_storageLocation ON pantry_items(storageLocation)")
                    db.execSQL("CREATE INDEX index_pantry_items_normalized_name ON pantry_items(normalized_name)")
                    db.execSQL("CREATE INDEX index_pantry_items_expirationTimestamp ON pantry_items(expirationTimestamp)")
                    db.execSQL("CREATE UNIQUE INDEX index_pantry_items_barcode ON pantry_items(barcode)")
                    db.execSQL("CREATE INDEX index_pantry_items_isDeleted ON pantry_items(isDeleted)")
                }
            }

        internal fun configureSearchIndex(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS pantry_items_fts " +
                    "USING fts5(name, notes, category, content='pantry_items', content_rowid='rowid')",
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS pantry_items_fts_ai AFTER INSERT ON pantry_items " +
                    "WHEN new.isDeleted = 0 " +
                    "BEGIN INSERT INTO pantry_items_fts(rowid, name, notes, category) " +
                    "VALUES (new.rowid, new.name, new.notes, new.category); END",
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS pantry_items_fts_ad AFTER DELETE ON pantry_items " +
                    "WHEN old.isDeleted = 0 " +
                    "BEGIN INSERT INTO pantry_items_fts(pantry_items_fts, rowid, name, notes, category) " +
                    "VALUES ('delete', old.rowid, old.name, old.notes, old.category); END",
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS pantry_items_fts_au AFTER UPDATE ON pantry_items " +
                    "BEGIN " +
                    "INSERT INTO pantry_items_fts(pantry_items_fts, rowid, name, notes, category) " +
                    "SELECT 'delete', old.rowid, old.name, old.notes, old.category WHERE old.isDeleted = 0; " +
                    "INSERT INTO pantry_items_fts(rowid, name, notes, category) " +
                    "SELECT new.rowid, new.name, new.notes, new.category WHERE new.isDeleted = 0; END",
            )
            db.execSQL("INSERT INTO pantry_items_fts(pantry_items_fts) VALUES ('delete-all')")
            db.execSQL(
                "INSERT INTO pantry_items_fts(rowid, name, notes, category) " +
                    "SELECT id, name, notes, category FROM pantry_items WHERE isDeleted = 0",
            )
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun resetEncryptedDatabase(context: Context) {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
                context.applicationContext.deleteDatabase("pantry_grocery_encrypted.db")
                DatabaseKeyProvider.reset(context)
            }
        }

        fun getDatabase(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                val passphrase = DatabaseKeyProvider.getKey(context)
                val factory = SupportOpenHelperFactory(passphrase.copyOf())
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
                            override fun onCreate(db: SupportSQLiteDatabase) {
                                super.onCreate(db)
                                configureSearchIndex(db)
                            }

                            override fun onOpen(db: SupportSQLiteDatabase) {
                                configureSearchIndex(db)
                                db.query("PRAGMA synchronous = NORMAL").close()
                                db.query("PRAGMA foreign_keys = ON").close()
                                db.query("PRAGMA temp_store = MEMORY").close()
                                db.query("PRAGMA busy_timeout = 5000").close()
                                db.query("PRAGMA mmap_size = 268435456").close()
                            }
                        },
                    ).addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
