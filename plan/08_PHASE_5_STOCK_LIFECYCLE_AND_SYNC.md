# PantryCraft: Phase 5 Implementation Plan

* * *

## Stock Lifecycle, Waste Prevention & Zero-Knowledge Sync

* * *

**Phase 5** | **Lifecycle & Logistics** | **Zero-Signup Synchronization**

### 1. Phase Scope & Objectives

Phase 5 delivers the complete logistical feedback loop for household kitchen management. It connects recipe consumption directly back into inventory levels, automates proactive food waste prevention, streamlines grocery shopping, and introduces optional zero-signup cloud synchronization.

Key outcomes include:

* **One-Tap Stock Depletion:** When a user cooks a recipe, the exact quantities are decremented from `pantry_items` in an atomic Room SQLite transaction, automatically marking exhausted items as depleted or moving them to the grocery list.
* **Unit Dimensional Conversion Matrix:** An automated conversion engine resolves common kitchen unit differences (e.g. tablespoons to grams or cups to ml) and provides a graceful 1-tap fractional deduction bottom sheet for dimensionally incompatible units (e.g. "bunch" of mint vs "grams").
* **Proactive Expiry Prevention:** Android WorkManager periodic background workers calculate perishability curves and dispatch daily local notifications for items expiring within 48 hours without needing remote push servers.
* **Smart Grocery Logistics:** A synchronized grocery list (`grocery_items`) that auto-suggests depleted household staples (`quantity <= threshold`), allows 1-tap addition of missing ingredients from recipe suggestions, and replenishes pantry stock with a single check-off gesture at the grocery store.
* **Zero-Signup Sovereignty & Sync:** Optional automated backup to the user's personal Google Drive AppData hidden folder (`drive.appdata` scope) and peer-to-peer local Wi-Fi synchronization using out-of-band mutual TLS pairing (SAS / QR code) with Last-Write-Wins (LWW) CRDT semantics and tombstone propagation.

### 2. Architectural Components & Responsibilities

| Component | Package / Namespace | Responsibilities |
| :--- | :--- | :--- |
| **StockDepletionEngine** | `feature.inventory.depletion` | Computes inventory deltas post-cooking, handles unit conversions and dimensional fallbacks during deduction, and issues atomic Room database updates. |
| **ExpiryNotificationWorker** | `feature.inventory.notifications` | Android CoroutineWorker triggered every 24 hours (with initial delay targeted to 8:00 AM) to evaluate expiring stock and post localized system notification trays. |
| **GroceryRepository** | `feature.grocery.data` | Manages the `grocery_items` table in Room; handles bi-directional transitions between grocery items and active `pantry_items` stock. |
| **DriveAppDataSyncManager** | `feature.sync.drive` | Performs encrypted delta synchronizations against Google Drive AppData folder using OAuth2 without custom app servers. |
| **P2PMeshSyncManager** | `feature.sync.p2p` | Manages local Wi-Fi mDNS discovery, SAS/QR mutual authentication, and encrypted LWW state replication with tombstone pruning. |
| **LocalBackupExporter** | `feature.sync.export` | Exports and imports password-protected AES-256 encrypted JSON/CSV archives via Android Storage Access Framework (SAF). |

### 3. Data Contracts & Database Schema Additions

#### A. Grocery Checklist Table (Room Schema)

```sql
CREATE TABLE IF NOT EXISTS grocery_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    name TEXT NOT NULL,
    targetLocation TEXT NOT NULL DEFAULT 'PANTRY', -- 'PANTRY', 'FRIDGE', 'FREEZER'
    category TEXT NOT NULL DEFAULT 'OTHER',
    quantity REAL NOT NULL DEFAULT 1.0,
    unit TEXT NOT NULL DEFAULT 'COUNT',
    isChecked INTEGER NOT NULL DEFAULT 0, -- Boolean 0/1
    sourceRecipeId TEXT DEFAULT NULL,
    fromPantryId INTEGER DEFAULT NULL, -- Foreign key reference to pantry_items.id
    isStapleAutoAdded INTEGER NOT NULL DEFAULT 0,
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS index_grocery_items_checked ON grocery_items(isChecked);
CREATE INDEX IF NOT EXISTS index_grocery_items_pantry ON grocery_items(fromPantryId);
```

#### B. Culinary Unit Conversion Fallback Matrix

When recipe requirements are deducted from inventory, the `StockDepletionEngine` applies the following conversion matrix:

| Ingredient Category / Item | Recipe Unit | Stock Unit | Conversion Ratio | Fallback Policy |
| :--- | :--- | :--- | :--- | :--- |
| **Liquids & Oils** | `TBSP` / `TSP` | `MILLILITERS` | $1\text{ tbsp} = 15\text{ ml}$, $1\text{ tsp} = 5\text{ ml}$ | Exact mathematical deduction |
| **Liquids & Broths** | `CUPS` | `MILLILITERS` | $1\text{ cup} = 240\text{ ml}$ | Exact mathematical deduction |
| **Butter & Solid Fats** | `TBSP` / `STICK` | `GRAMS` | $1\text{ stick} = 8\text{ tbsp} = 113.4\text{ g}$ | Exact mathematical deduction |
| **Flour & Dry Grains** | `CUPS` | `GRAMS` | $1\text{ cup flour} \approx 120\text{ g}$, $1\text{ cup rice} \approx 185\text{ g}$ | Culinary density table |
| **Sugar & Sweeteners** | `CUPS` / `TBSP` | `GRAMS` | $1\text{ cup} \approx 200\text{ g}$, $1\text{ tbsp} \approx 12.5\text{ g}$ | Culinary density table |
| **Aromatics (Garlic)** | `CLOVE` | `COUNT` | $1\text{ clove} = 1\text{ count}$ | Exact count match |
| **Incompatible Dimensions** | `GRAMS` / `TBSP` | `BUNCH` / `CAN` | Undefined density | Present 1-tap fractional modal: *[25%] [50%] [100%] [Custom]* |

#### C. Stock Depletion & Transition Transaction Contract

```text
User taps "Cook Recipe"  
      │  
      ▼  
[ StockDepletionEngine ]  
 ├── 1. Validate on-hand quantities against recipe requirements.  
 │     ├── Apply unit conversion matrix (e.g., TBSP -> ml, butter sticks -> grams).  
 │     └── If units dimensionally incompatible (e.g., 10g mint vs 1 BUNCH), present fractional selection modal.  
 ├── 2. Begin Room Transaction (@Transaction).  
 │     ├── Decrement pantry_items.quantity.  
 │     ├── Set pantry_items.lastUpdated = System.currentTimeMillis().  
 │     ├── If quantity <= threshold:  
 │     │     ├── Insert into grocery_items (isChecked = 0, fromPantryId = item.id, isStapleAutoAdded = 1).  
 │     │     └── If quantity <= 0: Set isDeleted = 1 (soft delete tombstone).  
 │     └── Insert into meals_history (recipe_id, cooked_at, portions_cooked).  
 └── 3. Commit Transaction.  
```

### 4. Background Workers & WorkManager Timing

`ExpiryNotificationWorker` is registered with Android WorkManager:
```kotlin
val now = Calendar.getInstance()
val target = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 8)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
}
val initialDelay = target.timeInMillis - now.timeInMillis

val expiryWork = PeriodicWorkRequestBuilder<ExpiryNotificationWorker>(24, TimeUnit.HOURS)
    .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
    .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
    .build()

WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    "pantrycraft_expiry_notifications",
    ExistingPeriodicWorkPolicy.UPDATE,
    expiryWork
)
```
*Note: Under Android Doze mode, the 24-hour periodic work executes in the system maintenance window nearest to 8:00 AM, avoiding battery-draining exact alarm permissions (`SCHEDULE_EXACT_ALARM`).*

### 5. Privacy, Zero-Knowledge Sync & P2P Tombstone Protocol

* **Google Drive AppData Scope:** When Drive backup is enabled, the app requests strictly `drive.appdata` scope.
* **Local P2P Out-of-Band Pairing:** Mutual TLS (mTLS) trust is established via Short Authentication String (SAS) or QR-code certificate fingerprint scan.
* **Last-Write-Wins (LWW) & Deletion Tombstones:**
  * When an item is deleted on Device A, it is flagged as `isDeleted = 1` with an updated `lastUpdated` timestamp.
  * During peer synchronization, records with `lastUpdated > lastSyncTimestamp` are exchanged.
  * Conflicts are resolved by comparing `lastUpdated`: the highest timestamp wins, propagating deletions and updates deterministically without resurrections.
  * Tombstones older than 30 days are purged locally in a background cleanup pass.

### 6. Acceptance & Verification Gates

* **Depletion Integrity:** Automated test verifying that cooking a 4-portion meal correctly subtracts scaled ingredient quantities without leaving floating negative inventory balances.
* **Incompatible Unit Resilience:** Automated UI test asserting that cooking a recipe with incompatible units prompts the fractional modal and safely commits deductions.
* **Background Worker Compliance:** Verification that `ExpiryNotificationWorker` executes cleanly within Android background execution limits and does not trigger battery drain alarms.
* **Sync Tombstone Consistency:** Automated two-node test asserting that an item deleted on Device A syncs as deleted on Device B and does not resurrect.
