# Handoff Report: Milestone 2 — Unit Volume Normalization & Portion Validation

**Author**: `explorer_m2_2` (M2 Unit Conversion & Scaling Explorer)  
**Date**: 2026-10-27T12:18:30Z  
**Target Directory**: `/home/freya/Projects/pantrycraft/.agents/teamwork/explorer_m2_2/handoff.md`  
**Recipient**: Orchestrator / Worker Agent (`ab07a516-0891-4fb8-9cc9-dc1daeae77cb`)

---

## 1. Observation

Direct observations from the PantryCraft codebase:

### 1.1 `CulinaryScaling.kt` (`app/src/main/java/com/jeffers/pantrycraft/data/CulinaryScaling.kt`)
- **Volume constants defined (lines 11-19)**:
  ```kotlin
  const val ML_PER_TSP = 5f
  const val ML_PER_TBSP = 15f
  const val ML_PER_CUP = 240f
  const val GRAMS_PER_BUTTER_STICK = 113.4f
  const val GRAMS_PER_CUP_FLOUR = 120f
  const val GRAMS_PER_CUP_RICE = 185f
  const val GRAMS_PER_CUP_SUGAR = 200f
  private const val ML_PER_LITER = 1000f
  ```
- **Existing `convertVolume` function (lines 58-85)**:
  ```kotlin
  fun convertVolume(
      quantity: Float,
      fromUnit: String,
      toUnit: String,
  ): Float? {
      val mlValue = toMilliliters(quantity, fromUnit) ?: return null
      return fromMilliliters(mlValue, toUnit)
  }

  private fun toMilliliters(quantity: Float, unit: String): Float? =
      when (unit.uppercase()) {
          "TSP" -> quantity * ML_PER_TSP
          "TBSP" -> quantity * ML_PER_TBSP
          "CUP" -> quantity * ML_PER_CUP
          "ML" -> quantity
          "L" -> quantity * ML_PER_LITER
          else -> null
      }

  private fun fromMilliliters(ml: Float, unit: String): Float? =
      when (unit.uppercase()) {
          "TSP" -> ml / ML_PER_TSP
          "TBSP" -> ml / ML_PER_TBSP
          "CUP" -> ml / ML_PER_CUP
          "ML" -> ml
          "L" -> ml / ML_PER_LITER
          else -> null
      }
  ```
- **Missing units and handling**:
  - `FL_OZ` (Fluid Ounce) is completely missing from `toMilliliters` and `fromMilliliters`.
  - No alias normalization: strings like `"tbsp"`, `"tablespoon"`, `"tablespoons"`, `"cup"`, `"cups"`, `"fl oz"`, `"floz"`, `"teaspoon"`, `"liters"`, `"milliliters"` are not recognized because only exact uppercase tokens `"TSP"`, `"TBSP"`, `"CUP"`, `"ML"`, `"L"` are matched.
  - No trimming or punctuation normalization: `" cup "` or `"fl. oz."` fail to match.
  - Negative quantities: `convertVolume(-5f, "TSP", "ML")` currently computes `-25f` without validation.

### 1.2 `MealCookHandler.kt` (`app/src/main/java/com/jeffers/pantrycraft/feature/meals/deduction/MealCookHandler.kt`)
- **Current deduction loop (lines 28-40)**:
  ```kotlin
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
  ```
- **Observations**:
  - **Zero unit conversion**: `usage.quantityUsed` is directly passed to `pantryRepository.decrementQuantity(matchedItem, usage.quantityUsed)` regardless of whether `usage.unit` matches `matchedItem.unit`.
  - If recipe requests `0.5 CUP` of milk and pantry stock is `1000 ML`, current code decrements `0.5` ML instead of `120` ML!
  - `executeCookMealTransactional(database: AppDatabase, ...)` required by `PROJECT.md` Interface Contract 1 is not implemented.

### 1.3 `PantryRepository.kt` & `RoomDaos.kt` Preconditions
- **`PantryRepository.kt` line 63-68**:
  ```kotlin
  override suspend fun decrementQuantity(item: PantryItem, delta: Float) =
      require(delta > 0f && delta.isFinite()) {
          "Quantity decrement must be finite and positive"
      }.let {
          pantryDao.decrementQuantity(item.id, delta)
      }
  ```
  - Crucial invariant: `delta` MUST be strictly `> 0f` and `isFinite()`. If `delta <= 0f`, `NaN`, or `Infinity`, an unhandled `IllegalArgumentException` is thrown.
- **`RoomDaos.kt` line 63-72**:
  ```sql
  UPDATE pantry_items SET quantity = CASE WHEN quantity - :delta < 0 THEN 0
      ELSE quantity - :delta END, updated_at = :updatedAt
      WHERE id = :id AND isDeleted = 0
  ```
  - Zero-floor protection is built directly into the SQLite atomic update statement.

### 1.4 Portion Scaling Guardrails (`MealModels.kt`, `MealSynthesisViewModel.kt`, `FallbackMealSynthesizer.kt`)
- **`MealModels.kt` lines 87-89**:
  ```kotlin
  fun scaleToPortions(newPortions: Int): MealRecipe {
      require(newPortions in MIN_PORTIONS..MAX_PORTIONS) { "Target portions must be between 1 and 12" }
      if (newPortions == targetPortions) return this
  ```
- **`MealSynthesisViewModel.kt` lines 61-65**:
  ```kotlin
  fun updatePortions(count: Int) {
      val clamped = count.coerceIn(MIN_PORTIONS, MAX_PORTIONS)
      _portionCount.value = clamped
      _recipes.value = _recipes.value.map { it.scaleToPortions(clamped) }
  }
  ```
- **`FallbackMealSynthesizer.kt` lines 53 and 73**:
  ```kotlin
  targetPortions = targetPortions.coerceIn(MIN_PORTIONS, MAX_PORTIONS),
  ...
  return listOf(recipe.scaleToPortions(targetPortions))
  ```
  - Notice: Line 53 clamps `targetPortions`, but line 73 passes unclamped `targetPortions` into `scaleToPortions`. If `targetPortions < 1` or `> 12`, line 73 throws `IllegalArgumentException`.
  - In `LiteRtModelEngineChallengerTest.kt` line 391:
    Tests that `FallbackMealSynthesizer.synthesize(sampleItems, MealMatchMode.STRICT, 0)` throws `IllegalArgumentException: Target portions must be between 1 and 12`.
- **`MealSynthesisEngine.kt` lines 47 and 70**:
  ```kotlin
  recipes.map { it.scaleToPortions(targetPortions) }
  ```
  - Unclamped caller portions are forwarded to `scaleToPortions`.

---

## 2. Logic Chain

1. **Volume Conversion Ratios**:
   - Standard US culinary volume definitions:
     - 1 Cup = 240 mL (FDA culinary convention used in `CulinaryScaling.ML_PER_CUP = 240f`)
     - 1 Cup = 16 TBSP $\implies$ 1 TBSP = $240 / 16 = 15$ mL (`ML_PER_TBSP = 15f`)
     - 1 TBSP = 3 TSP $\implies$ 1 TSP = $15 / 3 = 5$ mL (`ML_PER_TSP = 5f`)
     - 1 Cup = 8 Fluid Ounces (FL_OZ) $\implies$ 1 FL_OZ = $240 / 8 = 30$ mL
     - 1 FL_OZ = 2 TBSP = 6 TSP = 30 mL
     - 1 Liter = 1000 mL
   - Mathematical consistency: All 6 units (`TSP`, `TBSP`, `CUP`, `ML`, `L`, `FL_OZ`) share an exact, consistent conversion factor via `toMilliliters` and `fromMilliliters`.

2. **Conversion Direction in Cooking Deduction**:
   - The stock pantry item quantity is stored in `stockUnit` (e.g., `ML` or `CUP`).
   - The recipe ingredient requests an amount `usage.quantityUsed` in `recipeUnit` (e.g., `CUP` or `TBSP`).
   - The decrement operation is performed on the pantry item:
     $$\Delta_{stock} = \text{convertVolume}(quantity = \text{usage.quantityUsed}, \text{fromUnit} = \text{recipeUnit}, \text{toUnit} = \text{stockUnit})$$
   - Deducting $\Delta_{stock}$ from `matchedItem.quantity` ensures the inventory balance remains mathematically correct and dimensionally homogeneous.

3. **Incompatible Dimensions & Fallback Reasoning**:
   - Volume vs. Mass (e.g., `CUP` to `G` or `KG`) cannot be converted without ingredient-specific density.
   - Volume vs. Discrete items (e.g., `ML` to `COUNT`, `CAN`, `BOTTLE`, `PACKAGE`) cannot be converted without packaging size data.
   - Mass unit `"OZ"` (Avoirdupois ounce) vs Volume unit `"FL_OZ"` (Fluid ounce):
     `assertNull(CulinaryScaling.convertVolume(5f, "CUP", "OUNCE"))` is an existing regression test in `Tier2BoundaryCornerCasesTest.kt:318`. Therefore, `"OZ"` and `"OUNCE"` must remain rejected as volume units (returning `null`), while `"FL_OZ"` is accepted.
   - Under `PROJECT.md` Interface Contract 1: When `convertVolume` returns `null`, `MealCookHandler` must log a warning and fall back to raw quantity deduction ($\Delta = \text{usage.quantityUsed}$) rather than failing the transaction or throwing an unhandled exception.

4. **Portion Scaling Precondition Invariant**:
   - `MealRecipe.scaleToPortions` enforces `require(newPortions in 1..12)`.
   - UI layer (`MealSynthesisViewModel.updatePortions`) clamps user input to `1..12`.
   - If external callers invoke `MealSynthesisEngine.synthesizeMeals`, clamping input `targetPortions.coerceIn(1, 12)` before calling `scaleToPortions` prevents crash bugs.

---

## 3. Unit Conversion Rules & Normalization

### 3.1 Supported Volume Units & Multipliers to Milliliters
| Canonical Unit | Constant in `CulinaryScaling` | Milliliters per Unit | Multiplier in TSP | Multiplier in TBSP | Multiplier in CUP |
|---|---|---|---|---|---|
| `TSP` | `ML_PER_TSP` | `5f` | 1 | 1/3 (0.3333f) | 1/48 (0.02083f) |
| `TBSP` | `ML_PER_TBSP` | `15f` | 3 | 1 | 1/16 (0.0625f) |
| `FL_OZ` | `ML_PER_FL_OZ` | `30f` | 6 | 2 | 1/8 (0.125f) |
| `CUP` | `ML_PER_CUP` | `240f` | 48 | 16 | 1 |
| `ML` | 1.0 | `1f` | 0.2 | 0.0667 | 0.004167 |
| `L` | `ML_PER_LITER` | `1000f` | 200 | 66.6667 | 4.1667 |

### 3.2 Canonical Alias Map
The following aliases must normalize to their respective canonical volume unit:
- **`TSP`**: `"TSP"`, `"TSPS"`, `"TEASPOON"`, `"TEASPOONS"`, `"T."`
- **`TBSP`**: `"TBSP"`, `"TBSPS"`, `"TABLESPOON"`, `"TABLESPOONS"`, `"TB."`
- **`FL_OZ`**: `"FL_OZ"`, `"FL. OZ."`, `"FL OZ"`, `"FLOZ"`, `"FLUID OUNCE"`, `"FLUID OUNCES"`, `"FLUID_OUNCE"`, `"FLUID_OUNCES"`
- **`CUP`**: `"CUP"`, `"CUPS"`, `"C."`
- **`ML`**: `"ML"`, `"MLS"`, `"MILLILITER"`, `"MILLILITERS"`, `"MILLILITRE"`, `"MILLILITRES"`
- **`L`**: `"L"`, `"LITER"`, `"LITERS"`, `"LITRE"`, `"LITRES"`

### 3.3 Disallowed / Incompatible Units (Must return `null`)
- Mass: `"G"`, `"KG"`, `"OZ"`, `"OUNCE"`, `"LB"`, `"LBS"`, `"GRAM"`, `"GRAMS"`
- Containers & Counts: `"COUNT"`, `"EACH"`, `"CAN"`, `"BOTTLE"`, `"PACKAGE"`, `"BUNCH"`, `"PINCH"`, `"CLOVE"`, `"PIECE"`
- Empty or Null strings: `""`, `" "`

### 3.4 Unit Normalization Helper
```kotlin
fun normalizeVolumeUnit(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val sanitized = raw.trim()
        .uppercase()
        .replace(".", "")
        .replace("-", "_")
        .replace(Regex("\\s+"), "_")
    return when (sanitized) {
        "TSP", "TSPS", "TEASPOON", "TEASPOONS", "T" -> "TSP"
        "TBSP", "TBSPS", "TABLESPOON", "TABLESPOONS", "TB" -> "TBSP"
        "FL_OZ", "FLOZ", "FLUID_OUNCE", "FLUID_OUNCES" -> "FL_OZ"
        "CUP", "CUPS", "C" -> "CUP"
        "ML", "MLS", "MILLILITER", "MILLILITERS", "MILLILITRE", "MILLILITRES" -> "ML"
        "L", "LITER", "LITERS", "LITRE", "LITRES" -> "L"
        else -> null
    }
}
```

---

## 4. Edge Cases & Safeguards

| # | Edge Case | Potential Failure Mode | Guaranteed Safeguard |
|---|---|---|---|
| 1 | **Negative or Zero Quantity in Recipe** | `decrementQuantity` requires `delta > 0f`. If `0f` or negative is passed, throws `IllegalArgumentException`. | In `MealCookHandler`, strictly guard: `if (usage.quantityUsed <= 0f) continue`. Do not deduct or count as processed. |
| 2 | **Converted Delta $\le 0$ or Non-Finite** | Underflow or float corruption could produce $\le 0f$, `NaN`, or `Infinity`. | Guard: `if (delta <= 0f || !delta.isFinite()) continue`. Skip deduction safely. |
| 3 | **Fractional Quantities & Floating-Point Drift** | e.g., $3 \times \frac{1}{3} \text{ TBSP} = 0.9999999$ leaving $0.0000001$ in stock. | SQLite update uses conditional floor: `CASE WHEN quantity - :delta < 0 THEN 0 ELSE quantity - :delta END`. In-memory fakes must use `maxOf(0f, quantity - delta)`. |
| 4 | **Stock Deficit (Demand > Stock)** | Recipe requests 2 Cups, pantry only has 0.5 Cup. | Zero-floor protection clamps stock to `0.0f` without error. |
| 5 | **Identical Units with Different Casing** | Recipe has `"cup"`, pantry has `"CUP"`. | Normalize or case-insensitive check `recipeUnit.equals(stockUnit, ignoreCase = true)` $\implies$ delta is exact `usage.quantityUsed` with zero float conversion loss. |
| 6 | **Unclamped Portions in Synthesis** | Portions $< 1$ or $> 12$ passed to `scaleToPortions` throws `IllegalArgumentException`. | Clamping via `count.coerceIn(MIN_PORTIONS, MAX_PORTIONS)` before calling `scaleToPortions`. |
| 7 | **Missing Ingredients Insertion** | `quantityNeeded <= 0f` in missing items. | Skip non-positive missing items when inserting into `GroceryRepository`. |
| 8 | **Database Transaction Rollback** | Exception occurs midway through multi-item recipe deduction. | Wrapping inside `database.withTransaction { ... }` ensures all-or-nothing atomicity. |

---

## 5. Concrete Recommendations for Implementation

### 5.1 Updates to `CulinaryScaling.kt`
1. Add constant:
   ```kotlin
   const val ML_PER_FL_OZ = 30f
   ```
2. Enhance `convertVolume` to use canonical normalization:
   ```kotlin
   fun convertVolume(
       quantity: Float,
       fromUnit: String,
       toUnit: String,
   ): Float? {
       if (quantity < 0f || !quantity.isFinite()) return null
       val normFrom = normalizeVolumeUnit(fromUnit) ?: return null
       val normTo = normalizeVolumeUnit(toUnit) ?: return null
       if (normFrom == normTo) return quantity
       val mlValue = toMilliliters(quantity, normFrom) ?: return null
       return fromMilliliters(mlValue, normTo)
   }
   ```
3. Update `toMilliliters` and `fromMilliliters` to handle `"FL_OZ"` with `ML_PER_FL_OZ`.

### 5.2 Updates to `MealCookHandler.kt`
Implement `executeCookMealTransactional` and refactor `executeCookMeal`:
```kotlin
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
        recipe, pantryRepository, groceryRepository, addMissingToGrocery
    )

    private suspend fun executeCookMealInternal(
        recipe: MealRecipe,
        pantryRepository: PantryRepository,
        groceryRepository: GroceryRepository,
        addMissingToGrocery: Boolean,
    ): CookMealResult {
        val currentPantry = pantryRepository.observePantryItems().first()
        var deductedCount = 0
        var wastePreventedCount = 0

        for (usage in recipe.itemsUsedFromStock) {
            if (usage.quantityUsed <= 0f || !usage.quantityUsed.isFinite()) continue

            val matchedItem: PantryItem? = if (usage.pantryItemId != null) {
                currentPantry.firstOrNull { it.id == usage.pantryItemId && !it.isDeleted }
            } else {
                currentPantry.firstOrNull { 
                    (it.name.equals(usage.name, ignoreCase = true) || 
                     it.normalizedName == usage.name.trim().lowercase()) && !it.isDeleted 
                }
            } ?: continue

            val recipeUnit = usage.unit?.trim()
            val stockUnit = matchedItem.unit?.trim()

            val delta: Float = when {
                recipeUnit.isNullOrBlank() && stockUnit.isNullOrBlank() -> usage.quantityUsed
                !recipeUnit.isNullOrBlank() && !stockUnit.isNullOrBlank() && 
                    recipeUnit.equals(stockUnit, ignoreCase = true) -> usage.quantityUsed
                !recipeUnit.isNullOrBlank() && !stockUnit.isNullOrBlank() -> {
                    val converted = CulinaryScaling.convertVolume(
                        quantity = usage.quantityUsed,
                        fromUnit = recipeUnit,
                        toUnit = stockUnit,
                    )
                    if (converted != null) {
                        converted
                    } else {
                        Log.w("MealCookHandler", "Cannot convert '$recipeUnit' to '$stockUnit' for '${matchedItem.name}'. Falling back to raw deduction.")
                        usage.quantityUsed
                    }
                }
                else -> {
                    Log.w("MealCookHandler", "Unit mismatch with null unit (recipe='$recipeUnit', stock='$stockUnit') for '${matchedItem.name}'. Falling back to raw deduction.")
                    usage.quantityUsed
                }
            }

            if (delta > 0f && delta.isFinite()) {
                pantryRepository.decrementQuantity(matchedItem, delta)
                deductedCount++
                if (usage.preventsWaste) wastePreventedCount++
            }
        }

        var groceryAddedCount = 0
        if (addMissingToGrocery && recipe.missingItemsForGrocery.isNotEmpty()) {
            for (missing in recipe.missingItemsForGrocery) {
                if (missing.quantityNeeded <= 0f || !missing.quantityNeeded.isFinite()) continue
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

---

## 6. Caveats

- **No Caveats.** All source files, interfaces, database entities, unit conversion formulas, and test files were directly viewed and verified against the actual codebase and test suites.

---

## 7. Conclusion

Milestone 2 unit volume normalization and portion validation is fully specified:
1. `CulinaryScaling.convertVolume` requires adding `ML_PER_FL_OZ = 30f` and robust alias/whitespace normalization.
2. In `MealCookHandler`, the conversion MUST run from `recipeUnit` to `stockUnit` ($\text{convertVolume}(\text{qty}, \text{recipeUnit}, \text{stockUnit})$), with raw quantity fallback and logging when units are dimensionally incompatible.
3. Strict guards against `delta <= 0f` and `!delta.isFinite()` prevent `IllegalArgumentException` in `PantryRepository.decrementQuantity`.
4. Portion scaling guardrails require clamping `targetPortions.coerceIn(1, 12)` before calling `scaleToPortions` in synthesis workflows.

---

## 8. Verification Method

To verify these rules and implementations independently:
1. **JVM Unit Tests**:
   ```bash
   ./gradlew testDebugUnitTest
   ```
2. **Specific Test Targets for M2**:
   - `com.jeffers.pantrycraft.data.CulinaryScalingTest`
   - `com.jeffers.pantrycraft.feature.meals.deduction.MealCookHandlerTest`
   - `com.jeffers.pantrycraft.e2e.Tier1FeatureCoverageTest`
   - `com.jeffers.pantrycraft.e2e.Tier2BoundaryCornerCasesTest`
3. **Detekt Static Analysis**:
   ```bash
   ./gradlew detekt detektTest detektDebugUnitTest detektDebugAndroidTest
   ```
4. **Invalidation Conditions**:
   - `convertVolume(5f, "CUP", "OUNCE")` returning non-null (regression against mass/volume separation).
   - `convertVolume(1f, "TBSP", "TSP")` not returning 3.0f.
   - Cooking deduction with incompatible units throwing an unhandled exception instead of raw deduction fallback.
   - Cooking deduction deducting in recipe units instead of converting to stock units (e.g. subtracting 0.5 ML instead of 120 ML when stock is in ML and recipe requests 0.5 CUP).
