package com.jeffers.pantrycraft.feature.camera.vision

import com.jeffers.pantrycraft.data.PantryItem

fun ParsedVisionItem.toPantryItem(now: Long = System.currentTimeMillis()): PantryItem =
    PantryItem(
        name = name,
        quantity = quantity,
        unit = unit,
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

private const val MILLIS_PER_DAY = 86_400_000L
