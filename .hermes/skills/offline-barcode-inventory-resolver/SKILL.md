---
name: offline-barcode-inventory-resolver
description: Rapidly resolve grocery barcodes offline using embedded SQLite product caches, temporal debouncing, and haptic feedback.
---

# Offline Barcode Inventory Resolver

Architectural patterns for sub-10ms grocery barcode lookup on Android without internet access.

## When to Use
- Scanning pantry groceries in low-connectivity environments (basements, pantries).
- Matching UPC-A, EAN-13, and EAN-8 barcodes against offline product databases.
- Learning unindexed barcodes locally through user association caching.
- Throttling camera barcode detection to eliminate duplicate modal spam.

## Core Architectural Invariants
1. **Embedded SQLite Asset Database:** Package an indexed, compressed SQLite asset database containing top 50,000 common food barcodes (OpenFoodFacts extract). Lookups resolve in under 10ms.
2. **Custom Offline Barcode Cache:** When a barcode is not found in the 50k asset database, prompt the user with a 1-tap sheet to name the product once. Store the association in a local Room table (`custom_barcode_cache`) so future scans resolve offline instantly.
3. **Temporal Scan Debouncer:** When a barcode is recognized, ignore subsequent detections of the same raw barcode for 2,500ms to prevent duplicate dialogs.
4. **Instant Tactile Confirmation:** Trigger `VibrationEffect.createPredefined(EFFECT_TICK)` the exact millisecond a barcode is validated.

## Custom Barcode Schema & Resolver Implementation
```kotlin
@Entity(tableName = "custom_barcode_cache")
data class CustomBarcodeItem(
    @PrimaryKey val barcode: String,
    val name: String,
    val category: String,
    val defaultUnit: String,
    val estimatedShelfLifeDays: Int,
    val lastScanned: Long = System.currentTimeMillis()
)

class OfflineBarcodeResolver(
    private val assetBarcodeDb: AssetBarcodeDatabase,
    private val customBarcodeDao: CustomBarcodeDao
) {
    private var lastScannedBarcode: String? = null
    private var lastScannedTime = 0L

    suspend fun resolveBarcode(rawCode: String): BarcodeMatch? {
        val now = System.currentTimeMillis()
        if (rawCode == lastScannedBarcode && now - lastScannedTime < 2500) {
            return null // Debounced
        }
        lastScannedBarcode = rawCode
        lastScannedTime = now

        return withContext(Dispatchers.IO) {
            // Check user-learned cache first
            customBarcodeDao.findByBarcode(rawCode)?.let {
                return@withContext BarcodeMatch(it.name, it.category, it.defaultUnit, it.estimatedShelfLifeDays)
            }
            // Check embedded 50k database
            assetBarcodeDb.productDao().findByBarcode(rawCode)
        }
    }
}
```
