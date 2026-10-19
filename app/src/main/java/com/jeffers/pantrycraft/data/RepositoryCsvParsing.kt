package com.jeffers.pantrycraft.data

internal fun optionalCsvValue(
    cols: List<String>,
    index: Int,
    available: Boolean,
): String? = if (available) cols.getOrNull(index)?.trim()?.takeIf(String::isNotEmpty) else null

internal fun parseCsvLine(line: String): List<String> {
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
