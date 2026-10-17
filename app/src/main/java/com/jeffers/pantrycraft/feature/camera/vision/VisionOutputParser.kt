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
    val customUnitName: String? = null,
    val barcode: String? = null,
    val threshold: Float = 0f,
    val isOpened: Boolean = false,
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

        val box = parseBoundingBox(item)

        return ParsedVisionItem(
            name = name,
            category = item.optString("category", "OTHER").uppercase(),
            storageLocation = item.optString("storage_location", "PANTRY").uppercase(),
            quantity = item.optDouble("quantity", 1.0).toFloat().coerceAtLeast(0f),
            unit = item.optString("unit", "COUNT").uppercase(),
            confidence = item.optDouble("confidence", 0.0).toFloat().coerceIn(0f, 1f),
            estimatedShelfLifeDays = item.optInt("estimated_shelf_life_days", 0).coerceAtLeast(0),
            isAromaticOrSeasoning = item.optBoolean("is_aromatic_or_seasoning", false),
            boundingBox = box,
            conditionNotes = item.optString("condition_notes").takeIf { it.isNotBlank() },
            customUnitName = item.optString("custom_unit_name").takeIf { it.isNotBlank() },
            barcode = item.optString("barcode").takeIf { it.isNotBlank() },
            threshold = item.optDouble("threshold", 0.0).toFloat().coerceAtLeast(0f),
            isOpened = item.optBoolean("is_opened", false),
        )
    }

    private fun parseBoundingBox(item: JSONObject): NormalizedBox {
        val arrayBox = item.optJSONArray("bounding_box")
        if (arrayBox != null && arrayBox.length() == 4) {
            val values = (0 until 4).map { arrayBox.optDouble(it, Double.NaN).toFloat() }
            if (values.all { it.isFinite() }) {
                return NormalizedBox(
                    ymin = values[0].coerceIn(0f, 1f),
                    xmin = values[1].coerceIn(0f, 1f),
                    ymax = values[2].coerceIn(0f, 1f),
                    xmax = values[3].coerceIn(0f, 1f),
                )
            }
        }

        val objectBox = item.optJSONObject("bounding_box")
        if (objectBox != null) {
            val ymin =
                objectBox.optDouble("y_min", objectBox.optDouble("ymin", 0.0)).toFloat()
            val xmin =
                objectBox.optDouble("x_min", objectBox.optDouble("xmin", 0.0)).toFloat()
            val ymax =
                objectBox.optDouble("y_max", objectBox.optDouble("ymax", 1.0)).toFloat()
            val xmax =
                objectBox.optDouble("x_max", objectBox.optDouble("xmax", 1.0)).toFloat()
            if (listOf(ymin, xmin, ymax, xmax).all { it.isFinite() }) {
                return NormalizedBox(
                    ymin = ymin.coerceIn(0f, 1f),
                    xmin = xmin.coerceIn(0f, 1f),
                    ymax = ymax.coerceIn(0f, 1f),
                    xmax = xmax.coerceIn(0f, 1f),
                )
            }
        }

        return NormalizedBox(0f, 0f, 1f, 1f)
    }

    private fun sanitize(raw: String): String {
        val trimmed =
            raw
                .trim()
                .removePrefix("```json")
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
