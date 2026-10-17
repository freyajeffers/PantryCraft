# PantryCraft: Phase 1 Implementation Plan

* * *

## Foundation, Local-First Encrypted Architecture & Inventory Engine

* * *

**Phase 1** | **Foundational Milestone** | **100% Offline Persistence**

### 1. Phase Scope & Objectives

Phase 1 establishes the bedrock of PantryCraft: a zero-compromise, local-first persistence layer, security foundation, and minimalist inventory management user interface. The primary objective is to deliver an ultra-fast, encrypted SQLite database using Room and SQLCipher, fully integrated with Android KeyStore, supporting instant search via SQLite FTS5, and exposed to a reactive Jetpack Compose presentation layer.

By the end of Phase 1, users will be able to launch the app instantly without network access, create, read, update, categorize, soft-delete, and restore kitchen inventory items across Pantry, Fridge, Freezer, and Counter locations, and experience 60/120 FPS scrolling with zero network roundtrips.

### 2. Architectural Components & Responsibilities

| Component | Package / Namespace | Responsibilities |
| :--- | :--- | :--- |
| **SecurityKeyManager** | `core.security` | Manages Android KeyStore AES-256-GCM wrapping keys (StrongBox on API 28+ / TEE on API 23+); encrypts and decrypts a random 256-bit SQLCipher database passphrase. Catches `KeyPermanentlyInvalidatedException` and coordinates user recovery. |
| **AppDatabase** | `core.database` | Room database instance configured with SQLCipher OpenHelper (`SupportFactory`), WAL mode, MMAP, and connection pooling. |
| **PantryItem & FTS** | `core.database.entity` | Relational schema for inventory records (`pantry_items`) with tombstone support (`isDeleted`) and associated FTS5 full-text indexing table (`pantry_items_fts`) for sub-5ms prefix search. |
| **InventoryRepository** | `domain.repository` | Exposes reactive Kotlin `Flow<List<PantryItem>>`, enforces data validation, and manages atomic transactions. |
| **PantryViewModel** | `feature.inventory` | Manages inventory UI state, location filtering (Pantry/Fridge/Freezer/Counter), search queries, and sorting. |
| **InventoryLedgerScreen** | `feature.inventory.ui` | Minimalist Jetpack Compose ledger displaying stock items with expiration indicators and quick-action swipe gestures. |

### 3. Inter-Module Data Contracts & Schemas

#### A. Inventory Database Schema (Room)

```sql
-- Relational Table: pantry_items
CREATE TABLE IF NOT EXISTS pantry_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    name TEXT NOT NULL,
    quantity REAL NOT NULL DEFAULT 1.0,
    unit TEXT NOT NULL DEFAULT 'COUNT',
    storageLocation TEXT NOT NULL DEFAULT 'PANTRY', -- 'PANTRY', 'FRIDGE', 'FREEZER', 'COUNTER'
    category TEXT NOT NULL DEFAULT 'OTHER', -- 'PRODUCE', 'DAIRY', 'MEAT', 'BAKERY', 'PANTRY_STAPLE', 'FROZEN', 'SPICE', 'BEVERAGE', 'OTHER'
    threshold REAL NOT NULL DEFAULT 0.0,
    expirationTimestamp INTEGER DEFAULT NULL, -- epoch ms
    isOpened INTEGER NOT NULL DEFAULT 0, -- Boolean 0/1
    openedTimestamp INTEGER DEFAULT NULL, -- epoch ms
    isAromaticOrSeasoning INTEGER NOT NULL DEFAULT 0, -- Boolean 0/1: triggers 0.75 power scaling
    isDeleted INTEGER NOT NULL DEFAULT 0, -- Tombstone for decentralized sync and undo
    notes TEXT DEFAULT NULL,
    barcode TEXT DEFAULT NULL,
    lastUpdated INTEGER NOT NULL -- epoch ms
);

CREATE INDEX IF NOT EXISTS index_pantry_items_location ON pantry_items(storageLocation);
CREATE INDEX IF NOT EXISTS index_pantry_items_expiration ON pantry_items(expirationTimestamp);
CREATE INDEX IF NOT EXISTS index_pantry_items_barcode ON pantry_items(barcode);
CREATE INDEX IF NOT EXISTS index_pantry_items_deleted ON pantry_items(isDeleted);

-- Virtual Table: pantry_items_fts (FTS5)
CREATE VIRTUAL TABLE IF NOT EXISTS pantry_items_fts USING fts5(
    name,
    category,
    notes,
    content='pantry_items',
    content_rowid='id'
);

-- FTS Synchronization Triggers with Tombstone & Corruption Guards
CREATE TRIGGER IF NOT EXISTS pantry_items_ai AFTER INSERT ON pantry_items 
WHEN new.isDeleted = 0
BEGIN
    INSERT INTO pantry_items_fts(rowid, name, category, notes) 
    VALUES (new.id, new.name, new.category, new.notes);
END;

CREATE TRIGGER IF NOT EXISTS pantry_items_ad AFTER DELETE ON pantry_items 
WHEN old.isDeleted = 0
BEGIN
    INSERT INTO pantry_items_fts(pantry_items_fts, rowid, name, category, notes) 
    VALUES('delete', old.id, old.name, old.category, old.notes);
END;

CREATE TRIGGER IF NOT EXISTS pantry_items_au AFTER UPDATE ON pantry_items BEGIN
    INSERT INTO pantry_items_fts(pantry_items_fts, rowid, name, category, notes) 
    SELECT 'delete', old.id, old.name, old.category, old.notes WHERE old.isDeleted = 0;
    
    INSERT INTO pantry_items_fts(rowid, name, category, notes) 
    SELECT new.id, new.name, new.category, new.notes WHERE new.isDeleted = 0;
END;
```

#### B. SQLite Concurrency & Storage Hardening Directives

Room database configuration:
```kotlin
Room.databaseBuilder(context, AppDatabase::class.java, "pantry_encrypted.db")
    .openHelperFactory(SupportFactory(passphraseBytes))
    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
    .addCallback(object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            db.execSQL("PRAGMA synchronous = NORMAL;")
            db.execSQL("PRAGMA busy_timeout = 5000;")
            db.execSQL("PRAGMA mmap_size = 268435456;") // 256MB MMAP
            db.execSQL("PRAGMA foreign_keys = ON;")
            db.execSQL("PRAGMA temp_store = MEMORY;")
        }
    })
    .build()
```

### 4. UI Architecture & Minimalist User Experience

* **Top App Bar:** Fast search input with instantaneous FTS5 debounce (50ms). Instant filter pills: "All", "Fridge", "Pantry", "Freezer", "Expiring Soon".
* **Inventory Item Card:** Clean typography displaying item name, quantity pill, expiration badge (Color-coded: Green > 5 days, Amber 2-4 days, Red < 48 hours).
* **Swipe Actions:** Swipe right to decrement quantity (1 tap use); swipe left to soft-delete (`isDeleted = 1`) with an instant Snackbar undo action using `SwipeToDismissBox`.
* **Manual Add Sheet:** Lightweight bottom sheet for manual entry with auto-suggest categories and default shelf-life presets based on category.

### 5. Failure Modes & Edge Case Handling

| Failure Scenario | Handling & Recovery Protocol |
| :--- | :--- |
| **KeyPermanentlyInvalidatedException (Biometrics/PIN Reset)** | Catch exception during passphrase decryption. Transition to `SecurityRecoveryState`. Present two user recovery options: (1) Restore database from latest encrypted backup archive using user PIN, or (2) Re-initialize database with a fresh KeyStore key upon explicit user confirmation, avoiding app crash loops. |
| **Database disk exhaustion** | Catch `SQLiteFullException`, prune temporary cached thumbnails in `context.cacheDir/vision_scratch/`, and alert user with storage diagnostic dialog. |
| **Corrupted FTS index** | Automatic rebuild trigger on startup: `INSERT INTO pantry_items_fts(pantry_items_fts) VALUES('rebuild');` to guarantee search index integrity. |

### 6. Acceptance & Verification Gates

* **Unit Test Coverage:** 100% coverage on `InventoryRepository`, `PantryDao`, and FTS5 search queries (verifying soft-deleted items are excluded).
* **Cold Startup SLA:** App cold launch to fully rendered interactive inventory ledger under 350ms on mid-tier test hardware (Snapdragon 7-series or equivalent).
* **Search Latency SLA:** Sub-5ms query response time across a synthetic benchmark database containing 5,000 inventory items.
* **Security Verification:** Automated check verifying that the SQLite database file cannot be opened without the Hardware Keystore passphrase.
