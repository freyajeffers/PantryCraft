# Dispatch: Milestone 4 Challenger 2 (SLA & Systems Security Verification)

## Mission
You are Milestone 4 Challenger 2. Your role is SLA & Systems Security Empirical Stress-Testing.

## Working Directory
`/home/freya/Projects/pantrycraft/.agents/teamwork/challenger_m4_2`

## Mandatory Inputs
Read before starting:
1. `/home/freya/Projects/pantrycraft/.agents/teamwork/ORIGINAL_REQUEST.md`
2. `/home/freya/Projects/pantrycraft/AGENTS.md`
3. `/home/freya/Projects/pantrycraft/PROJECT.md`
4. `/home/freya/Projects/pantrycraft/TEST_INFRA.md`
5. `/home/freya/Projects/pantrycraft/TEST_READY.md`

## Task Objective
1. Empirically verify all Performance SLA targets from `AGENTS.md § 3` and `PROJECT.md § Feature 26`:
   - **FTS5 Search Query (5k records)**: `< 5 ms` latency.
   - **CameraX Live Frame Analysis**: `< 40 ms / frame` (15 FPS throttle with `AtomicBoolean` lock).
   - **App Cold Launch**: `< 350 ms`.
   - **Vision Snapshot Heap Allocation Delta**: `< 64 MB`.
2. Empirically verify all Invariants & Hard Constraints from `AGENTS.md § 2`:
   - **Zero Tracking SDKs**: Audit all build.gradle and source files for any analytics, telemetry, or remote crash reporting SDKs.
   - **Encrypted Storage**: Room SQLite using SQLCipher with random 256-bit passphrase encrypted via Android KeyStore (AES-256-GCM), byte scrubbing with `fill(0)` / `Arrays.fill(..., 0.toByte())`.
   - **WAL Concurrency**: `setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)`, `PRAGMA busy_timeout = 5000`, `PRAGMA mmap_size = 268435456`.
   - **Thread Pinning**: Disk/DB on `Dispatchers.IO`, heavy compute on `Dispatchers.Default`, UI on `Dispatchers.Main.immediate`.
   - **BYOKey Protection**: Store keys exclusively in `EncryptedSharedPreferences`, no leakage in logs.
   - **Ephemeral Media**: Immediate deletion of camera frames and audio buffers upon inference completion.
   - **Seasoning Power Curve**: Ingredient scaling for `isAromaticOrSeasoning == true` uses $portions^{0.75}$.
3. Author or execute SLA verification benchmarks in `app/src/test/java/com/jeffers/pantrycraft/e2e/Tier5SlaBenchmarkTest.kt` (or similar suite).
4. Run verification commands:
   - `./gradlew testDebugUnitTest`
   - `./gradlew detekt detektTest detektDebugUnitTest detektDebugAndroidTest`
5. Write your structured handoff report to `/home/freya/Projects/pantrycraft/.agents/teamwork/challenger_m4_2/handoff.md` with explicit Verdict line: `Verdict: CONFIRMED` or `Verdict: CONTESTED`.
6. Send message to caller when done.
