# PantryCraft: Deep-Dive Skills for Phases 1 & 2

* * *

## Domain Playbooks for Persistence Hardening & Multimodal Vision

* * *

**Phase 1: Persistence & Ledger** | **Phase 2: Vision & Barcodes**

---

## Skill 1: room-schema-migration-guard (Phase 1)

* * *

**Focus:** Zero-data-loss schema migrations, `MigrationTestHelper` validation, FTS5 triggers with tombstone guards, and SQLCipher schema compatibility.

### Operational Context & Problem Statement

Android applications with local SQLite databases often crash or wipe user data when evolving schemas if migrations are poorly defined. This skill guides the agent through constructing rock-solid, verified Room migrations (specifically transitioning `pantry_items` from v1 to v2) while preserving data integrity.

### Core Invariants & Rules

* **Strictly No Destructive Fallbacks:** `fallbackToDestructiveMigration()` is strictly prohibited in production builds.
* **Verification with MigrationTestHelper:** Every migration must be accompanied by an automated JUnit test utilizing `androidx.room.testing.MigrationTestHelper`. The test must insert dummy data into v1, execute the migration to v2, and assert that all columns, default values, and data records persist correctly.
* **Schema Evolution Definition (v1 to v2):**

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
       
       // Setup FTS5 Virtual Table and Triggers with Tombstone & Corruption Guards
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

---

## Skill 2: compose-high-perf-ledger (Phase 1)

* * *

**Focus:** Delivering fluid 120 FPS `LazyColumn` scrolling, layout phase skipping, stable keys, and swipe-to-dismiss gesture physics.

### Core Invariants & Rules

* **Stable Keys Invariant:** Every `items()` block in `LazyColumn` must provide a persistent, unique key: `items(items = state.items, key = { it.id })`. Never use index as key.
* **Derived State & Query Debouncing:** Filter pills and search queries must be combined using Kotlin Flow operators (`debounce(50)`, `distinctUntilChanged()`) or Compose `derivedStateOf` to prevent re-filtering on every single frame.
* **Skip Recomposition via Lambdas:** Pass method references or lambda callbacks (e.g. `onItemClick: (Long) -> Unit`) rather than passing entire ViewModels into child composables. Mark UI model wrappers with `@Immutable` or `@Stable`.
* **Custom Swipe Physics:** Implement swipe-to-delete and swipe-to-decrement using `SwipeToDismissBox` with tactile haptic feedback (`HapticFeedbackType.LongPress`) when thresholds are crossed.

---

## Skill 3: offline-barcode-inventory-resolver (Phase 2)

* * *

**Focus:** Sub-10ms UPC/EAN barcode resolution against an embedded offline SQLite food database, scanning debouncing, and haptic feedback.

### Core Invariants & Rules

* **Embedded Offline Cache:** Ship a lightweight, pre-indexed SQLite asset database containing top 50,000 common UPC-A, EAN-13, and EAN-8 grocery barcodes. Sub-10ms read latency via indexed queries.
* **Custom Barcode Association Learning:** When a barcode is not present in the 50k embedded cache, prompt the user with a 1-tap sheet to name the item once; write the association to a local `custom_barcode_cache` table so subsequent scans resolve offline in <10ms.
* **Scan Debounce Window:** Implement a temporal debouncer: when a barcode is recognized, ignore subsequent detections of the same raw barcode for 2,500ms to prevent duplicate modal popups.
* **Tactile Confirmation:** Trigger a subtle vibration tick (`VibrationEffect.createPredefined(EFFECT_TICK)`) the exact millisecond a barcode is validated.

---

## Skill 4: multimodal-shelf-vlm-parser (Phase 2)

* * *

**Focus:** High-resolution shelf decomposition, spatial bounding coordinate normalization, bitmap downsampling memory pooling, and JSON schema repair.

### Core Invariants & Rules

* **In-Memory Image Downsampling:** Downscale captured still frames to 1024x1024 or 1536x1536 WebP (quality 85) directly in volatile byte buffers. Peak bitmap memory allocation must remain under 32 MB.
* **Zero Disk Residue:** Wipe in-memory byte buffers immediately following model inference. No photos may leak to MediaStore or public storage.
* **Spatial Bounding Box Normalization:** Prompt the model to return coordinates normalized from 0.0 to 1.0 (`[ymin, xmin, ymax, xmax]`). This enables the UI to render interactive bounding box tap targets over the preview photo during batch review.
* **Robust JSON Repair Engine:** If an LLM emits formatting flaws (markdown backticks, trailing commas, or truncated closing brackets), route the raw response through a local JSON sanitizer before failing.
