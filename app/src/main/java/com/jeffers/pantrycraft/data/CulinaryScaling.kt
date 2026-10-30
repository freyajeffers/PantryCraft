package com.jeffers.pantrycraft.data

import kotlin.math.pow
import kotlin.math.round

object CulinaryScaling {
    const val DEFAULT_SEASONING_EXPONENT = 0.75
    const val MIN_PALATE_EXPONENT = 0.65
    const val MAX_PALATE_EXPONENT = 0.85

    const val ML_PER_TSP = 5f
    const val ML_PER_TBSP = 15f
    const val ML_PER_FL_OZ = 30f
    const val ML_PER_CUP = 240f
    const val GRAMS_PER_BUTTER_STICK = 113.4f
    const val GRAMS_PER_CUP_FLOUR = 120f
    const val GRAMS_PER_CUP_RICE = 185f
    const val GRAMS_PER_CUP_SUGAR = 200f

    private const val ML_PER_LITER = 1000f
    private const val ROUNDING_FACTOR = 100.0

    private val VOLUME_UNIT_ALIASES: Map<String, String> =
        mapOf(
            "tsp" to "TSP",
            "tsps" to "TSP",
            "teaspoon" to "TSP",
            "teaspoons" to "TSP",
            "t" to "TSP",
            "tbsp" to "TBSP",
            "tbsps" to "TBSP",
            "tablespoon" to "TBSP",
            "tablespoons" to "TBSP",
            "tb" to "TBSP",
            "fl oz" to "FL_OZ",
            "fl_oz" to "FL_OZ",
            "floz" to "FL_OZ",
            "fluid ounce" to "FL_OZ",
            "fluid ounces" to "FL_OZ",
            "cup" to "CUP",
            "cups" to "CUP",
            "c" to "CUP",
            "ml" to "ML",
            "mls" to "ML",
            "milliliter" to "ML",
            "milliliters" to "ML",
            "millilitre" to "ML",
            "millilitres" to "ML",
            "l" to "L",
            "liter" to "L",
            "liters" to "L",
            "litre" to "L",
            "litres" to "L",
        )

    private val VOLUME_TO_ML_FACTORS: Map<String, Float> =
        mapOf(
            "TSP" to ML_PER_TSP,
            "TBSP" to ML_PER_TBSP,
            "FL_OZ" to ML_PER_FL_OZ,
            "CUP" to ML_PER_CUP,
            "ML" to 1f,
            "L" to ML_PER_LITER,
        )

    /**
     * Scales an ingredient quantity from [basePortions] to [targetPortions].
     *
     * Seasonings and aromatics (where [isAromaticOrSeasoning] is true) scale non-linearly
     * using the power curve: baseQuantity * (targetPortions / basePortions) ^ exponent.
     * Standard bulk ingredients scale linearly: baseQuantity * (targetPortions / basePortions).
     */
    fun scaleQuantity(
        baseQuantity: Float,
        basePortions: Int,
        targetPortions: Int,
        isAromaticOrSeasoning: Boolean,
        exponent: Double = DEFAULT_SEASONING_EXPONENT,
    ): Float {
        require(baseQuantity >= 0f) { "Base quantity must not be negative" }
        require(basePortions > 0) { "Base portions must be greater than zero" }
        require(targetPortions > 0) { "Target portions must be greater than zero" }

        if (baseQuantity == 0f || basePortions == targetPortions) {
            return baseQuantity
        }

        val portionRatio = targetPortions.toDouble() / basePortions.toDouble()
        val scaled =
            if (isAromaticOrSeasoning) {
                val clampedExponent = exponent.coerceIn(MIN_PALATE_EXPONENT, MAX_PALATE_EXPONENT)
                baseQuantity.toDouble() * portionRatio.pow(clampedExponent)
            } else {
                baseQuantity.toDouble() * portionRatio
            }
        return (round(scaled * ROUNDING_FACTOR) / ROUNDING_FACTOR).toFloat()
    }

    /**
     * Converts between common culinary volume units (TSP, TBSP, FL_OZ, CUP, ML, L).
     * Returns null if units are incompatible or unrecognized.
     */
    fun convertVolume(
        quantity: Float,
        fromUnit: String,
        toUnit: String,
    ): Float? {
        if (!isQuantityValid(quantity)) return null
        val normFrom = normalizeVolumeUnit(fromUnit)
        val normTo = normalizeVolumeUnit(toUnit)
        return when {
            normFrom == null || normTo == null -> null
            normFrom == normTo -> quantity
            else -> toMilliliters(quantity, normFrom)?.let { fromMilliliters(it, normTo) }
        }
    }

    private fun normalizeVolumeUnit(unit: String): String? {
        val clean =
            unit
                .trim()
                .lowercase()
                .replace("_", " ")
                .replace(".", "")
        return VOLUME_UNIT_ALIASES[clean]
    }

    private fun isQuantityValid(quantity: Float): Boolean = quantity >= 0f && quantity.isFinite()

    private fun toMilliliters(
        quantity: Float,
        unit: String,
    ): Float? {
        val factor = normalizeVolumeUnit(unit)?.let { VOLUME_TO_ML_FACTORS[it] }
        return factor?.let { quantity * it }
    }

    private fun fromMilliliters(
        ml: Float,
        unit: String,
    ): Float? {
        val factor = normalizeVolumeUnit(unit)?.let { VOLUME_TO_ML_FACTORS[it] }
        return factor?.let { ml / it }
    }
}
