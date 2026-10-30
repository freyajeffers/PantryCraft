# TEST_INFRA.md — PantryCraft Test Infrastructure Specification

## 1. Test Philosophy
PantryCraft is an offline-first, local-encrypted Android application designed for absolute data sovereignty, high-concurrency local persistence, and intelligent culinary assistance. The test infrastructure adheres to these core tenets:

1. **Opaque-Box Requirement-Driven Verification**: Test suites evaluate domain behaviors, contracts, state transitions, and edge conditions against explicit system specifications rather than white-box implementation details.
2. **Zero Telemetry & Local Sovereignty**: Verification guarantees no analytics, tracking, or remote phone-home SDKs exist in dependencies or runtime execution paths.
3. **Cryptographic & Memory Safety**: Hardware-backed key lifecycles, memory scrubbing of passphrases and ephemeral vision buffers, and AES-256-GCM / SQLCipher integrity are validated under both valid execution and failure scenarios (e.g., KeyStore key invalidation).
4. **Culinary Precision & Non-Linear Power Curves**: Rigorous verification of aromatic and seasoning ingredient scaling ($portions^{0.75}$ with palate limits $[0.65, 0.85]$) alongside linear bulk ingredient scaling.
5. **Deterministic State Progression**: Tests are completely isolated, self-contained, order-independent, and free of persistent side-effects or network dependencies.

---

## 2. Feature Inventory
The test suites span four major requirement domains (R1 through R4):

### R1. Local-First Encrypted Architecture & Inventory Engine
- **SQLCipher Room Encryption**: 256-bit passphrase protection wrapped with AES-256-GCM KeyStore keys, heap scrubbing.
- **KeyStore Invalidation Recovery**: Strict handling of `KeyPermanentlyInvalidatedException` via `DatabaseKeyInvalidatedException`.
- **WAL Concurrency & Pragmas**: `WRITE_AHEAD_LOGGING`, `busy_timeout = 5000`, `mmap_size = 268435456`.
- **Sub-5ms FTS5 Prefix Search**: Tokenization, sanitization of reserved keywords (`AND`, `OR`, `NOT`, `NEAR`), prefix query generation (`"term"*`).
- **Inventory Ledger & Soft-Delete**: Tombstone soft-deletion (`isDeleted = 1`), restore workflows (`isDeleted = 0`), and updated timestamps.
- **Pantry Taxonomy & Aromatic Tagging**: 4 storage locations (`PANTRY`, `FRIDGE`, `FREEZER`, `COUNTER`), opened shelf life decay calculation, and automatic aromatic classification (`SPICES`, `CONDIMENTS`).
- **Cryptographic Backup (.pcbk)**: Header verification (`PCBK` magic, version 1, 16-byte salt, 12-byte IV), stream encryption/decryption, JSON backup record indexing.

### R2. CameraX Dual-Mode Vision Pipeline & Ingestion
- **15 FPS Viewfinder Throttle**: Strict `FRAME_INTERVAL_MS = 66L` inter-frame spacing governed by `AtomicBoolean` lock.
- **ImageProxy Resource Safety**: Deterministic `imageProxy.close()` guarantees on success, frame drops, null images, and exceptions.
- **Offline OpenFoodFacts Catalog Waterfall**: Multi-tier resolution (custom user cache -> SQLite catalog -> embedded fallback).
- **Barcode Validation & Debouncing**: Standard EAN/UPC format enforcement (`\d{8,14}`), rapid scan suppression within debounce window (`DEBOUNCE_MS`).
- **Ephemeral Media Scrubbing**: Immediate deletion of temporary scratch frames (`vision_scratch`) and zeroing of in-memory buffers.
- **Batch Review State Handling**: Barcode-to-inventory association, unit selection, inline editing, and atomic batch commitment.

### R3. Dual-Inference Meal Synthesis & Culinary Intelligence
- **Culinary Scaling Engine**: Linear scaling for bulk ingredients ($qty \times target / base$), sub-linear power curve for aromatics ($qty \times (target / base)^{exponent}$).
- **Palate Exponent Bounds**: Clamping custom seasoning exponents strictly within $[0.65, 0.85]$ (default 0.75).
- **Culinary Volume Unit Conversions**: Bidirectional conversion between `TSP`, `TBSP`, `CUP`, `ML`, `L`.
- **Four Synthesis Modes**:
  - `STRICT`: 100% on-hand zero-waste meals.
  - `MOSTLY`: 85-95% pantry-first meals with up to 2 staple allowances.
  - `SHOPPING`: Elevated chef recipes featuring pantry stars with grocery delta list.
  - `EMERGENCY_SALVAGE`: 15-minute rapid rescue recipes prioritizing items expiring within 48 hours.
- **Smart Swap Knowledge Graph**: 10-rule deterministic substitution graph checking active on-hand inventory availability.
- **Deterministic Fallback Synthesizer**: Zero-network rule-based meal generator optimizing urgency scores and macronutrient estimates.

### R4. End-to-End User Journey & Deduction Workflows
- **Recipe Portion Clamping**: Strict validation and portion scaling between 1 and 12 servings (`MIN_PORTIONS..MAX_PORTIONS`).
- **Transactional Cook Execution**: `MealCookHandler` orchestrating pantry quantity decrements and grocery delta insertions.
- **Zero-Floor Stock Protection**: SQLite conditional update ensuring inventory never drops below zero (`CASE WHEN quantity - delta < 0 THEN 0`).
- **Missing Items Grocery Insertion**: Automatic population of missing recipe ingredients into grocery checklist with `fromPantryId` linking.
- **Grocery Checklist Restock**: Checking items off grocery list and restocking back to pantry, updating existing items or creating new entries.
- **Full End-to-End Cycle**: Camera intake -> Ledger insertion -> Synthesis -> Cooking deduction -> Grocery restock -> Pantry replenishment.

---

## 3. Test Architecture

The E2E test track is structured under `app/src/test/java/com/jeffers/pantrycraft/e2e/`:

```
app/src/test/java/com/jeffers/pantrycraft/e2e/
├── E2ETestHarness.kt                  # In-memory realistic repository doubles & test factories
├── Tier1FeatureCoverageTest.kt        # Primary happy-path feature coverage (>=5 per feature domain)
├── Tier2BoundaryCornerCasesTest.kt    # Extremes, zeros, clamping, invalid inputs, KeyStore invalidation
├── Tier3CrossFeatureCombinationsTest.kt # Pairwise integration across pipeline components
└── Tier4RealWorldScenariosTest.kt     # Real-world culinary & pantry management scenarios
```

### In-Memory Storage & Execution Harness
`E2ETestHarness.kt` provides `InMemoryPantryRepository`, a faithful standalone implementation of `PantryRepository` and `GroceryRepository`:
- Manages separate collections for active pantry items, deleted tombstones, and grocery items.
- Mimics Room `@Transaction` atomicity and SQL conditional decrement zero-floor behavior.
- Implements `searchPantryItems` with sanitized tokens matching the SQL FTS5 contract.
- Implements `restockCheckedGroceryToPantry` updating existing pantry items or inserting new items.
- Provides test builder helpers for `PantryItem`, `MealRecipe`, `RecipePantryUsage`, and `GroceryItem`.

---

## 4. Real-World Scenarios (Tier 4)

1. **Emergency 15-Minute Salvage**:
   - Household refrigerator contains fresh chicken breast and spinach expiring within 24 hours.
   - User triggers Emergency 15-Minute Rescue mode.
   - Fallback engine synthesizes recipe with 5-minute prep and 10-minute cook time (15 minutes total).
   - High urgency score ($0.95$) is calculated, waste prevention counter increments, and cooking reduces stock.
2. **Holiday Feast Portion Scaling**:
   - Recipe designed for 2 people scaled to a 10-person holiday gathering.
   - Linear bulk ingredients (potatoes, broth) scale $5\times$.
   - Aromatic seasoning (garlic, rosemary, thyme) scales $5^{0.75} \approx 3.34\times$, preventing over-seasoning.
   - Palate bounds $[0.65, 0.85]$ ensure taste integrity.
3. **Offline Zero-Network Pantry Synthesis**:
   - Device is offline with zero cellular or Wi-Fi connectivity and no BYOKey configured.
   - Engine executes deterministic fallback synthesizer on local inventory.
   - Balanced recipe generated with instructions, cook time, and nutritional estimates.
4. **Weekly Meal Planning Multi-Meal Cycle**:
   - User begins week with baseline inventory.
   - Day 1: Cook dinner -> stock deducted.
   - Day 2: Cook lunch -> ingredients depleted, missing items flow to grocery list.
   - Day 3: Grocery shopping restock restores pantry quantities.
   - Day 4: Future recipe synthesis succeeds with restocked stock.

---

## 5. Coverage Thresholds & SLAs

| Tier | Category | Minimum Target | Actual Target | Passing Criteria |
|---|---|---|---|---|
| **Tier 1** | Feature Coverage (R1-R4) | $\ge 5$ per feature ($\ge 20$ total) | 24 tests (6 per feature) | 100% Pass |
| **Tier 2** | Boundary & Corner Cases | $\ge 5$ per feature ($\ge 20$ total) | 24 tests (6 per feature) | 100% Pass |
| **Tier 3** | Cross-Feature Combinations | $\ge 4$ pipeline flows | 6 comprehensive flows | 100% Pass |
| **Tier 4** | Real-World Scenarios | 4 distinct scenarios | 4 complete scenarios | 100% Pass |
| **Total** | Full E2E Track | $\ge 48$ test cases | **58 test cases** | **100% Pass** |

### Static Analysis & Verification Rules
- Detekt: 0 errors, 0 warnings (`./gradlew detekt detektTest detektDebugUnitTest`).
- Line Length: Strict $\le 120$ characters.
- Method Length: Keep within standard Detekt method complexity limits.
- Zero Facade Policy: Every test asserts concrete domain state, calculations, or exception types.
