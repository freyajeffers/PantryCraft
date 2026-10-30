# Handoff Report: Milestone 2 — Atomic Deduction Workflow & Transactional Architecture

**Agent**: `explorer_m2_1` (M2 Deduction Architecture Explorer)  
**Date**: 2026-10-27  
**Working Directory**: `/home/freya/Projects/pantrycraft/.agents/teamwork/explorer_m2_1`  
**Target Milestone**: Milestone 2 (Atomic Deduction Workflow & Room Transaction Architecture)  
**Applicable Standards**: `PROJECT.md` (Interface Contract 1, Features 19-20), `AGENTS.md`  

---

## 1. Observation

### 1.1 Existing Implementation of `MealCookHandler.kt`
In `app/src/main/java/com/jeffers/pantrycraft/feature/meals/deduction/MealCookHandler.kt` (lines 16–63):
```kotlin
object MealCookHandler {

    suspend fun executeCookMeal(
        recipe: MealRecipe,
        pantryRepository: PantryRepository,
        groceryRepository: GroceryRepository,
        addMissingToGrocery: Boolean = true,
    ): CookMealResult {
        val currentPantry = pantryRepository.observePantryItems().first()
        var deductedCount = 0
        var wastePreventedCount = 0

        for (usage in recipe.itemsUsedFromStock) {
            val matchedItem: PantryItem? = if (usage.pantryItemId != null) {
                currentPantry.firstOrNull { it.id == usage.pantryItemId }
            } else {
                currentPantry.firstOrNull { it.name.equals(usage.name, ignoreCase = true) }
            }

            if (matchedItem != null && usage.quantityUsed > 0f) {
                pantryRepository.decrementQuantity(matchedItem, usage.quantityUsed)
                deductedCount++
                if (usage.preventsWaste) wastePreventedCount++
            }
        }

        var groceryAddedCount = 0
        if (addMissingToGrocery && recipe.missingItemsForGrocery.isNotEmpty()) {
            for (missing in recipe.missingItemsForGrocery) {
                groceryRepository.insertGrocery(
                    GroceryItem(
                        name = missing.name,
                        quantity = missing.quantityNeeded,
                        unit = missing.unit,
                        checked = false,
                    ),
                )
                groceryAddedCount++
            }
        }

        return CookMealResult(
            itemsDeductedCount = deductedCount,
            foodWastePreventedCount = wastePreventedCount,
            groceryItemsAddedCount = groceryAddedCount,
        )
    }
}
```
**Direct Observations**:
1. `executeCookMeal` takes only `(recipe, pantryRepository, groceryRepository, addMissingToGrocery)`. It does not accept `database: AppDatabase`.
2. It lacks any Room transaction context (`database.withTransaction { ... }`). Each call to `pantryRepository.decrementQuantity` and `groceryRepository.insertGrocery` executes as an independent, uncoordinated write. If an error occurs on item $N$, items $1 \dots N-1$ remain deducted without rollback.
3. It performs no unit volume conversion. `usage.quantityUsed` is passed directly to `decrementQuantity(matchedItem, usage.quantityUsed)` regardless of whether `usage.unit` differs from `matchedItem.unit` (e.g. 2 TBSP vs 2 CUP).
4. In `MealSynthesisViewModel.kt` (line 94), `cookMeal(recipe)` directly invokes `MealCookHandler.executeCookMeal(...)`.
5. In unit test suites (`Tier1FeatureCoverageTest.kt`, `Tier2BoundaryCornerCasesTest.kt`, `Tier3CrossFeatureCombinationsTest.kt`, `Tier4RealWorldScenariosTest.kt`, `MealCookHandlerTest.kt`), all 11 test cases call `MealCookHandler.executeCookMeal(...)` passing `InMemoryPantryRepository()`, an in-memory test double without an `AppDatabase`.

---

### 1.2 Data Layer & Room Transaction Primitives
1. **`app/build.gradle` (line 120)**:
   ```groovy
   implementation 'androidx.room:room-ktx:2.8.5'
   ```
   `androidx.room.withTransaction` is explicitly available in the runtime classpath.
2. **`AppDatabase.kt`**:
   Room database subclass declaring `pantryDao(): PantryDao` and `groceryDao(): GroceryDao`. Uses SQLCipher 4.19.1 with WAL concurrency mode (`setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)`), `PRAGMA busy_timeout = 5000`, and `PRAGMA mmap_size = 268435456`.
3. **`RoomDaos.kt` (lines 63–72)**:
   ```kotlin
   @Query(
       "UPDATE pantry_items SET quantity = CASE WHEN quantity - :delta < 0 THEN 0 " +
           "ELSE quantity - :delta END, updated_at = :updatedAt " +
           "WHERE id = :id AND isDeleted = 0",
   )
   suspend fun decrementQuantity(
       id: Long,
       delta: Float,
       updatedAt: Long = System.currentTimeMillis(),
   )
   ```
   The zero-floor query is already formulated in `PantryMutationDao` and sets `updated_at = :updatedAt`.
4. **`PantryRepository.kt` (lines 63–68)**:
   ```kotlin
   override suspend fun decrementQuantity(item: PantryItem, delta: Float) =
       require(delta > 0f && delta.isFinite()) {
           "Quantity decrement must be finite and positive"
       }.let {
           pantryDao.decrementQuantity(item.id, delta)
       }
   ```
   Both `DefaultPantryRepository` and `InMemoryPantryRepository` require `delta > 0f && delta.isFinite()`, throwing `IllegalArgumentException` otherwise.
5. **`GroceryRepository.kt` (line 27)**:
   `groceryDao.insert(item)` inserts a `GroceryItem` with `OnConflictStrategy.REPLACE`.
6. **`Repository.kt` (lines 5–13)**:
   ```kotlin
   class Repository private constructor(
       internal val db: AppDatabase,
       internal val appContext: Context,
       pantryRepo: PantryRepository = DefaultPantryRepository(
           db,
           appContext,
           DefaultGroceryRepository(db, appContext),
       ),
   ) : PantryRepository by pantryRepo
   ```
   In production, `Repository` exposes `internal val db: AppDatabase`, and implements `PantryRepository` (which extends `GroceryRepository`).
7. **`CulinaryScaling.kt` (lines 58–65)**:
   ```kotlin
   fun convertVolume(
       quantity: Float,
       fromUnit: String,
       toUnit: String,
   ): Float? {
       val mlValue = toMilliliters(quantity, fromUnit) ?: return null
       return fromMilliliters(mlValue, toUnit)
   }
   ```
   Converts between volume units (`TSP`, `TBSP`, `CUP`, `ML`, `L`). Returns `null` when units are incompatible or unrecognized.

---

## 2. Logic Chain

1. **Transaction Boundary Requirement**:
   `PROJECT.md § Interface Contract 1` specifies:
   ```kotlin
   suspend fun executeCookMealTransactional(
       database: AppDatabase,
       recipe: MealRecipe,
       pantryRepository: PantryRepository,
       groceryRepository: GroceryRepository,
       addMissingToGrocery: Boolean = true
   ): CookMealResult
   ```
   "Atomicity Guarantee: Executes inside `database.withTransaction { ... }`. If any database operation fails, the transaction is rolled back completely."
   - When `database.withTransaction { ... }` runs, Room binds a `TransactionElement` to the coroutine context.
   - Any DAO calls dispatched within that coroutine block (such as `pantryDao.decrementQuantity` and `groceryDao.insert`) automatically share the same SQLite connection and participate in that single transaction.
   - If an unhandled exception or SQL failure occurs anywhere in the block, `database.withTransaction` skips `setTransactionSuccessful()`, executes `endTransaction()` (triggering an SQLite `ROLLBACK`), and re-throws the exception.
   - Per `AGENTS.md § 2`, disk/database coroutines must be pinned to `Dispatchers.IO`. Therefore, `withContext(Dispatchers.IO) { database.withTransaction { ... } }` satisfies both SQLite atomicity and coroutine threading constraints.

2. **SQLite Zero-Floor Decrement Mechanics**:
   - The query in `PantryMutationDao`:
     ```sql
     UPDATE pantry_items
     SET quantity = CASE WHEN quantity - :delta < 0 THEN 0.0 ELSE quantity - :delta END,
         updated_at = :updatedAt
     WHERE id = :id AND isDeleted = 0
     ```
   - Because the conditional evaluation `CASE WHEN quantity - :delta < 0 THEN 0.0 ELSE quantity - :delta END` executes directly in the SQLite virtual database engine under SQLite WAL writer serialization, it eliminates race conditions:
     - If stock has 500g and 150g is deducted: `500 - 150 = 350 >= 0` -> sets quantity to `350.0`.
     - If stock has 100g and 250g is deducted: `100 - 250 = -150 < 0` -> sets quantity to `0.0`.
     - Quantity can never become negative, satisfying Feature 19.
     - `updated_at = :updatedAt` tracks the modification timestamp, satisfying Feature 19 and data synchronization requirements.
     - Soft-deleted items (`isDeleted = 1`) are untouched.
   - Because `PantryRepository.decrementQuantity` asserts `delta > 0f && delta.isFinite()`, `MealCookHandler` must only execute decrements when `quantityUsed > 0f && quantityUsed.isFinite()` and the converted `delta > 0f && delta.isFinite()`. Zero or negative quantities must be skipped without error, matching `Tier2BoundaryCornerCasesTest.kt`.

3. **Culinary Unit Volume Normalization**:
   - Recipe ingredients (`RecipePantryUsage`) contain `quantityUsed: Float` and optional `unit: String?`.
   - Pantry items (`PantryItem`) contain `quantity: Float` and optional `unit: String?`.
   - When `usage.unit` differs from `matchedItem.unit` (e.g. `recipeUnit = "TBSP"`, `stockUnit = "CUP"`):
     - `CulinaryScaling.convertVolume(usage.quantityUsed, recipeUnit, stockUnit)` is invoked.
     - If `convertVolume` returns a valid Float, `delta` is set to this converted amount.
     - If `convertVolume` returns `null` (incompatible dimensions, such as "G" to "ML" or discrete items like "COUNT" to "TSP"), the handler falls back to raw quantity deduction with a warning, fulfilling `PROJECT.md § Interface Contract 1`.
   - Whitespace and case-sensitivity are normalized by trimming and case-insensitive comparison (`recipeUnit.trim().equals(stockUnit.trim(), ignoreCase = true)`).

4. **Missing Ingredient Mapping & Insertion**:
   - `recipe.missingItemsForGrocery` contains `RecipeMissingItem(name, quantityNeeded, unit, estimatedCostCents)`.
   - When `addMissingToGrocery == true`, each item is mapped to `GroceryItem`:
     ```kotlin
     GroceryItem(
         name = missing.name,
         quantity = if (missing.quantityNeeded > 0f && missing.quantityNeeded.isFinite()) missing.quantityNeeded else 1f,
         unit = missing.unit,
         checked = false,
     )
     ```
   - Items with blank names are skipped.
   - If `addMissingToGrocery == false`, no grocery items are inserted, and `groceryItemsAddedCount` returns `0`.

5. **Dual Interface Compatibility (Contract 1 + Backwards Compatibility)**:
   - To strictly fulfill `PROJECT.md § Interface Contract 1`, `MealCookHandler` must expose:
     ```kotlin
     suspend fun executeCookMealTransactional(
         database: AppDatabase,
         recipe: MealRecipe,
         pantryRepository: PantryRepository,
         groceryRepository: GroceryRepository,
         addMissingToGrocery: Boolean = true,
     ): CookMealResult
     ```
   - To preserve backward compatibility for existing tests using `InMemoryPantryRepository` (which has no `AppDatabase`) and `MealSynthesisViewModel`:
     ```kotlin
     suspend fun executeCookMeal(
         recipe: MealRecipe,
         pantryRepository: PantryRepository,
         groceryRepository: GroceryRepository,
         addMissingToGrocery: Boolean = true,
         database: AppDatabase? = (pantryRepository as? Repository)?.db,
     ): CookMealResult
     ```
     - If `database != null`, it delegates to `executeCookMealTransactional(database, ...)`.
     - If `database == null` (as in in-memory test harnesses), it runs the same core deduction logic on `Dispatchers.IO` without `database.withTransaction`.

---

## 3. Caveats

1. **Cross-Dimensional Unit Conversion**: `CulinaryScaling.convertVolume` currently supports volume-to-volume conversions (`TSP`, `TBSP`, `CUP`, `ML`, `L`). Mass-to-volume conversions (e.g. grams of flour to cups of flour) require ingredient density lookups and are handled via graceful fallback to raw quantity deduction per Interface Contract 1.
2. **Multiple Pantry Items with Same Name**: If the user has duplicate active pantry items with the identical normalized name and no `pantryItemId` is specified in `RecipePantryUsage`, the first item returned by `observePantryItems().first()` is selected. In practice, synthesis engines attach `pantryItemId` directly from on-hand inventory.
3. **Detekt and Android Logging**: Standard Android `Log.w` is avoided or guarded by `runCatching` to prevent test runner mock crashes during headless JVM unit testing (`./gradlew testDebugUnitTest`).

---

## 4. Conclusion & Proposed Architecture

### 4.1 Interface Contract 1 Architecture Design
The refactored `MealCookHandler.kt` provides:
1. `executeCookMealTransactional`: Strict implementation of `PROJECT.md § Interface Contract 1` with `database.withTransaction` rollback protection.
2. `executeCookMeal`: Overloaded entry point providing automatic database resolution from `Repository.db` and fallback execution for in-memory unit tests.
3. Thread pinning to `Dispatchers.IO`.
4. Automated unit volume normalization via `CulinaryScaling.convertVolume`.
5. Atomic zero-floor stock deduction and grocery insertion.

### 4.2 Proposed Code Implementation for `MealCookHandler.kt`

```kotlin
package com.jeffers.pantrycraft.feature.meals.deduction

import androidx.room.withTransaction
import com.jeffers.pantrycraft.data.AppDatabase
import com.jeffers.pantrycraft.data.CulinaryScaling
import com.jeffers.pantrycraft.data.GroceryItem
import com.jeffers.pantrycraft.data.GroceryRepository
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.PantryRepository
import com.jeffers.pantrycraft.data.Repository
import com.jeffers.pantrycraft.feature.meals.model.MealRecipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class CookMealResult(
    val itemsDeductedCount: Int,
    val foodWastePreventedCount: Int,
    val groceryItemsAddedCount: Int,
)

object MealCookHandler {

    /**
     * Executes atomic stock deduction and grocery insertion within a Room transaction.
     * Complies strictly with PROJECT.md Interface Contract 1.
     */
    suspend fun executeCookMealTransactional(
        database: AppDatabase,
        recipe: MealRecipe,
        pantryRepository: PantryRepository,
        groceryRepository: GroceryRepository,
        addMissingToGrocery: Boolean = true,
    ): CookMealResult = withContext(Dispatchers.IO) {
        database.withTransaction {
            executeDeductionLogic(
                recipe = recipe,
                pantryRepository = pantryRepository,
                groceryRepository = groceryRepository,
                addMissingToGrocery = addMissingToGrocery,
            )
        }
    }

    /**
     * Backward-compatible entry point. Automatically resolves [AppDatabase] if available;
     * falls back to in-memory execution for unit test doubles.
     */
    suspend fun executeCookMeal(
        recipe: MealRecipe,
        pantryRepository: PantryRepository,
        groceryRepository: GroceryRepository,
        addMissingToGrocery: Boolean = true,
        database: AppDatabase? = (pantryRepository as? Repository)?.db,
    ): CookMealResult {
        return if (database != null) {
            executeCookMealTransactional(
                database = database,
                recipe = recipe,
                pantryRepository = pantryRepository,
                groceryRepository = groceryRepository,
                addMissingToGrocery = addMissingToGrocery,
            )
        } else {
            withContext(Dispatchers.IO) {
                executeDeductionLogic(
                    recipe = recipe,
                    pantryRepository = pantryRepository,
                    groceryRepository = groceryRepository,
                    addMissingToGrocery = addMissingToGrocery,
                )
            }
        }
    }

    private suspend fun executeDeductionLogic(
        recipe: MealRecipe,
        pantryRepository: PantryRepository,
        groceryRepository: GroceryRepository,
        addMissingToGrocery: Boolean,
    ): CookMealResult {
        val currentPantry = pantryRepository.observePantryItems().first()
        var deductedCount = 0
        var wastePreventedCount = 0

        for (usage in recipe.itemsUsedFromStock) {
            if (usage.quantityUsed <= 0f || !usage.quantityUsed.isFinite()) {
                continue
            }

            val matchedItem: PantryItem? = if (usage.pantryItemId != null) {
                currentPantry.firstOrNull { it.id == usage.pantryItemId }
            } else {
                currentPantry.firstOrNull { it.name.equals(usage.name.trim(), ignoreCase = true) }
            }

            if (matchedItem == null) {
                continue
            }

            val delta = resolveDeductionDelta(
                quantityUsed = usage.quantityUsed,
                recipeUnit = usage.unit,
                stockUnit = matchedItem.unit,
            )

            if (delta > 0f && delta.isFinite()) {
                pantryRepository.decrementQuantity(matchedItem, delta)
                deductedCount++
                if (usage.preventsWaste) {
                    wastePreventedCount++
                }
            }
        }

        var groceryAddedCount = 0
        if (addMissingToGrocery && recipe.missingItemsForGrocery.isNotEmpty()) {
            for (missing in recipe.missingItemsForGrocery) {
                if (missing.name.isBlank()) continue
                val qty = if (missing.quantityNeeded > 0f && missing.quantityNeeded.isFinite()) {
                    missing.quantityNeeded
                } else {
                    1f
                }
                groceryRepository.insertGrocery(
                    GroceryItem(
                        name = missing.name.trim(),
                        quantity = qty,
                        unit = missing.unit?.trim(),
                        checked = false,
                    ),
                )
                groceryAddedCount++
            }
        }

        return CookMealResult(
            itemsDeductedCount = deductedCount,
            foodWastePreventedCount = wastePreventedCount,
            groceryItemsAddedCount = groceryAddedCount,
        )
    }

    private fun resolveDeductionDelta(
        quantityUsed: Float,
        recipeUnit: String?,
        stockUnit: String?,
    ): Float {
        val recipeU = recipeUnit?.trim()?.takeIf { it.isNotEmpty() }
        val stockU = stockUnit?.trim()?.takeIf { it.isNotEmpty() }

        if (recipeU == null || stockU == null || recipeU.equals(stockU, ignoreCase = true)) {
            return quantityUsed
        }

        val converted = CulinaryScaling.convertVolume(
            quantity = quantityUsed,
            fromUnit = recipeU,
            toUnit = stockU,
        )

        return converted ?: quantityUsed
    }
}
```

---

## 5. Verification Method

To independently verify this investigation and the proposed implementation:

1. **Compilation & Unit Test Execution**:
   Run the Gradle test suite to verify existing tests remain green:
   ```bash
   ./gradlew testDebugUnitTest
   ```
2. **Detekt Static Analysis**:
   Verify code style, line length ($\le 120$ characters), complexity ($\le 15$), and rules:
   ```bash
   ./gradlew detekt detektTest detektDebugUnitTest detektDebugAndroidTest
   ```
3. **Transaction Rollback Test Case**:
   Create a test using an in-memory Room database (`Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()`) where an exception is deliberately thrown during the second ingredient decrement or grocery insertion:
   - Verify that all pantry items retain their pre-cooking quantities.
   - Verify that 0 grocery items were inserted.
4. **Volume Unit Conversion Test Cases**:
   - Recipe calls for `2 TBSP`, pantry item has unit `CUP` -> delta decremented is `0.125f CUP`.
   - Recipe calls for `1 CUP`, pantry item has unit `ML` -> delta decremented is `240f ML`.
   - Recipe calls for `500 G`, pantry item has unit `ML` (inconvertible) -> falls back to `500f` without crashing.
5. **Zero-Floor Protection Verification**:
   - Stock has 10 units, recipe deducts 15 units -> remaining stock in SQLite is exactly `0.0`.
   - Stock has 0 units, recipe deducts 5 units -> remaining stock in SQLite is `0.0`.
   - Recipe has `quantityUsed = 0f` or `-5f` -> skipped, 0 items decremented.
6. **Invalidation Conditions**:
   - If `AppDatabase` schema version or `pantry_items` table structure changes column types or names.
   - If `androidx.room:room-ktx` dependency is removed or superseded.
