package com.jeffers.pantrycraft.data

import androidx.room.withTransaction

private const val EXTENDED_CSV_COLUMN_COUNT = 10
private const val DATE_PATTERN = "yyyy-MM-dd"
private const val CUSTOM_UNIT_COLUMN = 5
private const val QUANTITY_COLUMN = 3
private const val UNIT_COLUMN = 4
private const val THRESHOLD_COLUMN = 6
private const val EXPIRATION_COLUMN = 7
private const val LEGACY_EXPIRATION_COLUMN = 5
private const val IS_OPENED_COLUMN = 8
private const val LEGACY_IS_OPENED_COLUMN = 6
private const val NOTES_COLUMN = 9
private const val LEGACY_NOTES_COLUMN = 7

suspend fun Repository.importPantryFromCsv(csvString: String): Int =
    withTransaction {
        val lines = csvString.lines().filter { it.isNotBlank() }
        if (lines.size <= 1) return@withTransaction 0
        val dateFormat = java.text.SimpleDateFormat(DATE_PATTERN, java.util.Locale.US)
        var importedCount = 0
        lines.drop(1).forEach { line ->
            parsePantryCsvItem(parseCsvLine(line), dateFormat)?.let { item ->
                insertPantry(item)
                importedCount++
            }
        }
        importedCount
    }

private fun parsePantryCsvItem(
    cols: List<String>,
    dateFormat: java.text.SimpleDateFormat,
): PantryItem? {
    val name = cols.getOrNull(0)?.trim().orEmpty()
    if (name.isEmpty()) return null
    val category = cols.getOrNull(1)?.trim()?.uppercase()?.takeIf(String::isNotEmpty) ?: "OTHER"
    val location =
        cols.getOrNull(2)?.trim()?.uppercase()?.takeIf { it in PantryTaxonomy.storageLocations } ?: "PANTRY"
    val quantity = cols.getOrNull(QUANTITY_COLUMN)?.trim()?.toFloatOrNull()?.coerceAtLeast(0f) ?: 1f
    val unit = cols.getOrNull(UNIT_COLUMN)?.trim()?.uppercase()?.takeIf(String::isNotEmpty) ?: "COUNT"
    val extended = cols.size >= EXTENDED_CSV_COLUMN_COUNT
    val customUnit = optionalCsvValue(cols, CUSTOM_UNIT_COLUMN, extended)
    val threshold =
        if (extended) cols.getOrNull(THRESHOLD_COLUMN)?.trim()?.toFloatOrNull()?.coerceAtLeast(0f) ?: 0f
        else 0f
    val expirationColumn = if (extended) EXPIRATION_COLUMN else LEGACY_EXPIRATION_COLUMN
    val expiration = cols.getOrNull(expirationColumn)?.trim().orEmpty().takeIf(String::isNotEmpty)?.let {
        runCatching { dateFormat.parse(it)?.time }.getOrNull()
    }
    val openedColumn = if (extended) IS_OPENED_COLUMN else LEGACY_IS_OPENED_COLUMN
    val isOpened = cols.getOrNull(openedColumn)?.trim()?.toBoolean() ?: false
    val notesColumn = if (extended) NOTES_COLUMN else LEGACY_NOTES_COLUMN
    val notes = cols.getOrNull(notesColumn)?.trim()?.takeIf(String::isNotEmpty)
    return PantryItem(
        name = name,
        category = category,
        storageLocation = location,
        quantity = quantity,
        unit = unit,
        customUnitName = customUnit,
        threshold = threshold,
        expirationTimestamp = expiration,
        isOpened = isOpened,
        openedTimestamp = if (isOpened) System.currentTimeMillis() else null,
        notes = notes,
    )
}

private fun optionalCsvValue(cols: List<String>, index: Int, available: Boolean): String? =
    if (available) cols.getOrNull(index)?.trim()?.takeIf(String::isNotEmpty) else null

private fun parseCsvLine(line: String): List<String> {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var index = 0
    while (index < line.length) {
        val ch = line[index]
        if (ch == '"') {
            if (inQuotes && index + 1 < line.length && line[index + 1] == '"') {
                current.append('"')
                index++
            } else {
                inQuotes = !inQuotes
            }
        } else if (ch == ',' && !inQuotes) {
            result.add(current.toString())
            current.clear()
        } else {
            current.append(ch)
        }
        index++
    }
    result.add(current.toString())
    return result
}
