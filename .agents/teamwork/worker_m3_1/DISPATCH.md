# Dispatch Assignment — worker_m3_1
Timestamp: 2026-10-27T13:16:00Z

You are worker_m3_1 (M3 Implementation Worker).
Your working directory is /home/freya/Projects/pantrycraft/.agents/teamwork/worker_m3_1.
Implement Milestone 3: End-to-End UI Journey & AndroidTest Suite.
Conform to /home/freya/Projects/pantrycraft/PROJECT.md Features 21-23 and /home/freya/Projects/pantrycraft/AGENTS.md.
Device 127.0.0.1:3000 is online and ready for connectedDebugAndroidTest.

## 2026-10-27T13:16:09Z
From: ab07a516-0891-4fb8-9cc9-dc1daeae77cb
Your identity: M3 Implementation Worker
Working directory: /home/freya/Projects/pantrycraft/.agents/teamwork/worker_m3_1
Project root: /home/freya/Projects/pantrycraft

MANDATORY INPUTS:
Read the following files before writing any code:
1. /home/freya/Projects/pantrycraft/.agents/teamwork/ORIGINAL_REQUEST.md
2. /home/freya/Projects/pantrycraft/AGENTS.md
3. /home/freya/Projects/pantrycraft/PROJECT.md (Features 21-23, Milestone 3)
4. /home/freya/Projects/pantrycraft/.agents/teamwork/explorer_m3_1/handoff.md
5. /home/freya/Projects/pantrycraft/.agents/teamwork/explorer_m3_2/handoff.md
6. /home/freya/Projects/pantrycraft/.agents/teamwork/explorer_m3_3/handoff.md
7. /home/freya/Projects/pantrycraft/.agents/teamwork/worker_m3_1/DISPATCH.md

EXCLUSIVE FILE WRITE OWNERSHIP:
- app/src/main/java/com/jeffers/pantrycraft/feature/meals/ui/MealSynthesisScreen.kt
- app/src/main/java/com/jeffers/pantrycraft/feature/meals/ui/MealRecipeCard.kt
- app/src/androidTest/java/com/jeffers/pantrycraft/feature/meals/ui/MealSynthesisScreenComposeTest.kt
- app/src/androidTest/java/com/jeffers/pantrycraft/EndToEndMealJourneyTest.kt

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

TASK OBJECTIVE:
Implement Milestone 3 (End-to-End UI Journey & AndroidTest Suite):
1. In `MealSynthesisScreen.kt` and `MealRecipeCard.kt`:
   - Add the semantic `Modifier.testTag(...)` attributes specified in `explorer_m3_1/handoff.md` (`panic_button`, `portion_decrease_button`, `portion_increase_button`, `portion_value_text`, `mode_chip_${mode.name}`, `synthesize_meals_button`, `meal_synthesis_loading`, `meal_recipe_list`, `recipe_card_${recipe.recipeId}`, `cook_meal_button_${recipe.recipeId}`).
   - Ensure lines <= 120 chars and complexity <= 15.
2. In `app/src/androidTest/java/com/jeffers/pantrycraft/feature/meals/ui/MealSynthesisScreenComposeTest.kt`:
   - Implement the 8 Compose UI test cases designed in `explorer_m3_2/handoff.md` (mode selection chips, panic button salvage trigger, portion selector with sub-linear seasoning power curve $portions^{0.75}$, recipe cards rendering, and cooking stock deduction with Snackbar feedback).
   - Include keyguard screen-lock check (`km?.isKeyguardLocked == false`).
3. In `app/src/androidTest/java/com/jeffers/pantrycraft/EndToEndMealJourneyTest.kt`:
   - Implement the 7 integration test scenarios designed in `explorer_m3_3/handoff.md` (intake -> context compression -> meal synthesis -> "Cook This Meal" -> atomic Room quantity reduction with zero floor -> automatic missing ingredient population in `grocery_items`).
4. Execute verification commands:
   - Run `./gradlew assembleDebugAndroidTest`
   - Run `./gradlew connectedDebugAndroidTest` (device 127.0.0.1:3000 is online and ready)
   - Run `./gradlew testDebugUnitTest` (ensure all 543 unit tests continue to pass)
   - Run `./gradlew detekt detektTest detektDebugUnitTest detektDebugAndroidTest` (0 errors, 0 warnings across all targets)

OUTPUT REQUIREMENTS:
Write comprehensive report to /home/freya/Projects/pantrycraft/.agents/teamwork/worker_m3_1/handoff.md documenting all code changes, test results, connected test runs, and Detekt verification.
When complete, send message to caller.
