# PantryCraft: Master Phased Implementation Plan  
  
* * *

## Updated 5-Phase Roadmap with Agentic Architecture (Swapped Phases 3 & 4)

* * *

**Updated Roadmap** | **Swapped Phases 3 & 4** | **Autonomous Agentic Capabilities**

**Roadmap Optimization Note:** Phase 3 and Phase 4 have been strategically swapped. Meal synthesis and AI recipe reasoning now immediately follow CameraX visual ingestion in Phase 3. This enables an immediate, end-to-end "Scan Ingredients -> Store -> Suggest Recipes" value loop before introducing complex voice streaming infrastructure in Phase 4. Furthermore, Phases 3, 4, and 5 are enhanced with autonomous agentic perception-action loops (ReAct tool execution, episodic reflection, multi-day horizon planning, ambient sous-chef assistance, and sensor fusion drift reconciliation).

### 1. Architectural Roadmap Overview

| Phase | Phase Title | Primary Scope & Deliverables | Key Dependencies |
| :--- | :--- | :--- | :--- |
| **Phase 1** | Local-First Foundation, Encrypted Storage & Inventory Domain | Encrypted Room SQLite DB (WAL mode, FTS5), SQLCipher with KeyStore AES-GCM encrypted passphrase, Core Inventory Domain Models (`pantry_items`, `grocery_items`), Minimalist Jetpack Compose inventory UI ledger, CRUD & soft-delete operations. | None (Foundational) |
| **Phase 2** | CameraX Dual-Engine Vision Pipeline & Visual Ingestion | CameraX ImageAnalysis (15 FPS live HUD) + ImageCapture (high-res still photo), ML Kit Object Detection & Barcode scanning, Vision LLM ingestion adapter (Gemini Nano / BYOKey Vision), normalized coordinate scaling. | Phase 1 (Storage schema & Inventory Repository) |
| **Phase 3** | Portion-Aware Agentic Meal Synthesis & Horizon Planning | Local ReAct tool-calling loop (`agentic-culinary-tool-calling`), Full database ingestion context builder, Prompt templating engine, Portion calculation dial (1-12), Non-linear seasoning scaling, Episodic memory reflection (`culinary-episodic-memory-reflection`), Multi-day DAG horizon planning (`multi-day-meal-graph-planner`), 4 Generation Modes (Strict, Mostly, Shopping, Emergency Salvage), Android AICore / LiteRT-LM & BYOKey cloud providers. | Phase 1, Phase 2 |
| **Phase 4** | Real-Time Multimodal Voice & Ambient Kitchen NLP | On-device streaming STT (Sherpa-ONNX / Android SpeechRecognizer), Natural Language Semantic Parser (NLP extraction of item, quantity, unit, storage location, estimated shelf-life), Adaptive Sous-Chef Copilot (`adaptive-sous-chef-copilot`) with live mishap recovery and barge-in audio guidance. | Phase 1 (Storage schema), Phase 3 (Recipe UI integration) |
| **Phase 5** | Stock Lifecycle, Waste Prevention & Zero-Knowledge Sync | Smart stock depletion (one-tap recipe deduction with unit conversion handling), Inventory Drift Reconciliation (`inventory-drift-reconciliation`), Expiry warning notifications via WorkManager, Smart grocery list replenishment, Optional zero-signup Google Drive AppData backup & Local Wi-Fi P2P Mesh Sync (with SAS/QR pairing and LWW tombstones). | Phases 1-4 (Complete Application Suite) |

### 2. Inter-Module Data Interaction Architecture

```text
[ Ingestion Layer (Visual & Manual) ]  
 ├── CameraX Viewfinder / Snapshot  ──┐  
 ├── ML Kit Barcode / Bounding Box  ──┼──> [ Semantic Ingestion Normalizer ]  
 └── Manual Ledger Entry / Text     ──┘           │ (Validates & Enriches Metadata)  
                                                  ▼  
                                        [ InventoryRepository ]  
                                                  │ (Immediate WAL Transaction)  
                                                  ▼  
                                       [ Encrypted Room SQLite ]  
                                                  │  
                                                  ▼ (StateFlow Reactive Stream)  
                            [ Phase 3: Agentic Meal Synthesis & ReAct Loop ]  
                         ├── Local Tool Calls: search_inventory, simulate_depletion  
                         ├── Multi-Day Horizon Graph Planner (Shared Perishables)  
                         └── Episodic Memory Reflection (Palate Calibration)  
                                                  │  
                                                  ▼  
                                 [ AICore / LiteRT-LM / BYOKey Cloud ]  
                                                  │  
                                                  ▼  
                               [ Recipe Presentation & In-Cooking Copilot ]  
                                                  ▲  
                                                  │ (Hands-Free Voice & Live Replanning)  
                                [ Phase 4: Ambient Sous-Chef Copilot ]  
                                (Sherpa-ONNX STT + Adaptive Mishap Recovery)  
                                                  │  
                                                  ▼  
                                [ Phase 5: Stock Depletion & Drift Reconciler ]  
                                (Unit Conversion Matrix + LWW Mesh Sync)  
```

### 3. Storage Hardening & Concurrency Standards

* **Write-Ahead Logging (WAL Mode):** Enabled unconditionally on SQLite to allow concurrent read operations during background vision/voice batch writes and agent ReAct tool calls (`setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)`).
* **Memory-Mapped I/O:** SQLite `PRAGMA mmap_size = 268435456` (256 MB) to accelerate read passes during full-database meal synthesis scans.
* **Busy Timeout:** Set to `PRAGMA busy_timeout = 5000` ms to prevent database lock exceptions during rapid burst insertions.
* **Hardware KeyStore Passphrase Encryption:** Master passphrase generation uses `SecureRandom` 256-bit entropy, encrypted at rest via Android KeyStore AES-256-GCM (with StrongBox Keymaster backed keys on API 28+, falling back to standard TEE on API 23-27). Passphrase bytes are decrypted into memory only during database initialization.
