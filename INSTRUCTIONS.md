# INSTRUCTIONS.md: Scaffolding Setup & Operational Runbooks

## 1. Project Baseline & Package Information
- **Application Name:** `PantryCraft`
- **Application ID / Target Package:** `com.jeffers.pantrycraft`
- **Scaffolding Prototype Package:** `com.freya.pantrygrocery`
- **Architecture:** Single-module Android project (`app/`) with package organization:
  - `com.jeffers.pantrycraft.data` (`PantryItem`, `GroceryItem`, `PantryDao`, `GroceryDao`, `AppDatabase`, `Repository`)
  - `com.jeffers.pantrycraft.ui` (`AppNavigator`, `HomeScreen`, `PantryScreen`, `GroceryScreen`, `ViewModels`)
- **Target & Compile SDK:** 35 (Android 15 stable; SDK 36 preview compatibility tested)
- **Minimum SDK:** 23 (Android 6.0 Marshmallow; StrongBox KeyStore gated at API 28+ with TEE fallback)
- **Toolchain:** Java 17, Gradle 8.x wrapper, Kotlin 2.0+ with KSP & Compose compiler plugin

## 2. Package Refactoring Runbook (Scaffolding Transition)
To cleanly transition the prototype from `com.freya.pantrygrocery` to `com.jeffers.pantrycraft`:
1. Rename directory tree in `app/src/main/java/com/freya/pantrygrocery/` to `app/src/main/java/com/jeffers/pantrycraft/`.
2. Update `namespace = "com.jeffers.pantrycraft"` and `applicationId = "com.jeffers.pantrycraft"` in `app/build.gradle`.
3. Update package declarations and import references across all Kotlin source files.
4. Verify tests and assemble APK:
```bash
./gradlew clean testDebugUnitTest
./gradlew :app:assembleDebug
```

## 3. Scaffolding Build & Verification Sequence

```bash
# 1. Clean build and run unit tests on existing baseline
./gradlew testDebugUnitTest

# 2. Assemble debug APK
./gradlew :app:assembleDebug

# 3. Output APK location
# app/build/outputs/apk/debug/app-debug.apk

# 4. Install and verify on connected Android test device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 4. Incremental Migration Runbooks

### Phase 1: Database Migration (v1 → v2)
1. Add migration in `AppDatabase.kt`:
   - Add columns to `pantry_items`: `storageLocation`, `category`, `expirationTimestamp`, `isOpened`, `openedTimestamp`, `isAromaticOrSeasoning`, `notes`.
   - Setup FTS5 virtual table `pantry_items_fts` and database triggers.
2. Update `PantryDao.kt` to return reactive `Flow<List<PantryItem>>` instead of `suspend fun List<PantryItem>`.
3. Configure SQLCipher encryption factory using KeyStore AES-GCM encrypted passphrase.

### Phase 2: CameraX & Barcode Integration
1. Add CameraX dependencies (`camera-camera2`, `camera-lifecycle`, `camera-view`) and ML Kit Barcode to `app/build.gradle`.
2. Register camera permissions in `AndroidManifest.xml`.
3. Add `CameraScanScreen` composable to `AppNavigator.kt` and hook into `PantryItem.barcode`.

### Phase 3: Meal Synthesis Engine
1. Add `MealRecipeScreen` and route `"meals"` in `AppNavigator.kt`.
2. Implement `MealRepository` pulling inventory records from `pantryDao.getAllPantryItems()`.
3. Add AI Model Router (Android AICore local default + Ktor BYOKey client + on-demand LiteRT-LM).

### Phase 4: Offline Voice Dictation & Ambient Companion
1. Bundle Sherpa-ONNX JNI native libraries for `arm64-v8a` and INT8 Zipformer model in assets.
2. Add microphone FAB to `PantryScreen` to parse spoken ingredients directly into `PantryItem`.
3. Add ambient cooking step navigation voice listener to `MealRecipeScreen`.

### Phase 5: Stock Depletion & Grocery Replenishment
1. Implement atomic transaction deducting cooked ingredients from `pantry_items` with unit conversion fallbacks.
2. When `quantity <= threshold`, auto-generate a `GroceryItem(fromPantryId = item.id)`.
3. Checking off items in `GroceryScreen` restores stock to `pantry_items`.
