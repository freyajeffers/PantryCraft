---
name: culinary-episodic-memory-reflection
description: Persist on-device culinary feedback, learn household palate profiles, recalibrate prep-time estimates, and adjust seasoning scaling exponents via local reflection loops.
---

# Culinary Episodic Memory & Reflection Engine

Techniques for building persistent, privacy-preserving memory and adaptation systems on Android without central telemetry.

## When to Use
- Learning household flavor preferences (e.g. salt tolerance, chili heat dampening).
- Recalibrating recipe prep times based on actual user cooking speed.
- Preventing meal monotony through dynamic ingredient turnover tracking.

## Core Architectural Invariants
1. **Zero-Telemetry Local Memory:** Store episodic reflections exclusively in the encrypted Room database (`culinary_reflections`). Never transmit user ratings, cooking speed, or palates to remote servers.
2. **Adaptive Seasoning Exponent Calibration:** Adjust the standard $0.75$ seasoning power curve exponent per user based on explicit rating feedback:
   $$\text{calibrated\_exp} = 0.75 \times \text{palate\_multiplier}$$
   If a user rates spicy recipes as "too hot" ($<3$ stars), damp the exponent towards $0.65$; if "too bland", boost to $0.85$.
3. **Prep-Time Grounding Filter:** Compare estimated prep time against actual elapsed timer durations; compute a moving average correction ratio ($ratio = \text{actual} / \text{estimated}$) to ground future model predictions.

## Schema & Implementation Pattern
```kotlin
@Entity(tableName = "culinary_reflections")
data class CulinaryReflection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: String,
    val cookedAt: Long = System.currentTimeMillis(),
    val targetPortions: Int,
    val estimatedMinutes: Int,
    val actualMinutes: Int,
    val userRating: Int, // 1 to 5 stars
    val feedbackNotes: String?,
    val seasoningAdjustment: Float = 1.0f // Multiplier learned for this household
)

class PalateCalibrator(private val reflectionDao: ReflectionDao) {
    suspend fun getCalibratedExponent(baseExponent: Double = 0.75): Double {
        val recentFeedback = reflectionDao.getRecentReflections(limit = 10)
        if (recentFeedback.isEmpty()) return baseExponent
        
        val spicyFeedback = recentFeedback.filter { it.feedbackNotes?.contains("spicy", ignoreCase = true) == true }
        if (spicyFeedback.any { it.userRating <= 2 }) {
            return 0.65 // Dampen heat scaling
        }
        return baseExponent
    }
}
```
