package com.jeffers.pantrycraft.feature.camera.vision

import com.jeffers.pantrycraft.data.PantryItem

fun ParsedVisionItem.toPantryItem(now: Long = System.currentTimeMillis()): PantryItem =
    PantryItem(
        name = name,
        quantity = quantity,
        unit = unit,
        customUnitName = customUnitName,
        storageLocation = storageLocation,
        category = category,
        expirationTimestamp =
            estimatedShelfLifeDays.takeIf { it > 0 }?.let { days ->
                now + days * MILLIS_PER_DAY
            },
        isAromaticOrSeasoning = isAromaticOrSeasoning,
        notes = conditionNotes,
        lastUpdated = now,
        createdAt = now,
        updatedAt = now,
    )

fun PantryItem.toParsedVisionItem(now: Long = System.currentTimeMillis()): ParsedVisionItem =
    ParsedVisionItem(
        name = name,
        category = category,
        storageLocation = storageLocation,
        quantity = quantity,
        unit = unit ?: "COUNT",
        customUnitName = customUnitName,
        confidence = 1f,
        estimatedShelfLifeDays =
            expirationTimestamp?.let { ((it - now) / MILLIS_PER_DAY).toInt().coerceAtLeast(0) } ?: 0,
        isAromaticOrSeasoning = isAromaticOrSeasoning,
        boundingBox = NormalizedBox(0f, 0f, 1f, 1f),
        conditionNotes = notes,
    )

fun ParsedVisionItem.toUpdatedPantryItem(
    existing: PantryItem,
    now: Long = System.currentTimeMillis(),
): PantryItem =
    toPantryItem(now).copy(
        id = existing.id,
        threshold = existing.threshold,
        barcode = existing.barcode,
        purchaseTimestamp = existing.purchaseTimestamp,
        isOpened = existing.isOpened,
        openedTimestamp = existing.openedTimestamp,
        isDeleted = existing.isDeleted,
        createdAt = existing.createdAt,
    )

private const val MILLIS_PER_DAY = 86_400_000L
