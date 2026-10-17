# PantryCraft: Complete Technical Documentation & Skills Suite

This directory contains the complete set of architectural specifications, implementation plans, coding guidelines, hardened edge-case remediation reports, autonomous agentic extensions, and fully expanded agent skills for **PantryCraft** (Package: `com.jeffers.pantrycraft`).

## Project Documentation Suite

1. [00_README.md](00_README.md) - Project overview, features, and quickstart guide.
2. [01_HIGH_LEVEL_PROJECT_OVERVIEW.md](01_HIGH_LEVEL_PROJECT_OVERVIEW.md) - System architecture, threat model, privacy invariants, and technology stack.
3. [02_MASTER_PHASED_IMPLEMENTATION_PLAN.md](02_MASTER_PHASED_IMPLEMENTATION_PLAN.md) - 5-phase sequential roadmap (Swapped Phases 3 & 4), module boundaries, agentic ReAct loops, and SLAs.
4. [03_DETAILED_SUB_PHASES_AND_INNOVATIONS.md](03_DETAILED_SUB_PHASES_AND_INNOVATIONS.md) - 19 non-prescriptive sub-phases (Visual Delta Audits, Ethylene Gas Clashing, Salvage Mode, Mesh Sync).
5. [04_PHASE_1_FOUNDATION_AND_DATABASE.md](04_PHASE_1_FOUNDATION_AND_DATABASE.md) - Local encrypted Room database (SQLCipher, WAL, FTS5) with tombstone guards and reactive inventory ledger.
6. [05_PHASE_2_CAMERAX_DUAL_VISION.md](05_PHASE_2_CAMERAX_DUAL_VISION.md) - CameraX 15 FPS live HUD, offline ML Kit barcode scanning, coordinate transform, and high-res shelf decomposition.
7. [06_PHASE_3_MEAL_SYNTHESIS_AND_PORTIONS.md](06_PHASE_3_MEAL_SYNTHESIS_AND_PORTIONS.md) - Agentic AI recipe generation, portion multiplier math, unit conversion fallbacks, and dual-inference router (LiteRT / BYOKey).
8. [07_PHASE_4_REAL_TIME_VOICE_AND_STT.md](07_PHASE_4_REAL_TIME_VOICE_AND_STT.md) - 100% offline streaming STT via Sherpa-ONNX, semantic entity parsing, and adaptive hands-free cooking assistant.
9. [08_PHASE_5_STOCK_LIFECYCLE_AND_SYNC.md](08_PHASE_5_STOCK_LIFECYCLE_AND_SYNC.md) - Atomic recipe stock depletion, culinary unit matrix, WorkManager Doze timing, grocery replenishment, and local Wi-Fi mesh sync.
10. [09_SCAFFOLDING_ALIGNMENT_AND_RECONCILIATION.md](09_SCAFFOLDING_ALIGNMENT_AND_RECONCILIATION.md) - Bridges existing code (`com.freya.pantrygrocery` -> `com.jeffers.pantrycraft`) and Room v1->v2 migration.
11. [10_SPECIALIZED_ENGINEERING_SKILLS_SUITE.md](10_SPECIALIZED_ENGINEERING_SKILLS_SUITE.md) - Playbooks for CameraX, Room SQLCipher, LiteRT mobile AI, Sherpa-ONNX STT, agentic tool-calling, and culinary mechanics.
12. [11_PHASE_1_AND_2_SPECIALIZED_SKILLS.md](11_PHASE_1_AND_2_SPECIALIZED_SKILLS.md) - Deep-dive skills: Room migration testing, Compose 120 FPS performance, offline barcode cache, and VLM shelf decomposition.
13. [12_INSTRUCTIONS.md](12_INSTRUCTIONS.md) - Workstation setup (Android SDK 35/36, NDK r26d), build runbooks, and encryption audit commands.
14. [13_AGENTS.md](13_AGENTS.md) - Non-negotiable invariants, coding persona, latency budgets, and TDD rules for autonomous coding agents.
15. [14_EDGE_CASES_REMEDIATION_REPORT.md](14_EDGE_CASES_REMEDIATION_REPORT.md) - Comprehensive edge cases hardening report covering FTS5 triggers, KeyStore recovery, unit conversion matrix, P2P tombstones, and coordinate transforms.
16. [15_AUTONOMOUS_AGENT_SKILLS_EXPANSION_SUITE.md](15_AUTONOMOUS_AGENT_SKILLS_EXPANSION_SUITE.md) - Autonomous agent skills suite specification covering ReAct loops, episodic memory, adaptive sous-chef, multi-day planning, and drift reconciliation.

## Fully Expanded Agent Skills (`skills/`)

Each skill is organized in its own directory with a fully fleshed-out `SKILL.md` containing YAML frontmatter, core invariants, implementation patterns, and verification tests:

### Foundation & Infrastructure Skills
- **[skills/android-camerax-mlkit-pipeline/SKILL.md](skills/android-camerax-mlkit-pipeline/SKILL.md)**: Lifecycle-aware CameraX ImageAnalysis at 15 FPS, mandatory `finally { imageProxy.close() }` pattern, `AtomicBoolean` in-flight lock, coordinate transforms, and ML Kit integration.
- **[skills/room-sqlcipher-concurrency/SKILL.md](skills/room-sqlcipher-concurrency/SKILL.md)**: Hardware KeyStore StrongBox keys, SQLCipher AES-256 encryption, `KeyPermanentlyInvalidatedException` recovery, SQLite WAL mode, and memory key hygiene.
- **[skills/room-schema-migration-guard/SKILL.md](skills/room-schema-migration-guard/SKILL.md)**: Non-destructive Room migration (v1 → v2), table recreation, index preservation, guarded FTS5 triggers, and `MigrationTestHelper` test suite.
- **[skills/compose-high-perf-ledger/SKILL.md](skills/compose-high-perf-ledger/SKILL.md)**: 120 FPS Jetpack Compose `LazyColumn` keying, query debouncing, derived state, and swipe-to-decrement haptics.
- **[skills/offline-barcode-inventory-resolver/SKILL.md](skills/offline-barcode-inventory-resolver/SKILL.md)**: Sub-10ms UPC/EAN resolution via embedded SQLite cache, user-learned custom barcode mapping, temporal debouncing, and haptic feedback.
- **[skills/multimodal-shelf-vlm-parser/SKILL.md](skills/multimodal-shelf-vlm-parser/SKILL.md)**: In-memory WebP downsampling, spatial bounding coordinates, and resilient JSON repair for Vision LLMs.
- **[skills/litert-aicore-mobile-ai/SKILL.md](skills/litert-aicore-mobile-ai/SKILL.md)**: Hardware RAM capability checks, prompt context compression, and dual-inference router (LiteRT / BYOKey) with function-calling support.
- **[skills/sherpa-onnx-stt/SKILL.md](skills/sherpa-onnx-stt/SKILL.md)**: NDK ABI filtering, 16kHz PCM circular memory ring buffers, barge-in interruptibility, and 100% offline streaming STT.
- **[skills/culinary-recipe-mechanics/SKILL.md](skills/culinary-recipe-mechanics/SKILL.md)**: Calibrated non-linear seasoning math ($	ext{portions}^{exp}$), culinary unit conversion matrix, leftover carry-forward yield math, and ethylene gas clash alerts.

### Autonomous Agentic Capabilities Skills
- **[skills/agentic-culinary-tool-calling/SKILL.md](skills/agentic-culinary-tool-calling/SKILL.md)**: On-device ReAct function calling loops for Gemini Nano and LiteRT-LM with sandboxed Room query tools.
- **[skills/culinary-episodic-memory-reflection/SKILL.md](skills/culinary-episodic-memory-reflection/SKILL.md)**: Encrypted on-device reflection tables, user palate calibration, and moving average prep-time grounding.
- **[skills/adaptive-sous-chef-copilot/SKILL.md](skills/adaptive-sous-chef-copilot/SKILL.md)**: Hands-free ambient cooking companion with low-power VAD, live mishap recovery, and dynamic mid-recipe substitutions.
- **[skills/multi-day-meal-graph-planner/SKILL.md](skills/multi-day-meal-graph-planner/SKILL.md)**: Graph-based meal sequencing across 3–5 day horizons with shared perishable ingredient chaining and leftover carry-forward.
- **[skills/inventory-drift-reconciliation/SKILL.md](skills/inventory-drift-reconciliation/SKILL.md)**: Probabilistic inventory confidence decay, passive recipe usage inferences, and CameraX visual delta auditing.
