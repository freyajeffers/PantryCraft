package com.jeffers.pantrycraft.feature.meals.context

import com.jeffers.pantrycraft.data.PantryItem

object InventoryContextCompressor {

    private const val MILLIS_PER_DAY = 86_400_000L
    private const val URGENT_EXPIRY_DAYS = 2L
    private const val CHARACTERS_PER_TOKEN_ESTIMATE = 4

    fun compress(
        items: List<PantryItem>,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        val activeItems = items.filter { !it.isDeleted && it.quantity > 0f }
        if (activeItems.isEmpty()) return "[INVENTORY_CONTEXT]\nNo ingredients in stock."

        val urgentList = mutableListOf<String>()
        val grouped = activeItems.groupBy { it.storageLocation.uppercase() }

        val builder = StringBuilder("[INVENTORY_CONTEXT]\n")
        val orderedLocations = listOf("PANTRY", "FRIDGE", "FREEZER", "COUNTER")
        val allLocations = orderedLocations + (grouped.keys - orderedLocations.toSet())

        for (location in allLocations) {
            val zoneItems = grouped[location].orEmpty()
            if (zoneItems.isNotEmpty()) {
                val formattedItems = zoneItems.map { item ->
                    formatItemCompact(item, nowMillis, urgentList)
                }
                builder.append(location.lowercase().replaceFirstChar { it.uppercase() })
                    .append(": ")
                    .append(formattedItems.joinToString(", "))
                    .append("\n")
            }
        }

        if (urgentList.isNotEmpty()) {
            builder.append("[EXPIRY_URGENT]: ")
                .append(urgentList.joinToString(", "))
                .append("\n")
        }

        return builder.toString().trimEnd()
    }

    private fun formatItemCompact(
        item: PantryItem,
        nowMillis: Long,
        urgentCollector: MutableList<String>,
    ): String {
        val safeName = item.name.trim().lowercase().replace(' ', '_')
        val qtyDisplay = formatQuantity(item.quantity)
        val unitDisplay = item.unit?.trim()?.lowercase().orEmpty()
        val expInfo = item.expirationTimestamp?.let { exp ->
            val daysRemaining = ((exp - nowMillis) / MILLIS_PER_DAY).coerceAtLeast(0L)
            if (daysRemaining <= URGENT_EXPIRY_DAYS) {
                urgentCollector.add("$safeName ($daysRemaining days left!)")
            }
            ", EXP:${daysRemaining}d"
        }.orEmpty()

        val unitPart = if (unitDisplay.isNotBlank()) " $unitDisplay" else ""
        return "$safeName($qtyDisplay$unitPart$expInfo)"
    }

    private fun formatQuantity(quantity: Float): String =
        if (quantity % 1f == 0f) quantity.toInt().toString() else "%.1f".format(quantity)

    fun estimateTokenCount(text: String): Int =
        (text.length + CHARACTERS_PER_TOKEN_ESTIMATE - 1) / CHARACTERS_PER_TOKEN_ESTIMATE
}
