# Phase 3 & Phase 4 Architectural Specification & Discovery Report

**Author:** Phase 3 & 4 Specification Miner  
**Date:** 2026-10-27T11:05:00Z  
**Target:** R3 (Dual-Inference Meal Synthesis & Culinary Intelligence) & R4 (End-to-End User Journey & Deduction Workflows)  
**Location:** `/home/freya/Projects/pantrycraft/.agents/teamwork/spec_miner_survey_3/handoff.md`

---

## Executive Summary

This specification mining survey establishes the formal requirements, domain models, interface contracts, observable behaviors, edge cases, and quality gates for:
1. **R3: Dual-Inference Meal Synthesis & Culinary Intelligence (Phase 3)** — Dynamic portion scaling (1–12 servings) strictly enforcing the non-linear culinary power curve ($portions^{0.75}$) for aromatics/seasonings, four synthesis modes (`STRICT`, `MOSTLY`, `SHOPPING`, `EMERGENCY_SALVAGE`), deterministic Smart Swap substitution knowledge graph, and dual-inference engine routing between cloud BYOKey providers and local deterministic fallback synthesis.
2. **R4: End-to-End User Journey & Deduction Workflows (Phase 4 / Integration)** — Full-lifecycle data loop from camera snapshot/barcode intake to active encrypted Room ledger, token-compressed prompt generation, "Cook This Meal" atomic SQLite stock deduction, automated missing ingredient transfer to grocery checklist, and store check-off restock reconciliation.

Static analysis (`./gradlew detekt detektTest detektDebugUnitTest detektDebugAndroidTest`) passes with **0 errors and 0 warnings**. The JVM unit test suite (`./gradlew testDebugUnitTest`) demonstrates a **100% pass rate across 398 tests** with zero failures and zero skipped tests.

---

## 1. Features Discovered

| # | Category | Feature | Description | Inputs | Outputs | Error Behavior | Discovered Via |
|---|----------|---------|-------------|--------|---------|----------------|----------------|
| 1 | Culinary Scaling | Non-Linear Seasoning Power Curve | Scales ingredients tagged `isAromaticOrSeasoning == true` using $baseQuantity \times (target/base)^{exponent}$, where default exponent is 0.75 (clamped between 0.65 and 0.85). Dampens seasoning scaling so larger batches aren't overpowering and smaller batches aren't bland. | `baseQuantity: Float`, `basePortions: Int`, `targetPortions: Int`, `isAromaticOrSeasoning: Boolean`, `exponent: Double = 0.75` | Scaled quantity (`Float`, rounded to 2 decimal places) | Throws `IllegalArgumentException` on negative quantity, zero/negative portions | `CulinaryScaling.kt:29-52`, `CulinaryScalingTest.kt:24-42` |
| 2 | Culinary Scaling | Linear Bulk Scaling | Scales bulk staples, grains, vegetables, and proteins linearly: $baseQuantity \times (target/base)$. | `baseQuantity: Float`, `basePortions: Int`, `targetPortions: Int`, `isAromaticOrSeasoning = false` | Linearly scaled quantity (`Float`) | Throws `IllegalArgumentException` on negative quantity or invalid portions | `CulinaryScaling.kt:48-51`, `CulinaryScalingTest.kt:11-21` |
| 3 | Culinary Scaling | Kitchen Volume Unit Normalization | Converts between kitchen units (`TSP`, `TBSP`, `CUP`, `ML`, `L`) using canonical milliliters conversion ratios ($1\text{ tsp}=5\text{ ml}$, $1\text{ tbsp}=15\text{ ml}$, $1\text{ cup}=240\text{ ml}$, $1\text{ L}=1000\text{ ml}$). | `quantity: Float`, `fromUnit: String`, `toUnit: String` | Converted quantity (`Float?`) | Returns `null` if units are unrecognized or dimensionally incompatible (e.g. `GRAMS` to `ML`) | `CulinaryScaling.kt:58-86`, `CulinaryScalingTest.kt:114-133` |
| 4 | Culinary Scaling | Culinary Density Constants | Provides standardized kitchen density mappings for butter sticks (113.4g), flour (120g/cup), rice (185g/cup), and sugar (200g/cup). | Constant properties | Density ratios in grams per standard measure | N/A (compile-time constants) | `CulinaryScaling.kt:11-17`, `plan/06_PHASE_3_MEAL_SYNTHESIS_AND_PORTIONS.md:62-68` |
| 5 | Synthesis Modes | Strict Zero-Waste Mode (`STRICT`) | Generates recipes requiring 100% on-hand inventory. Zero outside purchases permitted (excluding tap water, salt, cooking oil). Prioritizes items nearest expiration. | Active inventory list (`List<PantryItem>`), portion count (1–12) | `List<MealRecipe>` where `missingItemsForGrocery` is empty (`[]`) | Emits status message if no recipes possible without outside purchases | `MealModels.kt:19-23`, `MealPromptBuilder.kt:85-89`, `plan/06_PHASE_3_MEAL_SYNTHESIS_AND_PORTIONS.md:17` |
| 6 | Synthesis Modes | Mostly Pantry-First Mode (`MOSTLY`) | Generates meals utilizing 85%–95% on-hand stock, allowing up to 2 common pantry staples or missing flexible substitutes in grocery delta list. | Active inventory, portion count | `List<MealRecipe>` where `missingItemsForGrocery.size <= 2` | Falls back to local deterministic generation on parse/network error | `MealModels.kt:24-28`, `MealPromptBuilder.kt:90-93`, `plan/06_PHASE_3_MEAL_SYNTHESIS_AND_PORTIONS.md:18` |
| 7 | Synthesis Modes | Inspirational Chef Mode (`SHOPPING`) | Generates elevated chef-quality recipes centered on available star ingredients while emitting a comprehensive grocery delta list of complementary items. | Active inventory, portion count | `List<MealRecipe>` with populated `missingItemsForGrocery` | Fallback engine generates creative recipe with missing garnish/produce | `MealModels.kt:29-33`, `MealPromptBuilder.kt:94-97`, `plan/06_PHASE_3_MEAL_SYNTHESIS_AND_PORTIONS.md:19` |
| 8 | Synthesis Modes | 15-Minute Emergency Salvage Mode (`EMERGENCY_SALVAGE`) | Single-tap "Pantry Panic Button" that targets ingredients expiring within 48 hours ($exp - now \le 2\text{ days}$) and constrains prep + cook time to $\le 20\text{ minutes}$. | Active inventory, portion count, clock timestamp | `List<MealRecipe>` with `totalTimeMinutes <= 20`, prioritizing `[EXPIRY_URGENT]` stock | Returns empty list if inventory empty; selects available stock if none urgent | `MealModels.kt:34-38`, `MealPromptBuilder.kt:98-102`, `FallbackMealSynthesizer.kt:54-56`, `plan/03_DETAILED_SUB_PHASES_AND_INNOVATIONS.md:80-85` |
| 9 | Knowledge Graph | Deterministic Smart Swap Engine | Evaluates missing recipe items against an embedded substitution catalog and compares against active on-hand inventory in $<5\text{ms}$ without LLM roundtrips. | `missingItems: List<String>`, `onHandItems: List<PantryItem>` | `Map<String, List<SmartSwapSuggestion>>` with `isFullyAvailableOnHand` flag | Returns empty suggestions for missing items not present in substitution catalog | `SmartSwapEngine.kt:20-119`, `SmartSwapEngineTest.kt:12-56` |
| 10 | Context Compression | Token-Optimized Context Compressor | Compresses active inventory into a compact location-grouped semantic block (`Pantry:`, `Fridge:`, `Freezer:`, `Counter:`), tagging days remaining (`EXP:Xd`) and appending an explicit `[EXPIRY_URGENT]` section for stock expiring in $\le 2\text{ days}$. Fits 200 items in $<800\text{ tokens}$. | `items: List<PantryItem>`, `nowMillis: Long` | Compact text string (e.g. `[INVENTORY_CONTEXT]\nFridge: greek_yogurt(500g, EXP:2d)...`) | Returns `"[INVENTORY_CONTEXT]\nNo ingredients in stock."` if empty/deleted | `InventoryContextCompressor.kt:11-72`, `InventoryContextCompressorTest.kt:1-80` |
| 11 | AI Routing | Multi-Provider BYOKey Engine | Executes HTTP REST calls directly to provider endpoints without intermediate proxies: Google Gemini (`v1beta/models`), OpenAI (`v1/chat/completions`), Anthropic Claude (`v1/messages`), and local Ollama (`api/generate`). | `AiProvider`, model name, endpoint, API key, system prompt, user prompt | Raw JSON completion string | Throws descriptive `IllegalStateException` on HTTP errors (e.g. `HTTP 401: Unauthorized`) | `ByokTextModel.kt:23-167`, `ByokPreferences.kt:15-65` |
| 12 | AI Routing | KeyStore Hardware Encryption | Protects BYOKey credentials in `SharedPreferences` using AES-256-GCM keys managed by Android KeyStore, favoring `StrongBox` hardware on API 28+ with fallback to TEE on API 23+. | `apiKey: CharArray` | KeyStore-encrypted ciphertext & IV | Scrubbing buffers: `CharArray.fill('\u0000')` and `ByteArray.fill(0)` | `ByokPreferences.kt:110-199`, `ByokPreferencesTest.kt:1-50` |
| 13 | AI Routing | Deterministic Fallback Synthesis | Generates structured recipes entirely offline in 0ms without network connectivity, API keys, or GPU inference. Selects highest-urgency and highest-quantity ingredients, scales portions, and estimates nutrition. | `items: List<PantryItem>`, `mode: MealMatchMode`, `targetPortions: Int`, `nowMillis: Long` | `List<MealRecipe>` | Returns empty list if active stock is completely exhausted | `FallbackMealSynthesizer.kt:30-125`, `FallbackMealSynthesizerTest.kt:1-80` |
| 14 | Recipe Parsing | Resilient Recipe JSON Parser | Extracts and validates `MealRecipe` objects from model completions, stripping markdown code fences (` ```json `), extracting raw JSON bracket substrings, and falling back safely on malformed fields. | `jsonText: String` | `List<MealRecipe>` | Returns `emptyList()` on blank, non-JSON, or truncated text | `MealRecipeParser.kt:14-124`, `MealRecipeParserTest.kt:11-105` |
| 15 | Recipe Presentation | Dynamic Portion Dial UI | Allows interactive portion adjustment between 1 and 12 servings via Compose increment/decrement controls, dynamically recalculating ingredient quantities via `scaleToPortions()`. | `newPortions: Int` (1..12) | Scaled `MealRecipe` with recomputed stock and missing item quantities | Clamps to `[MIN_PORTIONS, MAX_PORTIONS]` range; throws `IllegalArgumentException` on invalid values | `MealModels.kt:87-116`, `MealSynthesisScreen.kt:101-122`, `MealSynthesisViewModel.kt:61-65` |
| 16 | Recipe Presentation | Inline Smart Swap Suggestion Chips | Renders available and partial kitchen bridge substitutions directly on the recipe card below missing ingredients. | `recipe: MealRecipe`, `swaps: Map<String, List<SmartSwapSuggestion>>` | Compose `SuggestionChip`s with replacement ratios | Renders nothing if no missing items or no swaps applicable | `MealRecipeCard.kt:83-102` |
| 17 | Deduction Workflow | "Cook This Meal" Execution | Deducts recipe ingredients from active Room inventory and automatically populates missing items into the `grocery_items` replenishment list. | `recipe: MealRecipe`, `pantryRepository`, `groceryRepository`, `addMissingToGrocery: Boolean` | `CookMealResult(itemsDeducted, wastePrevented, groceryAdded)` | Matches items by `pantryItemId` or fallback case-insensitive name | `MealCookHandler.kt:18-63`, `MealCookHandlerTest.kt:19-48` |
| 18 | Stock Depletion | Atomic Quantity Decrement with Zero Floor | SQLite atomic update preventing negative inventory balances: `UPDATE pantry_items SET quantity = CASE WHEN quantity - :delta < 0 THEN 0 ELSE quantity - :delta END`. | `id: Long`, `delta: Float`, `updatedAt: Long` | SQLite transaction row update | Rejects non-positive or infinite delta values with `IllegalArgumentException` | `RoomDaos.kt:63-72`, `PantryRepository.kt:63-69` |
| 19 | Replenishment | Grocery-to-Pantry Restock Reconciliation | Transfers checked grocery items back into active pantry inventory within a Room `@Transaction`, incrementing existing stock by `fromPantryId` or inserting fresh records, then clearing checked items. | Checked items in `grocery_items` | Active `pantry_items` incremented/inserted; checked grocery rows deleted | Atomically rolls back on SQLite error | `GroceryRepository.kt:35-62`, `RepositoryGroceryIntegrationTest.kt:1-50` |
| 20 | Navigation & E2E | Unified Kitchen Dashboard Journey | Connects dashboard cards to Quick Ingestion (`camera`), Active Inventory (`pantry`), Grocery Replenishment (`grocery`), and Meal Synthesis (`meals`), complete with back-stack handling. | User destination navigation events | Jetpack Compose screen rendering with Material 3 bottom navigation | Lazy background repository loading displays `DatabaseLoadingScreen` | `AppNavigator.kt:53-80`, `HomeScreen.kt:37-76`, `AppNavigatorComposeTest.kt:41-69` |

---

## 2. Edge Cases Matrix

| # | Feature | Input / Condition | Observed Behavior | Authoritative Reference |
|---|---------|-------------------|-------------------|------------------------|
| 1 | `CulinaryScaling.scaleQuantity` | `baseQuantity = 0f` with `basePortions = 2`, `targetPortions = 8` | Returns `0f` immediately without power curve evaluation. | `CulinaryScaling.kt:40-42`, `CulinaryScalingTest.kt:64-72` |
| 2 | `CulinaryScaling.scaleQuantity` | `basePortions == targetPortions` (e.g. 4 -> 4) | Returns exact `baseQuantity` untouched without floating-point drift. | `CulinaryScaling.kt:40-42`, `CulinaryScalingTest.kt:44-61` |
| 3 | `CulinaryScaling.scaleQuantity` | Downscaling portions (4 -> 2, ratio = 0.5) with `isAromaticOrSeasoning = true` | Seasoning quantity scales down less aggressively than bulk ($0.5^{0.75} \approx 0.595 > 0.5$), preserving flavor intensity for smaller households. | `CulinaryScaling.kt:47`, `CulinaryScalingTest.kt:135-156` |
| 4 | `CulinaryScaling.scaleQuantity` | Negative quantity (`-5f`) or non-positive portions (`0`) | Throws `IllegalArgumentException` with explicit descriptive error message. | `CulinaryScaling.kt:36-38`, `CulinaryScalingTest.kt:74-85` |
| 5 | `CulinaryScaling.scaleQuantity` | Exponent out of palate bounds (e.g. 0.50 or 0.99) | Coerced into safe bounds $[0.65, 0.85]$: 0.50 clamps to 0.65; 0.99 clamps to 0.85. | `CulinaryScaling.kt:46`, `CulinaryScalingTest.kt:87-111` |
| 6 | `CulinaryScaling.convertVolume` | Same unit conversion (e.g. `ML` to `ML`, `CUP` to `CUP`) | Returns exact input quantity without rounding degradation. | `CulinaryScaling.kt:63-65`, `CulinaryScalingTest.kt:158-166` |
| 7 | `CulinaryScaling.convertVolume` | Incompatible dimensional units (e.g. `GRAMS` to `ML`) | Returns `null` without throwing exception, signaling caller to engage fractional modal. | `CulinaryScaling.kt:63`, `CulinaryScalingTest.kt:122` |
| 8 | `MealRecipe.scaleToPortions` | Target portions outside `1..12` (e.g. 0 or 13) | Throws `IllegalArgumentException("Target portions must be between 1 and 12")`. | `MealModels.kt:88`, `MealModelsTest.kt:83-88` |
| 9 | `MealRecipeParser.parseRecipes` | Completion wrapped in markdown code fences (```json ... ```) | Regex extracts JSON payload inside code fences cleanly and parses recipes. | `MealRecipeParser.kt:31-34`, `MealRecipeParserTest.kt:77-97` |
| 10 | `MealRecipeParser.parseRecipes` | Completion returns raw JSON array `[...]` instead of `{"recipes": [...]}` | Detects leading bracket and routes to `parseRecipeArray` directly. | `MealRecipeParser.kt:19-21`, `MealRecipeParserTest.kt:58-74` |
| 11 | `MealRecipeParser.parseRecipes` | Completion contains conversational text before/after JSON | Detects first '{' or '[' and last '}' or ']' substring bounds. | `MealRecipeParser.kt:35-43`, `MealRecipeParserTest.kt:29` |
| 12 | `MealRecipeParser.parseRecipes` | Truncated or malformed JSON (e.g. `{ invalid json`) | `runCatching` catches exception and returns `emptyList()` without crash. | `MealRecipeParser.kt:18-26`, `MealRecipeParserTest.kt:99-105` |
| 13 | `InventoryContextCompressor` | All inventory items deleted (`isDeleted == true`) or quantity 0 | Returns `"[INVENTORY_CONTEXT]\nNo ingredients in stock."`. | `InventoryContextCompressor.kt:15-16`, `InventoryContextCompressorTest.kt:20-29` |
| 14 | `InventoryContextCompressor` | Item with expired date in past ($exp < now$) | Clamps days remaining to 0 ($daysRemaining = \text{max}(0, \Delta / 86400000)$) and marks `(0 days left!)`. | `InventoryContextCompressor.kt:56-59` |
| 15 | `SmartSwapEngine` | Missing item keyword present in substitution catalog and stock present in pantry | Emits suggestion with `isFullyAvailableOnHand = true`. | `SmartSwapEngine.kt:98-109`, `SmartSwapEngineTest.kt:12-27` |
| 16 | `SmartSwapEngine` | Missing item keyword present in catalog but stock absent from pantry | Emits suggestion with `isFullyAvailableOnHand = false`. | `SmartSwapEngine.kt:98-109`, `SmartSwapEngineTest.kt:30-38` |
| 17 | `SmartSwapEngine` | Missing item has no matching keyword in swap catalog | Returns empty suggestion list for that item. | `SmartSwapEngine.kt:94-96` |
| 18 | `MealCookHandler.executeCookMeal` | Recipe ingredient not found in database (or deleted) | Ignores missing stock item gracefully, continues deducting remaining ingredients. | `MealCookHandler.kt:28-40` |
| 19 | `MealCookHandler.executeCookMeal` | Pantry item matched by case-insensitive name when `pantryItemId` is null | Matches successfully (e.g. "rice" matches "Rice") and decrements quantity. | `MealCookHandler.kt:32-37` |
| 20 | `MealCookHandler.executeCookMeal` | Deducting quantity greater than on-hand stock ($qtyUsed > stock$) | SQLite `CASE WHEN quantity - :delta < 0 THEN 0 ELSE quantity - :delta END` clamps stock to 0 without negative balance. | `RoomDaos.kt:64-67`, `MealCookHandlerTest.kt:35-47` |
| 21 | `ByokPreferences.saveApiKey` | Empty or whitespace-only CharArray | Invokes `clearApiKey(context)`, securely wipes char array, and removes preferences keys. | `ByokPreferences.kt:114-119`, `ByokPreferencesTest.kt:30-40` |
| 22 | `ByokPreferences.saveApiKey` | Encryption failure or KeyPermanentlyInvalidatedException | Finally blocks execute guaranteed memory wiping (`plaintext.fill(0)` and `apiKey.fill('\u0000')`). | `ByokPreferences.kt:131-134` |
| 23 | `ByokPreferences.loadApiKey` | Key ciphertext or IV missing in preferences | Returns `null` without throwing exception, allowing synthesis engine to trigger fallback. | `ByokPreferences.kt:140-141` |

---

## 3. Authoritative Domain Models & Data Contracts

### 3.1. Core Meal Synthesis Models (`com.jeffers.pantrycraft.feature.meals.model`)

```kotlin
const val MIN_PORTIONS = 1
const val MAX_PORTIONS = 12

data class MealConstraints(
    val dietaryPreferences: List<String> = emptyList(),
    val availableAppliances: List<String> = emptyList(),
    val maxCookTimeMinutes: Int? = null,
)

enum class MealMatchMode(val id: String, val title: String, val description: String) {
    STRICT("STRICT", "Zero-Waste Strict", "Uses 100% on-hand inventory. Zero purchases required."),
    MOSTLY("MOSTLY", "Pantry First", "Uses 85-95% on-hand items; allows up to 2 flexible staples."),
    SHOPPING("SHOPPING", "Inspirational Chef", "Features on-hand star ingredients with a grocery delta list."),
    EMERGENCY_SALVAGE("EMERGENCY_SALVAGE", "15-Min Rescue", "Rapid rescue meals for items expiring within 48 hours.");

    companion object {
        fun fromId(id: String?): MealMatchMode =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: STRICT
    }
}

data class NutritionalEstimates(
    val calories: Int,
    val proteinGrams: Int,
    val carbsGrams: Int,
    val fatGrams: Int,
)

data class RecipePantryUsage(
    val name: String,
    val pantryItemId: Long? = null,
    val quantityUsed: Float,
    val unit: String? = null,
    val preventsWaste: Boolean = false,
    val isAromaticOrSeasoning: Boolean = false,
)

data class RecipeMissingItem(
    val name: String,
    val quantityNeeded: Float,
    val unit: String? = null,
    val estimatedCostCents: Int? = null,
)

data class MealRecipe(
    val recipeId: String,
    val title: String,
    val matchMode: MealMatchMode = MealMatchMode.STRICT,
    val targetPortions: Int = 2,
    val prepTimeMinutes: Int = 10,
    val cookTimeMinutes: Int = 15,
    val urgencyScore: Float = 0.5f,
    val itemsUsedFromStock: List<RecipePantryUsage> = emptyList(),
    val missingItemsForGrocery: List<RecipeMissingItem> = emptyList(),
    val stepByStepInstructions: List<String> = emptyList(),
    val nutritionalEstimates: NutritionalEstimates? = null,
) {
    val totalTimeMinutes: Int get() = prepTimeMinutes + cookTimeMinutes
    val wastePreventionCount: Int get() = itemsUsedFromStock.count { it.preventsWaste }

    fun scaleToPortions(newPortions: Int): MealRecipe {
        require(newPortions in MIN_PORTIONS..MAX_PORTIONS) { "Target portions must be between 1 and 12" }
        if (newPortions == targetPortions) return this

        val scaledStockItems = itemsUsedFromStock.map { item ->
            val scaledQty = CulinaryScaling.scaleQuantity(
                baseQuantity = item.quantityUsed,
                basePortions = targetPortions,
                targetPortions = newPortions,
                isAromaticOrSeasoning = item.isAromaticOrSeasoning,
            )
            item.copy(quantityUsed = scaledQty)
        }

        val scaledMissingItems = missingItemsForGrocery.map { item ->
            val scaledQty = CulinaryScaling.scaleQuantity(
                baseQuantity = item.quantityNeeded,
                basePortions = targetPortions,
                targetPortions = newPortions,
                isAromaticOrSeasoning = false,
            )
            item.copy(quantityNeeded = scaledQty)
        }

        return copy(
            targetPortions = newPortions,
            itemsUsedFromStock = scaledStockItems,
            missingItemsForGrocery = scaledMissingItems,
        )
    }
}
```

### 3.2. Smart Swap Knowledge Graph Model (`com.jeffers.pantrycraft.feature.meals.swap`)

```kotlin
data class SmartSwapSuggestion(
    val missingItem: String,
    val substituteName: String,
    val ratioExplanation: String,
    val requiredStockItems: List<String>,
    val isFullyAvailableOnHand: Boolean,
)

data class SwapRule(
    val targetKeyword: String,
    val substituteName: String,
    val ratioExplanation: String,
    val requiredKeywords: List<String>,
)
```

### 3.3. Deduction Result Model (`com.jeffers.pantrycraft.feature.meals.deduction`)

```kotlin
data class CookMealResult(
    val itemsDeductedCount: Int,
    val foodWastePreventedCount: Int,
    val groceryItemsAddedCount: Int,
)
```

---

## 4. Public Interface Contracts

### 4.1. `CulinaryScaling` Object
```kotlin
object CulinaryScaling {
    const val DEFAULT_SEASONING_EXPONENT = 0.75
    const val MIN_PALATE_EXPONENT = 0.65
    const val MAX_PALATE_EXPONENT = 0.85

    fun scaleQuantity(
        baseQuantity: Float,
        basePortions: Int,
        targetPortions: Int,
        isAromaticOrSeasoning: Boolean,
        exponent: Double = DEFAULT_SEASONING_EXPONENT,
    ): Float

    fun convertVolume(
        quantity: Float,
        fromUnit: String,
        toUnit: String,
    ): Float?
}
```

### 4.2. `InventoryContextCompressor` Object
```kotlin
object InventoryContextCompressor {
    fun compress(items: List<PantryItem>, nowMillis: Long = System.currentTimeMillis()): String
    fun estimateTokenCount(text: String): Int
}
```

### 4.3. `SmartSwapEngine` Object
```kotlin
object SmartSwapEngine {
    fun findSwapsForMissingItem(missingItem: String, onHandItems: List<PantryItem>): List<SmartSwapSuggestion>
    fun enrichMissingItemsWithSwaps(
        missingItems: List<String>,
        onHandItems: List<PantryItem>,
    ): Map<String, List<SmartSwapSuggestion>>
}
```

### 4.4. `MealSynthesisEngine` Class
```kotlin
class MealSynthesisEngine(
    private val context: Context,
    private val textModel: TextGenerationModel = ByokTextModel(context),
) {
    suspend fun synthesizeMeals(
        inventory: List<PantryItem>,
        mode: MealMatchMode,
        targetPortions: Int,
        constraints: MealConstraints = MealConstraints(),
    ): List<MealRecipe>
}
```

### 4.5. `MealCookHandler` Object
```kotlin
object MealCookHandler {
    suspend fun executeCookMeal(
        recipe: MealRecipe,
        pantryRepository: PantryRepository,
        groceryRepository: GroceryRepository,
        addMissingToGrocery: Boolean = true,
    ): CookMealResult
}
```

### 4.6. `ByokPreferences` Object
```kotlin
object ByokPreferences {
    fun selectedProvider(context: Context): AiProvider
    fun selectedModel(context: Context): String
    fun selectedEndpoint(context: Context): String
    fun saveConfiguration(context: Context, provider: AiProvider, modelName: String, endpointUrl: String)
    fun saveApiKey(context: Context, apiKey: CharArray)
    fun loadApiKey(context: Context): String?
    fun hasApiKey(context: Context): Boolean
    fun clearApiKey(context: Context)
}
```

---

## 5. End-to-End User Journey Sequence Diagram

```
[ User ]          [ Camera / Ingestion ]        [ Encrypted Ledger ]       [ Meal Synthesis ]      [ Grocery Checklist ]
   │                        │                            │                         │                         │
   │─── 1. Scan Shelf ─────>│                            │                         │                         │
   │    or Barcode          │── 2. Batch Review Commit ─>│                         │                         │
   │                        │   (Atomic SQLite Insert)   │                         │                         │
   │                                                     │                         │                         │
   │─── 3. Open "AI Meal Synthesis" ──────────────────────────────────────────────>│                         │
   │    Dial Portions (1-12) & Mode (Strict/Mostly/Shopping/Rescue)                │                         │
   │                                                     │<── 4. Query Stock ──────│                         │
   │                                                     │    (Active Inventory)   │                         │
   │                                                     │─── Active Items ───────>│                         │
   │                                                                               │── 5. Compress Context   │
   │                                                                               │── 6. Infer (Cloud/Local)│
   │                                                                               │── 7. Scale Portions     │
   │                                                                               │── 8. Enrich Smart Swaps │
   │<── 9. Render Recipe Cards with Instructions & Nutrition ──────────────────────│                         │
   │                                                                               │                         │
   │─── 10. Tap "Cook This Meal" ─────────────────────────────────────────────────>│                         │
   │                                                     │<── 11. Decrement Stock ─│                         │
   │                                                     │    (Atomic SQLite Delta)│                         │
   │                                                     │                         │── 12. Add Missing Items>│
   │                                                     │                         │       (Delta Grocery)   │
   │<── 13. Confirm Cooked (Snackbar: "Deducted N items") ─────────────────────────│                         │
   │                                                                                                         │
   │─── 14. Open Grocery at Supermarket ────────────────────────────────────────────────────────────────────>│
   │─── 15. Check off Purchased Items ──────────────────────────────────────────────────────────────────────>│
   │─── 16. Tap "Restock" ──────────────────────────────>│<── 17. Re-increment Pantry / Delete Checked ─────│
   │    (Pantry Stock Replenished!)                      │                                                   │
```

---

## 6. Security, Quality & Performance Verification Gates

### 6.1. Security Invariants
1. **Zero External Telemetry:** Absolute absence of analytics, tracking, or crash reporting SDKs in `app/build.gradle`.
2. **Encrypted Persistence:** SQLCipher 256-bit passphrase encrypted via KeyStore AES-256-GCM. Passphrase scrubbed from memory after Room connection factory setup.
3. **Hardware-Backed BYOKey Credentials:** Encrypted at rest via `ByokPreferences` using Android KeyStore `StrongBox` (API 28+) or TEE (API 23+). Decrypted exclusively in-memory during dispatch. Byte arrays and char arrays scrubbed (`fill(0)` / `fill('\u0000')`).
4. **Ephemeral Media Wipe:** Private downsampled camera WebP frames and audio buffers are scrubbed immediately post-inference.

### 6.2. Code Quality & Static Analysis SLA
1. **Detekt Static Analysis:**
   - Command: `./gradlew detekt detektTest detektDebugUnitTest detektDebugAndroidTest`
   - Gate: **0 errors, 0 warnings**.
   - Complexity constraints: Line length $\le 120$ characters, cognitive complexity limits honored, no wildcard imports.
2. **JVM Unit Tests:**
   - Command: `./gradlew testDebugUnitTest`
   - Gate: **100% pass rate** across all 398 test cases.

### 6.3. Latency & Memory SLA Targets
- App Cold Launch: $< 350\text{ ms}$
- FTS5 Prefix Search (5,000 records): $< 5\text{ ms}$
- CameraX Live Frame Analysis: $< 40\text{ ms / frame}$ (15 FPS throttle with `AtomicBoolean`)
- First streamed recipe token: $< 1.2\text{ s}$ (cloud BYOKey) / $< 2.5\text{ s}$ (on-device SLM)
- Heap Memory Allocation Delta: $< 64\text{ MB}$

---

## 7. 5-Component Handoff Report

### 1. Observation
- **Codebase State:** The Phase 3 meal synthesis engine and culinary intelligence suite are implemented under `app/src/main/java/com/jeffers/pantrycraft/feature/meals/` and `app/src/main/java/com/jeffers/pantrycraft/data/CulinaryScaling.kt`.
- **Files Verified:**
  - `data/CulinaryScaling.kt` (lines 1–87): Implements `scaleQuantity` with $0.75$ exponent for `isAromaticOrSeasoning == true` and volume conversions for `TSP`, `TBSP`, `CUP`, `ML`, `L`.
  - `feature/meals/model/MealModels.kt` (lines 1–118): Implements `MealMatchMode` (`STRICT`, `MOSTLY`, `SHOPPING`, `EMERGENCY_SALVAGE`), `MealRecipe`, `RecipePantryUsage`, `RecipeMissingItem`, and `scaleToPortions` bounded to `1..12`.
  - `feature/meals/swap/SmartSwapEngine.kt` (lines 1–120): Implements 10 deterministic culinary bridge rules and evaluates availability against active on-hand pantry stock.
  - `feature/meals/context/InventoryContextCompressor.kt` (lines 1–73): Serializes inventory by storage zone with `[EXPIRY_URGENT]` tags for items $\le 2\text{ days}$.
  - `feature/meals/deduction/MealCookHandler.kt` (lines 1–64): Executes atomic stock decrement and missing item insertion into `grocery_items`.
  - `feature/meals/engine/ByokTextModel.kt` (lines 1–168): Implements direct REST clients for Gemini, OpenAI, Anthropic, and Ollama.
  - `feature/meals/engine/FallbackMealSynthesizer.kt` (lines 1–126): Generates emergency salvage and zero-waste recipes locally.
  - `ui/AppNavigator.kt` (lines 165–173) & `ui/HomeScreen.kt` (lines 210–252): Wire up the `"meals"` navigation route and dashboard entry point.
- **Test Execution:**
  - `app/build/reports/tests/testDebugUnitTest/index.html` confirmed **398 tests passed**, **0 failures**, **0 skipped**, duration 5.466s.
  - `./gradlew detekt` completed with exit code 0 across all test and source targets.

### 2. Logic Chain
1. *Observation:* User requirements in `ORIGINAL_REQUEST.md` (R3 & R4) demand dynamic portion scaling (1–12 servings) with $portions^{0.75}$ scaling for aromatic/seasoning ingredients and linear scaling for bulk ingredients.
2. *Observation:* `CulinaryScaling.scaleQuantity` implements `portionRatio.pow(clampedExponent)` when `isAromaticOrSeasoning == true` and `portionRatio` when false.
3. *Inference:* The mathematical scaling model adheres strictly to the authoritative culinary specifications and AGENTS.md invariant #7.
4. *Observation:* R3 specifies four modes: Strict, Mostly, Shopping, and 15-Minute Emergency Salvage.
5. *Observation:* `MealMatchMode` enums, `MealPromptBuilder`, and `FallbackMealSynthesizer` differentiate behaviors across these exact four modes.
6. *Observation:* R4 mandates an end-to-end data flow from intake -> ledger -> meal synthesis -> "Cook This Meal" execution with atomic stock deduction and grocery replenishment.
7. *Observation:* `MealCookHandler.executeCookMeal` links `pantryRepository.decrementQuantity` (backed by Room SQLite atomic decrement) with `groceryRepository.insertGrocery`, and `GroceryRepository.restockCheckedGroceryToPantry` completes the return loop.
8. *Inference:* The end-to-end lifecycle loop is fully specified and mathematically grounded.

### 3. Caveats
- Android AICore (Gemini Nano) local SLM inference on production devices requires Android 14+ AICore system service binding; on devices without AICore, execution gracefully routes to `FallbackMealSynthesizer` or BYOKey cloud providers.
- Physical connected instrumentation tests (`connectedDebugAndroidTest`) require an active unlocked Android emulator or hardware device; when the screen is locked, Compose UI tests execute `Assume.assumeTrue(km?.isKeyguardLocked == false)` to prevent false test failures.

### 4. Conclusion
The authoritative specifications for R3 (Dual-Inference Meal Synthesis & Culinary Intelligence) and R4 (End-to-End User Journey & Deduction Workflows) are completely probed, verified, and formalized. The interface contracts are sound, type-safe, and thoroughly covered by existing unit tests and static analysis.

### 5. Verification Method
- **Unit Test Suite:**
  ```bash
  ./gradlew testDebugUnitTest
  ```
  Expected output: 398 tests passing, 0 failures.
- **Static Analysis Quality Gate:**
  ```bash
  ./gradlew detekt detektTest detektDebugUnitTest detektDebugAndroidTest
  ```
  Expected output: BUILD SUCCESSFUL, 0 errors, 0 warnings.
- **Key Inspection Points:**
  - Scaling power curve: `com.jeffers.pantrycraft.data.CulinaryScalingTest`
  - Portion models: `com.jeffers.pantrycraft.feature.meals.model.MealModelsTest`
  - Cook deduction: `com.jeffers.pantrycraft.feature.meals.deduction.MealCookHandlerTest`
  - Smart swaps: `com.jeffers.pantrycraft.feature.meals.swap.SmartSwapEngineTest`
  - Context compression: `com.jeffers.pantrycraft.feature.meals.context.InventoryContextCompressorTest`
