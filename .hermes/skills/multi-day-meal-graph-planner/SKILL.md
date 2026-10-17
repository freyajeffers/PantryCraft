---
name: multi-day-meal-graph-planner
description: Construct multi-day meal sequences (3-5 day horizon graphs) that chain shared perishable ingredients and carry forward leftovers to systematically eliminate household food waste.
---

# Multi-Day Meal Graph Planner

Algorithms and graph sequencing patterns for chaining interdependent meals across multi-day household horizons.

## When to Use
- Planning weekly household meals anchored around large bulk items (e.g. cabbage, whole roast chicken, 2kg rice).
- Generating coordinated meal graphs that schedule perishables by urgency order.
- Managing leftover carry-forward yields (e.g. dinner surplus assigned to next-day lunch ledger).

## Core Architectural Invariants
1. **Perishability Graph Ingestion:** Order candidate meals as a Directed Acyclic Graph (DAG) where nodes represent meals and edges represent shared/leftover ingredients carried forward.
2. **Zero-Strand Policy:** Prohibit generating isolated meals that leave partial high-perishability containers stranded without a paired follow-up meal within 48 hours.
3. **Leftover Yield Tracking:** Factor planned portions vs. immediate consumption: if 4 portions are prepared for 2 people, the remaining 2 portions are automatically scheduled as next-day lunch stock.

## Graph Sequencing Pattern
```kotlin
data class MealPlanNode(
    val dayOffset: Int, // 0 = Today, 1 = Tomorrow, 2 = Day After
    val mealType: MealSlot, // DINNER, LUNCH, BREAKFAST
    val recipeTitle: String,
    val primaryIngredientsUsed: List<String>,
    val leavesSurplusForNextMeal: List<StockSurplus>
)

class MealGraphSequencer {
    fun sequenceHorizon(activeStock: List<PantryItem>, horizonDays: Int = 3): List<MealPlanNode> {
        val urgentItems = activeStock.filter { it.daysUntilExpiry <= horizonDays }.sortedBy { it.daysUntilExpiry }
        val nodes = mutableListOf<MealPlanNode>()
        
        // Day 1: Consume most urgent items (e.g. fresh fish / leafy greens)
        // Day 2: Utilize bulk proteins and carried-over surplus
        // Day 3: Utilize hardy root vegetables and pantry staples
        return nodes
    }
}
```
