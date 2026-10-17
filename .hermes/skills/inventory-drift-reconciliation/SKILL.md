---
name: inventory-drift-reconciliation
description: Reconcile inventory drift between physical kitchen reality and database records using sensor fusion, CameraX visual deltas, and consumption burn-rate heuristics.
---

# Inventory Drift Reconciliation & Sensor Fusion

Heuristics and reconciliation pipelines for detecting and correcting discrepancies between tracked database stock and real kitchen shelves.

## When to Use
- Reconciling untracked snacking, spills, and spontaneous cooking.
- Comparing CameraX shelf snapshots against prior stored visual states (Visual Delta Auditing).
- Predicting depleted staples using historical consumption burn rates.

## Core Architectural Invariants
1. **Probabilistic Drift Scoring:** Compute a confidence degradation score over time for items that haven't been scanned or verified in $>14$ days:
   $$\text{confidence} = \text{initial\_confidence} \times e^{-\lambda \cdot \Delta t}$$
2. **Passive Recipe Inference:** When a user logs a multi-ingredient meal without explicit stock deductions, flag correlated pantry staples (oil, salt, butter, garlic) and prompt gentle inline verification.
3. **Visual Delta Bounding Box Comparison:** Align current camera snapshot bounding boxes with the prior shelf state. Flag missing bounding boxes as *"Candidate Depleted"* rather than deleting immediately.

## Reconciliation Algorithm
```kotlin
class InventoryDriftReconciler(private val pantryDao: PantryDao) {
    suspend fun auditStapleBurnRates(): List<DriftAlert> {
        val staples = pantryDao.getStapleItems()
        val alerts = mutableListOf<DriftAlert>()
        val now = System.currentTimeMillis()
        
        for (item in staples) {
            val daysSinceUpdate = (now - item.lastUpdated) / (1000 * 60 * 60 * 24)
            if (daysSinceUpdate > 14 && item.quantity > 0) {
                alerts.add(DriftAlert(item, "Not scanned in 2 weeks. Confirm quantity is still ${item.quantity}?"))
            }
        }
        return alerts
    }
}
```
