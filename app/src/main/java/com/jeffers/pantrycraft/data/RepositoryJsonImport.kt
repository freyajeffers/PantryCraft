package com.jeffers.pantrycraft.data

import androidx.room.withTransaction
import org.json.JSONObject

internal suspend fun Repository.importInventoryFromJson(jsonString: String): Int =
    db.withTransaction {
        val importedItems = parsePantryItemsFromJson(jsonString)
        var count = 0
        for (item in importedItems) {
            insertPantry(item)
            count++
        }
        count
    }

internal fun parsePantryItemsFromJson(jsonString: String): List<PantryItem> {
    val pantry = runCatching { JSONObject(jsonString) }.getOrNull()?.optJSONArray("pantry")
        ?: return emptyList()
    return (0 until pantry.length()).mapNotNull { index ->
        pantry.optJSONObject(index)?.toPantryItem()
    }
}

internal fun JSONObject.toPantryItem(): PantryItem? {
    val name = optString("name").trim().takeIf(String::isNotEmpty) ?: return null
    return PantryItem(
        name = name,
        quantity = optDouble("quantity", 1.0).toFloat().coerceAtLeast(0f),
        unit = optString("unit", "COUNT"),
        customUnitName = optString("customUnitName").takeIf(String::isNotEmpty),
        threshold = optDouble("threshold", 0.0).toFloat().coerceAtLeast(0f),
        barcode = optString("barcode").takeIf(String::isNotEmpty),
        storageLocation = optString("storageLocation", "PANTRY"),
        category = optString("category", "OTHER"),
        expirationTimestamp = optLong("expirationTimestamp").takeIf { it > 0L },
        isOpened = optBoolean("isOpened", false),
        openedTimestamp = optLong("openedTimestamp").takeIf { it > 0L },
        isAromaticOrSeasoning = optBoolean("isAromaticOrSeasoning", false),
        notes = optString("notes").takeIf(String::isNotEmpty),
    )
}
