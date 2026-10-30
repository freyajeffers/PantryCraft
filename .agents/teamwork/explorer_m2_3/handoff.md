# Milestone 2 QA & Test Specification: Atomic Cooking Deduction

## 1. Observation

### 1.1 Existing Tests in `MealCookHandlerTest.kt`
In `app/src/test/java/com/jeffers/pantrycraft/feature/meals/deduction/MealCookHandlerTest.kt`:
- Lines 16–47 define only a single test method: `executeCookMealDeductsPantryAndInsertsGrocery()`.
- Lines 49–69 define `FakePantryRepository`:
  ```kotlin
  override suspend fun decrementQuantity(item: PantryItem, delta: Float) {
      lastDecrementedDelta = delta
  }
  ```
  `FakePantryRepository` does not update the underlying state of items in `flow.value`. It records `lastDecrementedDelta`, but does not test whether quantity is reduced, whether it clamps at zero, or how subsequent queries see the mutated stock.
- `FakeGroceryRepository` (lines 71–83) records `insertedItems.add(item)`.
- There are no tests in `MealCookHandlerTest.kt` covering:
  - `executeCookMealTransactional` with atomic transaction execution or rollback.
  - Zero-floor quantity decrement protection (e.g. usage > on-hand).
  - Culinary unit volume conversions (e.g. recipe `TBSP` vs pantry `ML`, `CUP` vs `ML`, `TSP` vs `TBSP`, `L` vs `ML`).
  - Unit conversion fallbacks for incompatible units (e.g. mass `g` vs volume `ml`).
  - Disabled grocery insertion (`addMissingToGrocery = false`).
  - Portion validation, zero portions, portion scaling boundaries.
  - Disambiguation of multiple pantry items by ID vs name.
  - Zero on-hand items / empty inventory behavior.
  - Zero, negative, or non-finite ingredient usage quantities.

### 1.2 Current `MealCookHandler.kt` Implementation
In `app/src/main/java/com/jeffers/pantrycraft/feature/meals/deduction/MealCookHandler.kt`:
- Lines 18–63:
  ```kotlin
  object MealCookHandler {
      suspend fun executeCookMeal(
          recipe: MealRecipe,
          pantryRepository: PantryRepository,
          groceryRepository: GroceryRepository,
          addMissingToGrocery: Boolean = true,
      ): CookMealResult {
  ```
- Line 36 directly decrements the raw unscaled quantity without unit conversion:
  ```kotlin
  pantryRepository.decrementQuantity(matchedItem, usage.quantityUsed)
  ```
  If `usage.unit` is `"TBSP"` and `matchedItem.unit` is `"ML"`, it subtracts `quantityUsed` directly (e.g., 2 instead of 30 mL), producing an order-of-magnitude error.
- There is no transactional wrapper (`database.withTransaction { ... }`), nor does `executeCookMealTransactional` exist yet as specified by `PROJECT.md` Interface Contract 1:
  ```kotlin
  suspend fun executeCookMealTransactional(
      database: AppDatabase,
      recipe: MealRecipe,
      pantryRepository: PantryRepository,
      groceryRepository: GroceryRepository,
      addMissingToGrocery: Boolean = true
  ): CookMealResult
  ```

### 1.3 Volume Conversions in `CulinaryScaling.kt`
In `app/src/main/java/com/jeffers/pantrycraft/data/CulinaryScaling.kt`:
- Lines 11–19 and 58–86 define:
  ```kotlin
  const val ML_PER_TSP = 5f
  const val ML_PER_TBSP = 15f
  const val ML_PER_CUP = 240f
  private const val ML_PER_LITER = 1000f
  ```
- `convertVolume(quantity: Float, fromUnit: String, toUnit: String): Float?` converts between `TSP`, `TBSP`, `CUP`, `ML`, `L`, returning `null` when units are incompatible (e.g. `GRAMS` to `ML`) or unrecognized.

### 1.4 Zero-Floor SQLite Implementation
In `app/src/main/java/com/jeffers/pantrycraft/data/RoomDaos.kt`:
- Lines 63–72 of `PantryMutationDao`:
  ```sql
  UPDATE pantry_items SET quantity = CASE WHEN quantity - :delta < 0 THEN 0
      ELSE quantity - :delta END, updated_at = :updatedAt
      WHERE id = :id AND isDeleted = 0
  ```
- In `DefaultPantryRepository.kt` (lines 63–68):
  ```kotlin
  override suspend fun decrementQuantity(item: PantryItem, delta: Float) =
      require(delta > 0f && delta.isFinite()) {
          "Quantity decrement must be finite and positive"
      }.let {
          pantryDao.decrementQuantity(item.id, delta)
      }
  ```
- In `app/src/test/java/com/jeffers/pantrycraft/e2e/E2ETestHarness.kt` (lines 118–132):
  ```kotlin
  val newQty = (existing.quantity - delta).coerceAtLeast(0f)
  ```

### 1.5 Transaction Architecture & Verification in Existing Tests
- `app/src/androidTest/java/com/jeffers/pantrycraft/data/PantryDaoTransactionTest.kt` (lines 43–64):
  `db.withTransaction { ... error("Simulated atomic failure") }` verifies in physical SQLite/SQLCipher instrumentation tests that uncommitted inserts are rolled back.
- In JVM unit tests (`app/src/test`), `AppDatabase.Companion` triggers `System.loadLibrary("sqlcipher")`, which is unavailable on host desktop Linux (`no sqlcipher in java.library.path`). Therefore, JVM unit tests utilize repository fakes or transactional doubles (`InMemoryPantryRepository`, `FakePantryRepository`).

### 1.6 Static Analysis Baseline
Running `./gradlew detekt detektTest detektDebugUnitTest detektDebugAndroidTest`:
- 38 tasks executed / up-to-date, exit code 0, 0 errors, 0 warnings.
- `config/detekt.yml` applies `buildUponDefaultConfig = true`, enforcing:
  - MaxLineLength: 120 characters
  - Cyclomatic complexity: <= 15 per function
  - Method length: <= 60 lines
  - Zero warnings allowed across `main`, `test`, and `androidTest`.

---

## 2. Logic Chain

1. **Transaction Atomicity Gap**:
   - `MealCookHandler` currently executes isolated calls to `pantryRepository.decrementQuantity` and `groceryRepository.insertGrocery`.
   - If an error occurs halfway through (e.g., during grocery insertion or decrementing a subsequent item), earlier decrements remain applied, leaving the user's pantry in an inconsistent, partially deducted state.
   - Interface Contract 1 requires wrapping the cooking deduction within `database.withTransaction { ... }` in `executeCookMealTransactional`.
   - To preserve backward compatibility with existing tests and callers (`MealSynthesisViewModel`, E2E test tiers 1–4), the core deduction algorithm must be decoupled from the transaction runner, allowing execution with or without a database handle.

2. **Unit Volume Conversion Necessity**:
   - Recipe ingredients generated by AI or standard recipes specify culinary units such as `TSP`, `TBSP`, `CUP`, `ML`, `L`.
   - On-hand pantry stock is frequently tracked in different volume units (e.g., recipe calls for 2 TBSP olive oil, but pantry tracks 500 ML).
   - Deducting the raw float 2 from 500 ML results in 498 ML remaining instead of 470 ML remaining (under-deducting by a factor of 15).
   - `MealCookHandler` must invoke `CulinaryScaling.convertVolume(usage.quantityUsed, recipeUnit, stockUnit)` whenever `recipeUnit` differs from `stockUnit`.
   - If `convertVolume` returns `null` (e.g. recipe specifies 100g, but pantry has 200ml), Interface Contract 1 mandates falling back to raw quantity deduction with a logged warning, preventing cooking operations from crashing.

3. **Zero-Floor Protection Verification**:
   - Even when a recipe calls for more than what is on hand (e.g., recipe uses 200g, pantry has 50g), the inventory ledger must never record negative quantities.
   - `PantryMutationDao` enforces this in SQLite via `CASE WHEN quantity - :delta < 0 THEN 0 ELSE quantity - :delta END`.
   - In-memory unit test doubles must mirror this invariant using `(existing.quantity - delta).coerceAtLeast(0f)`.
   - Unit tests must verify that exact depletion (50g - 50g = 0g), excess depletion (50g - 200g = 0g), and zero depletion (0g - 10g = 0g) all terminate at 0.0 without errors or negative stock.

4. **Missing Ingredient Auto-Grocery Workflow**:
   - When a synthesized meal requires missing staples (contained in `recipe.missingItemsForGrocery`), `executeCookMeal` must insert each item into `groceryRepository` with `checked = false`.
   - When `addMissingToGrocery = false`, zero items must be inserted.
   - When `missingItemsForGrocery` is empty, zero items must be inserted.
   - The returned `CookMealResult.groceryItemsAddedCount` must accurately report the count of inserted items.

5. **Edge Case Safety & Portions**:
   - Zero or negative usage (`quantityUsed <= 0f`) must be ignored to prevent `IllegalArgumentException` in `decrementQuantity` (`require(delta > 0f && delta.isFinite())`).
   - Multiple items with identical names must be disambiguated by `pantryItemId`. If `pantryItemId` is specified and exists, it takes precedence. If null, matching by normalized name is used.
   - If pantry is empty, deduction must proceed gracefully without crashing, recording 0 items deducted.
   - Portions scaling must adhere to `MIN_PORTIONS` (1) to `MAX_PORTIONS` (12); scaling to 0 portions throws `IllegalArgumentException`.

6. **Detekt Compliance Architecture**:
   - Nesting loops, `when` blocks for unit conversions, and grocery insertions in a single method would exceed cyclomatic complexity 15 and 60 lines.
   - Decomposing the deduction into dedicated helpers (`resolveMatchedPantryItem`, `calculateDeductionDelta`, `deductStockItems`, `insertMissingGroceryItems`) guarantees that no method exceeds 15 complexity or 40 lines, and keeps all lines <= 120 characters.

---

## 3. Caveats

- **JVM vs AndroidTest Execution for `database.withTransaction`**:
  - `AppDatabase` cannot be instantiated directly in JVM unit tests (`testDebugUnitTest`) because SQLCipher's native C library is not available in the host Linux environment without an Android runtime or Robolectric.
  - Therefore, transactional rollback verification is split across two tiers:
    1. Pure JVM unit tests in `MealCookHandlerTest.kt` using a transactional repository test double (`TransactionalFakePantryRepository` / `FakePantryRepository`) that supports rollback simulation upon failure.
    2. Physical/emulator instrumentation tests in `app/src/androidTest/` (`MealCookHandlerAndroidTest.kt` or `PantryDaoTransactionTest.kt`) that execute real SQLCipher `database.withTransaction { ... }` transactions.
- **Mass vs Volume Conversions**:
  - `CulinaryScaling.convertVolume` converts volume-to-volume (`TSP`, `TBSP`, `CUP`, `ML`, `L`). It does not convert mass-to-volume without ingredient density lookup. Per Interface Contract 1, incompatible conversions fall back to raw quantity deduction.

---

## 4. Conclusion & Test Suite Specification

### 4.1 Enhanced Test Double Design for `MealCookHandlerTest.kt`

To enable testing of all M2 deduction behaviors in pure JVM unit tests without Android dependencies, `FakePantryRepository` and `FakeGroceryRepository` should be upgraded with state tracking, zero-floor clamping, and transaction simulation:

```kotlin
private class FakePantryRepository(
    initial: List<PantryItem> = emptyList(),
) : PantryRepository {
    private val items = initial.map { it.copy() }.toMutableList()
    private val snapshotItems = mutableListOf<PantryItem>()
    val flow = MutableStateFlow<List<PantryItem>>(items.toList())
    var lastDecrementedDelta: Float? = null
    var failOnDecrementItemName: String? = null
    var inTransaction: Boolean = false

    fun beginTransaction() {
        inTransaction = true
        snapshotItems.clear()
        snapshotItems.addAll(items.map { it.copy() })
    }

    fun commitTransaction() {
        inTransaction = false
        snapshotItems.clear()
    }

    fun rollbackTransaction() {
        inTransaction = false
        items.clear()
        items.addAll(snapshotItems.map { it.copy() })
        flow.value = items.toList()
    }

    override fun observePantryItems(): Flow<List<PantryItem>> = flow
    override fun searchPantryItems(query: String): Flow<List<PantryItem>> = flow
    override suspend fun insertPantry(item: PantryItem): Long = 1L
    override suspend fun updatePantry(item: PantryItem) { /* no-op */ }

    override suspend fun decrementQuantity(item: PantryItem, delta: Float) {
        require(delta > 0f && delta.isFinite()) { "Quantity decrement must be finite and positive" }
        if (failOnDecrementItemName != null && item.name.equals(failOnDecrementItemName, ignoreCase = true)) {
            error("Simulated database failure during decrement of ${item.name}")
        }
        lastDecrementedDelta = delta
        val index = items.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            val existing = items[index]
            val clamped = (existing.quantity - delta).coerceAtLeast(0f)
            items[index] = existing.copy(quantity = clamped)
            flow.value = items.toList()
        }
    }

    override suspend fun deletePantry(item: PantryItem) { /* no-op */ }
    override suspend fun restorePantry(item: PantryItem) { /* no-op */ }
    override fun observeGroceryItems(): Flow<List<GroceryItem>> = MutableStateFlow(emptyList())
    override suspend fun insertGrocery(item: GroceryItem): Long = 1L
    override suspend fun updateGrocery(item: GroceryItem) { /* no-op */ }
    override suspend fun deleteGrocery(item: GroceryItem) { /* no-op */ }
    override suspend fun clearCheckedGrocery() { /* no-op */ }
    override suspend fun restockCheckedGroceryToPantry(): Int = 0
    override fun reclaimTemporaryMedia(): Int = 0
}

private class FakeGroceryRepository : GroceryRepository {
    val insertedItems = mutableListOf<GroceryItem>()
    private val snapshotItems = mutableListOf<GroceryItem>()
    var failOnInsert: Boolean = false
    var inTransaction: Boolean = false

    fun beginTransaction() {
        inTransaction = true
        snapshotItems.clear()
        snapshotItems.addAll(insertedItems.map { it.copy() })
    }

    fun commitTransaction() {
        inTransaction = false
        snapshotItems.clear()
    }

    fun rollbackTransaction() {
        inTransaction = false
        insertedItems.clear()
        insertedItems.addAll(snapshotItems.map { it.copy() })
    }

    override fun observeGroceryItems(): Flow<List<GroceryItem>> = MutableStateFlow(insertedItems.toList())
    override suspend fun insertGrocery(item: GroceryItem): Long {
        if (failOnInsert) {
            error("Simulated disk error inserting grocery item: ${item.name}")
        }
        insertedItems.add(item)
        return insertedItems.size.toLong()
    }
    override suspend fun updateGrocery(item: GroceryItem) { /* no-op */ }
    override suspend fun deleteGrocery(item: GroceryItem) { /* no-op */ }
    override suspend fun clearCheckedGrocery() { /* no-op */ }
    override suspend fun restockCheckedGroceryToPantry(): Int = 0
    override fun reclaimTemporaryMedia(): Int = 0
}
```

---

### 4.2 Comprehensive Unit Test Matrix (33 Test Cases)

| # | Test Name | Category | Given | When | Then / Assertions |
|---|-----------|----------|-------|------|-------------------|
| 1 | `transactionCommitPersistsAllDeductionsAndGroceries` | Atomicity | Rice (500g), missing Oil (1 CAN) | `executeCookMeal` succeeds | Rice = 350g, 1 grocery item inserted, counts match |
| 2 | `transactionRollbackRevertsDeductionWhenGroceryFails` | Atomicity | Rice (500g), `failOnInsert = true` | `executeCookMeal` throws | Rice remains 500g, 0 groceries in repo, exception propagated |
| 3 | `transactionRollbackRevertsDeductionsWhenSecondItemFails` | Atomicity | Rice (500g), Milk (1L), Milk fails | `executeCookMeal` throws | Rice remains 500g, Milk remains 1L, 0 groceries inserted |
| 4 | `decrementExactQuantityReducesStockToZero` | Zero Floor | Flour (100g), usage 100g | Cook meal | Flour = 0g, `itemsDeductedCount = 1` |
| 5 | `decrementExcessQuantityClampsStockAtZero` | Zero Floor | Sugar (50g), usage 150g | Cook meal | Sugar = 0g (not -100g), `itemsDeductedCount = 1` |
| 6 | `decrementWhenStockAlreadyZeroRemainsZero` | Zero Floor | Salt (0g), usage 10g | Cook meal | Salt = 0g, `itemsDeductedCount = 1` |
| 7 | `decrementMultipleItemsWithPartialAndZeroFloorDepletion` | Zero Floor | Item A (100g, uses 40g), Item B (20g, uses 50g) | Cook meal | Item A = 60g, Item B = 0g, `itemsDeductedCount = 2` |
| 8 | `unitConversionRecipeTspToPantryMl` | Volume Conversion | Vanilla (50 ML), usage 2 TSP | Cook meal | Delta = 10 ML (2 * 5), remaining = 40 ML |
| 9 | `unitConversionRecipeTbspToPantryMl` | Volume Conversion | Olive Oil (100 ML), usage 3 TBSP | Cook meal | Delta = 45 ML (3 * 15), remaining = 55 ML |
| 10 | `unitConversionRecipeCupToPantryMl` | Volume Conversion | Milk (500 ML), usage 0.5 CUP | Cook meal | Delta = 120 ML (0.5 * 240), remaining = 380 ML |
| 11 | `unitConversionRecipeLiterToPantryMl` | Volume Conversion | Stock (1000 ML), usage 0.5 L | Cook meal | Delta = 500 ML (0.5 * 1000), remaining = 500 ML |
| 12 | `unitConversionRecipeMlToPantryCup` | Volume Conversion | Water (2 CUP), usage 240 ML | Cook meal | Delta = 1 CUP, remaining = 1 CUP |
| 13 | `unitConversionRecipeMlToPantryTbsp` | Volume Conversion | Soy Sauce (5 TBSP), usage 30 ML | Cook meal | Delta = 2 TBSP, remaining = 3 TBSP |
| 14 | `unitConversionRecipeMlToPantryTsp` | Volume Conversion | Lemon Juice (10 TSP), usage 15 ML | Cook meal | Delta = 3 TSP, remaining = 7 TSP |
| 15 | `unitConversionRecipeMlToPantryLiter` | Volume Conversion | Broth (1.0 L), usage 500 ML | Cook meal | Delta = 0.5 L, remaining = 0.5 L |
| 16 | `unitConversionRecipeTbspToPantryTsp` | Volume Conversion | Spice (10 TSP), usage 2 TBSP | Cook meal | Delta = 6 TSP (30 ML / 5), remaining = 4 TSP |
| 17 | `unitConversionRecipeCupToPantryTbsp` | Volume Conversion | Butter (8 TBSP), usage 0.25 CUP | Cook meal | Delta = 4 TBSP (60 ML / 15), remaining = 4 TBSP |
| 18 | `unitConversionIdenticalUnitsDoesNotRescale` | Volume Conversion | Milk (500 ML), usage 250 ML | Cook meal | Delta = 250 ML, remaining = 250 ML |
| 19 | `unitConversionCaseInsensitiveMatching` | Volume Conversion | Cream (500 "ml"), usage 1 "CUP" | Cook meal | Delta = 240 ML, remaining = 260 ML |
| 20 | `incompatibleUnitConversionFallsBackToRawQuantity` | Volume Fallback | Flour (500 "ml"), usage 100 "g" | Cook meal | Delta = 100f (fallback), remaining = 400 ML |
| 21 | `nullUnitInRecipeFallsBackToRawQuantity` | Volume Fallback | Sugar (500g), usage 150 (unit null) | Cook meal | Delta = 150f, remaining = 350g |
| 22 | `nullUnitInPantryFallsBackToRawQuantity` | Volume Fallback | Bread (10, unit null), usage 2 "SLICES" | Cook meal | Delta = 2f, remaining = 8 |
| 23 | `missingItemsInsertedWhenAddMissingIsTrue` | Missing Grocery | 2 missing items, `addMissingToGrocery = true` | Cook meal | 2 items in grocery, `checked = false`, names/units match |
| 24 | `missingItemsSkippedWhenAddMissingIsFalse` | Missing Grocery | 2 missing items, `addMissingToGrocery = false` | Cook meal | 0 items in grocery, `groceryItemsAddedCount = 0` |
| 25 | `emptyMissingItemsResultsInZeroGroceryInserts` | Missing Grocery | Missing items list empty | Cook meal | 0 items in grocery, `groceryItemsAddedCount = 0` |
| 26 | `foodWastePreventionCountCorrectlyTracked` | Metrics | 2 usages with `preventsWaste = true`, 1 false | Cook meal | `foodWastePreventedCount = 2` |
| 27 | `zeroOrNegativeUsageQuantityIgnored` | Edge Case | Usages with `quantityUsed = 0f` and `-5f` | Cook meal | `itemsDeductedCount = 0`, pantry stock unchanged |
| 28 | `pantryEmptyCausesNoDeductionsWithoutCrashing` | Edge Case | Pantry is empty, recipe has usages | Cook meal | `itemsDeductedCount = 0`, no crash, grocery items added |
| 29 | `multipleMatchingItemsDisambiguatedByPantryItemId` | Edge Case | Two "Eggs" (ID 101: 6, ID 102: 12), usage ID 102 | Cook meal | ID 102 decremented to 10; ID 101 remains 6 |
| 30 | `matchingByNameWhenPantryItemIdIsNull` | Edge Case | "Cheddar Cheese" in stock, usage has name only | Cook meal | Name matched case-insensitively, decremented |
| 31 | `pantryItemIdNotFoundDoesNotDeduct` | Edge Case | Usage specifies `pantryItemId = 9999L` (absent) | Cook meal | `itemsDeductedCount = 0`, no crash |
| 32 | `recipeScalePortionsEnforcesBounds` | Portion Bounds | Recipe scale to 0 portions or 13 portions | `scaleToPortions` | Throws `IllegalArgumentException` |
| 33 | `recipeScaledPortionsCorrectlyDeductsMultipliedQuantities` | Portion Scaling | Recipe 2 portions (100g) scaled to 4 portions | Scale & Cook | Deducts 200g, remaining stock updated accurately |

---

### 4.3 Proposed Code Architecture for `MealCookHandler.kt`

To guarantee 100% compliance with Interface Contract 1, Detekt rules (cyclomatic complexity <= 15, method length <= 60 lines, line length <= 120 chars), and backward compatibility:

```kotlin
package com.jeffers.pantrycraft.feature.meals.deduction

import androidx.room.withTransaction
import com.jeffers.pantrycraft.data.AppDatabase
import com.jeffers.pantrycraft.data.CulinaryScaling
import com.jeffers.pantrycraft.data.GroceryItem
import com.jeffers.pantrycraft.data.GroceryRepository
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.PantryRepository
import com.jeffers.pantrycraft.feature.meals.model.MealRecipe
import com.jeffers.pantrycraft.feature.meals.model.RecipeMissingItem
import com.jeffers.pantrycraft.feature.meals.model.RecipePantryUsage
import kotlinx.coroutines.flow.first

data class CookMealResult(
    val itemsDeductedCount: Int,
    val foodWastePreventedCount: Int,
    val groceryItemsAddedCount: Int,
)

object MealCookHandler {

    suspend fun executeCookMealTransactional(
        database: AppDatabase,
        recipe: MealRecipe,
        pantryRepository: PantryRepository,
        groceryRepository: GroceryRepository,
        addMissingToGrocery: Boolean = true,
    ): CookMealResult = database.withTransaction {
        executeCookMealInternal(recipe, pantryRepository, groceryRepository, addMissingToGrocery)
    }

    suspend fun executeCookMeal(
        recipe: MealRecipe,
        pantryRepository: PantryRepository,
        groceryRepository: GroceryRepository,
        addMissingToGrocery: Boolean = true,
    ): CookMealResult = executeCookMealInternal(
        recipe = recipe,
        pantryRepository = pantryRepository,
        groceryRepository = groceryRepository,
        addMissingToGrocery = addMissingToGrocery,
    )

    private suspend fun executeCookMealInternal(
        recipe: MealRecipe,
        pantryRepository: PantryRepository,
        groceryRepository: GroceryRepository,
        addMissingToGrocery: Boolean,
    ): CookMealResult {
        val currentPantry = pantryRepository.observePantryItems().first()
        val (deductedCount, wastePreventedCount) = deductStockItems(
            currentPantry = currentPantry,
            itemsUsed = recipe.itemsUsedFromStock,
            pantryRepository = pantryRepository,
        )

        val groceryAddedCount = if (addMissingToGrocery && recipe.missingItemsForGrocery.isNotEmpty()) {
            insertMissingGroceryItems(recipe.missingItemsForGrocery, groceryRepository)
        } else {
            0
        }

        return CookMealResult(
            itemsDeductedCount = deductedCount,
            foodWastePreventedCount = wastePreventedCount,
            groceryItemsAddedCount = groceryAddedCount,
        )
    }

    private suspend fun deductStockItems(
        currentPantry: List<PantryItem>,
        itemsUsed: List<RecipePantryUsage>,
        pantryRepository: PantryRepository,
    ): Pair<Int, Int> {
        var deductedCount = 0
        var wastePreventedCount = 0

        for (usage in itemsUsed) {
            if (usage.quantityUsed <= 0f) continue
            val matchedItem = resolveMatchedItem(currentPantry, usage) ?: continue
            val delta = calculateDeductionDelta(usage, matchedItem)

            if (delta > 0f && delta.isFinite()) {
                pantryRepository.decrementQuantity(matchedItem, delta)
                deductedCount++
                if (usage.preventsWaste) {
                    wastePreventedCount++
                }
            }
        }
        return Pair(deductedCount, wastePreventedCount)
    }

    private fun resolveMatchedItem(pantry: List<PantryItem>, usage: RecipePantryUsage): PantryItem? {
        val targetId = usage.pantryItemId
        return if (targetId != null) {
            pantry.firstOrNull { it.id == targetId }
        } else {
            pantry.firstOrNull { it.name.equals(usage.name, ignoreCase = true) }
        }
    }

    private fun calculateDeductionDelta(usage: RecipePantryUsage, stock: PantryItem): Float {
        val recipeUnit = usage.unit?.trim()
        val stockUnit = stock.unit?.trim()

        if (recipeUnit.isNullOrEmpty() || stockUnit.isNullOrEmpty()) {
            return usage.quantityUsed
        }
        if (recipeUnit.equals(stockUnit, ignoreCase = true)) {
            return usage.quantityUsed
        }

        val converted = CulinaryScaling.convertVolume(usage.quantityUsed, recipeUnit, stockUnit)
        return converted ?: usage.quantityUsed
    }

    private suspend fun insertMissingGroceryItems(
        missingItems: List<RecipeMissingItem>,
        groceryRepository: GroceryRepository,
    ): Int {
        var addedCount = 0
        for (missing in missingItems) {
            groceryRepository.insertGrocery(
                GroceryItem(
                    name = missing.name,
                    quantity = missing.quantityNeeded,
                    unit = missing.unit,
                    checked = false,
                ),
            )
            addedCount++
        }
        return addedCount
    }
}
```

---

### 4.4 Detekt Compliance Verification Rules

1. **MaxLineLength (<= 120 characters)**:
   - Break parameters across multiple indented lines.
   - SQL queries and error strings must be concatenated with trailing `+` and indentation <= 120 columns.
2. **Cyclomatic Complexity (<= 15)**:
   - Each helper function has complexity between 1 and 4:
     - `executeCookMealTransactional`: 1
     - `executeCookMeal`: 1
     - `executeCookMealInternal`: 3
     - `deductStockItems`: 5
     - `resolveMatchedItem`: 2
     - `calculateDeductionDelta`: 4
     - `insertMissingGroceryItems`: 2
   - All well within Detekt's threshold of 15.
3. **Method Length (<= 60 lines)**:
   - Longest method is `deductStockItems` (20 lines), well within the 60-line limit.
4. **Return Count (<= 2)**:
   - All helper functions contain 1 or 2 return statements.
5. **Zero Warnings**:
   - Compiles cleanly under `warningsAsErrors = true`.

---

## 5. Verification Method

### 5.1 Verification Commands
To independently verify the test suite and quality requirements:

```bash
# 1. Run all JVM unit tests (including MealCookHandlerTest and CulinaryScalingTest)
./gradlew testDebugUnitTest

# 2. Run Detekt static analysis across main, unit test, and AndroidTest suites
./gradlew detekt detektTest detektDebugUnitTest detektDebugAndroidTest

# 3. Verify specific test class execution and HTML report generation
./gradlew testDebugUnitTest --tests "com.jeffers.pantrycraft.feature.meals.deduction.MealCookHandlerTest"
```

### 5.2 Verification Files to Inspect
1. `app/src/main/java/com/jeffers/pantrycraft/feature/meals/deduction/MealCookHandler.kt`
   - Verify `executeCookMealTransactional` signature matches Interface Contract 1.
   - Verify `CulinaryScaling.convertVolume` invocation with raw fallback.
   - Verify helper decomposition for Detekt cyclomatic complexity <= 15.
2. `app/src/test/java/com/jeffers/pantrycraft/feature/meals/deduction/MealCookHandlerTest.kt`
   - Verify all 33 test specifications are implemented and pass.
   - Verify `FakePantryRepository` mutates `flow.value` and clamps at zero.
   - Verify transaction rollback simulation tests pass.
3. `app/build/reports/detekt/detekt.txt` and `detekt.html`
   - Confirm 0 code smells, 0 warnings, 0 errors.
4. `app/build/reports/tests/testDebugUnitTest/index.html`
   - Confirm 100% test pass rate with 0 failures and 0 skips.

### 5.3 Invalidation Conditions
- Any line exceeding 120 characters in Kotlin source files.
- Any method having cyclomatic complexity > 15.
- Failure of any unit test in `MealCookHandlerTest`.
- Discrepancy in unit conversion calculations (e.g. 1 TBSP != 15 ML or 1 CUP != 240 ML).
- Unhandled negative quantity decrements in pantry items.
