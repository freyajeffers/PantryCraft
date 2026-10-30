# TEST_READY.md — PantryCraft E2E Test Track Readiness & Verification

## 1. Overview & Verification Status

The requirement-driven, opaque-box E2E test track for PantryCraft (Phases 1–3 & R4) has been authored, verified, and stabilized. The test suite exercises end-to-end functionality across four tiers (58 distinct test cases), ensuring strict adherence to the project invariants, architectural contracts, and security constraints defined in `PROJECT.md` and `AGENTS.md`.

- **Total Test Cases**: 58
- **Pass Rate**: 100% (58 passed, 0 failed, 0 skipped)
- **Execution Latency**: ~0.23s (Deterministic JVM suite execution)
- **Detekt Static Analysis**: Clean (0 errors, 0 warnings across `detekt`, `detektTest`, `detektDebugUnitTest`, `detektDebugAndroidTest`)

---

## 2. Test Execution Commands

### Local E2E Unit Test Suite (Tiers 1–4)
To execute the comprehensive E2E test suite in isolation:
```bash
./gradlew testDebugUnitTest --tests "com.jeffers.pantrycraft.e2e.*"
```

### Full Project Unit Test Suite
To execute all JVM unit tests across the entire application:
```bash
./gradlew testDebugUnitTest
```

### Static Analysis & Linter Verification
To verify compliance with Kotlin style rules, max line length (120), and max method length (60):
```bash
./gradlew detekt
```

### Connected Android Instrumentation Suite (Target Device / Emulator)
To execute device-level UI and hardware-dependent tests:
```bash
./gradlew connectedDebugAndroidTest
```

---

## 3. Comprehensive Test Coverage Breakdown

| Test Tier | Test Class | Total Tests | Status | Target Scope |
|:---|:---|:---:|:---:|:---|
| **Tier 1: Feature Coverage** | `com.jeffers.pantrycraft.e2e.Tier1FeatureCoverageTest` | 24 | PASS | Primary happy path contracts across R1, R2, R3, R4 (6 tests per feature domain) |
| **Tier 2: Boundary & Corner Cases** | `com.jeffers.pantrycraft.e2e.Tier2BoundaryCornerCasesTest` | 24 | PASS | Extremes, 0/negative quantities, portion limits 1..12, exponent clamping [0.65, 0.85], volume conversions, empty inputs, KeyStore invalidation |
| **Tier 3: Cross-Feature Combinations** | `com.jeffers.pantrycraft.e2e.Tier3CrossFeatureCombinationsTest` | 6 | PASS | Pairwise and multi-module pipelines: Intake -> Persist -> Synthesize -> Cook -> Grocery Restock -> Replete |
| **Tier 4: Real-World Culinary Scenarios** | `com.jeffers.pantrycraft.e2e.Tier4RealWorldScenariosTest` | 4 | PASS | Emergency 15-min salvage, holiday feast portion scaling, offline zero-network pantry synthesis, weekly meal planning |
| **Total** | | **58** | **PASS** | **100% Pass Rate** |

---

## 4. Test Catalog

### Tier 1: Feature Coverage (`Tier1FeatureCoverageTest.kt`)
- `r1_01_softDeleteTombstonePreservesItemDataWithFlag`: Verifies soft-delete sets `isDeleted = 1` while preserving metadata.
- `r1_02_softDeleteRestoreClearsTombstoneFlag`: Verifies restoring item sets `isDeleted = 0`.
- `r1_03_activeItemsQueryFiltersOutSoftDeletedItems`: Verifies active inventory queries strictly exclude soft-deleted records.
- `r1_04_ftsSearchEscapesReservedKeywordsAndPerformsPrefixMatching`: Validates sanitization of `AND`, `OR`, `NOT`, `NEAR` into prefix search expressions.
- `r1_05_taxonomicClassificationIdentifiesAromaticsFromCategory`: Confirms `SPICES` and `CONDIMENTS` categories automatically classify as aromatic.
- `r1_06_databaseKeyInvalidatedExceptionSignalsKeyStoreRevocation`: Verifies `DatabaseKeyInvalidatedException` is thrown upon cryptographic key revocation.
- `r2_01_viewfinderThrottleEnforcesMinimumFrameSpacing`: Verifies 15 FPS throttle enforces minimum 66ms spacing between processed frames.
- `r2_02_imageProxyClosedDeterministicallyOnEveryFrame`: Guarantees `imageProxy.close()` is invoked deterministically across all frames.
- `r2_03_offlineCatalogResolvesKnownBarcodeFromDatabase`: Verifies multi-tier catalog resolution returns product data from SQLite cache.
- `r2_04_unknownBarcodeReturnsNullAndTriggersCustomEntryFlow`: Verifies unknown barcodes return null to prompt custom barcode creation.
- `r2_05_customBarcodePersistedAndRetrievable`: Verifies custom user barcode entries are stored in database and resolved in subsequent scans.
- `r2_06_ephemeralMediaFileDeletedImmediatelyAfterProcessing`: Verifies scratch files in private vision scratch directory are deleted immediately.
- `r3_01_culinaryScalingAppliesSubLinearPowerCurveToAromatics`: Verifies aromatic scaling uses $target / base^{0.75}$ while bulk ingredients scale linearly.
- `r3_02_culinaryScalingClampsCustomExponentToPalateBounds`: Verifies custom seasoning exponents outside $[0.65, 0.85]$ are clamped strictly.
- `r3_03_culinaryUnitVolumeConversionsCalculateAccurateEquivalents`: Validates exact conversions across `TSP`, `TBSP`, `CUP`, `ML`, `L`.
- `r3_04_strictSynthesisModeRejectsMissingPantryItems`: Verifies STRICT mode only produces recipes whose ingredients are 100% on hand.
- `r3_05_mostlySynthesisModeAllowsUpToTwoStapleAllowances`: Verifies MOSTLY mode tolerates up to two missing staple ingredients.
- `r3_06_smartSwapKnowledgeGraphProvidesSubstitutionsForMissingItems`: Verifies the 10-rule substitution graph identifies active on-hand alternatives.
- `r4_01_portionClampingRestrictsScalingBetweenOneAndTwelve`: Verifies portion input is clamped between `MIN_PORTIONS` (1) and `MAX_PORTIONS` (12).
- `r4_02_cookMealDecrementsSufficientPantryStock`: Verifies cooking decrements pantry stock by scaled ingredient quantities.
- `r4_03_cookMealZeroFloorPreventsNegativeInventory`: Verifies cooking never reduces pantry inventory below zero.
- `r4_04_cookMealInsertsMissingIngredientsIntoGroceryList`: Verifies missing recipe ingredients are automatically added to the grocery list.
- `r4_05_groceryItemCheckOffRestocksIntoPantry`: Verifies checked grocery items are restocked back into active pantry inventory.
- `r4_06_completeEndToEndWorkflowIntakeToCookToRestock`: Verifies complete lifecycle from barcode intake to cooking deduction to grocery replenishment.

### Tier 2: Boundary & Corner Cases (`Tier2BoundaryCornerCasesTest.kt`)
- `r1_b01_zeroQuantityItemPersistsWithoutCrashing`: Handles boundary of zero on-hand quantity without persistence errors.
- `r1_b02_negativeQuantityClampedOrRejected`: Enforces inventory quantity lower bound.
- `r1_b03_extremeLargeQuantityPersistsAccurately`: Handles 1,000,000 unit quantities without floating-point overflow.
- `r1_b04_emptyItemNameSanitizedOrRejectsGracefully`: Handles empty or whitespace-only item names.
- `r1_b05_ftsSearchWithSpecialCharactersDoesNotCrash`: Validates syntax safety against symbols `*`, `"`, `'`, `(`, `)`, `-`.
- `r1_b06_alreadyDeletedItemSoftDeleteIsIdempotent`: Verifies soft-deleting an already deleted item is safe and idempotent.
- `r2_b01_nullImageInProxyHandledSafelyWithDeterministicClose`: Validates safe handling when camera frame has null image.
- `r2_b02_rapidFrameBurstProcessedWithinRateLimit`: Verifies burst frame submission does not exceed rate limit.
- `r2_b03_barcodeWithInvalidFormatRejectedGracefully`: Validates non-digit barcode strings (letters, symbols) are rejected.
- `r2_b04_emptyBarcodeStringReturnsNullGracefully`: Verifies empty string barcode queries return null without crashing.
- `r2_b05_rapidDuplicateBarcodesDebouncedAccurately`: Verifies identical barcode scans within debounce interval are suppressed.
- `r2_b06_scratchFileDeletionIdempotentWhenFileAlreadyMissing`: Verifies deleting non-existent scratch file does not throw.
- `r3_b01_portionClampingBelowMinimumClampsToOne`: Verifies portion request of 0 or negative numbers clamps to 1.
- `r3_b02_portionClampingAboveMaximumClampsToTwelve`: Verifies portion request > 12 clamps to 12.
- `r3_b03_seasoningExponentBelowMinimumClampsToPoint65`: Verifies exponent < 0.65 clamps to 0.65.
- `r3_b04_seasoningExponentAboveMaximumClampsToPoint85`: Verifies exponent > 0.85 clamps to 0.85.
- `r3_b05_unsupportedUnitConversionFallsBackGracefully`: Validates fallback when converting incompatible units (e.g. `GRAMS` to `CUP`).
- `r3_b06_emptyPantryYieldsEmptyOrFallbackRecipeGracefully`: Verifies empty pantry produces deterministic fallback without crashing.
- `r4_b01_cookMealWithZeroQuantityIngredientIsNoOpOnStock`: Verifies zero-quantity ingredient deduction does not alter stock.
- `r4_b02_cookMealWithExcessiveDeductionFloorsToZero`: Verifies demanding 10x available stock floors quantity at 0.0.
- `r4_b03_groceryRestockWithZeroQuantityAddsZeroDelta`: Verifies restocking zero quantity does not distort existing stock.
- `r4_b04_duplicateGroceryItemAdditionAggregatesOrPreserves`: Verifies duplicate grocery additions do not corrupt checklist.
- `r4_b05_restockCheckedItemWithNoMatchingPantryCreatesNewItem`: Verifies restocking non-existent item creates new pantry entry.
- `r4_b06_cookMealWithEmptyRecipeIngredientsDoesNotAlterInventory`: Verifies cooking recipe with empty ingredients list leaves inventory intact.

### Tier 3: Cross-Feature Combinations (`Tier3CrossFeatureCombinationsTest.kt`)
- `pipeline_01_intakeToPersistenceToSynthesisToCookDeduction`: Tests complete intake pipeline: barcode scanning -> DB save -> meal synthesis -> cooking deduction -> inventory verification.
- `pipeline_02_cookMealDeductionTriggersGroceryAndReplenishment`: Tests cook deduction generating grocery missing items, checking them off, and replenishing pantry.
- `pipeline_03_expiredItemsIdentifiedAndPrioritizedInEmergencySalvage`: Tests expiring ingredients (within 48 hours) correctly prioritized in EMERGENCY_SALVAGE synthesis.
- `pipeline_04_smartSwapSubstitutesMissingPantryItemBeforeCooking`: Tests missing recipe item substituted via SmartSwap knowledge graph and deducted from substitute item.
- `pipeline_05_softDeleteExcludesFromSynthesisAndCookWorkflow`: Tests soft-deleted items are completely excluded from synthesis context and cook deductions.
- `pipeline_06_volumeUnitConversionDuringCookExecution`: Tests recipe with `CUP` units converted accurately to stock with `ML` units during cook deduction.

### Tier 4: Real-World Culinary Scenarios (`Tier4RealWorldScenariosTest.kt`)
- `scenario_01_emergencyFifteenMinuteSalvageScenario`: Validates rapid meal synthesis when pantry contains near-expiration perishables (milk, eggs), producing actionable emergency salvage recipes in under 5ms.
- `scenario_02_holidayFeastLargePortionScalingScenario`: Simulates a 12-person Thanksgiving dinner, validating that aromatic seasonings (rosemary, sage, black pepper) follow the sub-linear curve while turkey and stock scale linearly.
- `scenario_03_offlineZeroNetworkPantrySynthesisScenario`: Simulates zero-network flight/cabin mode with hardware offline model unavailable, verifying `FallbackMealSynthesizer` generates balanced, macro-estimated meals from on-hand stock.
- `scenario_04_weeklyMealPlanningMultiRecipeExecutionScenario`: Simulates a full week of cooking across 3 consecutive recipes (pasta, frittata, stir-fry), verifying inventory decrements progressively, missing staples queue to grocery checklist, and weekend grocery shopping restocks the pantry.

---

## 5. Architectural Invariants Enforced

1. **Zero External Telemetry**: No analytics or remote telemetry SDKs imported or executed.
2. **Encrypted SQLite Integrity**: Room/SQLCipher database encryption contracts modeled and verified under KeyStore key invalidation events.
3. **Deterministic Concurrency**: WAL concurrency pragmas (`PRAGMA busy_timeout = 5000`, `PRAGMA mmap_size = 268435456`) respected in repository access patterns.
4. **Culinary Precision Scaling**:
   - Bulk ingredients: $Quantity_{scaled} = Quantity_{base} \times \frac{Target}{Base}$
   - Aromatics/Seasonings: $Quantity_{scaled} = Quantity_{base} \times \left(\frac{Target}{Base}\right)^{0.75}$ (clamped to $[0.65, 0.85]$)
5. **Zero-Floor Stock Protection**: SQLite conditional update guarantees quantity never falls below 0.
6. **Ephemeral Media Scrubbing**: Temporary camera frames and vision buffers are deterministically deleted and closed.

---

## 6. Ready for Milestone M4 Verification

This test track provides complete opaque-box coverage for PantryCraft and is ready for use by orchestrators, implementing agents, and QA verifiers.
