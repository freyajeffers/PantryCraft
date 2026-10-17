# PantryCraft: Privacy-First Local AI Kitchen & Pantry Assistant

* * *

## High-Level System Architecture & Operational Overview

* * *

**Local-First** | **Zero Accounts Required** | **On-Device & BYOKey AI** | **Hardware-Encrypted Storage**

### 1. Executive Summary & Mission

PantryCraft is a minimalist, ultra-responsive, privacy-centric Android application designed to eliminate household food waste, automate kitchen inventory management, and generate chef-grade, portion-accurate meal suggestions. Operating strictly on a local-first philosophy, the app requires no account sign-ups, transmits zero telemetry, and stores all user inventory locally on the device in an encrypted SQLite database.

Users ingest ingredients through four complementary multimodal channels: high-resolution still camera capture, continuous real-time live viewfinder detection, hands-free on-device voice dictation, and unstructured natural language text input. Meal synthesis leverages a dual-engine AI architecture: users can run entirely offline on-device SLMs (Google Gemini Nano via Android AICore, or on-demand downloadable LiteRT-LM models) or configure their personal Bring-Your-Own-Key (BYOKey) cloud model providers (OpenAI, Anthropic, Google Gemini, OpenRouter, or private self-hosted Ollama endpoints).

### 2. Core Value Propositions & Pillars

* **Complete Sovereignty & Privacy:** Zero central servers, zero telemetry trackers, and zero mandatory cloud dependencies. All inventory data is stored in hardware-backed encrypted local storage (`pantry_items`, `grocery_items`).
* **Minimal Friction, Maximum Utility:** Instant cold start under 350ms, single-tap capture, batch scanning across fridge and pantry shelves, and hands-free voice logging for dirty kitchen hands.
* **Dual-Inference Freedom (On-Device & BYOKey):** Capable of 100% offline meal synthesis and vision inference on supported mobile hardware (via system AICore Gemini Nano or on-demand LiteRT-LM), with zero-markup BYOKey support for users wanting cutting-edge frontier cloud reasoning.
* **Strict vs. Flexible Meal Matching:** Tailored meal generation modes allow users to restrict recipes strictly to on-hand inventory ("Strict Zero-Waste"), allow pantry staples ("Mostly What I Have"), or generate inspirational meals with auto-populated missing grocery lists.
* **Decentralized Optional Synchronization:** Users who desire multi-device access can opt into zero-knowledge cloud backup via personal Google Drive AppData folders or authenticated peer-to-peer Wi-Fi synchronization--without creating an app account.

### 3. Subsystem Architecture & Topology

The system is architected in a modular, clean unidirectional data-flow structure (UDF) adhering strictly to Modern Android Architecture guidelines:

| Subsystem Layer | Key Components | Primary Responsibilities |
| :--- | :--- | :--- |
| **Presentation (UI)** | Jetpack Compose, Material 3, Navigation Compose, Glance Widgets | Minimalist, gesture-driven inventory ledger, viewfinder HUD, portion sliders, meal recommendation cards, and grocery checklist. |
| **Multimodal Ingestion** | CameraX (Analysis & Capture), ML Kit (Vision & Barcode), Sherpa-ONNX / Android SpeechRecognizer | Dual-mode visual scanning (live object bounding box + high-res snapshot), barcode fallback, and real-time streaming audio transcription. |
| **Intelligence & Reasoning** | Android AICore (Gemini Nano), LiteRT-LM (Gemma 2 / Qwen 2.5), BYOKey REST Client (Ktor) | Semantic entity parsing from natural language/audio, multi-item visual extraction, and full-database constraint-satisfaction meal synthesis. |
| **Persistence & Domain Engine** | Room SQLite (SQLCipher), FTS5 Search, StateFlow Repositories | Thread-hardened ACID storage (WAL mode, mmap 256MB), full-text search, inventory lifecycle management, and expiry countdowns. |
| **Decentralized Sync & Export** | Google Drive AppData REST, Local P2P TLS Mesh / JSON Export Engine | Optional encrypted cloud backup, exportable JSON/CSV schema, zero-signup multi-device state synchronization with out-of-band pairing. |

### 4. End-to-End Operational Workflows

#### A. Dual-Mode Vision Ingestion
1. **Live Scanning Viewfinder:** CameraX runs ImageAnalysis at 15 FPS. Lightweight local models (ML Kit Object Detection + Barcode) track items with dynamic bounding boxes. When a product barcode or recognizable item is stabilized, a quick-add chip appears instantly on screen.
2. **High-Resolution Shelf Snapshot:** The user taps capture on an open fridge or pantry shelf. The full-resolution image is downsampled in memory and sent to the active Vision LLM (on-device Gemini Nano or BYOKey multimodal model). The model emits structured JSON listing all detected items, recommended storage zones, estimated quantity, and default shelf-life estimates.

#### B. Hands-Free Semantic Voice Ingestion
1. User taps push-to-talk (or activates hands-free toggle) while unpacking groceries: *"Put two cartons of almond milk in the fridge and a bag of basmati rice in the pantry."*
2. Sherpa-ONNX streaming STT converts raw audio to text in real time on-device without network calls.
3. Semantic Parser extracts normalized entity JSON (Item: Almond Milk, Qty: 2 cartons, Location: Fridge; Item: Basmati Rice, Qty: 1 bag, Location: Pantry) and performs an atomic batch insert into Room DB.

#### C. Context-Aware Meal Synthesis
1. User selects meal scope (Strict: 100% on-hand; Mostly: ~90% on-hand + staple substitutes; Shopping: open culinary suggestions; Salvage: expiring < 48h).
2. User adjusts the portion dial (e.g., 2 adults, 2 kids = 4 portions).
3. The Inventory Engine queries active stock from Room DB, calculates items nearest to expiration, formats a lightweight semantic inventory payload, and injects it into the AI prompt template.
4. The AI streams 3 distinct recipe options with complete cooking steps, portion-scaled ingredient measurements (applying non-linear power curves for seasonings), prep time, and direct stock deduction diffs.

### 5. Security Model, Privacy & Sandboxing Invariants

* **Hardware-Backed KeyStore & Passphrase Encryption:** Master database encryption is secured via an AES-256-GCM key generated in Android KeyStore (backed by StrongBox Keymaster where available on API 28+, with graceful fallback to TEE KeyStore). Because hardware-backed KeyStore keys cannot export raw key bytes, the KeyStore key encrypts and authenticates a cryptographically secure 256-bit random passphrase generated via `SecureRandom`. This encrypted passphrase blob is stored in private app storage. At runtime, the passphrase is decrypted in memory via `Cipher` and supplied to SQLCipher SupportFactory. BYOKey cloud API keys are protected in `EncryptedSharedPreferences`.
* **Zero Network Default:** The core application requires zero network permissions when running in pure on-device mode. Network permission is requested conditionally only if the user activates BYOKey cloud providers, Google Drive backup, or downloads optional on-device model weights.
* **On-Device Model Distribution:** Zero-download, zero-network on-device SLM inference utilizes system-provided Android AICore (Gemini Nano) on supported devices. For devices without AICore wishing to run LiteRT-LM (Gemma 2 2B / Qwen 2.5 3B, ~1.5GB), weights are downloaded on-demand through an explicit user-initiated setup flow via Play Feature Delivery, ensuring the base APK remains under Google Play Store limits (<200MB).
* **Ephemeral Image & Audio Lifecycle:** Captured viewfinder frames and photos taken solely for inventory parsing are analyzed in-memory or in app-private cache (`context.cacheDir/vision_scratch/`) and wiped immediately after inference. Audio buffers exist exclusively in volatile memory ring buffers (max 10s capacity) and are never written to disk.
* **Defense Against Prompt Injection:** Ingredient names and notes are strictly sanitized and structured into validated JSON schema payloads before entering the LLM inference context.

### 6. Target Technology Stack

| Dimension | Selected Technology | Rationale & Justification |
| :--- | :--- | :--- |
| **Language & Runtime** | Kotlin 2.0+ / Coroutines & Flow | Idiomatic modern Android, reactive asynchronous data streams, zero boilerplate. |
| **UI Framework** | Jetpack Compose & Material 3 | Declarative UI, high performance, fluid gesture animations, dynamic theming. |
| **Local Database** | Room 2.6+ with SQLCipher & SQLite FTS5 | Encrypted at rest, compile-time SQL verification, non-blocking WAL concurrency. |
| **Camera Integration** | CameraX 1.4+ (ImageAnalysis & ImageCapture) | Device-agnostic camera pipeline, lifecycle awareness, hardware acceleration. |
| **On-Device Vision / Barcode** | Google ML Kit (Object Detection, Barcode) | Ultra-low latency (<30ms), zero network requirement, tiny binary footprint. |
| **On-Device STT (Voice)** | Sherpa-ONNX (Zipformer / Whisper Tiny) & Android SpeechRecognizer | Completely offline, low memory overhead, real-time streaming speech transcription. |
| **On-Device GenAI / LLM** | Android AICore (Gemini Nano) & LiteRT-LM (Gemma 2 / Qwen 2.5) | Hardware NPU/GPU accelerated inference on modern mobile silicon. |
| **Cloud Network Client** | Ktor Client 2.3+ / OkHttp (SSE streaming support) | Minimalist HTTP engine with native streaming support for BYOKey endpoints. |
