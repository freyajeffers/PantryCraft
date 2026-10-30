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
    db.withTransaction {
        val items = parsePantryItemsFromCsv(csvString)
        var importedCount = 0
        items.forEach { item ->
            insertPantry(item)
            importedCount++
        }
        importedCount
    }

internal fun parsePantryItemsFromCsv(
    csvString: String,
    now: Long = System.currentTimeMillis(),
): List<PantryItem> {
    val lines = csvString.lines().filter { it.isNotBlank() }
    if (lines.size <= 1) return emptyList()
    val dateFormat = java.text.SimpleDateFormat(DATE_PATTERN, java.util.Locale.US)
    return lines.drop(1).mapNotNull { line ->
        parsePantryCsvItem(parseCsvLine(line), dateFormat, now)
    }
}

internal fun parsePantryCsvItem(
    cols: List<String>,
    dateFormat: java.text.SimpleDateFormat,
    now: Long,
): PantryItem? {
    val name = cols.getOrNull(0)?.trim().orEmpty()
    if (name.isEmpty()) return null

    val extended = cols.size >= EXTENDED_CSV_COLUMN_COUNT
    val category =
        cols
            .getOrNull(1)
            ?.trim()
            ?.uppercase()
            ?.takeIf(String::isNotEmpty) ?: "OTHER"
    val location =
        cols
            .getOrNull(2)
            ?.trim()
            ?.uppercase()
            ?.takeIf { it in PantryTaxonomy.storageLocations } ?: "PANTRY"
    val quantity =
        cols
            .getOrNull(QUANTITY_COLUMN)
            ?.trim()
            ?.toFloatOrNull()
            ?.coerceAtLeast(0f) ?: 1f
    val unit =
        cols
            .getOrNull(UNIT_COLUMN)
            ?.trim()
            ?.uppercase()
            ?.takeIf(String::isNotEmpty) ?: "COUNT"
    val customUnit = optionalCsvValue(cols, CUSTOM_UNIT_COLUMN, extended)
    val threshold = parseThreshold(cols, extended)
    val expiration = parseExpiration(cols, dateFormat, extended)
    val isOpened = parseIsOpened(cols, extended)
    val notes = parseNotes(cols, extended)

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
        openedTimestamp = if (isOpened) now else null,
        notes = notes,
    )
}

internal fun parseThreshold(
    cols: List<String>,
    extended: Boolean,
): Float =
    if (extended) {
        val raw = cols.getOrNull(THRESHOLD_COLUMN)?.trim()
        raw?.toFloatOrNull()?.coerceAtLeast(0f) ?: 0f
    } else {
        0f
    }

private fun parseExpiration(
    cols: List<String>,
    dateFormat: java.text.SimpleDateFormat,
    extended: Boolean,
): Long? =
    cols
        .getOrNull(if (extended) EXPIRATION_COLUMN else LEGACY_EXPIRATION_COLUMN)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let {
            runCatching { dateFormat.parse(it)?.time }.getOrNull()
        }

private fun parseIsOpened(
    cols: List<String>,
    extended: Boolean,
): Boolean = cols.getOrNull(if (extended) IS_OPENED_COLUMN else LEGACY_IS_OPENED_COLUMN)?.trim()?.toBoolean() ?: false

private fun parseNotes(
    cols: List<String>,
    extended: Boolean,
): String? = cols.getOrNull(if (extended) NOTES_COLUMN else LEGACY_NOTES_COLUMN)?.trim()?.takeIf(String::isNotEmpty)
