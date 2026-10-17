# PantryCraft: Detailed Sub-Phases & Architectural Innovations

* * *

## A Deep-Dive, Non-Prescriptive Engineering Blueprint for Local-First Kitchen Intelligence

* * *

**Architecture Blueprint** | **Novel Concepts Included** | **Pragmatic Agentic Innovations**

**Guiding Engineering Philosophy:** This blueprint defines strict architectural boundaries, data flow contracts, and acceptance gates. Rather than relying on slow, battery-draining multi-agent LLM swarms, PantryCraft achieves responsive "agentic" utility through **instant deterministic local logic (Room SQLite, ML Kit, culinary heuristics, and WorkManager timers)** augmented by targeted AI only where generative creativity is strictly required.

---

## Phase 1: Local Foundation, Storage Hardening & Semantic Inventory Ledger

* * *

### Sub-Phase 1.1: Cryptographic Storage & Key Lifecycle Management
* **Architectural Objectives:** Establish an unbreakable local persistence foundation ensuring zero plaintext data leakage even if a physical device is extracted or inspected.
* **Component Boundaries:**
  * **Key Authority (`SecurityKeyManager`):** Interacts exclusively with Android KeyStore using hardware-backed security modules (StrongBox Keymaster on API 28+, falling back to TEE KeyStore on API 23–27). Generates an AES-256-GCM master key used to encrypt and authenticate a random 256-bit passphrase generated via `SecureRandom`. The ciphertext is stored in private app storage.
  * **Invalidation Recovery:** Catches `KeyPermanentlyInvalidatedException` if device lock credentials change, offering safe backup restoration via PIN or explicit clean database re-initialization rather than crashing.
  * **Encrypted Engine Wrapper (`DatabaseFactory`):** Decrypts the 256-bit passphrase in memory and bridges Room with SQLCipher (`SupportFactory(passphraseBytes)`). Zeroes decrypted passphrase buffers from memory once database initialization completes.
* **Storage Hardening Invariants:**
  * Mandate SQLite Write-Ahead Logging (WAL mode via `RoomDatabase.Builder.setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)`).
  * Configure memory-mapped I/O (`PRAGMA mmap_size = 268435456`) and set busy timeout ceilings to at least 5000ms (`PRAGMA busy_timeout = 5000`).
  * Enforce private sandbox isolation (0600 permissions on database files, 0700 on database directory).
* **Acceptance & Verification:** Physical database binary pulled via ADB must be unreadable and throw corruption/invalid format errors when queried without the decrypted passphrase.

### Sub-Phase 1.2: Dynamic Domain Model & Ethylene Gas Storage Watchdog
* **Architectural Objectives:** Formulate an extensible relational schema (`pantry_items`, `grocery_items`) representing culinary units, storage zones, perishability states, and nutritional tags.
* **Domain Invariants:**
  * **Storage Zones:** First-class categorization for `PANTRY`, `FRIDGE`, `FREEZER`, and `COUNTER` (ambient).
  * **Unit Flexibility:** Support dual discrete (`COUNT`, `BUNCH`, `CAN`) and continuous volumetric/mass units (`GRAMS`, `KILOGRAMS`, `MILLILITERS`, `LITERS`, `OUNCES`, `POUNDS`, `CUPS`, `TBSP`, `TSP`).
  * **Opened vs. Unopened State:** Opening a container triggers a dynamic shelf-life adjustment (e.g., unopened canned broth lasts 12 months; opened broth expires in 4 days).
  * **Seasoning & Aromatic Distinction:** `isAromaticOrSeasoning: Boolean = false` enables routing spices, chilies, and salt through non-linear scaling power curves ($portions^{0.75}$).
  * **Soft-Deletion Tombstones:** `isDeleted: Boolean = false` enables undo snackbars and safe decentralized synchronization.
* **💡 Pragmatic Agentic Innovation: Ethylene Gas & Biochemical Storage Watchdog:**  
  An embedded 5 KB offline botanical lookup table of ethylene producers (apples, bananas, avocados, tomatoes) and ethylene-sensitive produce (leafy greens, carrots, cucumbers, broccoli).
  * Executes deterministically in $<1\text{ms}$ upon item insertion.
  * If a user logs ethylene producers alongside sensitive items in the same storage zone, triggers an instant advisory banner: *"Bananas emit ethylene gas, wilting spinach 3x faster. Move spinach to the crisper drawer?"* [1-Tap Zone Move].
* **Acceptance & Verification:** Inserting conflicting produce triggers the biochemical compatibility warning without network or LLM latency.

### Sub-Phase 1.3: Instant FTS5 Search & Query Engine
* **Architectural Objectives:** Deliver instantaneous prefix and phonetic searching across thousands of ingredients with zero perceivable latency.
* **Component Boundaries:** Establish an SQLite FTS5 virtual table (`pantry_items_fts`) synchronized with `pantry_items` via guarded triggers (`WHEN new.isDeleted = 0` / `WHEN old.isDeleted = 0`).
* **Performance Invariant:** Multi-term search queries across 5,000 synthetic records must complete and emit updated results to the UI in under 5 milliseconds.
* **Acceptance & Verification:** Queries handling typos, pluralization, and partial prefixes return consistent ranking results.

### Sub-Phase 1.4: Minimalist Presentation Ledger & 60-Second "Ghost Inventory" Audit
* **Architectural Objectives:** Build an uncluttered, high-density, gesture-driven inventory interface honoring Modern Android Material 3 design.
* **UX Patterns:**
  * Single-handed swipe gestures: Swipe right to decrement a portion; swipe left to soft-delete with an instant undo action.
  * Visual urgency badges: Dynamic color halos indicating perishability (safe > 5d, consuming soon 2-4d, urgent salvage < 48h).
  * Instant filtering chips: Filter seamlessly by zone (`Fridge` / `Pantry` / `Freezer`) or urgency state.
* **💡 Pragmatic Agentic Innovation: The 60-Second "Ghost Inventory" Audit:**  
  Solves digital pantry drift where users forget to log consumed items:
  * On Sunday mornings, an inline card presents a 5-card rapid verification carousel for items untouched for $>14$ days:
    `SELECT * FROM pantry_items WHERE lastUpdated < (now - 14 days) AND storageLocation = 'FRIDGE' AND isDeleted = 0 LIMIT 5`.
  * Swipe right to confirm stock is still good; swipe left to mark finished (with auto-prompt: *"Finished? Add to grocery list?"*).
  * Reconciles physical fridge contents with database state in under 30 seconds.
* **Acceptance & Verification:** UI maintains steady 60/120 FPS frame rates during continuous fling scrolling across an inventory ledger of 1,000 items.

---

## Phase 2: CameraX Dual-Mode Vision Pipeline & Visual Auditing

* * *

### Sub-Phase 2.1: Lifecycle-Aware Camera Hardware Integration
* **Architectural Objectives:** Construct a rock-solid CameraX pipeline that binds preview, analysis, and high-res capture to Android lifecycle owners with instant warm-up.
* **Invariants:** Zero resource leaks when switching apps; graceful unbinding of hardware buffers; automated resolution matching.
* **Acceptance & Verification:** 100 consecutive background/foreground app switches with active camera without causing memory creep or `CameraAccessException`.

### Sub-Phase 2.2: Low-Latency On-Device Object & Barcode Stream (15 FPS)
* **Architectural Objectives:** Project interactive bounding boxes and instant recognition chips over the live camera viewfinder in real time without network connectivity.
* **Processing Pipeline:**
  * Throttled to 12–15 FPS on a background executor with an `AtomicBoolean` in-flight lock.
  * Dual-analyzer pass: Barcode detection first, followed by object boundary detection before releasing the frame buffer.
  * Local barcode resolution: Checks local `custom_barcode_cache` first, then embedded 50,000-item SQLite cache in sub-10ms.
* **Acceptance & Verification:** Average analysis latency per frame must remain below 40ms, preventing viewfinder stuttering.

### Sub-Phase 2.3: High-Res Shelf Decomposition & Multimodal VLM Adapter
* **Architectural Objectives:** Allow users to capture a single still photograph of a crowded refrigerator or pantry shelf and decompose the entire scene into individual inventory records.
* **Engineering Boundaries:**
  * In-memory downsampling to WebP/JPEG (1024x1024 or 1536x1536); zero raw bitmaps persisted to public storage.
  * Enforce strict JSON output schemas requiring item names, estimated quantities, packaging states, normalized coordinates (`[ymin, xmin, ymax, xmax]`), and storage zones.
  * Staging & review bottom sheet allows 1-tap bulk corrections before database insertion.
* **Acceptance & Verification:** Multi-ingredient test scenes (5–10 items) parse with $\ge 85\%$ recall and 100% schema conformity.

### Sub-Phase 2.4: Differential Shelf Audits (Visual Delta Detection)
* **Architectural Objectives:** Tagged shelf photos ("Top Refrigerator Shelf") compare current scenes against prior snapshots:
  * Detects missing items: *"Greek yogurt is no longer visible. Did you finish it?"*
  * Detects moved or newly introduced items and updates their zone automatically.
* **Acceptance & Verification:** Visual delta engine identifies missing objects between two consecutive state photos of a controlled test shelf.

### Sub-Phase 2.5: Receipt OCR-to-Inventory & List Reconciliation (Single-Tap Batch Ingestion)
* **💡 Pragmatic Agentic Innovation: Receipt Batch Ingestion:**  
  Eliminates the friction of scanning 25 individual barcodes after grocery shopping:
  * Uses Google ML Kit Text Recognition (100% on-device, $<500\text{ms}$) to parse long paper supermarket receipts.
  * Cleans receipt abbreviations (`ORG GREEK YOG 32OZ` $\to$ `Greek Yogurt`, `BANANAS ORG` $\to$ `Bananas`) using local regex and FTS phonetic matching.
  * Automatically reconciles against `grocery_items`: checks off purchased items, removes them from the shopping list, and commits active items into `pantry_items` with pre-computed default shelf-lives in a single confirmation tap.
* **Acceptance & Verification:** Test receipt of 15 standard grocery items parses and populates active stock with $>90\%$ line-item precision.

---

## Phase 3: Context-Aware Culinary Reasoning & Portion Synthesis

* * *

### Sub-Phase 3.1: Compact Inventory State Representation & Token Optimization
* **Architectural Objectives:** Serialize active inventory into a hyper-compact semantic context block fitting within on-device SLM context windows (2k–4k tokens).
* **Compression Heuristics:** Sort items hierarchically by urgency score (days remaining) followed by protein/produce/pantry categories; omit internal IDs and timestamps.
* **Acceptance & Verification:** 200 inventory items serialize into fewer than 800 tokens of prompt context.

### Sub-Phase 3.2: Dual-Inference Model Router & BYOKey Isolation
* **Architectural Objectives:** Seamlessly abstract between on-device SLMs (Android AICore Gemini Nano / on-demand LiteRT-LM) and cloud BYOKey providers (OpenAI, Anthropic, Gemini, OpenRouter, local Ollama).
* **Security Isolation:** BYOKey credentials reside exclusively in hardware-backed `EncryptedSharedPreferences`.
* **Acceptance & Verification:** Switching between Local and BYOKey cloud engines requires zero changes to the presentation layer or recipe parser.

### Sub-Phase 3.3: Portioned Culinary Mechanics & Non-Linear Scaling
* **Architectural Objectives:** Scale recipes dynamically from 1 to 12 servings with culinary accuracy:
  * Bulk ingredients scale linearly: $\text{scaled} = \text{base} \times (\text{portions} / \text{base})$.
  * Potent seasonings, chilies, and salt (`isAromaticOrSeasoning == true`) scale on a fractional power curve: $\text{scaled} = \text{base} \times (\text{portions} / \text{base})^{0.75}$.
* **Acceptance & Verification:** Scaling 2 portions to 8 portions maintains balanced seasoning proportions and emits clean measurement units.

### Sub-Phase 3.4: 15-Minute Emergency Salvage Mode ("Pantry Panic Button")
* **Architectural Objectives:** Dedicated single-tap feature for busy users facing expiring food:
  * Queries items expiring in $<48\text{ hours}$.
  * Constrains generation to fast, forgiving preparation styles (stir-fries, frittatas, salvage soups, skillet hash).
  * Calculates and displays *"Food Waste Prevented: $X.XX and Y grams of CO₂ saved"* to gamify sustainability.

### Sub-Phase 3.5: The "Smart Swap" Pantry-Aware Substitution Engine
* **💡 Pragmatic Agentic Innovation: Deterministic Kitchen Bridges:**  
  When a recipe calls for an ingredient the user is missing, instead of failing or forcing a grocery run:
  * An embedded SQLite substitution graph maps missing items against active on-hand stock in $<5\text{ms}$:
    * Missing Buttermilk? Active stock has Whole Milk + Lemon Juice $\to$ Suggests *"1 cup milk + 1 tbsp lemon juice"*.
    * Missing Sour Cream? Active stock has Greek Yogurt $\to$ Suggests *"1:1 Greek yogurt swap"*.
    * Missing Eggs? Active stock has Yogurt or Flaxseed $\to$ Suggests baking binder swap.
  * Inline suggestion chips appear directly on the recipe card without re-prompting the AI model.
* **Acceptance & Verification:** Recipes with missing minor components identify valid on-hand culinary substitutions deterministically.

### Sub-Phase 3.6: "Energy-Adaptive" Prep Dial & The "Thaw & Prep" Temporal Coordinator
* **💡 Pragmatic Agentic Innovations:**  
  * **Energy-Adaptive Prep Dial:** Home screen toggle allows filtering dinner proposals by cook bandwidth:
    * *Low Energy:* Constrains generation to $\le 20\text{ mins}$ and 1 pan/appliance (sheet-pan bakes, skillet hash).
    * *Standard:* 30–45 mins everyday cooking.
    * *Weekend Project:* Complex, multi-stage culinary exploration.
  * **The "Thaw & Prep" Coordinator:** When a user selects a recipe featuring frozen proteins in the morning, the app checks `storageLocation == 'FREEZER'` and schedules a local WorkManager notification 4–5 hours before dinner: *"Thaw 500g chicken thighs in the fridge now for tonight's stir-fry."*
* **Acceptance & Verification:** Selecting a freezer-anchored recipe automatically enqueues the temporal thawing reminder.

---

## Phase 4: Real-Time Multimodal Voice & Ambient Kitchen NLP

* * *

### Sub-Phase 4.1: Hardware Audio Capture & Ephemeral Ring Buffering
* **Architectural Objectives:** Capture pristine 16kHz mono PCM audio streams into volatile ring buffers (max 10s capacity) with zero audio files persisted to disk.
* **Acceptance & Verification:** Profiler verifies zero disk writes or audio file descriptor leaks during 5 minutes of continuous audio recording.

### Sub-Phase 4.2: On-Device Streaming Speech-to-Text Engine
* **Architectural Objectives:** Sub-150ms latency voice transcription using Sherpa-ONNX (INT8 Zipformer / Whisper-tiny) with fallback to system offline `SpeechRecognizer`.
* **Acceptance & Verification:** Real-Time Factor (RTF) remains below 0.35 on standard ARM64 CPUs.

### Sub-Phase 4.3: Natural Language Semantic Extraction & Normalization
* **Architectural Objectives:** Parse colloquial spoken transcripts into structured `pantry_items` records with quantities, units, zones, and shelf-life estimates.
* **Acceptance & Verification:** Test suite of 100 conversational speech transcripts achieves $>90\%$ accuracy in entity and attribute mapping.

### Sub-Phase 4.4: Hands-Free "Cooking Companion" Voice Interface
* **Architectural Objectives:** Ambient hands-free voice controller for messy kitchen hands:
  * Voice-guided step navigation (*"Next step"*, *"Repeat that"*).
  * Low-power Voice Activity Detection (VAD) listening consumes $<4\%$ battery over 30 minutes.
  * Barge-in support: TTS step readout automatically pauses when incoming speech is detected.

---

## Phase 5: Stock Logistics, Food Waste Prevention & Zero-Knowledge Mesh

* * *

### Sub-Phase 5.1: Atomic Stock Depletion & Leftover "Yield Staging"
* **Architectural Objectives:** Connect the act of cooking directly back into real-time inventory balances without manual re-entry.
* **Transaction Contract:** Tapping "Cooked This Recipe" initiates an atomic Room SQLite transaction that decrements measured quantities.
* **Culinary Unit Conversion & Fallback Modal:** Resolves dimension mismatches ($1\text{ tbsp} = 15\text{ ml}$, $1\text{ stick butter} = 113.4\text{ g}$) or presents a 1-tap fractional modal (*[25%] [50%] [100%] [Custom]*).
* **💡 Pragmatic Agentic Innovation: Leftover Yield Staging:**  
  When a 4-portion meal is cooked for a 2-person household, the completion prompt offers 1-tap leftover logging:
  * Deducts raw ingredients.
  * Inserts `PantryItem(name = "Leftover " + title, quantity = 2, unit = "PORTION", storageLocation = "FRIDGE", expirationTimestamp = now + 4 days)`.
  * Closes the culinary loop without manual cataloging.
* **Acceptance & Verification:** Concurrently executing depletion transactions while background ingestion occurs completes without deadlocks or dirty reads.

### Sub-Phase 5.2: Perishability Curves & Local Notification Daemon
* **Architectural Objectives:** Proactively alert users to expiring food before it spoils using WorkManager periodic background checks registered with an initial delay targeted to 8:00 AM local time.
* **Acceptance & Verification:** WorkManager job satisfies Android battery optimization constraints (Doze mode) without dropping scheduled alerts.

### Sub-Phase 5.3: Intelligent Grocery Logistics & Staple Burn-Rate Forecasting
* **Architectural Objectives:** Unified grocery replenishment loop bridging depleted pantry stock with supermarket shopping.
* **💡 Pragmatic Agentic Innovation: Staple Burn-Rate Velocity Forecaster:**  
  Calculates household consumption intervals for staples (eggs, milk, olive oil, coffee):
  $$\text{Burn Velocity} = \frac{\sum (\text{restock\_date}_{n} - \text{restock\_date}_{n-1})}{N}$$
  On the projected depletion day, proactively flags the item in the grocery list: *"Eggs (Predicted low stock based on 12-day restock cycle)"*.
* **Acceptance & Verification:** Depleted staple items automatically suggest for replenishment.

### Sub-Phase 5.4: Zero-Knowledge Decentralized Mesh & Drive Backup
* **Architectural Objectives:** Peer-to-peer Wi-Fi household inventory sync:
  * Discovers peers via mDNS; authenticates via Short Authentication String (SAS) or QR-code certificate fingerprint exchange for mutual TLS (mTLS).
  * Last-Write-Wins (LWW) conflict resolution with soft-deletion tombstones (`isDeleted = 1`) pruned after 30 days.
  * Optional personal cloud backup via Google Drive `drive.appdata` hidden scope.
* **Acceptance & Verification:** Two separate devices on the same Wi-Fi network synchronize inventory updates within 2 seconds.
