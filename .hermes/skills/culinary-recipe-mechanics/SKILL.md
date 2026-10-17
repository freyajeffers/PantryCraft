---
name: culinary-recipe-mechanics
description: Apply food chemistry rules, non-linear seasoning scaling algorithms, user palate calibration, USDA FoodKeeper shelf-life curves, and ethylene gas clash warnings.
---

# Culinary Recipe Mechanics & Food Chemistry

Domain rules for culinary scaling, food preservation, palate calibration, and recipe synthesis constraints.

## When to Use
- Scaling recipes accurately from 1 to 12 servings without ruining seasoning balance.
- Dynamically calibrating the seasoning exponent ($0.75$) based on episodic user feedback.
- Resolving culinary unit conversions (mass, volume, count) and dimension mismatches during stock depletion.
- Chaining shared perishable ingredients across multi-day horizons to prevent food waste.
- Detecting food storage conflicts (e.g. ethylene gas clashes).

## Core Architectural Invariants
1. **Calibrated Non-Linear Seasoning Scaling:** Standard linear scaling ruins recipes at high portion counts. Spices, salt, chilies, and aromatics (tagged with `isAromaticOrSeasoning == true`) scale on an adaptive power curve:
   $$\text{scaled} = \text{base} \times \left(\frac{\text{portions}}{\text{base\_portions}}\right)^{\text{exponent}}$$
   The baseline exponent is $0.75$, calibrated dynamically ($0.65$ to $0.85$) based on historical feedback in `culinary_reflections`.
2. **Leftover Carry-Forward Yield Math:** Factor cooking yields: batch portions minus current meal consumption are carried forward as scheduled leftovers in the next day's inventory ledger.
3. **Culinary Unit Conversion Matrix:**
   - Liquids / Oils: $1\text{ tbsp} = 15\text{ ml}$, $1\text{ tsp} = 5\text{ ml}$, $1\text{ cup} = 240\text{ ml}$.
   - Butter / Fats: $1\text{ stick} = 8\text{ tbsp} = 113.4\text{ g}$.
   - Flour / Grains: $1\text{ cup flour} \approx 120\text{ g}$, $1\text{ cup rice} \approx 185\text{ g}$.
   - Sugar: $1\text{ cup} \approx 200\text{ g}$.
   - Garlic: $1\text{ clove} = 1\text{ count}$.
4. **Incompatible Unit Fallback Modal:** If an item cannot be mathematically converted (e.g. recipe calls for `10g` mint, inventory tracks `1 BUNCH`), trigger a 1-tap fractional deduction modal (`[25%] [50%] [100%] [Custom]`) to prevent hanging transactions.
5. **Ethylene Gas Clash Detection:** Flag ethylene gas conflicts during inventory entry (e.g. storing apples, bananas, or avocados next to leafy greens, cucumbers, or carrots accelerates spoiling).
6. **Dynamic Opened-State Degradation:** Opening a container dynamically recomputes the expiration timestamp based on refrigerated opened degradation curves rather than the original manufacturer shelf-life date.
