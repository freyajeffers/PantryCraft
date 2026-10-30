# Milestone 2 Review & Adversarial Challenge Report

**Reviewer**: `reviewer_m2_2` (Milestone 2 Reviewer 2)  
**Date**: 2026-10-27T12:53:30Z  
**Target Scope**: Milestone 2 (Atomic Deduction Workflow & Unit Conversion Engine)  
**Contract**: `PROJECT.md` Interface Contract 1, Features 19–20, `AGENTS.md`  
**Verdict**: APPROVE  

---

## Review Summary

**Verdict: APPROVE**

The work product delivered by `worker_m2_1` satisfies all functional requirements, architectural invariants, and security constraints outlined in `PROJECT.md`, `AGENTS.md`, and `ORIGINAL_REQUEST.md`. Specifically:
1. **Integrity**: Clean implementation with genuine business logic, realistic test doubles, and zero hardcoding or shortcuts.
2. **Thread Pinning**: `MealCookHandler.executeCookMealTransactional` is explicitly pinned to `Dispatchers.IO` via `withContext(Dispatchers.IO)`.
3. **Room Transaction Atomicity**: Executes inside `database.withTransaction { ... }`, ensuring all pantry decrements and grocery checklist additions succeed together or roll back atomically.
4. **Zero-Floor Decrement Mechanics**: Enforced in SQLite by `PantryMutationDao` using `CASE WHEN quantity - :delta < 0 THEN 0 ELSE quantity - :delta END` and guarded against negative/infinite/NaN inputs via `isDeltaValid`.
5. **Volume Unit Conversions**: Complete support for `TSP`, `TBSP`, `FL_OZ`, `CUP`, `ML`, `L` with 28 normalizations/aliases, accurate conversion math, and graceful fallback to raw quantities for non-volume or incompatible units.
6. **Code Quality & Static Analysis**: Zero lines exceed 120 characters across all modified files; all function cyclomatic complexities are <= 4 (threshold <= 15); method lengths <= 29 lines; Detekt passes with 0 errors and 0 warnings across all targets.
7. **Test Verification**: 100% pass rate across the full project test suite (`528 tests passing, 0 failures, 0 errors, 0 skipped` via `./gradlew testDebugUnitTest --rerun-tasks`).

---

## 1. Observation

### 1.1 Verified File Modifications
Direct inspection of modified files in repository:
1. `app/src/main/java/com/jeffers/pantrycraft/data/CulinaryScaling.kt`:
   - Line 13: Added `const val ML_PER_FL_OZ = 30f`.
   - Lines 23–53: Added `VOLUME_UNIT_ALIASES: Map<String, String>` mapping 28 volume aliases to canonical keys (`TSP`, `TBSP`, `FL_OZ`, `CUP`, `ML`, `L`).
   - Lines 55–62: Added `VOLUME_TO_ML_FACTORS: Map<String, Float>` mapping canonical units to mL equivalents (`TSP` -> 5f, `TBSP` -> 15f, `FL_OZ` -> 30f, `CUP` -> 240f, `ML` -> 1f, `L` -> 1000f).
   - Lines 100–113: Implemented `convertVolume(quantity, fromUnit, toUnit): Float?` with validation, normalization, and conversion via mL intermediary.
   - Lines 115–131: Implemented clean helper methods (`normalizeVolumeUnit`, `isQuantityValid`, `toMilliliters`, `fromMilliliters`).

2. `app/src/main/java/com/jeffers/pantrycraft/feature/meals/deduction/MealCookHandler.kt`:
   - Lines 30–45: Implemented `executeCookMealTransactional(database: AppDatabase, ...): CookMealResult` wrapped in `withContext(Dispatchers.IO) { database.withTransaction { ... } }`.
   - Lines 51–72: Implemented overloaded `executeCookMeal(...)` supporting default parameter `database: AppDatabase? = (pantryRepository as? Repository)?.db`.
   - Lines 74–98: Implemented `executeDeductionLogic` coordinating stock deduction and missing grocery additions.
   - Lines 100–134: Implemented `deductStockItems` and `deductSingleUsage` with validation of delta bounds (`isDeltaValid`), resolution of non-deleted pantry items (`resolveMatchedItem`), and volume conversion (`calculateDeductionDelta`).
   - Lines 136–166: Implemented `resolveMatchedItem`, `calculateDeductionDelta`, `isSameOrBlankUnit`, and `isDeltaValid`.
   - Lines 168–187: Implemented `insertMissingGroceryItems` inserting un-checked items with trimmed non-blank names.

3. `app/src/test/java/com/jeffers/pantrycraft/feature/meals/deduction/MealCookHandlerTest.kt`:
   - Lines 18–588: 38 distinct unit test methods covering:
     * Transaction commit and rollback (`transactionCommitPersistsAllDeductionsAndGroceries`, `transactionRollbackRevertsDeductionWhenGroceryFails`, `transactionRollbackRevertsDeductionsWhenSecondItemFails`).
     * Zero-floor clamping (`decrementExactQuantityReducesStockToZero`, `decrementExcessQuantityClampsStockAtZero`, `decrementWhenStockAlreadyZeroRemainsZero`, `decrementMultipleItemsWithPartialAndZeroFloorDepletion`).
     * Volume conversion permutations across TSP, TBSP, CUP, ML, L, FL_OZ (14 distinct pair tests + case insensitivity + identical units).
     * Missing grocery addition flags and blank filtering.
     * Portion bounds validation (1..12) and scaled deduction.
   - Lines 590–698: Structured `VolumeSpec` test parameter holder and isolated file-private test doubles (`FakePantryRepository`, `FakeGroceryRepository`) supporting transactional snapshot rollback.

### 1.2 Verbatim Test & Static Analysis Output
1. **Full Unit Test Suite Execution**:
   - Command: `./gradlew testDebugUnitTest --rerun-tasks`
   - Exit Code: `0`
   - Test Results: `528 tests completed, 0 failed, 0 errors, 0 skipped` in 2m 24s.
   - Specific Suite: `MealCookHandlerTest` executed 38 tests, 0 failures, 0 errors.

2. **Detekt Static Analysis Execution**:
   - Command: `./gradlew detekt detektTest detektDebugUnitTest detektDebugAndroidTest`
   - Exit Code: `0`
   - Output: `BUILD SUCCESSFUL in 5m 39s, 38 actionable tasks: 6 executed, 32 up-to-date`.
   - Report: `app/build/reports/detekt/detekt.txt` contains 0 code smells, 0 warnings, 0 errors.

3. **Line Length & Method Complexity Audit**:
   - `CulinaryScaling.kt`: 132 lines, 0 lines > 120 chars, max method length 29 lines, max cyclomatic complexity 4.
   - `MealCookHandler.kt`: 188 lines, 0 lines > 120 chars, max method length 26 lines, max cyclomatic complexity 4.
   - `MealCookHandlerTest.kt`: 697 lines, 0 lines > 120 chars, max method length 38 lines.

---

## 2. Logic Chain

1. **Integrity Verification**:
   - Audited the implementation files for suspicious patterns:
     * No hardcoded test responses or bypasses.
     * `CulinaryScaling` implements real mathematical transformations between volume units using standard culinary metric factors.
     * `MealCookHandler` directly coordinates `PantryRepository` and `GroceryRepository` methods inside Room's transactional context.
     * Test doubles in `MealCookHandlerTest` implement true stateful mutations, snapshot rollbacks, and error injection.
   - In accordance with integrity rules, no cheating or facade implementation was detected.

2. **Thread Pinning (`AGENTS.md` Invariant 4)**:
   - `AGENTS.md` specifies: "Disk/Database: Dispatchers.IO".
   - `MealCookHandler.executeCookMealTransactional` encloses `database.withTransaction` within `withContext(Dispatchers.IO)`. This ensures that all SQLite read, update, and insert queries run exclusively on the I/O coroutine dispatcher.
   - In the fallback overload `executeCookMeal`, when `database` is null (e.g. during lightweight test execution with pure in-memory test doubles in ViewModel tests), execution remains on the caller coroutine context to prevent dispatcher deadlocks in standard single-threaded test environments.

3. **Room Transaction Atomicity (`PROJECT.md` Interface Contract 1)**:
   - `PROJECT.md` Interface Contract 1 requires that cooking stock deduction and missing grocery checklist insertion execute atomically inside `database.withTransaction { ... }`.
   - If any step throws an uncaught exception (e.g. database disk failure or constraint violation), Room catches the exception, executes SQLite `ROLLBACK`, and rethrows the exception, leaving all tables in their exact pre-transaction state.
   - Verified empirically in `MealCookHandlerTest` where both grocery failure and second pantry item decrement failure rolled back all previous deductions.

4. **Zero-Floor Stock Protection**:
   - The database layer enforces zero floor via `PantryMutationDao`:
     ```sql
     UPDATE pantry_items 
     SET quantity = CASE WHEN quantity - :delta < 0 THEN 0 ELSE quantity - :delta END, 
         updated_at = :updatedAt 
     WHERE id = :id AND isDeleted = 0
     ```
   - In `MealCookHandler`, inputs are pre-validated via `isDeltaValid(delta)` (`delta > 0f && delta.isFinite()`).
   - If a recipe specifies negative, zero, NaN, or infinite quantities, `MealCookHandler` ignores the decrement, preventing `IllegalArgumentException` from `pantryRepository.decrementQuantity`.
   - If a recipe requests more than the on-hand quantity, SQLite's conditional update clamps the stored inventory to `0.0f` without integer/float underflow.

5. **Volume Unit Conversions (Feature 20)**:
   - US customary to metric culinary volume conversion:
     * 1 TSP = 5 mL
     * 1 TBSP = 15 mL (3 TSP)
     * 1 FL_OZ = 30 mL (2 TBSP = 6 TSP)
     * 1 CUP = 240 mL (8 FL_OZ = 16 TBSP = 48 TSP)
     * 1 L = 1000 mL
   - Conversions route through `toMilliliters` and `fromMilliliters`.
   - Identical or normalized identical units return the raw quantity directly, avoiding floating-point division drift.
   - If units are non-volume (e.g., mass units like `"g"`, `"kg"`, `"oz"`, `"lb"`, or counts like `"slices"`), `convertVolume` returns `null`, and `calculateDeductionDelta` falls back cleanly to raw quantity.

6. **Static Analysis & SLA Conformance**:
   - All files adhere to max line length <= 120 chars.
   - Cyclomatic complexity is <= 4 across all new methods (well under the threshold of 15).
   - Method length is <= 29 lines (well under the threshold of 60).
   - Detekt static analysis executed cleanly across all modules without suppressions or violations.

---

## 3. Adversarial Challenges & Stress Testing

### 3.1 Challenge Scenarios Evaluated

#### Challenge 1: Incompatible Unit Conversion Fallback
- **Assumption Challenged**: Recipe might specify mass units (e.g. `"g"`) while pantry item is measured in volume (e.g. `"ml"`), or vice versa.
- **Attack Scenario**: Passing `fromUnit = "g"` and `toUnit = "ml"` to `calculateDeductionDelta`.
- **Observed Behavior**: `CulinaryScaling.convertVolume` returns `null` because `"g"` is not in `VOLUME_UNIT_ALIASES`. `calculateDeductionDelta` catches the `null` and safely falls back to `usage.quantityUsed` without crashing.
- **Result**: PASS.

#### Challenge 2: Non-Finite and Negative Quantities
- **Assumption Challenged**: Corrupted recipe JSON or adversarial client input passing `NaN`, `Infinity`, `0f`, or negative float values.
- **Attack Scenario**: Passing `quantityUsed = Float.NaN`, `Float.POSITIVE_INFINITY`, `0f`, or `-15f`.
- **Observed Behavior**: `isDeltaValid` evaluates to `false`. `deductSingleUsage` short-circuits and returns `false`. No database update is dispatched, preventing `IllegalArgumentException` from `PantryRepository.decrementQuantity`.
- **Result**: PASS.

#### Challenge 3: Soft-Deleted Item Ingestion
- **Assumption Challenged**: Soft-deleted pantry items (`isDeleted = 1`) might accidentally be resolved and decremented by name or id.
- **Attack Scenario**: Pantry contains an active and a soft-deleted item with the same name.
- **Observed Behavior**: `resolveMatchedItem` filters strictly with `!it.isDeleted` in both the `pantryItemId` branch and the name-matching branch. Soft-deleted items are ignored.
- **Result**: PASS.

#### Challenge 4: Whitespace and Case Sensitivity in Unit Strings
- **Attack Scenario**: Recipe specifies unit as `"  FL_OZ  "` and pantry item unit is `"fl oz"`.
- **Observed Behavior**: `CulinaryScaling.normalizeVolumeUnit` applies `.trim().lowercase().replace("_", " ").replace(".", "")`. Both resolve to `"FL_OZ"`, and `normFrom == normTo` returns the exact quantity without loss of precision.
- **Result**: PASS.

#### Challenge 5: Multi-Ingredient Depletion of Same Stock Record
- **Assumption Challenged**: Recipe lists the same item twice (e.g. 50 mL for marinade, 100 mL for sauce).
- **Attack Scenario**: Sequential deductions against item ID 10 with on-hand quantity 80 mL.
- **Observed Behavior**: Both iterations execute `pantryDao.decrementQuantity`. The first reduces quantity from 80 mL to 30 mL; the second triggers SQLite's conditional clamp reducing quantity from 30 mL to 0 mL.
- **Result**: PASS.

---

## 4. Caveats

No caveats. All requirements from `PROJECT.md` (Interface Contract 1, Features 19–20), `AGENTS.md`, and the dispatch instructions have been rigorously audited, stress-tested, and verified.

---

## 5. Conclusion

**Verdict: APPROVE**

Milestone 2 implementation is hardened, robust, and fully verified.
- Atomicity is guaranteed via `AppDatabase.withTransaction`.
- Thread pinning is enforced via `withContext(Dispatchers.IO)`.
- Zero-floor inventory protection is guaranteed in both Room SQL and memory validation.
- Unit volume conversions across TSP, TBSP, FL_OZ, CUP, ML, L are accurate and resilient.
- Detekt passes with 0 errors and 0 warnings.
- All 528 JVM unit tests pass with 0 regressions.

Milestone 2 is ready to proceed to Milestone 3 (UI Journey Wiring & AndroidTest Suite).

---

## 6. Verification Method

To independently reproduce and verify this review:

1. **Execute All JVM Unit Tests**:
   ```bash
   ./gradlew testDebugUnitTest --rerun-tasks
   ```
   *Expected Result*: `528 tests completed, 0 failed, 0 errors, 0 skipped`, BUILD SUCCESSFUL.

2. **Execute Full Detekt Analysis**:
   ```bash
   ./gradlew detekt detektTest detektDebugUnitTest detektDebugAndroidTest
   ```
   *Expected Result*: 0 code smells, 0 errors, 0 warnings, BUILD SUCCESSFUL.

3. **Inspect Modified Files**:
   - `app/src/main/java/com/jeffers/pantrycraft/data/CulinaryScaling.kt`
   - `app/src/main/java/com/jeffers/pantrycraft/feature/meals/deduction/MealCookHandler.kt`
   - `app/src/test/java/com/jeffers/pantrycraft/feature/meals/deduction/MealCookHandlerTest.kt`

4. **Invalidation Conditions**:
   - Any unit test failure in `MealCookHandlerTest` or existing suites.
   - Any Detekt violation regarding line length (> 120), method length (> 60), or complexity (> 15).
   - Any regression causing inventory to fall below zero or database transactions to fail to roll back.
