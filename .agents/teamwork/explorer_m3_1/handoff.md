# Handoff Report: Milestone 3 UI Journey & Compose Architecture Analysis

**Agent ID**: explorer_m3_1  
**Timestamp**: 2026-10-27T13:08:50Z  
**Target Path**: `/home/freya/Projects/pantrycraft/.agents/teamwork/explorer_m3_1/handoff.md`  
**Recipient**: ab07a516-0891-4fb8-9cc9-dc1daeae77cb (Orchestrator)

---

## 1. Observation

### 1.1 Existing Codebase & UI Architecture Inspection
1. **Screen Layout & Composition (`MealSynthesisScreen.kt:44-163`)**:
   - **Scaffold & TopBar** (lines 58–60): Scaffold with top bar title `"Portion-Aware Meal Synthesis"` and a `SnackbarHost(snackbarHostState)`.
   - **Snackbar Feedback** (lines 54–56): `LaunchedEffect(viewModel)` listens to `viewModel.statusMessage.collect { snackbarHostState.showSnackbar(it) }`.
   - **Panic Button Banner** (lines 66, 88–98):
     ```kotlin
     Button(
         onClick = onTrigger,
         colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
         modifier = Modifier.fillMaxWidth(),
     ) {
         Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
         Text("15-Min Pantry Panic Button (Rescue Expiring Food)")
     }
     ```
     Invokes `viewModel.triggerEmergencySalvage()`.
   - **Portion Selector Row** (lines 67, 100–122):
     - Displays title `"Servings / Portions:"`.
     - Decrease `IconButton` with `contentDescription = "Decrease portions"`, enabled when `portions > MIN_PORTIONS` (1).
     - Text display: `"$portions"`.
     - Increase `IconButton` with `contentDescription = "Increase portions"`, enabled when `portions < MAX_PORTIONS` (12).
   - **Mode Selector Chips** (lines 68, 124–136):
     - `LazyRow` displaying 4 `ElevatedFilterChip` widgets corresponding to `MealMatchMode.entries`:
       1. `STRICT`: Title `"Zero-Waste Strict"`, description `"Uses 100% on-hand inventory. Zero purchases required."`
       2. `MOSTLY`: Title `"Pantry First"`, description `"Uses 85-95% on-hand items; allows up to 2 flexible staples."`
       3. `SHOPPING`: Title `"Inspirational Chef"`, description `"Features on-hand star ingredients with a grocery delta list."`
       4. `EMERGENCY_SALVAGE`: Title `"15-Min Rescue"`, description `"Rapid rescue meals for items expiring within 48 hours."`
     - Selection state is bound to `selected == mode`, calling `viewModel.selectMode(mode)`.
   - **Synthesis Action Button & Progress Indicator** (lines 69–80):
     - Full-width button: `onClick = viewModel::generateMeals`, enabled when `!isLoading`.
     - Displays `Icons.Filled.AutoAwesome` and text: `if (isLoading) "Synthesizing Meals…" else "Synthesize Meals"`.
     - When `isLoading == true`: Renders `Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }`.
   - **Recipe List View** (lines 82, 138–162):
     - Empty state: Text `"Select a mode, dial your portions, and tap 'Synthesize Meals' to generate recipes."`.
     - Populated state: `LazyColumn` rendering `MealRecipeCard` for each item keyed by `recipe.recipeId`.

2. **Recipe Card Structure (`MealRecipeCard.kt:25-121`)**:
   - **Card Container**: `Card(modifier = modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp))`.
   - **Header Section** (lines 53–71):
     - Recipe Title: `Text(recipe.title, style = MaterialTheme.typography.titleMedium)`.
     - Total Time: `Row` with `Icons.Filled.Timer` and `Text("${recipe.totalTimeMinutes}m")`.
     - Subtitle: `Text("${recipe.matchMode.title} • ${recipe.targetPortions} Servings")`.
   - **Stock Section** (lines 73–82):
     - Header: `Text("From Your Pantry:", style = MaterialTheme.typography.labelLarge)`.
     - Items: `Text("• ${item.name}: ${item.quantityUsed}$unitStr$wasteTag")`, where `wasteTag` is `" (Rescued!)"` if `item.preventsWaste == true`.
   - **Missing & Swaps Section** (lines 83–102):
     - Condition: rendered only if `recipe.missingItemsForGrocery.isNotEmpty()`.
     - Header: `Text("Needed / Smart Swaps:", style = MaterialTheme.typography.labelLarge)`.
     - Missing item: `Text("• ${missing.name} (${missing.quantityNeeded}$unitStr)")`.
     - Swaps: `SuggestionChip(label = { Text("Swap: ${swap.substituteName} (${swap.ratioExplanation})") })`.
   - **Instructions Section** (lines 103–111):
     - Header: `Text("Instructions:")`.
     - Steps: `recipe.stepByStepInstructions.forEach { Text(it) }`.
   - **Nutrition Section** (lines 112–120):
     - Condition: rendered only if `recipe.nutritionalEstimates != null`.
     - Text: `"Per Portion: ${n.calories} kcal • ${n.proteinGrams}g Protein • ${n.carbsGrams}g Carbs • ${n.fatGrams}g Fat"`.
   - **Cook Button** (lines 42–48):
     - Full-width button with `Icons.Filled.Restaurant` and label `"Cook This Meal (${recipe.targetPortions} Servings)"`.
     - Invokes `onCook(recipe)` callback.

3. **ViewModel State Flow & Deduction Actions (`MealSynthesisViewModel.kt:30-113`)**:
   - Observes `pantryRepository.observePantryItems()` into `inventory: StateFlow<List<PantryItem>>`.
   - State flows: `selectedMode` (defaults to `STRICT`), `portionCount` (defaults to `2`), `isLoading` (defaults to `false`), `recipes` (defaults to `emptyList()`), `smartSwaps` (defaults to `emptyMap()`), `statusMessage` (`SharedFlow<String>`).
   - `updatePortions(count: Int)`: Clamps portions between `MIN_PORTIONS` (1) and `MAX_PORTIONS` (12), sets `_portionCount.value = clamped`, and immediately invokes `_recipes.value = _recipes.value.map { it.scaleToPortions(clamped) }`.
   - `triggerEmergencySalvage()`: Sets `_selectedMode.value = MealMatchMode.EMERGENCY_SALVAGE` and executes `generateMeals()`.
   - `cookMeal(recipe: MealRecipe)`: Invokes `MealCookHandler.executeCookMeal(...)` and emits `_statusMessage.emit("Cooked '${recipe.title}'! Deducted ${result.itemsDeductedCount} items.")`.

4. **Navigation Integration (`AppNavigator.kt:151-175`, `HomeScreen.kt:210-252`)**:
   - In `HomeScreen.kt`, `MealSynthesisCard` contains a button: `Text("Synthesize Meals")` that calls `onNavigate("meals")`.
   - In `AppNavigator.kt`:
     ```kotlin
     "meals" -> MealSynthesisScreen(
         viewModel(
             factory = MealSynthesisViewModelFactory(
                 repository = repository,
                 engine = MealSynthesisEngine(context),
             ),
         ),
     )
     ```
   - `BottomDestination` does not contain a dedicated "meals" tab; entry is through `HomeScreen`'s `MealSynthesisCard` or programmatic navigation.

5. **Existing Test Tags Audit in `MealSynthesisScreen.kt` & `MealRecipeCard.kt`**:
   - Searching pattern `testTag` across `app/src` returned **0 results**.
   - No `Modifier.testTag(...)` is currently present in `MealSynthesisScreen.kt` or `MealRecipeCard.kt`.
   - Existing semantic identifiers are limited to:
     - `contentDescription = "Decrease portions"`
     - `contentDescription = "Increase portions"`
     - Visible text strings for buttons, chips, titles, and sections.
   - `CircularProgressIndicator` currently has **NO** testTag, **NO** text, and **NO** contentDescription, making loading state assertions impossible without test tags.

6. **Current AndroidTest Suite Status**:
   - `app/src/androidTest/` currently contains 66 files covering Database, Backup, Camera (analyzer, barcode, UI components), and Settings/Home screens.
   - **Zero** tests exist in `app/src/androidTest/` for `feature/meals/` or `MealSynthesisScreen`.

---

## 2. Logic Chain

1. **Testability Assessment**:
   - From Observation 1.5, Compose UI testing can partially find elements via `onNodeWithText(...)` and `onNodeWithContentDescription(...)`.
   - However, relying purely on text presents three severe failure risks:
     a. **Ambiguity on portion values**: A node with text `"2"` could match a count of 2 portions, 2 ingredients in stock, or 2 flexible staples in the chip subtitle.
     b. **Untargetable progress indicator**: The `CircularProgressIndicator` cannot be queried by text or content description.
     c. **Dynamic recipe and card identifiers**: When multiple recipes are rendered in `LazyColumn`, targeting a specific card's cook button or ingredient list by generic text `"Cook This Meal"` matches multiple nodes.
   - Therefore, introducing semantic `Modifier.testTag(...)` attributes to `MealSynthesisScreen.kt` and `MealRecipeCard.kt` will harden testing and eliminate flakiness.

2. **Test Tag Semantic Specification**:
   - To make the UI deterministically testable without breaking existing layout or styling, the following test tags should be added:
     - `Modifier.testTag("panic_button")` on the panic button banner.
     - `Modifier.testTag("portion_decrease_button")` and `Modifier.testTag("portion_increase_button")` on portion icon buttons.
     - `Modifier.testTag("portion_value_text")` on the portion number display.
     - `Modifier.testTag("mode_chip_${mode.id}")` (e.g. `mode_chip_STRICT`, `mode_chip_MOSTLY`, `mode_chip_SHOPPING`, `mode_chip_EMERGENCY_SALVAGE`) on the filter chips.
     - `Modifier.testTag("synthesize_meals_button")` on the main synthesis action button.
     - `Modifier.testTag("meal_synthesis_loading")` on the loading `CircularProgressIndicator`.
     - `Modifier.testTag("meal_synthesis_empty_state")` on the empty state text box.
     - `Modifier.testTag("meal_recipe_list")` on the `LazyColumn`.
     - `Modifier.testTag("recipe_card_${recipe.recipeId}")` on each `MealRecipeCard`.
     - `Modifier.testTag("cook_meal_button_${recipe.recipeId}")` on the "Cook This Meal" button.
     - `Modifier.testTag("recipe_swap_chip_${swap.substituteName}")` on swap suggestion chips.

3. **Fallback Selectors for Immediate AndroidTest Execution**:
   - While test tags are being applied by the implementation worker, the following semantic matchers can be used immediately without modifications:
     - Mode chips: `onNode(hasText("Zero-Waste Strict") and isSelectable())`
     - Decrease button: `onNodeWithContentDescription("Decrease portions")`
     - Increase button: `onNodeWithContentDescription("Increase portions")`
     - Panic banner: `onNodeWithText("15-Min Pantry Panic Button (Rescue Expiring Food)")`
     - Synthesize button: `onNodeWithText("Synthesize Meals")`
     - Cook button: `onNode(hasText("Cook This Meal", substring = true))`
     - Snackbar: `onNode(hasText("Cooked '", substring = true))`

4. **Component Test Specification Design (`MealSynthesisScreenComposeTest`)**:
   - Tests should be divided into isolated UI component checks using `createAndroidComposeRule<ComponentActivity>()` with a controlled `MealSynthesisViewModel` or mock repository:
     - **Spec 1: Default State**: Strict chip selected, 2 portions displayed, Decrease enabled, Increase enabled, Synthesize button enabled, empty placeholder visible.
     - **Spec 2: Mode Switching**: Clicking each of the 4 chips updates selected state; only 1 chip selected at any time.
     - **Spec 3: Portion Dial Boundaries**: Decreasing at 1 keeps 1 and disables decrease button; increasing at 12 keeps 12 and disables increase button.
     - **Spec 4: Panic Button Instant Salvage**: Clicking banner updates mode to `EMERGENCY_SALVAGE` (`15-Min Rescue` chip selected) and triggers generation.
     - **Spec 5: Dynamic Scaling on Loaded Cards**: When recipes are displayed, incrementing portions immediately updates the card's subtitle (e.g. `"4 Servings"`), cook button text (`"Cook This Meal (4 Servings)"`), and ingredient quantities according to scaling rules.
     - **Spec 6: Seasoning Power Curve Scaling Verification**: Aromatic ingredients (`isAromaticOrSeasoning == true`) scale sub-linearly ($(4/2)^{0.75} \approx 1.68x$) while standard ingredients scale linearly ($4/2 = 2.0x$).
     - **Spec 7: Smart Swap Rendering**: When recipes have missing items with suggestions, suggestion chips `"Swap: <substitute> (<ratio>)"` are rendered.
     - **Spec 8: Cook This Meal Interaction & Snackbar**: Clicking "Cook This Meal" triggers deduction and causes the Scaffold `SnackbarHost` to show `"Cooked '<title>'! Deducted <N> items."`.
     - **Spec 9: Loading State**: When `isLoading == true`, synthesize button shows `"Synthesizing Meals…"`, is disabled, and progress indicator is displayed.

---

## 3. Caveats

1. **Read-Only Scope**:
   - No application code or test files were modified during this investigation.
   - Proposed test tags and test implementations must be applied by the designated implementation worker.
2. **Screen Lock Precondition**:
   - As observed in `HomeScreenComposeTest.kt` and `GroceryScreenComposeTest.kt`, all connected Compose tests must include:
     ```kotlin
     val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
     org.junit.Assume.assumeTrue("Screen is locked; skipping Compose UI test", km?.isKeyguardLocked == false)
     ```
     to prevent false failures when device screens are locked or uninitialized.
3. **Detekt Complexity & Line Length**:
   - `config/detekt.yml` enforces maximum line length <= 120 characters and method complexity <= 15.
   - All proposed test code and tag additions must maintain lines under 120 chars and split helper assertions into dedicated functions to prevent `ComplexMethod` or `LongMethod` violations.

---

## 4. Conclusion

1. **Architecture Status**: The M3 UI architecture in `MealSynthesisScreen.kt`, `MealRecipeCard.kt`, and `MealSynthesisViewModel.kt` is structurally sound, clean, and reactive. It adheres to `AGENTS.md` thread pinning rules (`Dispatchers.Main.immediate` for UI, `Default` for synthesis, `IO` for Room deduction).
2. **Testing Gap**: Milestone 3 currently has **zero** Compose instrumentation tests in `app/src/androidTest/feature/meals/`.
3. **Actionable Roadmap**:
   - **Step 1 (Implementer)**: Add the recommended `Modifier.testTag(...)` attributes to `MealSynthesisScreen.kt` and `MealRecipeCard.kt`.
   - **Step 2 (Test Writer)**: Implement `app/src/androidTest/java/com/jeffers/pantrycraft/feature/meals/ui/MealSynthesisScreenComposeTest.kt` executing the 9 formulated component test specifications.
   - **Step 3 (Integration Test Writer)**: Implement `EndToEndMealJourneyTest.kt` verifying the full loop (pantry stock insertion -> meal synthesis -> "Cook This Meal" -> stock deduction in Room DB -> grocery checklist insertion).

---

## 5. Verification Method

To verify these findings and validate future implementations:

1. **Verify Existing Tests and Static Analysis**:
   - Run unit tests:
     `./gradlew testDebugUnitTest`
   - Run Detekt across all targets (including androidTest):
     `./gradlew detekt detektTest detektDebugUnitTest detektDebugAndroidTest`
   - Check existing zero-error baseline:
     Ensure exit code is 0 and `detekt.txt` has 0 warnings.

2. **Verify androidTest Compilation Target**:
   - Assemble debug androidTest APK:
     `./gradlew assembleDebugAndroidTest`
   - Verify that all dependencies (`androidx.compose.ui:ui-test-junit4`, `androidx.test.ext:junit`, `espresso-core`) compile cleanly.

3. **Verify UI Component Test Execution**:
   - When `MealSynthesisScreenComposeTest.kt` is created, run connected tests on physical device:
     `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.jeffers.pantrycraft.feature.meals.ui.MealSynthesisScreenComposeTest`

4. **Invalidation Conditions**:
   - If `MealMatchMode` titles or descriptions change without updating test matchers.
   - If `MealRecipeCard` button text deviates from `"Cook This Meal ($portions Servings)"`.
   - If `MIN_PORTIONS` or `MAX_PORTIONS` bounds change from [1, 12].
