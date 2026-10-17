---
name: room-schema-migration-guard
description: Safely migrate Room database schemas with automated MigrationTestHelper tests, SQLite table recreation, and zero data loss.
---

# Room Schema Migration Guard

Engineering protocols for executing deterministic, non-destructive Room schema evolutions on Android.

## When to Use
- Upgrading Room database versions (e.g. v1 to v2 in `AppDatabase`).
- Adding columns, indexes, or new relational entities to existing user installations.
- Writing migration unit tests to ensure zero data loss.

## Core Architectural Invariants
1. **Destructive Fallback Ban:** `fallbackToDestructiveMigration()` is strictly prohibited in production. Schema changes must never wipe user inventory.
2. **Explicit Migration Objects:** Every version bump requires an explicit `Migration(startVersion, endVersion)` defining exact SQL DDL statements.
3. **MigrationTestHelper Mandatory:** Every migration must be tested using `androidx.room.testing.MigrationTestHelper` asserting data persistence and column consistency before PR merge.
4. **FTS5 Trigger Tombstone Guards:** When updating FTS5 triggers, never delete rowids from FTS that are not present in the index (`old.isDeleted == 0`), which corrupts the FTS tree.

## Implementation Pattern (v1 to v2 Migration)
```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE pantry_items ADD COLUMN storageLocation TEXT NOT NULL DEFAULT 'PANTRY'")
        db.execSQL("ALTER TABLE pantry_items ADD COLUMN category TEXT NOT NULL DEFAULT 'OTHER'")
        db.execSQL("ALTER TABLE pantry_items ADD COLUMN expirationTimestamp INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE pantry_items ADD COLUMN isOpened INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE pantry_items ADD COLUMN openedTimestamp INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE pantry_items ADD COLUMN isAromaticOrSeasoning INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE pantry_items ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE pantry_items ADD COLUMN notes TEXT DEFAULT NULL")
        
        db.execSQL("CREATE INDEX IF NOT EXISTS index_pantry_items_location ON pantry_items(storageLocation)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_pantry_items_expiration ON pantry_items(expirationTimestamp)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_pantry_items_deleted ON pantry_items(isDeleted)")
        
        // Setup FTS5 Virtual Table and Triggers with Tombstone Filtering
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS pantry_items_fts USING fts5(name, category, notes, content='pantry_items', content_rowid='id')")
        
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS pantry_items_ai AFTER INSERT ON pantry_items 
            WHEN new.isDeleted = 0
            BEGIN
                INSERT INTO pantry_items_fts(rowid, name, category, notes) VALUES (new.id, new.name, new.category, new.notes);
            END;
        """)
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS pantry_items_ad AFTER DELETE ON pantry_items 
            WHEN old.isDeleted = 0
            BEGIN
                INSERT INTO pantry_items_fts(pantry_items_fts, rowid, name, category, notes) VALUES('delete', old.id, old.name, old.category, old.notes);
            END;
        """)
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS pantry_items_au AFTER UPDATE ON pantry_items BEGIN
                INSERT INTO pantry_items_fts(pantry_items_fts, rowid, name, category, notes) 
                SELECT 'delete', old.id, old.name, old.category, old.notes WHERE old.isDeleted = 0;
                
                INSERT INTO pantry_items_fts(rowid, name, category, notes) 
                SELECT new.id, new.name, new.category, new.notes WHERE new.isDeleted = 0;
            END;
        """)
    }
}
```

## Migration Test Suite (JUnit 4)
```kotlin
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate1To2_preservesExistingData() {
        helper.createDatabase("test_db", 1).apply {
            execSQL("INSERT INTO pantry_items (id, name, quantity, unit, threshold, barcode, lastUpdated) VALUES (1, 'Milk', 1.0, 'GALLON', 0.2, '12345', 1000)")
            close()
        }

        val db = helper.runMigrationsAndValidate("test_db", 2, true, MIGRATION_1_2)
        val cursor = db.query("SELECT * FROM pantry_items WHERE id = 1")
        assertTrue(cursor.moveToFirst())
        assertEquals("Milk", cursor.getString(cursor.getColumnIndexOrThrow("name")))
        assertEquals("PANTRY", cursor.getString(cursor.getColumnIndexOrThrow("storageLocation")))
        assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("isAromaticOrSeasoning")))
        assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("isDeleted")))
        cursor.close()
    }
}
```
