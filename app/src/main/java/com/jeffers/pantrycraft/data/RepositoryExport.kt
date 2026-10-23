package com.jeffers.pantrycraft.data

import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

internal suspend fun Repository.exportInventoryToJson(): String =
    exportInventoryToJson(observePantryItems().first(), observeGroceryItems().first())

internal fun exportInventoryToJson(
    pantry: List<PantryItem>,
    grocery: List<GroceryItem>,
    now: Long = System.currentTimeMillis(),
): String {
    val json =
        JSONObject().apply {
            put("version", 1)
            put("exportedAt", now)
            put("pantry", pantry.map(::pantryExportObject).toJsonArray())
            put("grocery", grocery.map(::groceryExportObject).toJsonArray())
        }
    return json.toString(2)
}

private fun pantryExportObject(item: PantryItem) =
    JSONObject().apply {
        put("name", item.name)
        put("quantity", item.quantity.toDouble())
        put("unit", item.unit ?: "COUNT")
        put("customUnitName", item.customUnitName ?: JSONObject.NULL)
        put("threshold", item.threshold.toDouble())
        put("barcode", item.barcode ?: "")
        put("storageLocation", item.storageLocation)
        put("category", item.category)
        put("expirationTimestamp", item.expirationTimestamp ?: JSONObject.NULL)
        put("isOpened", item.isOpened)
        put("openedTimestamp", item.openedTimestamp ?: JSONObject.NULL)
        put("isAromaticOrSeasoning", item.isAromaticOrSeasoning)
        put("notes", item.notes ?: "")
    }

private fun groceryExportObject(item: GroceryItem) =
    JSONObject().apply {
        put("name", item.name)
        put("quantity", item.quantity.toDouble())
        put("unit", item.unit ?: "COUNT")
        put("checked", item.checked)
    }

private fun List<JSONObject>.toJsonArray() = JSONArray().also { array -> forEach(array::put) }

internal suspend fun Repository.exportPantryToCsv(): String =
    exportPantryToCsv(observePantryItems().first())

internal fun exportPantryToCsv(items: List<PantryItem>): String {
    val header = "name,category,storageLocation,quantity,unit,customUnitName,threshold,expirationDate,isOpened,notes"
    val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    return buildString {
        appendLine(header)
        items.forEach { item ->
            appendLine(
                listOf(
                    item.name,
                    item.category,
                    item.storageLocation,
                    item.quantity,
                    item.unit ?: "COUNT",
                    item.customUnitName.orEmpty(),
                    item.threshold,
                    item.expirationTimestamp?.let(dateFormat::format).orEmpty(),
                    item.isOpened,
                    item.notes.orEmpty(),
                ).joinToString(",") { value -> "\"${value.toString().replace("\"", "\"\"")}\"" },
            )
        }
    }
}
