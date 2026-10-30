# Milestone 1: Core Architecture, Build Hardening & On-Device AI Engine — Review & Adversarial Challenge Report

## Review Summary

**Verdict**: APPROVE

---

## 1. Observation

### 1.1 Direct Code Inspections
1. **`app/build.gradle` (Lines 127–144)**:
   ```groovy
   // Optional - lifecycle and coroutines
   implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'

   // Networking
   implementation 'com.squareup.okhttp3:okhttp:4.12.0'
   ```
   - Explicit `implementation 'com.squareup.okhttp3:okhttp:4.12.0'` dependency is present, securing `GeminiLiveVisionClient.kt` against transitive dependency eviction.
   - Redundant duplicate declaration `implementation 'androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2'` was cleaned up.

2. **`app/src/main/java/com/jeffers/pantrycraft/feature/meals/engine/OnDeviceMealModel.kt` (Lines 1–28)**:
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
   - Fully conforms to `PROJECT.md` Interface Contract 2.

3. **`app/src/main/java/com/jeffers/pantrycraft/feature/meals/engine/LiteRtModelEngine.kt` (Lines 23–157)**:
   - **Thread Pinning**:
     - Line 25: `private val dispatcher: CoroutineDispatcher = Dispatchers.Default`
     - Line 42: `override suspend fun generateMealRecipes(...) = withContext(dispatcher) {`
     - All heavy computational parsing, hardware checks, buffer allocation, and synthesis run on `Dispatchers.Default`.
   - **Hardware Feasibility Pre-checks**:
     - Lines 113–123: Inspects `ActivityManager.MemoryInfo`: requires `totalMem >= 6GB`, `availMem >= 1.5GB`, and `!lowMemory`.
     - Lines 124–132: Checks for model weights at `context.filesDir/models/litert_meal_gen_q8.tflite` or presence of `com.google.ai.edge.aicore.AiCore`.
   - **Memory Hygiene (Byte Zeroing)**:
     - Lines 56–66: Allocates `promptBytes = userPrompt.toByteArray(StandardCharsets.UTF_8)` and executes `promptBytes.fill(0)` and `onBufferScrubbed?.invoke(promptBytes)` in a guaranteed `finally` block.
     - Lines 77–102: Allocates `outputBytes = rawCompletion.toByteArray(StandardCharsets.UTF_8)` and executes `outputBytes.fill(0)` and `onBufferScrubbed?.invoke(outputBytes)` in a guaranteed `finally` block.
   - **Safe Fallback & Cancellation Propagation**:
     - Lines 59–60, 90–91: Explicitly catches and rethrows `CancellationException`, preserving coroutine structured concurrency cancellation semantics.
     - Lines 46–53, 68–75, 83–89, 93–98: Gracefully delegates to `FallbackMealSynthesizer.synthesize(items = onHandItems, mode = extractedMode, targetPortions = extractedPortions)` whenever uninitialized, runtime error occurs, completion is blank, or parsing produces no recipes.
   - **Prompt Parameter Extraction**:
     - Lines 133–140: `extractMatchMode` matches `- Match Mode: ${mode.id}` for all enum entries, safely defaulting to `MealMatchMode.STRICT`.
     - Lines 142–146: `extractTargetPortions` uses `PORTIONS_REGEX = Regex("""- Target Portions:\s*(\d+)""")`, parses with `toIntOrNull()` (preventing number overflow crashes), and clamps to `[MIN_PORTIONS, MAX_PORTIONS]` (1..12).

4. **`app/src/main/java/com/jeffers/pantrycraft/feature/meals/engine/MealSynthesisEngine.kt` (Lines 17–79)**:
   - Line 20: Injects `private val onDeviceModel: OnDeviceMealModel = LiteRtModelEngine(context)`.
   - Line 21: Injects `private val dispatcher: CoroutineDispatcher = Dispatchers.Default`.
   - Line 29: Pins execution to `withContext(dispatcher)`.
   - Lines 43–55: If `provider == AiProvider.OFFLINE_MLKIT`, delegates to `onDeviceModel.generateMealRecipes(systemPrompt, userPrompt, active)`. If recipes are returned, scales them to `targetPortions`; otherwise falls back cleanly to `FallbackMealSynthesizer.synthesize(...)`.

5. **Static Analysis & Complexity Verification**:
   - `python3 line_length_check`:
     - `OnDeviceMealModel.kt`: 27 lines, 0 lines > 120 chars (max length: 100).
     - `LiteRtModelEngine.kt`: 157 lines, 0 lines > 120 chars (max length: 101).
     - `MealSynthesisEngine.kt`: 79 lines, 0 lines > 120 chars (max length: 99).
     - `LiteRtModelEngineTest.kt`: 352 lines, 0 lines > 120 chars (max length: 102).
   - Cyclomatic Complexity: Max cyclomatic complexity across methods is <= 6 (well within the Detekt threshold of <= 15).

### 1.2 Independent Tool Execution Outputs
1. **Unit Test Suite Execution (`./gradlew testDebugUnitTest`)**:
   - Executed clean across entire codebase.
   - Verbatim XML test report parsing across all 70 test suites:
     ```text
     Total test suites (XML files): 70
     Total tests: 491, Failures: 0, Errors: 0, Skipped: 0
     ```
   - Includes:
     - `LiteRtModelEngineTest`: 12 tests, 0 failures.
     - `LiteRtModelEngineChallengerTest`: 18 tests, 0 failures.
     - `LiteRtModelEngineEmpiricalChallengeTest`: 5 tests, 0 failures.
     - `FallbackMealSynthesizerTest`: 3 tests, 0 failures.
     - `Tier1FeatureCoverageTest` - `Tier4RealWorldScenariosTest` (E2E Suite): 58 tests, 0 failures.
     - Existing repository, database, camera, and UI suites: 395 tests, 0 failures.
   - Result: 100% pass rate. Zero regressions introduced.

2. **Detekt Static Analysis Execution (`./gradlew detekt detektTest detektDebugUnitTest detektDebugAndroidTest --rerun-tasks`)**:
   - Output:
     ```text
     > Task :app:detektDebugAndroidTest
     > Task :app:detektDebugUnitTest
     > Task :app:detektTest
     > Task :app:detekt

     BUILD SUCCESSFUL in 1m 55s
     38 actionable tasks: 38 executed
     ```
   - Violations: 0 errors, 0 warnings across `main`, `test`, and `androidTest`.

---

## 2. Logic Chain

1. **Build Hardening**:
   - Direct inspection of `app/build.gradle` confirms `com.squareup.okhttp3:okhttp:4.12.0` is declared in `dependencies`.
   - This eliminates the risk of compile-time or runtime classpath missing errors in `GeminiLiveVisionClient.kt` if transitive dependencies are pruned.
   - Clean removal of the duplicate `androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2` avoids build configuration warnings.

2. **Interface Conformance & Architectural Decoupling**:
   - `OnDeviceMealModel` matches `PROJECT.md` Interface Contract 2 signature identically.
   - `LiteRtModelEngine` implements `OnDeviceMealModel` with sensible production defaults and test hooks (`mockInferenceExecutor`, `hardwareFeasibilityOverride`, `onBufferScrubbed`) that enable hermetic JVM unit testing without sacrificing production fidelity.

3. **Invariants Compliance**:
   - **Thread Pinning (AGENTS.md Invariant 4)**: Heavy computation and inference are dispatched to `Dispatchers.Default` via `withContext(dispatcher)`.
   - **Memory Hygiene (AGENTS.md Invariant 6)**: Both input `promptBytes` and output `outputBytes` are explicitly scrubbed via `.fill(0)` inside `finally` blocks.
   - **Zero Telemetry (AGENTS.md Invariant 1)**: No external tracking or telemetry SDKs added or referenced.
   - **Structured Concurrency**: `CancellationException` is properly caught and re-thrown, ensuring that coroutine cancellation is never swallowed.

4. **Integrity & Code Quality Audit**:
   - No hardcoded test results, facade shortcuts, or bypasses exist in the source code.
   - The fallback logic is deterministic and production-ready.
   - Detekt analysis passed with 0 errors and 0 warnings across all source sets.
   - All 491 unit tests passed with 0 failures and 0 skipped tests.

---

## 3. Adversarial Challenge & Stress-Testing

### Challenge Summary
- **Overall Risk Assessment**: LOW

### Challenges & Stress Test Scenarios Evaluated

1. **Challenge 1: Memory Scrubbing Under Coroutine Cancellation**
   - *Attack Scenario*: If a user navigates away or cancels a coroutine during inference, could the prompt buffer remain in memory unscroobbed?
   - *Stress Test*: `challengePromptBufferZeroedUnderCoroutineCancellation` launched inference on `Dispatchers.Default`, cancelled the job mid-inference, and inspected the captured buffer.
   - *Result*: PASS. The `finally` block reliably executed `promptBytes.fill(0)` and rethrew `CancellationException`. All bytes were verified to be zero.

2. **Challenge 2: Memory Scrubbing Under Malformed / Blank Model Output**
   - *Attack Scenario*: If the local model hallucinates non-JSON content, blank text, or malformed schema, does it scrub memory and fall back gracefully without crashing?
   - *Stress Tests*:
     - `testMalformedOutput_WhitespaceAndHtml_FallsBackGracefully`
     - `challengeBothBuffersZeroedUnderParsingFailure`
   - *Result*: PASS. Both prompt and output buffers were wiped to zero in `finally`, and valid fallback recipes were synthesized without crashing.

3. **Challenge 3: High-Concurrency Buffer Isolation**
   - *Attack Scenario*: Under burst requests (e.g. 50 parallel coroutines), do buffers leak across coroutine boundaries or suffer race conditions?
   - *Stress Test*: `challengeHighConcurrencyBufferIsolationAndScrubbing` executed 50 concurrent requests.
   - *Result*: PASS. Exactly 100 buffers (50 input + 50 output) were isolated, scrubbed, and verified to contain only zeros.

4. **Challenge 4: Prompt Extraction Boundary Stress**
   - *Attack Scenario*: User prompt contains negative portions (`-5`), zero portions (`0`), excessive portions (`99`), or integer overflow strings (`9999999999999999999999999999`).
   - *Stress Tests*:
     - `testExtractPortions_NegativePortionPrompt_DefaultsSafely`
     - `testExtractPortions_ZeroPortionsPrompt_ClampsToMinPortions`
     - `testExtractPortions_ExcessivePortionsPrompt_ClampsToMaxPortions`
     - `testExtractPortions_IntegerOverflowPortions_DefaultsSafely`
   - *Result*: PASS. Safely coerced to [1, 12] or defaulted to 2 without `NumberFormatException`.

5. **Challenge 5: Thread Pinning Verification**
   - *Attack Scenario*: Engine might execute inference on the caller's thread or `Dispatchers.Main`.
   - *Stress Test*: `generateMealRecipesExecutesOnProvidedDispatcher`.
   - *Result*: PASS. All computation pinned strictly to the provided dispatcher.

---

## 4. Integrity Violation Audit

| Integrity Check | Observation | Status |
|:---|:---|:---:|
| Hardcoded test results in source code | No hardcoded recipe responses or test string bypasses in `LiteRtModelEngine.kt` or `MealSynthesisEngine.kt`. | PASS |
| Dummy or facade implementations | Real `ActivityManager` RAM checks, real file presence checks, real `MealRecipeParser` invocations, real byte zeroing. | PASS |
| Shortcuts bypassing intended task | Implemented genuine on-device adapter with fallback and build hardening. | PASS |
| Fabricated verification outputs | Independent re-execution confirmed 491/491 tests pass and 0 Detekt warnings. | PASS |
| Self-certifying work without verification | Independently verified via fresh Gradle builds and adversarial challenge suites. | PASS |

---

## 5. Caveats

1. **Weights Packaging**: Model weights are not bundled directly inside the repository/APK to keep app download sizes compliant with Play Store thresholds (<200MB). In production without installed weights or AICore, the engine gracefully falls back to `FallbackMealSynthesizer`.
2. **Local Test Environment**: Pure JVM unit tests simulate Android framework context via `ContextWrapper(null)` and verify hardware branching via `hardwareFeasibilityOverride` and `mockInferenceExecutor`.

---

## 6. Conclusion

Milestone 1 satisfies all requirements, architectural invariants, security rules, and code quality thresholds:
- Explicit `OkHttp` dependency secured.
- `OnDeviceMealModel` interface and `LiteRtModelEngine` implementation are robust, thread-safe, and memory-hygienic.
- Prompt and output buffers are scrubbed via `fill(0)` in `finally` blocks under all normal and exceptional paths (including coroutine cancellation).
- Static analysis is 100% clean (0 Detekt errors/warnings; max line length <= 120; complexity <= 15).
- 100% unit test pass rate (491/491 tests pass, 0 regressions).

**Verdict: APPROVE**

---

## 7. Verification Method

To independently reproduce and verify this review:

1. **Verify Dependencies**:
   ```bash
   grep "com.squareup.okhttp3:okhttp:4.12.0" app/build.gradle
   ```

2. **Run All Project Unit Tests**:
   ```bash
   ./gradlew testDebugUnitTest --tests "*"
   ```
   Confirm: 491 tests passed, 0 failures, 0 errors, 0 skipped.

3. **Run Meals Engine Specific & Challenger Tests**:
   ```bash
   ./gradlew testDebugUnitTest --tests "com.jeffers.pantrycraft.feature.meals.engine.*"
   ```
   Confirm: 38 tests passed, 0 failures.

4. **Run Detekt Static Analysis Across All Targets**:
   ```bash
   ./gradlew detekt detektTest detektDebugUnitTest detektDebugAndroidTest --rerun-tasks
   ```
   Confirm: `BUILD SUCCESSFUL` with 0 errors and 0 warnings.
