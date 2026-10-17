# PantryCraft: Autonomous Agent Skills Suite & Expansion Specification

This document details the newly added and updated project engineering skills designed to equip autonomous coding agents and mobile developers to build an agentic, proactive, and resilient kitchen operating system.

---

## 1. Summary of Skills Portfolio (14 Skills Total)

| Skill Name | Status | Category | Primary Focus & Responsibility |
| :--- | :---: | :--- | :--- |
| **`agentic-culinary-tool-calling`** | **NEW** | Agentic Reasoning | Local ReAct function calling loops for Gemini Nano / LiteRT-LM over Room SQLite. |
| **`culinary-episodic-memory-reflection`** | **NEW** | Cognitive Memory | On-device reflection tables, user palate calibration, and prep-time grounding. |
| **`adaptive-sous-chef-copilot`** | **NEW** | Ambient In-Kitchen | Low-power VAD ambient companion, mishap recovery, and mid-cooking substitutions. |
| **`multi-day-meal-graph-planner`** | **NEW** | Horizon Planning | 3–5 day DAG meal sequencing, shared perishable chaining, and leftover carry-forward. |
| **`inventory-drift-reconciliation`** | **NEW** | Sensor Fusion | Time-decay confidence scoring, passive recipe inference, and visual delta auditing. |
| **`litert-aicore-mobile-ai`** | **UPDATED** | Mobile AI Core | Added structured function signatures and multi-turn ReAct tool execution interface. |
| **`culinary-recipe-mechanics`** | **UPDATED** | Food Chemistry | Added palate-calibrated power curves ($0.65$–$0.85$) and leftover carry-forward yield math. |
| **`sherpa-onnx-stt`** | **UPDATED** | Audio Pipeline | Added TTS barge-in interruptibility and low-power VAD intent routing. |
| **`room-sqlcipher-concurrency`** | **UPDATED** | Persistence | Hardened WAL concurrency for background agent tool calls and episodic reflection tables. |
| **`android-camerax-mlkit-pipeline`** | **HARDENED** | Vision & Camera | ImageProxy closing, `AtomicBoolean` lock, dual-pass chaining, and canvas mapping. |
| **`room-schema-migration-guard`** | **HARDENED** | Database Migrations | Non-destructive v1->v2 migrations, guarded FTS5 triggers, and migration tests. |
| **`compose-high-perf-ledger`** | **BASELINE** | Presentation UI | 120 FPS LazyColumn stable keys, query debouncing, and swipe-to-delete physics. |
| **`offline-barcode-inventory-resolver`** | **HARDENED** | Logistics | Embedded 50k SQLite cache + local user-learned `custom_barcode_cache` fallback. |
| **`multimodal-shelf-vlm-parser`** | **HARDENED** | Vision LLMs | In-memory WebP downsampling and normalized bounding boxes `[ymin, xmin, ymax, xmax]`. |

---

## 2. Detailed Specifications: 5 New Agentic Skills

### Skill 1: `agentic-culinary-tool-calling`
* **Purpose:** Enables on-device SLMs (Gemini Nano via AICore or LiteRT-LM) and cloud BYOKey providers to execute multi-turn ReAct loops (*Reason $\to$ Call Local Tool $\to$ Observe $\to$ Plan*) over the encrypted database without network roundtrips.
* **Core Invariants:**
  1. *Sandbox Boundaries:* Tools exposed to the model (`search_inventory`, `simulate_depletion`, `add_grocery_draft`, `schedule_prep_timer`) are read-only or staged as uncommitted drafts. Direct mutations require explicit user approval.
  2. *Turn Ceiling:* Enforce a strict ceiling of $\le 3$ tool turns per task to conserve battery and remain within the 2k–4k token context window.
  3. *Resilient Parameter Extraction:* Decouple tool calling from fragile JSON formatters by supporting regex/BNF fallback decoders.
* **Tool Contract:**
```kotlin
sealed class CulinaryTool(val name: String, val description: String) {
    data class SearchInventory(val zone: String?, val category: String?, val maxExpiryDays: Int?) : CulinaryTool("search_inventory", "Query active stock by zone or category")
    data class SimulateDepletion(val recipeId: String, val portions: Int) : CulinaryTool("simulate_depletion", "Verify if recipe leaves invalid negative balances")
    data class AddGroceryDraft(val items: List<String>) : CulinaryTool("add_grocery_draft", "Queue missing bridge items into grocery checklist")
}
```

---

### Skill 2: `culinary-episodic-memory-reflection`
* **Purpose:** Builds an on-device reflection and calibration loop that learns household palate profiles, recalibrates model prep-time estimates, and prevents recipe fatigue without telemetry.
* **Core Invariants:**
  1. *Zero-Telemetry Storage:* Reflection metrics reside exclusively in the local encrypted `culinary_reflections` table.
  2. *Adaptive Exponent Calibration:* Adjusts the standard $0.75$ seasoning power curve exponent per household profile ($0.65$ for sensitive palates; $0.85$ for bold palates) based on 1–5 star ratings.
  3. *Prep-Time Grounding:* Tracks moving average ratios ($ratio = \text{actual} / \text{estimated}$) across cooking timers to correct optimistic model prep times.
* **Entity Schema:**
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
    val seasoningAdjustment: Float = 1.0f
)
```

---

### Skill 3: `adaptive-sous-chef-copilot`
* **Purpose:** Powers a low-power, ambient hands-free voice companion during cooking that handles live kitchen mishaps, step navigation, and on-the-fly substitutions.
* **Core Invariants:**
  1. *Low-Power VAD:* Utilizes Voice Activity Detection to listen only during human speech, keeping battery consumption $<4\%$ over 30 minutes.
  2. *Live Mishap Recovery:* Dynamically computes pantry bridge substitutions when a cook announces burned food, broken sauces, or missing eggs mid-recipe.
  3. *Audio Barge-In:* Automatically pauses TTS narration immediately when user speech is detected to receive commands or questions.

---

### Skill 4: `multi-day-meal-graph-planner`
* **Purpose:** Constructs multi-day meal sequence graphs (3–5 day horizon DAGs) that chain shared perishable ingredients and carry forward leftovers to systematically eliminate household food waste.
* **Core Invariants:**
  1. *DAG Sequencing:* Candidate meals are modeled as nodes with shared perishable ingredients as directed edges.
  2. *Zero-Strand Invariant:* Prohibits generating isolated recipes that leave partial perishable containers stranded without a paired follow-up dish within 48 hours.
  3. *Leftover Carry-Forward Math:* Batch portion surplus (e.g. 4 portions cooked for 2 people) is automatically scheduled into the next-day lunch ledger.

---

### Skill 5: `inventory-drift-reconciliation`
* **Purpose:** Detects and corrects discrepancies between database records and physical kitchen shelves using visual deltas, consumption burn rates, and passive inference.
* **Core Invariants:**
  1. *Probabilistic Confidence Decay:* Degrades stock confidence over time ($confidence = initial \times e^{-\lambda \cdot \Delta t}$) for items not scanned in $>14$ days.
  2. *Passive Recipe Inference:* Prompts gentle verification when correlated staple ingredients (oil, salt, butter) are used in meals without explicit logging.
  3. *Visual Delta Auditing:* Aligns CameraX shelf bounding boxes against historical states to flag candidate-depleted items without premature deletion.

---

## 3. Specifications: 4 Updated Baseline Skills

### 1. `litert-aicore-mobile-ai` (Updated)
* Added the `generateWithTools` interface to support local ReAct tool calling with on-device models.
* Enforced context window budgeting and hardware memory gates before loading multi-turn models into RAM.

### 2. `culinary-recipe-mechanics` (Updated)
* Integrated the dynamic palate calibration exponent ($0.65$ to $0.85$) derived from `culinary_reflections`.
* Formulated leftover carry-forward yield math and culinary density conversions.

### 3. `sherpa-onnx-stt` (Updated)
* Integrated ambient VAD barge-in interruptibility: TTS audio pauses immediately when incoming microphone speech is detected.
* Thread-pinned JNI audio recognition to `Dispatchers.Default` away from the UI thread.

### 4. `room-sqlcipher-concurrency` (Updated)
* Added concurrent WAL handling for background agent ReAct tool calls while UI read flows are active.
* Provisioned the `culinary_reflections` schema and KeyStore invalidation recovery protocol.
