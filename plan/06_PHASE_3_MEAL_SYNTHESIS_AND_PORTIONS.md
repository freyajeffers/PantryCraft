# PantryCraft: Phase 3 Implementation Plan

* * *

## Portion-Aware Meal Synthesis & Dual-Inference Reasoning Engine

* * *

**Phase 3** | **Core Culinary Intelligence** | **Dual-Inference (On-Device & BYOKey)**

### 1. Phase Scope & Objectives

Phase 3 delivers the primary culinary value loop of PantryCraft immediately following camera and manual inventory ingestion (Phases 1 & 2). By advancing meal synthesis to Phase 3, users can scan ingredients with their camera, store them in the encrypted local database, and immediately experience portion-accurate, chef-quality meal recommendations without waiting for audio/voice subsystem buildouts.

The system supports full-database ingestion, dynamic portion scaling (1 to 12 servings), and four distinct matching philosophies:

1. **Strict Mode ("Zero-Waste / Use Only What I Have"):** Uses 100% ingredients already in stock. Zero outside purchases allowed (except water, cooking oil, and salt). Specifically prioritizes ingredients nearest to expiration.
2. **Mostly Mode ("Pantry First"):** Uses 85–95% on-hand inventory while allowing up to 2 common pantry staples or flexible substitutes.
3. **Shopping Mode ("Inspirational Chef"):** Generates elevated meals based around on-hand star ingredients, automatically outputting a clean grocery delta list for any missing components.
4. **15-Minute Emergency Salvage Mode:** Single-tap panic button targeting ingredients expiring within 48 hours for rapid rescue meals (stir-fries, frittatas, soups, skillet bakes).

### 2. Architectural Components & Responsibilities

| Component | Package / Namespace | Responsibilities |
| :--- | :--- | :--- |
| **InventoryContextCompressor** | `feature.meals.context` | Serializes active Room inventory (`isDeleted == 0`) into a token-optimized semantic context block, sorting by expiration urgency and categorizing by storage zone. |
| **MealPromptBuilder** | `feature.meals.prompt` | Constructs system and user prompts injecting user dietary constraints, portion count, cooking appliance filters, and selected matching mode. |
| **ModelRouter** | `core.ai.router` | Abstracts inference behind a unified interface; dynamically routes requests to either local on-device models or cloud BYOKey providers based on user preference. |
| **LiteRtModelEngine** | `core.ai.local` | Executes on-device inference: uses Android AICore (Gemini Nano) with zero network setup on supported hardware; or loads on-demand downloadable LiteRT-LM (Gemma 2 2B / Qwen 2.5 3B) via Play Feature Delivery. |
| **ByokCloudModelEngine** | `core.ai.cloud` | Ktor HTTP client with Server-Sent Events (SSE) streaming supporting OpenAI, Anthropic, Google Gemini, OpenRouter, and self-hosted Ollama endpoints. |
| **MealRecipeScreen** | `feature.meals.ui` | Jetpack Compose UI with interactive portion counter, cooking mode selector, streaming recipe markdown cards, and "Cook This Meal" deduction trigger with fractional fallback modal support. |

### 3. Data Contracts & Recipe Output Schema

#### A. Full-Database Context Serialization Format

To fit within local SLM context windows (2k–4k tokens), the context compressor encodes stock in a hyper-compact notation:

```text
[INVENTORY_CONTEXT]  
Pantry: jasmine_rice(2kg), olive_oil(500ml), canned_black_beans(2cans), yellow_onion(3count), garlic(1head)  
Fridge: greek_yogurt(500g, EXP:2d), baby_spinach(200g, EXP:1d), cheddar_cheese(250g, EXP:12d), eggs(6count, EXP:4d)  
Freezer: chicken_thighs(1kg, EXP:90d), frozen_peas(400g, EXP:120d)  
[EXPIRY_URGENT]: baby_spinach (1 day left!), greek_yogurt (2 days left!)  
```

#### B. Structured Meal Output JSON Contract

```json
{  
 "recipes": [  
   {  
     "recipe_id": "rec_spinach_cheddar_omelet",  
     "title": "Creamy Spinach & Cheddar Farmhouse Omelet",  
     "match_mode": "STRICT",  
     "target_portions": 2,  
     "prep_time_minutes": 10,  
     "cook_time_minutes": 10,  
     "urgency_score": 0.95,  
     "items_used_from_stock": [  
       { "name": "baby_spinach", "pantry_item_id": 2, "quantity_used": 150.0, "unit": "GRAMS", "prevents_waste": true },  
       { "name": "eggs", "pantry_item_id": 4, "quantity_used": 4.0, "unit": "COUNT", "prevents_waste": false },  
       { "name": "cheddar_cheese", "pantry_item_id": 3, "quantity_used": 50.0, "unit": "GRAMS", "prevents_waste": false },  
       { "name": "olive_oil", "pantry_item_id": 1, "quantity_used": 15.0, "unit": "MILLILITERS", "prevents_waste": false }  
     ],  
     "missing_items_for_grocery": [],  
     "step_by_step_instructions": [  
       "1. Heat 1 tbsp olive oil in a skillet over medium heat.",  
       "2. Add 150g baby spinach and saute for 2 minutes until wilted.",  
       "3. Whisk 4 eggs in a bowl with a pinch of salt; pour over spinach.",  
       "4. Top with 50g grated cheddar, fold, and cook for 3 minutes until golden."  
     ],  
     "nutritional_estimates_per_portion": {  
       "calories": 320,  
       "protein_grams": 22,  
       "carbs_grams": 3,  
       "fat_grams": 24  
     }  
   }  
 ]  
}  
```

### 4. Portion Scaling Logic & Culinary Mathematics

* **Linear vs. Non-Linear Multipliers:**
  * Bulk ingredients (proteins, produce, grains, liquids) scale linearly:  
    $$	ext{scaled} = 	ext{base} 	imes \left(rac{	ext{portions}}{	ext{base\_portions}}ight)$$
  * Potent seasonings, salt, chili heat, and pungent spices (identified via `isAromaticOrSeasoning == true` on `PantryItem`) scale with fractional dampening:  
    $$	ext{scaled} = 	ext{base} 	imes \left(rac{	ext{portions}}{	ext{base\_portions}}ight)^{0.75}$$
* **Unit Normalization & Deduction Fallbacks:** Quantities automatically convert to user-friendly kitchen measures (e.g., 16 tbsp -> 1 cup). When stock deduction involves dimensionally incompatible units (e.g. recipe calls for `10g` mint while inventory has `1 BUNCH`), the system prompts with a 1-tap fractional deduction modal (*[25%] [50%] [100%] [Custom]*).

### 5. BYOKey Security & On-Device Packaging Protocol

* **Zero External Proxy:** Requests dispatch straight from device to official endpoints (OpenAI, Anthropic, Google, OpenRouter, or local LAN Ollama).
* **Hardware-Backed Protection:** API keys encrypted at rest in `EncryptedSharedPreferences` via Android KeyStore; decrypted strictly in-memory during request dispatch.
* **Model Packaging & APK Compliance:** Base APK bundles zero heavy neural weights to maintain compliance with Google Play (<200MB). On-device SLM inference defaults to system Android AICore (Gemini Nano). LiteRT-LM models (~1.5GB) are downloaded on-demand through Play Feature Delivery only upon explicit user opt-in.

### 6. Acceptance & Verification Gates

* **Strict Constraint Gate:** 100% of generated ingredients in Strict Mode must be confirmed present in inventory.
* **Portion Scaling Test:** Verified scaling accuracy from 1 to 12 servings with zero unit conversion anomalies.
* **Response Latency Budget:** First streamed recipe token displayed in <1.2s on cloud BYOKey and <2.5s on on-device models.
