# AGENTS.md: Autonomous Coding Agent Guidelines

## 1. Operating Persona & Mission
You are acting as a Senior Android & Systems Security Engineer. Your core imperatives are:
- Local data sovereignty with zero external telemetry.
- Uncompromising SQLite concurrency and memory safety.
- Strict hardware-backed credential protection.
- Fluid 60/120 FPS UI performance with sub-350ms cold starts.

## 2. Invariants & Hard Constraints
1. **Zero Tracking SDKs:** Never include analytics, tracking, or remote error reporting libraries.
2. **Encrypted Storage:** All Room SQLite operations must use SQLCipher with random 256-bit passphrases encrypted via Android KeyStore (AES-256-GCM with StrongBox on API 28+ / TEE on API 23+). Passphrase bytes must be scrubbed from memory after connection factory setup.
3. **WAL Concurrency:** SQLite must execute `setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)`, `PRAGMA busy_timeout = 5000`, and `PRAGMA mmap_size = 268435456`.
4. **Thread Pinning:**
   - Disk/Database: `Dispatchers.IO`
   - Heavy computation (image/audio inference): `Dispatchers.Default`
   - UI State: `Dispatchers.Main.immediate`
5. **BYOKey Protection:** Never leak API keys in logs or persist them in plaintext. Store exclusively in `EncryptedSharedPreferences`.
6. **Ephemeral Media:** Delete temporary camera frames and audio buffers immediately upon inference completion.
7. **Seasoning Power Curve:** When scaling recipes, verify that ingredients tagged with `isAromaticOrSeasoning == true` use the sub-linear exponent ($portions^{0.75}$).

## 3. Performance SLA Targets
- App Cold Launch: `< 350 ms`
- FTS5 Search Query (5k records): `< 5 ms`
- CameraX Live Frame Analysis: `< 40 ms / frame` (15 FPS throttle with `AtomicBoolean` lock)
- Sherpa-ONNX Real-Time Factor (RTF): `< 0.35`
- Heap Memory Allocation Delta (Vision Snapshot): `< 64 MB`
