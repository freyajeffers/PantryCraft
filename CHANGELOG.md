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
