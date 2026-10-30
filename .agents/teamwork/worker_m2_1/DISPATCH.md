## 2026-10-27T12:21:48Z

Your identity: M2 Implementation Worker
Working directory: /home/freya/Projects/pantrycraft/.agents/teamwork/worker_m2_1
Project root: /home/freya/Projects/pantrycraft

MANDATORY INPUTS:
Read the following files before writing any code:
1. /home/freya/Projects/pantrycraft/.agents/teamwork/ORIGINAL_REQUEST.md
2. /home/freya/Projects/pantrycraft/AGENTS.md
3. /home/freya/Projects/pantrycraft/PROJECT.md (Special attention to Interface Contract 1 and Features 19-20)
4. /home/freya/Projects/pantrycraft/.agents/teamwork/explorer_m2_1/handoff.md
5. /home/freya/Projects/pantrycraft/.agents/teamwork/explorer_m2_2/handoff.md
6. /home/freya/Projects/pantrycraft/.agents/teamwork/explorer_m2_3/handoff.md

EXCLUSIVE FILE WRITE OWNERSHIP:
- app/src/main/java/com/jeffers/pantrycraft/data/CulinaryScaling.kt
- app/src/main/java/com/jeffers/pantrycraft/feature/meals/deduction/MealCookHandler.kt
- app/src/test/java/com/jeffers/pantrycraft/feature/meals/deduction/MealCookHandlerTest.kt

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

TASK OBJECTIVE:
Implement Milestone 2: Atomic Deduction Workflow & Unit Conversion Engine.
1. In `app/src/main/java/com/jeffers/pantrycraft/data/CulinaryScaling.kt`:
   - Add `ML_PER_FL_OZ = 30f` in volume conversion constants (1 CUP = 8 FL_OZ = 240 mL, 1 FL_OZ = 30 mL).
   - In `toMilliliters` and `fromMilliliters`, support volume normalization with aliases/trimming (`"tsp"`, `"tbsp"`, `"cup"`, `"ml"`, `"l"`, `"fl oz"` / `"fl_oz"`). Keep mass units returning null.
2. In `app/src/main/java/com/jeffers/pantrycraft/feature/meals/deduction/MealCookHandler.kt`:
   - Implement `executeCookMealTransactional(database: AppDatabase, recipe: MealRecipe, pantryRepository: PantryRepository, groceryRepository: GroceryRepository, addMissingToGrocery: Boolean = true): CookMealResult` wrapped in `withContext(Dispatchers.IO) { database.withTransaction { ... } }`.
   - Provide overloaded `executeCookMeal(recipe: MealRecipe, pantryRepository: PantryRepository, groceryRepository: GroceryRepository, addMissingToGrocery: Boolean = true, database: AppDatabase? = (pantryRepository as? Repository)?.db): CookMealResult` for backwards compatibility.
   - For each ingredient in `recipe.itemsUsedFromStock`:
     * Match against pantry items by `usage.pantryItemId` or case-insensitive trimmed name.
     * Convert volume units from `usage.unit` to `matchedItem.unit` using `CulinaryScaling.convertVolume`. If inconvertible (or null), fall back to raw `usage.quantityUsed`.
     * Precondition check: only decrement when delta > 0f and delta.isFinite().
     * Call `pantryRepository.decrementQuantity(matchedItem, delta)`.
   - For missing ingredients:
     * When `addMissingToGrocery == true`, insert `GroceryItem(name = missing.name.trim(), quantity = missing.quantityNeeded, unit = missing.unit?.trim(), checked = false)`.
   - Decompose into clean helper methods so each function has cyclomatic complexity <= 5, method length <= 30 lines, line length <= 120 chars.
3. In `app/src/test/java/com/jeffers/pantrycraft/feature/meals/deduction/MealCookHandlerTest.kt`:
   - Implement the comprehensive test suite specified in `explorer_m2_3/handoff.md` (transaction rollback simulation, zero-floor clamping at 0.0, unit volume conversions across TSP/TBSP/CUP/ML/L/FL_OZ, missing ingredient auto-grocery, portion bounds 1..12).
4. Run verification commands:
   - Run `./gradlew testDebugUnitTest` and ensure 100% tests pass.
   - Run `./gradlew detekt detektTest detektDebugUnitTest detektDebugAndroidTest` and ensure 0 errors, 0 warnings.

OUTPUT REQUIREMENTS:
Write your structured report to /home/freya/Projects/pantrycraft/.agents/teamwork/worker_m2_1/handoff.md documenting all changes, build/test results, and Detekt verification. When complete, send message to caller.
