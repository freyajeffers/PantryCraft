package com.jeffers.pantrycraft.data

internal fun sanitizeSearchQuery(raw: String): String =
    raw
        .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() && !isFtsReservedWord(it) }
        .joinToString(" ")

private fun isFtsReservedWord(word: String): Boolean =
    word.equals("AND", ignoreCase = true) ||
        word.equals("OR", ignoreCase = true) ||
        word.equals("NOT", ignoreCase = true) ||
        word.equals("NEAR", ignoreCase = true)
