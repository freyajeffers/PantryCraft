---
name: compose-high-perf-ledger
description: Optimize Jetpack Compose LazyColumn performance with stable keys, layout phase skipping, derivedStateOf filtering, and swipe physics.
---

# Jetpack Compose High-Performance Ledger

Techniques and performance invariants for building 120 FPS inventory ledger lists in Jetpack Compose.

## When to Use
- Rendering high-density, scrollable inventory lists with dynamic filtering.
- Implementing swipe-to-decrement or swipe-to-delete gestures without stutter.
- Preventing unnecessary recompositions during live search typing.

## Core Architectural Invariants
1. **Persistent Stable Keys:** Every `items()` call must specify a persistent, unique identity key: `items(items = items, key = { it.id })`. Never use item list index.
2. **Lambda Callbacks Over ViewModels:** Child item composables must receive granular lambdas (e.g. `onIncrement: (Long) -> Unit`) rather than full ViewModel references, allowing Compose to skip recomposition when sibling items update.
3. **Derived State & Debouncing:** Search input must debounce state updates (50ms) to prevent re-filtering the list on every raw frame. Use `derivedStateOf` for filter calculations.
4. **Stable / Immutable Annotations:** Annotate UI state models with `@Immutable` or `@Stable` to allow Compose compiler smart skipping.
5. **Tactile Haptic Feedback:** Trigger `HapticFeedbackType.LongPress` or `CLOCK_TICK` when gesture swipe thresholds are crossed.

## Implementation Pattern (Compose Material 3)
```kotlin
@Composable
fun PantryLedger(
    items: List<PantryItem>,
    onItemClick: (Long) -> Unit,
    onDecrement: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = items,
            key = { it.id } // STABLE KEY INVARIANT
        ) { item ->
            PantryItemCard(
                item = item,
                onClick = { onItemClick(item.id) },
                onDecrement = { onDecrement(item.id) }
            )
        }
    }
}
```
