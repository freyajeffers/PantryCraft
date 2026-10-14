package com.jeffers.pantrycraft.feature.camera.vision

import org.json.JSONArray
import org.json.JSONObject

/** Strict, local parser for the normalized shelf-inventory response contract. */
data class ParsedVisionItem(
    val name: String,
    val category: String,
    val storageLocation: String,
    val quantity: Float,
    val unit: String,
    val confidence: Float,
    val estimatedShelfLifeDays: Int,
    val isAromaticOrSeasoning: Boolean,
    val boundingBox: NormalizedBox,
    val conditionNotes: String?,
)

data class NormalizedBox(
    val ymin: Float,
    val xmin: Float,
    val ymax: Float,
    val xmax: Float,
)

class VisionOutputParser {
    fun parse(raw: String): List<ParsedVisionItem> {
        val json = JSONObject(sanitize(raw))
        val items = json.optJSONArray("detected_items") ?: json.optJSONArray("items") ?: JSONArray()
        return buildList(items.length()) {
            for (index in 0 until items.length()) {
                parseItem(items.optJSONObject(index))?.let(::add)
            }
        }
    }

    private fun parseItem(item: JSONObject?): ParsedVisionItem? {
        if (item == null) return null
        val name = item.optString("name").trim()
        if (name.isEmpty()) return null
        val box = item.optJSONArray("bounding_box") ?: return null
        if (box.length() != 4) return null
        val values = (0 until 4).map { box.optDouble(it, Double.NaN).toFloat() }
        if (values.any { !it.isFinite() }) return null
        return ParsedVisionItem(
            name = name,
            category = item.optString("category", "OTHER").uppercase(),
            storageLocation = item.optString("storage_location", "PANTRY").uppercase(),
            quantity = item.optDouble("quantity", 1.0).toFloat().coerceAtLeast(0f),
            unit = item.optString("unit", "COUNT").uppercase(),
            confidence = item.optDouble("confidence", 0.0).toFloat().coerceIn(0f, 1f),
            estimatedShelfLifeDays = item.optInt("estimated_shelf_life_days", 0).coerceAtLeast(0),
            isAromaticOrSeasoning = item.optBoolean("is_aromatic_or_seasoning", false),
            boundingBox =
                NormalizedBox(
                    ymin = values[0].coerceIn(0f, 1f),
                    xmin = values[1].coerceIn(0f, 1f),
                    ymax = values[2].coerceIn(0f, 1f),
                    xmax = values[3].coerceIn(0f, 1f),
                ),
            conditionNotes = item.optString("condition_notes").takeIf { it.isNotBlank() },
        )
    }

    private fun sanitize(raw: String): String {
        val trimmed =
            raw
                .trim()
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
        val start = trimmed.indexOf('{')
        require(start >= 0) { "Vision response did not contain JSON" }
        val candidate = trimmed.substring(start).replace(Regex("[\\u0000-\\u001F&&[^\\r\\n\\t]]"), "")
        return candidate + closingSuffix(candidate)
    }

    private fun closingSuffix(json: String): String {
        var braces = 0
        var brackets = 0
        var quoted = false
        var escaped = false
        json.forEach { character ->
            if (escaped) {
                escaped = false
            } else if (character == '\\' && quoted) {
                escaped = true
            } else if (character == '"') {
                quoted = !quoted
            } else if (!quoted) {
                if (character == '{') braces++
                if (character == '}') braces--
                if (character == '[') brackets++
                if (character == ']') brackets--
            }
        }
        return buildString {
            repeat(brackets.coerceAtLeast(0)) { append(']') }
            repeat(braces.coerceAtLeast(0)) { append('}') }
        }
    }
}
