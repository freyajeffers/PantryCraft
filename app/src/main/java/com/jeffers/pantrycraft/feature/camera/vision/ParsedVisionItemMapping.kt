package com.jeffers.pantrycraft.feature.camera.vision

import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.markOpened

fun ParsedVisionItem.toPantryItem(now: Long = System.currentTimeMillis()): PantryItem {
    val base =
        PantryItem(
            name = name,
            quantity = quantity,
            unit = unit,
            customUnitName = customUnitName,
            threshold = threshold,
            barcode = barcode,
            storageLocation = storageLocation,
            category = category,
            expirationTimestamp =
                estimatedShelfLifeDays.takeIf { it > 0 }?.let { days ->
                    now + days * MILLIS_PER_DAY
                },
            isOpened = isOpened,
            openedTimestamp = if (isOpened) now else null,
            isAromaticOrSeasoning = isAromaticOrSeasoning,
            notes = conditionNotes,
            lastUpdated = now,
            createdAt = now,
            updatedAt = now,
        )
    return if (isOpened) base.copy(isOpened = false).markOpened(now) else base
}

fun PantryItem.toParsedVisionItem(now: Long = System.currentTimeMillis()): ParsedVisionItem =
    ParsedVisionItem(
        name = name,
        category = category,
        storageLocation = storageLocation,
        quantity = quantity,
        unit = unit ?: "COUNT",
        customUnitName = customUnitName,
        barcode = barcode,
        threshold = threshold,
        isOpened = isOpened,
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
        threshold = threshold,
        barcode = barcode ?: existing.barcode,
        purchaseTimestamp = existing.purchaseTimestamp,
        isOpened = isOpened,
        openedTimestamp =
            when {
                isOpened && existing.isOpened -> existing.openedTimestamp ?: now
                isOpened && !existing.isOpened -> now
                else -> null
            },
        isDeleted = existing.isDeleted,
        createdAt = existing.createdAt,
    )

private const val MILLIS_PER_DAY = 86_400_000L
