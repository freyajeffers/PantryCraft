---
name: adaptive-sous-chef-copilot
description: Manage real-time, hands-free cooking sessions, dynamic step navigation, on-the-fly ingredient substitutions for kitchen mishaps, and ambient voice replanning.
---

# Adaptive Sous-Chef Copilot

Engineering protocols for orchestrating hands-free, ambient cooking companions with real-time error recovery and substitution replanning.

## When to Use
- Operating an ambient voice companion while cooking with messy, wet hands.
- Recovering dynamically when a cook burns an ingredient, spills a measure, or discovers missing items mid-recipe.
- Managing concurrent audio timers, step playback, and stock deductions hands-free.

## Core Architectural Invariants
1. **Low-Power Ambient VAD:** Utilize energy-efficient Voice Activity Detection (VAD) during active cooking mode; process audio frames only when human speech is detected to consume $<4\%$ battery over 30 minutes.
2. **Dynamic Mid-Recipe Substitution Engine:** When a cook states a problem (*"I only have 1 egg, what do I do?"*), query the active pantry database for culinary bridge ingredients (applesauce, yogurt, flaxseed) and rewrite downstream cooking instructions on the fly.
3. **Audio Session Interruptibility:** Hands-free TTS narration must pause immediately when speech is detected (barge-in support) so the cook can interrupt with questions or timer commands.

## Substitution Matrix & State Replanning
```kotlin
class SousChefCopilot(
    private val pantryDao: PantryDao,
    private val ttsEngine: AndroidTextToSpeechEngine
) {
    suspend fun handleMishap(problem: String, currentStep: RecipeStep): RecoveryAdvice {
        return when {
            problem.contains("burned", ignoreCase = true) -> {
                RecoveryAdvice("Lower heat immediately. Remove undamaged portion to a fresh pan. Do not scrape burned fond.")
            }
            problem.contains("missing egg", ignoreCase = true) -> {
                val hasYogurt = pantryDao.hasItem("yogurt")
                if (hasYogurt) {
                    RecoveryAdvice("Substitute 1/4 cup plain yogurt for the missing egg to maintain moisture.")
                } else {
                    RecoveryAdvice("Substitute 1 tablespoon ground flaxseed mixed with 3 tablespoons water.")
                }
            }
            else -> RecoveryAdvice("Let's adjust: reduce cooking temperature by 25 degrees and simmer 5 minutes longer.")
        }
    }
}
```
