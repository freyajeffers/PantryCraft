# Changelog

## Unreleased

### PantryCraft
- Added Room schema v1-to-v2 migration for storage location, category, expiration, opened-state, and notes fields.
- Added indexes for pantry location, expiration, and barcode lookup.
- Enabled Room write-ahead logging for concurrent UI and background access.
- Replaced manual repository refreshes with reactive Room `Flow` streams.
- Added migration instrumentation coverage that verifies existing pantry data survives the schema upgrade.
- Added a Gradle ktlint quality gate for Kotlin sources.
- Added Android KeyStore-derived SQLCipher encryption with WAL, busy timeout, and memory-mapped I/O settings.
- Added Pantry location filter chips and color-coded expiration status indicators.
- Added reactive DAO insert/update instrumentation coverage and location-filter unit tests.
- Added manual SQLite FTS5 indexing with synchronization triggers and prefix search over item names, notes, and categories.
- Added debounced pantry search UI and an Expiring Soon inventory filter.
- Added normalized names, unit metadata, purchase/created/updated timestamps, and migration backfill for legacy pantry rows.
- Added StrongBox-first SQLCipher key generation with TEE fallback and explicit invalidated-key failure handling.
- Added tombstone-based soft delete and restore support; default inventory flows and FTS searches exclude deleted records.
- Added deterministic clock injection to Expiring Soon filtering.
- Replaced deterministic HMAC database-key derivation with a random 256-bit SQLCipher passphrase wrapped by an Android KeyStore AES-256-GCM key.
- Added Snackbar undo for soft-deleted pantry items.
- Added Counter storage filtering to complete the four supported inventory locations.
- Added atomic quantity decrement with a zero floor and a "Use 1" inventory action.
- Disabled Android app backup to prevent encrypted database and key-wrapper state from being restored inconsistently.
- Rebuilt the FTS5 index from non-deleted rows only, preventing tombstones from entering the search index.
- Added explicit SQLCipher native-library loading and device-backed instrumentation coverage for migration, key wrapping, DAO flows, and FTS search.
- Fixed the production crash on navigation by executing SQLCipher PRAGMA assignments through query cursors instead of execSQL.
- Added bounded error handling for pantry database mutations so recoverable write failures surface as Snackbar messages instead of crashing the UI coroutine.
- Applied the same mutation-error handling and Snackbar feedback to grocery-list operations.
- Added a user-visible recovery path for invalidated hardware-backed keys; reset explicitly deletes the encrypted database and regenerates its wrapping key.
- Rejected non-positive and non-finite quantity decrements before reaching SQLite.
- Added swipe gestures to decrement inventory items or invoke the existing soft-delete/undo flow.
- Classified SQLite-full failures separately so pantry and grocery screens report storage exhaustion with actionable feedback.
- SQLite-full handling now prunes only temporary `cacheDir/vision_scratch` media before reporting the storage error.
- Added a device instrumentation benchmark for prefix FTS search over 5,000 synthetic pantry rows; the verified warmed run completed in 2.53 ms.
- Expanded manual pantry entry with category suggestions and shelf-life presets that populate expiration timestamps.
