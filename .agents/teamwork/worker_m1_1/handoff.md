# Milestone 1: Core Architecture, Build Hardening & On-Device AI Engine — Implementation Handoff Report

## 1. Observation

### 1.1 Direct Observations of Modified and Created Files
1. **`app/build.gradle`**:
   - Lines 127–134:
     ```groovy
     // Optional - lifecycle and coroutines
     implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'

     // Networking
     implementation 'com.squareup.okhttp3:okhttp:4.12.0'
     ```
   - Added direct module implementation dependency `com.squareup.okhttp3:okhttp:4.12.0` securing `GeminiLiveVisionClient.kt` against transitive dependency dropping.
   - Removed duplicate redundant `androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2` from line 143.

2. **`app/src/main/java/com/jeffers/pantrycraft/feature/meals/engine/OnDeviceMealModel.kt`**:
   - Newly created interface satisfying `PROJECT.md` Interface Contract 2:
     ```kotlin
     package com.jeffers.pantrycraft.feature.meals.engine

     import com.jeffers.pantrycraft.data.PantryItem
     import com.jeffers.pantrycraft.feature.meals.model.MealRecipe

     interface OnDeviceMealModel {
         val isAvailable: Boolean
         suspend fun generateMealRecipes(
             systemPrompt: String,
             userPrompt: String,
             onHandItems: List<PantryItem>,
         ): Result<List<MealRecipe>>
     }
     ```

3. **`app/src/main/java/com/jeffers/pantrycraft/feature/meals/engine/LiteRtModelEngine.kt`**:
   - Newly created concrete engine implementing `OnDeviceMealModel`:
     - Checks hardware RAM feasibility via `ActivityManager.MemoryInfo`: requires total RAM >= 6GB (`6L * 1024L * 1024L * 1024L`), `!memoryInfo.lowMemory`, and available memory >= 1.5GB (`1536L * 1024L * 1024L`).
     - Heavy computation and parsing pinned to `Dispatchers.Default` via constructor parameter `dispatcher: CoroutineDispatcher = Dispatchers.Default`.
     - Ephemeral memory hygiene: Prompt UTF-8 byte buffers and output byte buffers are wiped in `finally` blocks using `ByteArray.fill(0)`.
     - Safe fallback delegation: When `!isAvailable`, on runtime inference error, or when output is blank/malformed, extracts match mode and portions from `userPrompt` and delegates to `FallbackMealSynthesizer.synthesize(items = onHandItems, mode = extractedMode, targetPortions = extractedPortions)`.
     - Test verification hook: Accepts `onBufferScrubbed: ((ByteArray) -> Unit)? = null` allowing unit tests to assert memory byte zeroing.

4. **`app/src/main/java/com/jeffers/pantrycraft/feature/meals/engine/MealSynthesisEngine.kt`**:
   - Updated constructor:
     ```kotlin
     class MealSynthesisEngine(
         private val context: Context,
         private val textModel: TextGenerationModel = ByokTextModel(context),
         private val onDeviceModel: OnDeviceMealModel = LiteRtModelEngine(context),
         private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
     )
     ```
   - Updated `synthesizeMeals`:
     - If `provider == AiProvider.OFFLINE_MLKIT`: delegates to `onDeviceModel.generateMealRecipes(systemPrompt, userPrompt, active)`. If recipes are returned, scales to `targetPortions`; otherwise delegates to `FallbackMealSynthesizer.synthesize(...)`.
     - If cloud provider without API key: falls back to `FallbackMealSynthesizer.synthesize(...)`.
     - If cloud provider with key: generates via `textModel`, parses with `MealRecipeParser`, and falls back to `FallbackMealSynthesizer` if empty or on failure.

5. **`app/src/test/java/com/jeffers/pantrycraft/feature/meals/engine/LiteRtModelEngineTest.kt`**:
   - Newly created test suite with 12 comprehensive unit tests:
     1. `isAvailableReturnsFalseWhenHardwareInsufficient`
     2. `isAvailableReturnsTrueWhenHardwareFeasibleAndOverrideTrue`
     3. `generateMealRecipesFallsBackWhenRuntimeUnavailable`
     4. `generateMealRecipesFallsBackWhenModelWeightsFileDoesNotExist`
     5. `generateMealRecipesDelegatesAndParsesEmergencySalvageMode`
     6. `generateMealRecipesDelegationWithEmptyItemsReturnsEmptyList`
     7. `generateMealRecipesDelegatesAndClampsInvalidPortionCounts`
     8. `generateMealRecipesScrubsInputAndOutputBuffersOnSuccess`
     9. `generateMealRecipesScrubsPromptBufferOnInferenceException`
     10. `generateMealRecipesExecutesOnProvidedDispatcher`
     11. `generateMealRecipesHandlesMalformedJsonByGracefullyFallingBack`
     12. `generateMealRecipesHandlesBlankOutputByGracefullyFallingBack`

### 1.2 Verification Tool Execution & Verbatim Outputs
- **Command**: `./gradlew testDebugUnitTest --rerun-tasks`
  - Verbatim Output:
    ```text
    BUILD SUCCESSFUL in 37s
    31 actionable tasks: 31 executed
    ```
  - XML Test Report Results:
    Total tests across project: 468. Failures: 0. Errors: 0. Skipped: 0.
    In `TEST-com.jeffers.pantrycraft.feature.meals.engine.LiteRtModelEngineTest.xml`:
    `<testsuite name="com.jeffers.pantrycraft.feature.meals.engine.LiteRtModelEngineTest" tests="12" skipped="0" failures="0" errors="0" ...>`

- **Command**: `./gradlew detekt detektTest detektDebugUnitTest detektDebugAndroidTest --rerun-tasks`
  - Verbatim Output:
    ```text
    > Task :app:detektDebugAndroidTest
    > Task :app:detektDebugUnitTest
    > Task :app:detektTest
    > Task :app:detekt

    BUILD SUCCESSFUL in 1m 1s
    38 actionable tasks: 38 executed
    ```
  - Violations: 0 errors, 0 warnings across all 4 targets.
  - Line lengths: 100% of lines in all modified/created files <= 120 characters.
  - Cyclomatic complexity: <= 15 for all methods.

---

## 2. Logic Chain

1. **Dependency Hardening Logic**:
   - `GeminiLiveVisionClient.kt` imports and uses `okhttp3.*` classes directly (Obs 1.1).
   - In `app/build.gradle`, OkHttp was previously declared only in a `constraints` block, leaving compilation dependent on transitive resolution from ML Kit's internal VKP library.
   - Adding explicit `implementation 'com.squareup.okhttp3:okhttp:4.12.0'` guarantees compile-time classpath availability, preventing future breakage from upstream dependency modifications.
   - Removing the duplicate declaration of `androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2` keeps build definitions clean.

2. **On-Device AI Engine Architecture & Invariants**:
   - `PROJECT.md` Feature 8 mandates an on-device LiteRT / AICore model engine adapter interfacing with `MealSynthesisEngine` alongside deterministic fallback.
   - `OnDeviceMealModel` formalizes this contract with `isAvailable: Boolean` and `generateMealRecipes(...)`.
   - `LiteRtModelEngine` enforces all security and performance invariants:
     - RAM feasibility pre-check (>= 6GB RAM and !lowMemory) prevents launching heavy inference on memory-constrained devices.
     - Pinned to `Dispatchers.Default` for all heavy computational work.
     - UTF-8 byte arrays for prompts and completions are zeroed (`ByteArray.fill(0)`) in `finally` blocks, satisfying Ephemeral Media & Memory Hygiene (Invariant 6).
     - Graceful delegation to `FallbackMealSynthesizer` when uninitialized, missing weights, or on error guarantees zero crashes.
   - `MealSynthesisEngine` injects `OnDeviceMealModel = LiteRtModelEngine(context)` and `dispatcher = Dispatchers.Default`, routing `AiProvider.OFFLINE_MLKIT` through the on-device model and falling back cleanly.

3. **Static Analysis & Test Verification**:
   - All 12 newly written unit tests in `LiteRtModelEngineTest.kt` exercise both success paths, error paths, memory scrubbing, portion clamping, and fallback routing.
   - Detekt static analysis and unit test suites were executed with `--rerun-tasks` confirming zero regressions and 100% passing status across 468 test cases.

---

## 3. Caveats

1. **Neural Weights Packaging**: Base APK does not bundle multi-gigabyte neural weights (e.g., Gemma 2 2B / Qwen 2.5 3B) to adhere strictly to Google Play size limits (<200MB). On-device hardware evaluation defaults `isAvailable = false` when local weight files or system AICore are uninitialized, preventing crashes and gracefully delegating to `FallbackMealSynthesizer`.
2. **Hermetic JVM Unit Testing**: Pure JVM unit tests run with simulated Android framework context (`ContextWrapper(null)`). In `LiteRtModelEngine`, `hardwareFeasibilityOverride` and `mockInferenceExecutor` enable deterministic testing of RAM feasibility, parsing, threading, and buffer zeroing without requiring an emulator.
3. **No Network in Tests**: All tests remain completely offline and hermetic.

---

## 4. Conclusion

Milestone 1 is **complete, verified, and fully hardened**:
1. `app/build.gradle`: Explicit `implementation 'com.squareup.okhttp3:okhttp:4.12.0'` declared; duplicate lifecycle dependency removed.
2. `com.jeffers.pantrycraft.feature.meals.engine.OnDeviceMealModel`: Created and compliant with Interface Contract 2.
3. `com.jeffers.pantrycraft.feature.meals.engine.LiteRtModelEngine`: Implemented with RAM feasibility pre-checks (>=6GB RAM), `Dispatchers.Default` pinning, ephemeral UTF-8 buffer zeroing in `finally` blocks, safe fallback delegation to `FallbackMealSynthesizer`, and test verification hook `onBufferScrubbed`.
4. `com.jeffers.pantrycraft.feature.meals.engine.MealSynthesisEngine`: Integrated with `OnDeviceMealModel = LiteRtModelEngine(context)` and `dispatcher = Dispatchers.Default`, routing `OFFLINE_MLKIT` through `onDeviceModel`.
5. `app/src/test/java/com/jeffers/pantrycraft/feature/meals/engine/LiteRtModelEngineTest.kt`: 12 comprehensive unit tests created and passing.
6. Verification: 100% test pass rate (468/468 tests pass) and 0 Detekt errors/warnings across all 4 targets.

---

## 5. Verification Method

To independently verify the Milestone 1 changes:

1. **Inspect Dependencies**:
   ```bash
   grep "com.squareup.okhttp3:okhttp:4.12.0" app/build.gradle
   ```

2. **Run All Unit Tests**:
   ```bash
   ./gradlew testDebugUnitTest
   ```
   Confirm all 468 unit tests pass with zero failures.

3. **Run Meals Engine Specific Unit Tests**:
   ```bash
   ./gradlew testDebugUnitTest --tests "com.jeffers.pantrycraft.feature.meals.engine.*"
   ```

4. **Run Detekt Static Analysis**:
   ```bash
   ./gradlew detekt detektTest detektDebugUnitTest detektDebugAndroidTest
   ```
   Confirm BUILD SUCCESSFUL with 0 errors and 0 warnings.

5. **Verify Memory Hygiene**:
   Inspect `LiteRtModelEngineTest.generateMealRecipesScrubsInputAndOutputBuffersOnSuccess` and `generateMealRecipesScrubsPromptBufferOnInferenceException` to verify that `buffer.all { it == 0.toByte() }` is tested and verified on both prompt and output buffers.
