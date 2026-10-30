# BRIEFING — 2026-10-27T11:10:45Z

## Mission
Investigate Milestone 1: On-Device AI Engine Architecture and formulate technical design/contracts for OnDeviceMealModel and LiteRtModelEngine.

## 🔒 My Identity
- Archetype: explorer
- Roles: Teamwork explorer, Senior Android & Systems Security Engineer
- Working directory: /home/freya/Projects/pantrycraft/.agents/teamwork/explorer_m1_2
- Original parent: ab07a516-0891-4fb8-9cc9-dc1daeae77cb
- Milestone: Milestone 1: On-Device AI Engine Architecture

## 🔒 Key Constraints
- Read-only investigation — do NOT implement
- Local data sovereignty with zero external telemetry
- Thread pinning: Heavy computation / inference on Dispatchers.Default
- Ephemeral media / prompt / output memory hygiene (byte zeroing)
- Graceful fallback to FallbackMealSynthesizer

## Current Parent
- Conversation ID: ab07a516-0891-4fb8-9cc9-dc1daeae77cb
- Updated: 2026-10-27T11:07:37Z

## Investigation State
- **Explored paths**:
  - `feature/meals/engine/MealSynthesisEngine.kt`
  - `feature/meals/engine/ByokTextModel.kt`
  - `feature/meals/engine/FallbackMealSynthesizer.kt`
  - `feature/meals/model/MealModels.kt`
  - `feature/meals/model/MealRecipeParser.kt`
  - `feature/meals/prompt/MealPromptBuilder.kt`
  - `feature/meals/ui/MealSynthesisViewModel.kt`
  - `app/build.gradle`
  - `data/ByokPreferences.kt`
  - `PROJECT.md` (Feature 8 & Interface Contract 2)
  - `plan/` implementation specifications & skills suites
- **Key findings**:
  - `MealSynthesisEngine.kt` currently lacks `OnDeviceMealModel` adapter and routes `AiProvider.OFFLINE_MLKIT` directly to `FallbackMealSynthesizer`.
  - `PROJECT.md` Contract 2 defines `OnDeviceMealModel` with `isAvailable: Boolean` and `generateMealRecipes(systemPrompt, userPrompt, onHandItems): Result<List<MealRecipe>>`.
  - `LiteRtModelEngine` needs to evaluate hardware capability (>=6GB RAM, low memory flags), model file availability, thread pinning on `Dispatchers.Default`, prompt/output byte zeroing in `finally` blocks, and graceful delegation to `FallbackMealSynthesizer`.
  - `MealSynthesisEngine` constructor can default `onDeviceModel: OnDeviceMealModel = LiteRtModelEngine(context)` to preserve 100% binary and source compatibility.
- **Unexplored areas**: None for M1 scope.

## Key Decisions Made
- Design `OnDeviceMealModel` interface matching `PROJECT.md` Contract 2.
- Design `LiteRtModelEngine` with RAM checking (>=6GB RAM), model asset check, prompt extraction for fallback params, ephemeral buffer zeroing, and `Dispatchers.Default` pinning.
- Provide full replacement/diff specifications in handoff report.

## Artifact Index
- DISPATCH.md — Task dispatch record
- progress.md — Liveness heartbeat
- BRIEFING.md — Working memory
- handoff.md — Final structured report
