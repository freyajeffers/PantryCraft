# PantryCraft: Scaffolding Alignment & Reconciliation Plan

* * *

## Reconciling the Existing Prototype (com.freya.pantrygrocery) with Target Architecture (com.jeffers.pantrycraft)

* * *

**Non-Degradation & Preservation Rule:** The existing working prototype contains established build scripts, Gradle configurations, Room entities, and Jetpack Compose navigation. This reconciliation plan preserves working code and defines exact, non-destructive migration bridges to layer in privacy hardening, multimodal AI ingestion, and culinary synthesis without discarding functional assets.

### 1. Existing Codebase Audit & Scaffolding Baseline

| Layer / File | Current State in Scaffolding | Architectural Assessment |
| :--- | :--- | :--- |
| **Application ID & Package** | `com.freya.pantrygrocery` | Clean single-module structure under `app/`. Refactor to target package `com.jeffers.pantrycraft` via clean refactoring runbook. |
| **Room Database & Schema** | `AppDatabase` (v1, unencrypted), `PantryItem`, `GroceryItem` | Functional relational baseline. `PantryItem` already has `id: Long`, `name`, `quantity`, `unit`, `threshold`, `barcode`, and `lastUpdated`. `GroceryItem` already has `fromPantryId: Long?`. |
| **Data Access (DAOs)** | `PantryDao`, `GroceryDao` | Uses `suspend fun getAllPantryItems(): List<PantryItem>` requiring manual refreshes. Has `getLowStock()` and `deleteChecked()`. |
| **Repository & State** | `Repository` singleton, `PantryViewModel`, `GroceryViewModel` | Manual `refresh()` triggers updating internal `MutableStateFlow`. |
| **Presentation / Navigation** | `AppNavigator`, `HomeScreen`, `PantryScreen`, `GroceryScreen` | Jetpack Compose with Material 3. Working navigation between Home, Pantry, and Grocery screens. |

### 2. Required Scaffolding Changes & Alignment Bridges

#### A. Phase 1 Alignment: Database Schema Migration & Reactive Streams

* **Keep:** Retain existing table names (`pantry_items`, `grocery_items`) and primary key `id: Long`.
* **Modify (Room Migration v1 -> v2):**  
  Add missing inventory dimensions to `PantryItem`:
  * `storageLocation: String = "PANTRY"` (distinguishing Pantry vs. Fridge vs. Freezer vs. Counter)
  * `category: String = "OTHER"` (Produce, Dairy, Meat, Staple, Frozen, etc.)
  * `expirationTimestamp: Long? = null` (perishability tracking)
  * `isOpened: Boolean = false` (opened shelf-life calculation)
  * `openedTimestamp: Long? = null`
  * `isAromaticOrSeasoning: Boolean = false` (programmatic culinary power scaling flag)
  * `isDeleted: Boolean = false` (tombstone for synchronization and undo)
  * `notes: String? = null`

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

* **Upgrade DAOs to Reactive Flows:** Change `suspend fun getAllPantryItems(): List<PantryItem>` to return `Flow<List<PantryItem>>` filtering `WHERE isDeleted = 0`.
* **Security Hardening:** Configure `AppDatabase` with SQLite Write-Ahead Logging (`setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)`) and integrate SQLCipher encryption with `SupportFactory` using an AES-GCM KeyStore encrypted passphrase.

#### B. Phase 2 Alignment: CameraX & Barcode Integration
* **Existing Hook:** `PantryItem` already defines `val barcode: String? = null`.
* **Integration:**
  * Add CameraX (`camera-camera2`, `camera-lifecycle`, `camera-view`) and Google ML Kit Barcode dependencies to `app/build.gradle`.
  * Add a Camera scanner button to `PantryScreen` top app bar or Floating Action Button (FAB).
  * When a barcode is detected, query the offline database or pre-fill the existing `PantryItem.barcode` field.
  * Add new composable screen: `CameraScanScreen` in `AppNavigator` routing.

#### C. Phase 3 Alignment: Portion-Aware Meal Synthesis Integration
* **Navigation Hook:** Add an action button on `HomeScreen`: "Generate Meals / AI Chef" (navigating to "meals" route in `AppNavigator`).
* **Repository Hook:** Add `suspend fun getAvailableIngredientsForMeals()` to `Repository`, leveraging existing `getAllPantryItems()`.
* **Low-Stock Optimization:** Leverage existing `pantryDao.getLowStock()` to automatically mark depleted or low ingredients in meal prompts.

#### D. Phase 4 Alignment: Real-Time Multimodal Voice Ingestion
* **UI Hook:** Add a microphone push-to-talk FAB on `PantryScreen` and hands-free assistant toggle in `MealRecipeScreen`.
* **Repository Hook:** Parsed voice entities map directly into existing `PantryItem` models and commit via existing `Repository.insertPantry()`.

#### E. Phase 5 Alignment: Stock Lifecycle & Grocery Replenishment
* **Existing Hook:** `GroceryItem` already has `val fromPantryId: Long? = null`!
* **Alignment:**
  * When a recipe is cooked, decrement `PantryItem.quantity`. If `quantity <= threshold`, automatically insert a `GroceryItem(name=item.name, fromPantryId=item.id)`.
  * When a user checks off an item in `GroceryScreen`, offer an automated replenish action that restores stock to `PantryItem`.

### 3. Summary of Dependency & Build Adjustments

| Module | Dependency Addition in `app/build.gradle` | Purpose |
| :--- | :--- | :--- |
| **Security** | `net.zetetic:sqlcipher-android:4.5.4`, `androidx.security:security-crypto:1.1.0-alpha06` | Hardware KeyStore encryption at rest. |
| **Vision** | `androidx.camera:camera-camera2:1.4.0`, `androidx.camera:camera-lifecycle:1.4.0`, `androidx.camera:camera-view:1.4.0`, `com.google.mlkit:barcode-scanning:17.3.0` | CameraX viewfinder, 15 FPS frame stream, barcode scanning. |
| **Voice** | Sherpa-ONNX JNI native runtime (or Android offline SpeechRecognizer) | 100% offline streaming STT. |
| **Network / AI** | `io.ktor:ktor-client-android:2.3.12`, `io.ktor:ktor-client-content-negotiation:2.3.12`, `io.ktor:ktor-serialization-kotlinx-json:2.3.12` | BYOKey cloud model streaming (OpenAI, Claude, Gemini, Ollama). |
