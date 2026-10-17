# PantryCraft

**Android 14+ (SDK 34–36)** | **Kotlin 2.0+ / Jetpack Compose** | **100% Local-First & Zero Telemetry** | **Autonomous Agentic Architecture**

> A minimalist, privacy-centric Android application that recognizes pantry/fridge ingredients via camera and speech, manages inventory locally with zero telemetry, and autonomously synthesizes portion-accurate, waste-eliminating recipes using on-device models or user-provided API keys.

---

## Codebase Scaffolding & Package Information

- **Application ID & Namespace:** `com.jeffers.pantrycraft`
- **Application Label:** `PantryCraft`
- **Architecture:** Single-module Jetpack Compose app with Material 3, Room database (`pantry_items`, `grocery_items`, `culinary_reflections`), low-stock query, and working Home/Pantry/Grocery navigation.
- **Incremental Evolution:** Non-destructive Room migration (v1 → v2) adding storage locations (Pantry/Fridge/Freezer) and expiration timestamps, upgrading DAOs to reactive `Flow`, integrating CameraX barcode/snapshot recognition into `PantryItem.barcode`, adding autonomous agentic meal synthesis (ReAct tool loops, episodic reflection, multi-day horizon planning, and adaptive sous-chef voice guidance).

---

## Completed Milestones

### Phase 1: Local-First Encrypted Foundation & Inventory Ledger
- SQLCipher AES-256 Room database with hardware-backed KeyStore protection, WAL mode, 256 MB memory-mapped I/O, and busy-timeout protection.
- SQLite FTS5 search across pantry records with synchronized triggers, reactive Jetpack Compose inventory and grocery screens, and sub-3 ms prefix-search targets.
- PIN-protected encrypted backups with PBKDF2-HMAC-SHA256, AES-256-GCM, scheduled automatic backups, and non-destructive WAL checkpointing.

### Phase 2: CameraX Dual-Mode Vision & Barcode Pipeline
- Continuous 15 FPS live analysis with ML Kit barcode scanning, confidence-filtered bounding boxes, and interactive quick-add controls.
- Offline Open Food Facts catalog support with custom barcode association memory for unlisted items.
- Ephemeral shelf snapshots with EXIF rotation correction, local ML Kit fallback classification, and flexible vision-schema parsing.
- Batch review with editable taxonomy, shelf-life and aromatic/seasoning fields, bounded layout, and atomic database transactions.
- Grocery restocking, swipe-action polish, and a live kitchen dashboard with inventory metrics and Phase 3 meal-synthesis preview.

### Upcoming Phase 3: Portion-Aware Meal Synthesis
- Inventory context compression prioritized by expiration urgency.
- Unified on-device and BYOKey model routing for recipe generation.
- Non-linear scaling for spices and aromatics: `scale = (portions / base)^0.75`, with Strict, Mostly, and emergency salvage modes.

---

## Technical Documentation Suite

All planning and architectural reconciliation artifacts reside in Google Drive:

- [Project Dedicated Google Drive Folder](https://drive.google.com/drive/folders/1P7vbnOy6ySxgwvoyVMIH7dvJbdVhlDiq)
- [Scaffolding Alignment & Reconciliation Plan](https://docs.google.com/document/d/1FfqthD05bOTjXBxsInbGE1HZmQi_yWyibgqKmlGC7BE/edit?usp=drivesdk&ouid=114458103927152953091)
- [Master Phased Implementation Plan](https://docs.google.com/document/d/1VvwgYusyUbe774KKjV6U00Od3YJZYB2F6s3B8GSVuTI/edit?usp=drivesdk&ouid=114458103927152953091)
- [Detailed Sub-Phases & Architectural Innovations](https://docs.google.com/document/d/15DIWpLYRag1o50kxoMT2JiY1phU_HV_y1xfQa5E7Nfg/edit?usp=drivesdk&ouid=114458103927152953091)
- [Phase 1 Plan: Foundation & Database](https://docs.google.com/document/d/161rW-9srG7SzOjInpzaUIelk2Uz3i_rbQEWEkuFVijM/edit?usp=drivesdk&ouid=114458103927152953091)
- [Phase 2 Plan: CameraX Dual-Mode Vision](https://docs.google.com/document/d/1Rwgi0ps3uu7kD_EuZvqyuG_T3_MzLf10kPnqGxM6mu4/edit?usp=drivesdk&ouid=114458103927152953091)
- [Phase 3 Plan: Meal Synthesis & Portions Engine](https://docs.google.com/document/d/1nuS1v7y_eAfvru_cZY6Zk08UVjULBqczuFIyZO_QeuE/edit?usp=drivesdk&ouid=114458103927152953091)
- [Phase 4 Plan: Real-Time Multimodal Voice](https://docs.google.com/document/d/1Du-zJPU9oyhRm8-5uBseHZi2bfAMY-_8_wwvzs7z6IA/edit?usp=drivesdk&ouid=114458103927152953091)
- [Phase 5 Plan: Lifecycle, Depletion & Sync](https://docs.google.com/document/d/1aablO_0pRm1poiq7Yql_srFhIDOiHKDl-L-7G5o4jpI/edit?usp=drivesdk&ouid=114458103927152953091)
- [Specialized Engineering Skills Suite](https://docs.google.com/document/d/1jfBPbNjzL7DRapFaB-XWDmu6qqJhgu7z8TxnTY94oc8/edit?usp=drivesdk&ouid=114458103927152953091)
- [Phase 1 & 2 Specialized Skills](https://docs.google.com/document/d/1s2SaCO0XidgTNFqSY52YO1NAko0TDXMQKkRNElnCbU0/edit?usp=drivesdk&ouid=114458103927152953091)
- [Edge Cases Hardening & Remediation Report](14_EDGE_CASES_REMEDIATION_REPORT.md)
- [Autonomous Agent Skills Suite & Expansion Specification](15_AUTONOMOUS_AGENT_SKILLS_EXPANSION_SUITE.md)

---

## Build & Run

Prerequisites: JDK 17+ and Android SDK 35/36.

```bash
# 1. Run unit test suite
./gradlew testDebugUnitTest

# 2. Assemble debug APK
./gradlew :app:assembleDebug

# 3. Install on a connected Android device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
