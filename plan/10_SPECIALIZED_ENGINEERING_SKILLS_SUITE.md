# PantryCraft: Specialized Engineering Skills Suite  
  
* * *

## Autonomous Agent Capabilities & Domain Playbooks for Local-First Android AI

* * *

**Engineering Skills** | **Agent Playbooks** | **Agentic Architecture** | **Domain Invariants**

### Overview

This document specifies the purpose-built engineering skills designed to equip autonomous coding agents and software engineers with deep domain expertise required to build PantryCraft without trial-and-error bugs.

---

## Core Infrastructure Skills

### Skill 1: android-camerax-mlkit-pipeline
* **Description:** Specialized expertise in implementing low-latency, leak-free CameraX `ImageAnalysis` and `ImageCapture` pipelines with Google ML Kit.
* **Core Invariants:** Mandatory `finally { imageProxy.close() }` pattern, `AtomicBoolean` in-flight frame locks, dual-pass analyzer chaining (Barcode + Object Detection), and `mapNormalizedToCanvas` coordinate mapping.

### Skill 2: room-sqlcipher-concurrency
* **Description:** Hardening local Room SQLite persistence with SQLCipher encryption, Android KeyStore StrongBox keys, WAL concurrency, episodic memory tables, and non-destructive migrations.
* **Core Invariants:** Master passphrase generated with `SecureRandom` (256-bit) and wrapped via KeyStore AES-256-GCM (StrongBox on API 28+ / TEE on API 23+). Decrypted into memory at runtime and scrubbed after connection establishment. Includes `KeyPermanentlyInvalidatedException` recovery.

### Skill 3: litert-aicore-mobile-ai
* **Description:** Engineering on-device SLM inference via Google AI Edge (Android AICore Gemini Nano / LiteRT-LM) and resilient BYOKey streaming HTTP routing with local tool-calling capabilities.
* **Core Invariants:** Zero-download AICore defaults vs. on-demand LiteRT-LM Play Feature Delivery. Structured JSON function signatures for local ReAct loops (`search_inventory`, `simulate_depletion`). Hardware RAM feasibility pre-checks (>= 6GB RAM).

### Skill 4: sherpa-onnx-stt
* **Description:** Building 100% offline streaming speech-to-text on Android using Sherpa-ONNX (Zipformer / Whisper) with ephemeral memory buffers and barge-in interruptibility.
* **Core Invariants:** NDK ABI filtering (`arm64-v8a`), circular ring buffers (max 10s capacity, zero disk writes), and ambient barge-in TTS pausing for the hands-free cooking assistant.

### Skill 5: culinary-recipe-mechanics
* **Description:** Domain-specific culinary mathematics, calibrated non-linear seasoning scaling, USDA perishability curves, and ethylene gas interaction rules.
* **Core Invariants:** Calibrated power curve scaling ($\\text{scaled} = \\text{base} \\times (\\text{portions}/\\text{base})^{exp}$, baseline $exp = 0.75$), culinary unit conversion matrix (tablespoons to ml, butter sticks to grams), and incompatible dimension fallback modals.

---

## Advanced Agentic Capabilities Skills

### Skill 6: agentic-culinary-tool-calling
* **Description:** Implementing local on-device ReAct function calling loops for Gemini Nano and LiteRT-LM, executing Room SQLite tools for constraint verification, inventory search, and meal drafting.
* **Core Invariants:** Staged read-only / draft tool execution; hard turn ceiling of 3 iterations per task to conserve mobile battery; robust JSON parameter repair.

### Skill 7: culinary-episodic-memory-reflection
* **Description:** Persisting on-device culinary feedback, learning household palate profiles, recalibrating prep-time estimates, and adjusting seasoning scaling exponents via local reflection loops.
* **Core Invariants:** Encrypted local reflection table (`culinary_reflections`); zero remote telemetry; dynamic palate multipliers adjusting spice and salt scaling exponents based on explicit star ratings.

### Skill 8: adaptive-sous-chef-copilot
* **Description:** Managing real-time, hands-free cooking sessions, dynamic step navigation, on-the-fly ingredient substitutions for kitchen mishaps, and ambient voice replanning.
* **Core Invariants:** Low-power VAD listening ($<4\\%$ battery over 30 mins); real-time pantry bridge substitutions when ingredients burn or are missing mid-recipe; barge-in audio playback pausing.

### Skill 9: multi-day-meal-graph-planner
* **Description:** Constructing multi-day meal sequences (3–5 day horizon graphs) that chain shared perishable ingredients and carry forward leftovers to systematically eliminate household food waste.
* **Core Invariants:** Directed Acyclic Graph (DAG) meal sequencing; zero-strand policy for perishable bulk items (cabbage, broths, roast proteins); automated portion surplus carried into next-day lunch stock.

### Skill 10: inventory-drift-reconciliation
* **Description:** Reconciling inventory drift between physical kitchen reality and database records using sensor fusion, CameraX visual deltas, and consumption burn-rate heuristics.
* **Core Invariants:** Time-decay confidence scoring on unverified stock; passive recipe usage inferences for untracked pantry staples; visual delta bounding box comparisons marking missing items as candidate-depleted.
