package com.jeffers.pantrycraft.data

import kotlin.math.pow
import kotlin.math.round

object CulinaryScaling {
    const val DEFAULT_SEASONING_EXPONENT = 0.75
    const val MIN_PALATE_EXPONENT = 0.65
    const val MAX_PALATE_EXPONENT = 0.85

    const val ML_PER_TSP = 5f
    const val ML_PER_TBSP = 15f
    const val ML_PER_CUP = 240f
    const val GRAMS_PER_BUTTER_STICK = 113.4f
    const val GRAMS_PER_CUP_FLOUR = 120f
    const val GRAMS_PER_CUP_RICE = 185f
    const val GRAMS_PER_CUP_SUGAR = 200f

    private const val ML_PER_LITER = 1000f
    private const val ROUNDING_FACTOR = 100.0

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
        val scaled = if (isAromaticOrSeasoning) {
            val clampedExponent = exponent.coerceIn(MIN_PALATE_EXPONENT, MAX_PALATE_EXPONENT)
            baseQuantity.toDouble() * portionRatio.pow(clampedExponent)
        } else {
            baseQuantity.toDouble() * portionRatio
        }
        return (round(scaled * ROUNDING_FACTOR) / ROUNDING_FACTOR).toFloat()
    }

    /**
     * Converts between common culinary volume units (TSP, TBSP, CUP, ML, L).
     * Returns null if units are incompatible or unrecognized.
     */
    fun convertVolume(
        quantity: Float,
        fromUnit: String,
        toUnit: String,
    ): Float? {
        val mlValue = toMilliliters(quantity, fromUnit) ?: return null
        return fromMilliliters(mlValue, toUnit)
    }

    private fun toMilliliters(quantity: Float, unit: String): Float? =
        when (unit.uppercase()) {
            "TSP" -> quantity * ML_PER_TSP
            "TBSP" -> quantity * ML_PER_TBSP
            "CUP" -> quantity * ML_PER_CUP
            "ML" -> quantity
            "L" -> quantity * ML_PER_LITER
            else -> null
        }

    private fun fromMilliliters(ml: Float, unit: String): Float? =
        when (unit.uppercase()) {
            "TSP" -> ml / ML_PER_TSP
            "TBSP" -> ml / ML_PER_TBSP
            "CUP" -> ml / ML_PER_CUP
            "ML" -> ml
            "L" -> ml / ML_PER_LITER
            else -> null
        }
}
