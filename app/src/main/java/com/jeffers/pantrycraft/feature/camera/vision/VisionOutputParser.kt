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
        return item?.let { json ->
            val name = json.optString("name").trim()
            name.takeIf(String::isNotEmpty)?.let {
                ParsedVisionItem(
                    name = it,
                    category = json.optString("category", DEFAULT_CATEGORY).uppercase(),
                    storageLocation = json.optString("storage_location", DEFAULT_STORAGE).uppercase(),
                    quantity = json.optDouble("quantity", DEFAULT_QUANTITY)
                        .toFloat().coerceAtLeast(MIN_QUANTITY_FLOAT),
                    unit = json.optString("unit", DEFAULT_UNIT).uppercase(),
                    confidence = json.optDouble("confidence", MIN_CONFIDENCE_DOUBLE)
                        .toFloat().coerceIn(MIN_CONFIDENCE_FLOAT, MAX_CONFIDENCE_FLOAT),
                    estimatedShelfLifeDays = json.optInt("estimated_shelf_life_days", MIN_DAYS)
                        .coerceAtLeast(MIN_DAYS),
                    isAromaticOrSeasoning = json.optBoolean("is_aromatic_or_seasoning", false),
                    boundingBox = parseBoundingBox(json),
                    conditionNotes = json.optString("condition_notes").takeIf(String::isNotBlank),
                    customUnitName = json.optString("custom_unit_name").takeIf(String::isNotBlank),
                    barcode = json.optString("barcode").takeIf(String::isNotBlank),
                    threshold = json.optDouble("threshold", MIN_THRESHOLD_DOUBLE)
                        .toFloat().coerceAtLeast(MIN_THRESHOLD_FLOAT),
                    isOpened = json.optBoolean("is_opened", false),
                )
            }
        }
    }

    private fun parseBoundingBox(item: JSONObject): NormalizedBox {
        val arrayBox = item.optJSONArray("bounding_box")
        val arrayResult = arrayBox
            ?.takeIf { it.length() == BOX_VALUE_COUNT }
            ?.let { jsonArray ->
                (0 until BOX_VALUE_COUNT).map { index ->
                    jsonArray.optDouble(index, Double.NaN).toFloat()
                }
            }?.takeIf { values -> values.all(Float::isFinite) }
            ?.let { values ->
                NormalizedBox(
                    ymin = values[0].coerceIn(0f, 1f),
                    xmin = values[1].coerceIn(0f, 1f),
                    ymax = values[2].coerceIn(0f, 1f),
                    xmax = values[3].coerceIn(0f, 1f),
                )
            }

        val objectBox = item.optJSONObject("bounding_box")
        val objectResult = objectBox?.let { box ->
            val ymin =
                box.optDouble("y_min", box.optDouble("ymin", MIN_BOUND)).toFloat()
            val xmin =
                box.optDouble("x_min", box.optDouble("xmin", MIN_BOUND)).toFloat()
            val ymax =
                box.optDouble("y_max", box.optDouble("ymax", MAX_BOUND)).toFloat()
            val xmax =
                box.optDouble("x_max", box.optDouble("xmax", MAX_BOUND)).toFloat()
            listOf(ymin, xmin, ymax, xmax).takeIf { values -> values.all(Float::isFinite) }?.let {
                NormalizedBox(
                    ymin = ymin.coerceIn(0f, 1f),
                    xmin = xmin.coerceIn(0f, 1f),
                    ymax = ymax.coerceIn(0f, 1f),
                    xmax = xmax.coerceIn(0f, 1f),
                )
            }
        }

        return arrayResult ?: objectResult ?: NormalizedBox(
            MIN_BOUND_FLOAT,
            MIN_BOUND_FLOAT,
            MAX_BOUND_FLOAT,
            MAX_BOUND_FLOAT,
        )
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

    private companion object {
        const val BOX_VALUE_COUNT = 4
        const val DEFAULT_CATEGORY = "OTHER"
        const val DEFAULT_STORAGE = "PANTRY"
        const val DEFAULT_UNIT = "COUNT"
        const val DEFAULT_QUANTITY = 1.0
        const val MIN_QUANTITY_FLOAT = 0f
        const val MIN_CONFIDENCE_DOUBLE = 0.0
        const val MIN_CONFIDENCE_FLOAT = 0f
        const val MAX_CONFIDENCE_FLOAT = 1f
        const val MIN_DAYS = 0
        const val MIN_THRESHOLD_DOUBLE = 0.0
        const val MIN_THRESHOLD_FLOAT = 0f
        const val MIN_BOUND = 0.0
        const val MAX_BOUND = 1.0
        const val MIN_BOUND_FLOAT = 0f
        const val MAX_BOUND_FLOAT = 1f
    }
}
