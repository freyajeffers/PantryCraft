# Original User Request

## Initial Request — 2026-10-27T10:52:26Z

Deliver a production-ready, fully hardened implementation of Phases 1 through 3 for PantryCraft: an encrypted local-first inventory database, a CameraX dual-mode edge vision pipeline with ML Kit and Gemini Live WebSocket streaming, and a portion-aware meal synthesis reasoning engine supporting on-device LiteRT/AICore and BYOKey cloud models with seamless end-to-end user journeys.

Working directory: /home/freya/Projects/pantrycraft
Integrity mode: development

## Requirements

### R1. Local-First Encrypted Architecture & Inventory Engine (Phase 1)
- Encrypted SQLite database using Room and SQLCipher with random 256-bit passphrase protected by Android KeyStore (AES-256-GCM with StrongBox/TEE), ensuring memory byte scrubbing post-initialization.
- WAL concurrency (`setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)`, `PRAGMA busy_timeout = 5000`, `PRAGMA mmap_size = 268435456`) and sub-5ms SQLite FTS5 prefix search across inventory records.
- Complete inventory ledger UI with tombstone soft-deletion (`isDeleted`), location filtering (Pantry, Fridge, Freezer, Counter), and secure backup/restore recovery workflows.
- Absolute zero telemetry and zero external tracking SDKs.

### R2. CameraX Dual-Mode Vision Pipeline & Visual Ingestion (Phase 2)
- Continuous real-time 15 FPS viewfinder HUD utilizing ML Kit Object Detection and Barcode Scanning with `AtomicBoolean` frame locking.
- Offline OpenFoodFacts SQLite lookup cache mapping UPC/EAN barcodes to food items, brands, and shelf lives without requiring internet.
- High-resolution shelf snapshot analysis and optional Gemini Multimodal Live API bidirectional WebSocket streaming (`wss://.../BidiGenerateContent`) using BYOKey authentication.
- Ephemeral media enforcement: temporary camera frames and byte buffers must be immediately wiped from memory upon analysis completion.
- Interactive batch review bottom sheet allowing users to review, edit, and commit detected items into active inventory.

### R3. Dual-Inference Meal Synthesis & Culinary Intelligence (Phase 3)
- Dynamic portion scaling (1 to 12 servings) strictly enforcing the non-linear culinary seasoning exponent ($portions^{0.75}$) for aromatic and seasoning ingredients.
- Four distinct synthesis modes: Strict (100% on-hand zero-waste), Mostly (85-95% pantry-first), Shopping (elevated meals with grocery delta list), and 15-Minute Emergency Salvage (expiring ingredient rescue).
- Deterministic Smart Swap substitution knowledge graph verifying available on-hand stock.
- Dual-inference architecture: on-device LiteRT / AICore Gemini Nano execution, direct BYOKey cloud caller (Gemini, OpenAI, Anthropic, Ollama) with encrypted key storage, and local deterministic fallback meal synthesis.

### R4. End-to-End User Journey & Deduction Workflows
- Seamless data flow from camera snapshot / barcode intake -> active pantry ledger -> context-compressed meal synthesis -> "Cook This Meal" execution.
- Atomic stock deduction upon cooking, automatically reducing pantry quantities and inserting missing ingredients directly into the grocery checklist.

## Acceptance Criteria

### Security & Invariants
- [ ] No plaintext API keys or credentials exist in logs, storage, or version control (all stored in KeyStore-encrypted preferences).
- [ ] Temporary image byte arrays and buffers are scrubbed immediately after inference.
- [ ] Zero telemetry, analytics, or remote tracking libraries present in dependencies or runtime.
- [ ] Ingredients tagged with `isAromaticOrSeasoning == true` scale using $portions^{0.75}$.

### Code Quality & Static Analysis
- [ ] Detekt static analysis passes with 0 errors and 0 warnings across `main`, `test`, and `androidTest` (`./gradlew detekt detektTest detektDebugUnitTest detektDebugAndroidTest`).
- [ ] All functions, classes, and files adhere to project complexity, line length (<= 120 chars), and method length constraints.

### Test Verification & SLAs
- [ ] 100% pass rate on all JVM unit tests (`./gradlew testDebugUnitTest`).
- [ ] 100% pass rate on physical connected device instrumentation tests (`./gradlew connectedDebugAndroidTest`).
- [ ] FTS5 prefix search query latency remains under 5 ms across 5,000 mock records.
- [ ] Camera live frame analysis latency stays under 40 ms per frame.
- [ ] App cold launch latency remains under 350 ms.

## Follow-up — 2026-10-27T13:14:08Z

Device 127.0.0.1:3000 is verified online and ready for connectedDebugAndroidTest execution.
