# Project: PantryCraft Production & Hardening (Phases 1 - 3 & R4)

## Architecture
PantryCraft is an offline-first, local-encrypted Android application (Kotlin 2.2.10, Jetpack Compose, Room + SQLCipher 4.19.1, CameraX 1.4.2, ML Kit, Detekt 1.23.8).

```
                      ┌───────────────────────────────────────────┐
                      │          UI Layer (Jetpack Compose)        │
                      │  HomeScreen, PantryScreen, CameraScreen,  │
                      │  MealSynthesisScreen, SettingsScreen      │
                      └─────────────────────┬─────────────────────┘
                                            │
                      ┌─────────────────────▼─────────────────────┐
                      │                Domain Layer               │
                      │  PantryRepository, GroceryRepository      │
                      │  CulinaryScaling, SmartSwapEngine         │
                      │  MealCookHandler, BackupManager           │
                      └─────────────┬─────────────────┬───────────┘
                                    │                 │
             ┌──────────────────────▼──┐       ┌──────▼─────────────────────┐
             │    Data & Storage Layer  │       │     Inference & Vision     │
             │  Room + SQLCipher (AES- │       │  CameraX 15 FPS Live HUD   │
             │  256-GCM KeyStore Key)  │       │  ML Kit Object/Barcode     │
             │  FTS5 Prefix Search     │       │  Dual-Inference (LiteRT/   │
             │  WAL Concurrency        │       │  AICore + BYOKey Cloud +   │
             │  Streaming .pcbk Backup │       │  Local Fallback Synthesizer│
             └─────────────────────────┘       └────────────────────────────┘
```

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | SQLCipher Encrypted SQLite | Room + SQLCipher with 256-bit passphrase encrypted via KeyStore AES-256-GCM (StrongBox/TEE) and heap scrubbing | M1 | Survey (R1) |
| 2 | WAL Concurrency & Pragmas | `WRITE_AHEAD_LOGGING`, `busy_timeout = 5000`, `mmap_size = 268435456` | M1 | Survey (R1) |
| 3 | Sub-5ms FTS5 Prefix Search | External content FTS5 table `pantry_items_fts` with guarded triggers | M1 | Survey (R1) |
| 4 | Inventory Ledger & Soft-Delete | Tombstone soft-deletion (`isDeleted`), 4-zone location filtering (Pantry, Fridge, Freezer, Counter), accelerated perishability | M1 | Survey (R1) |
| 5 | Scoped Storage Cryptographic Backup | Streaming `.pcbk` backup with PBKDF2-HMAC-SHA256 and AES-256-GCM, KeyStore recovery | M1 | Survey (R1) |
| 6 | Zero Telemetry Enforcement | Zero external analytics, tracking, or remote error reporting SDKs | M1 | Survey (R1) |
| 7 | Build Hardening & Explicit OkHttp | Add explicit `implementation 'com.squareup.okhttp3:okhttp:4.12.0'` dependency in `app/build.gradle` | M1 | Survey (R1) |
| 8 | On-Device LiteRT / AICore Engine | Concrete LiteRT/AICore on-device Gemini Nano model engine adapter interfacing with `MealSynthesisEngine` alongside deterministic fallback | M1 | Survey (R3) |
| 9 | 15 FPS Viewfinder HUD with Lock | Continuous 15 FPS throttling (`FRAME_INTERVAL_MS = 66L`) with `AtomicBoolean` frame lock, guaranteed `imageProxy.close()` | M2 | Survey (R2) |
| 10 | Offline OpenFoodFacts Catalog | 59,723 SQLite barcode records with multi-tier resolution waterfall and custom barcode persistence | M2 | Survey (R2) |
| 11 | Ephemeral Media Scrubbing | Private scratch JPEG deleted in `finally`, RGB_565 to WebP downscaling, memory byte zeroing in `SnapshotFrame.close()` and WebSocket client | M2 | Survey (R2) |
| 12 | Gemini Multimodal Live Streaming | `wss://.../BidiGenerateContent` WebSocket streaming with BYOKey KeyStore authentication | M2 | Survey (R2) |
| 13 | Interactive Batch Review Sheet | Compose review sheet supporting single-item inline editing, multi-item selection, and atomic transaction commit | M2 | Survey (R2) |
| 14 | Non-Linear Seasoning Power Curve | Enforcing $portions^{0.75}$ scaling for `isAromaticOrSeasoning == true` with palate bounds [0.65, 0.85] and volume normalization | M2 | Survey (R3) |
| 15 | Four Meal Synthesis Modes | Strict (100% on-hand), Mostly (85-95%), Shopping (grocery delta), and 15-Minute Emergency Salvage | M2 | Survey (R3) |
| 16 | Smart Swap Knowledge Graph | Deterministic 10-rule substitution graph checking active on-hand inventory in <5ms | M2 | Survey (R3) |
| 17 | Multi-Provider BYOKey Caller | Direct REST calls to Gemini, OpenAI, Anthropic, and local Ollama with KeyStore AES-256-GCM encryption | M2 | Survey (R3) |
| 18 | Deterministic Fallback Synthesis | Zero-network rule-based meal generator prioritizing urgent and abundant on-hand stock | M2 | Survey (R3) |
| 19 | Atomic Stock Deduction Transaction | Room `@Transaction` / `AppDatabase.withTransaction` in `MealCookHandler` ensuring atomic inventory decrement (zero floor) and grocery insertion | M2 | Survey (R4) |
| 20 | Culinary Unit Volume Conversion | Automatic conversion between `TSP`, `TBSP`, `CUP`, `ML`, `L` in stock deduction matching recipe units to pantry units | M2 | Survey (R4) |
| 21 | End-to-End User Journey Wiring | Seamless flow from camera intake -> active inventory ledger -> context compression -> meal synthesis -> "Cook This Meal" execution | M3 | Survey (R4) |
| 22 | Meal Synthesis Compose AndroidTest | Connected / instrumentation tests in `app/src/androidTest` covering `MealSynthesisScreen`, mode selection, portion dial, and recipe card interactions | M3 | Survey (R4) |
| 23 | End-to-End Deduction AndroidTest | Instrumentation tests verifying atomic stock reduction and grocery checklist insertion upon cooking a meal | M3 | Survey (R4) |
| 24 | E2E Test Suite Pass (Tiers 1-4) | Pass 100% of the comprehensive opaque-box E2E test suite published in `TEST_READY.md` | M4 | Survey (All) |
| 25 | Adversarial Coverage Hardening (Tier 5) | White-box stress-testing and adversarial coverage hardening with challenger verification | M4 | Survey (All) |
| 26 | SLA & Static Analysis Verification | Detekt 0 errors/0 warnings, Unit tests 100% pass, FTS5 <5ms, Frame analysis <40ms, Cold start <350ms | M4 | Survey (All) |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Core Architecture, Build Hardening & On-Device AI Engine | Explicit OkHttp dependency, LiteRT / AICore model engine adapter in `feature/meals/engine` | none | DONE |
| M2 | Atomic Deduction Workflow & Unit Conversion Engine | Transactional `MealCookHandler` with `AppDatabase.withTransaction`, zero floor, grocery insertion, and volume unit conversions | M1 | DONE |
| M3 | End-to-End UI Journey & AndroidTest Suite | Connected UI instrumentation tests for `MealSynthesisScreen` and end-to-end deduction journey in `app/src/androidTest` | M2 | DONE |
| M4 | Final Milestone: 100% E2E Pass & Adversarial Hardening | Phase 1: Pass 100% of E2E tests (Tiers 1-4). Phase 2: Adversarial coverage hardening (Tier 5), Detekt 0 errors/0 warnings, SLAs | M3, E2E Track | IN_PROGRESS |

## Interface Contracts

### 1. `MealCookHandler` ↔ `AppDatabase` & `PantryRepository`
- **Method Signature**:
  ```kotlin
  suspend fun executeCookMealTransactional(
      database: AppDatabase,
      recipe: MealRecipe,
      pantryRepository: PantryRepository,
      groceryRepository: GroceryRepository,
      addMissingToGrocery: Boolean = true
  ): CookMealResult
  ```
- **Atomicity Guarantee**:
  Executes inside `database.withTransaction { ... }`. If any database operation fails, the transaction is rolled back completely.
- **Unit Conversion**:
  Uses `CulinaryScaling.convertVolume(qty, recipeUnit, stockUnit)` when recipe unit differs from stock unit. If conversion is impossible, falls back to raw quantity deduction with warning.
- **Zero Floor Protection**:
  Uses SQLite conditional decrement:
  `UPDATE pantry_items SET quantity = CASE WHEN quantity - :delta < 0 THEN 0 ELSE quantity - :delta END, updated_at = :updatedAt WHERE id = :id`

### 2. `OnDeviceAiEngine` ↔ `MealSynthesisEngine`
- **Interface**:
  ```kotlin
  interface OnDeviceMealModel {
      val isAvailable: Boolean
      suspend fun generateMealRecipes(
          systemPrompt: String,
          userPrompt: String,
          onHandItems: List<PantryItem>
      ): Result<List<MealRecipe>>
  }
  ```
- **Implementation**:
  `LiteRtModelEngine` wraps on-device execution or gracefully detects hardware availability, delegating to `FallbackMealSynthesizer` when model weights or AICore are uninitialized, preventing crashes.

### 3. E2E Testing Track Contract
- **Test Entry Point**: `./gradlew testDebugUnitTest` and `./gradlew connectedDebugAndroidTest`
- **Specification**: `TEST_READY.md` containing test counts across Tier 1 (Feature Coverage >= 5*N), Tier 2 (Boundary >= 5*N), Tier 3 (Pairwise Interactions), and Tier 4 (Real-World Scenarios).

## Code Layout
- `app/src/main/java/com/jeffers/pantrycraft/`:
  - `data/`: Database (`AppDatabase.kt`), entities (`RoomEntities.kt`), DAOs (`RoomDaos.kt`), repositories (`PantryRepository.kt`, `GroceryRepository.kt`), KeyStore key provider (`DatabaseKeyProvider.kt`), backups (`DatabaseBackupManager.kt`), culinary scaling (`CulinaryScaling.kt`), BYOKey preferences (`ByokPreferences.kt`).
  - `feature/camera/`: Viewfinder (`CameraScreen.kt`), analyzer (`LiveVisionAnalyzer.kt`), offline catalog (`OfflineBarcodeResolver.kt`), Gemini Live client (`GeminiLiveVisionClient.kt`), snapshot service (`SnapshotCaptureService.kt`), batch review (`BatchReviewBottomSheet.kt`).
  - `feature/meals/`: Models (`MealModels.kt`), context compressor (`InventoryContextCompressor.kt`), substitution graph (`SmartSwapEngine.kt`), prompt builder (`MealPromptBuilder.kt`), synthesis engine (`MealSynthesisEngine.kt`), BYOKey text client (`ByokTextModel.kt`), deterministic fallback (`FallbackMealSynthesizer.kt`), cook deduction (`MealCookHandler.kt`), UI (`MealSynthesisScreen.kt`, `MealRecipeCard.kt`, `MealSynthesisViewModel.kt`).
  - `ui/`: Main screens (`HomeScreen.kt`, `PantryScreen.kt`, `GroceryScreen.kt`, `SettingsScreen.kt`), navigation (`AppNavigator.kt`).
- `app/src/test/java/com/jeffers/pantrycraft/`: JVM unit tests covering all data, feature, scaling, and parsing logic.
- `app/src/androidTest/java/com/jeffers/pantrycraft/`: Instrumentation and connected UI tests.

<!-- GOAL_COMPLETE -->
