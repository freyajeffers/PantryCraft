package com.jeffers.pantrycraft.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CulinaryScalingTest {
    @Test
    fun bulkIngredientsScaleLinearly() {
        val baseQty = 100f
        val scaled = CulinaryScaling.scaleQuantity(
            baseQuantity = baseQty,
            basePortions = 2,
            targetPortions = 6,
            isAromaticOrSeasoning = false,
        )
        // 100 * (6 / 2) = 300
        assertEquals(300f, scaled, 0.01f)
    }

    @Test
    fun seasoningIngredientsScaleWithPowerCurve() {
        val baseSaltGrams = 10f
        val scaled = CulinaryScaling.scaleQuantity(
            baseQuantity = baseSaltGrams,
            basePortions = 2,
            targetPortions = 6,
            isAromaticOrSeasoning = true,
        )
        // Ratio = 3.0. 3^0.75 ≈ 2.2795. 10 * 2.2795 = 22.80
        val linearScaled = CulinaryScaling.scaleQuantity(
            baseQuantity = baseSaltGrams,
            basePortions = 2,
            targetPortions = 6,
            isAromaticOrSeasoning = false,
        )
        assertEquals(30f, linearScaled, 0.01f)
        assertTrue(scaled < linearScaled)
        assertEquals(22.80f, scaled, 0.05f)
    }

    @Test
    fun equalPortionsReturnsOriginalQuantity() {
        val qty = 42.5f
        val scaledSeasoning = CulinaryScaling.scaleQuantity(
            baseQuantity = qty,
            basePortions = 4,
            targetPortions = 4,
            isAromaticOrSeasoning = true,
        )
        val scaledBulk = CulinaryScaling.scaleQuantity(
            baseQuantity = qty,
            basePortions = 4,
            targetPortions = 4,
            isAromaticOrSeasoning = false,
        )
        assertEquals(qty, scaledSeasoning, 0.001f)
        assertEquals(qty, scaledBulk, 0.001f)
    }

    @Test
    fun zeroQuantityReturnsZero() {
        val scaled = CulinaryScaling.scaleQuantity(
            baseQuantity = 0f,
            basePortions = 2,
            targetPortions = 8,
            isAromaticOrSeasoning = true,
        )
        assertEquals(0f, scaled, 0.001f)
    }

    @Test
    fun invalidInputsThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            CulinaryScaling.scaleQuantity(-1f, 2, 4, false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CulinaryScaling.scaleQuantity(10f, 0, 4, false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CulinaryScaling.scaleQuantity(10f, 2, 0, false)
        }
    }

    @Test
    fun calibratedExponentIsClampedWithinBounds() {
        val base = 10f
        // Sensitive palate (below min 0.65) should clamp to 0.65
        val clampedMin = CulinaryScaling.scaleQuantity(
            baseQuantity = base,
            basePortions = 1,
            targetPortions = 4,
            isAromaticOrSeasoning = true,
            exponent = 0.50,
        )
        // Bold palate (above max 0.85) should clamp to 0.85
        val clampedMax = CulinaryScaling.scaleQuantity(
            baseQuantity = base,
            basePortions = 1,
            targetPortions = 4,
            isAromaticOrSeasoning = true,
            exponent = 0.99,
        )
        val expectedMin = (10.0 * Math.pow(4.0, 0.65)).toFloat()
        val expectedMax = (10.0 * Math.pow(4.0, 0.85)).toFloat()

        assertEquals(expectedMin, clampedMin, 0.05f)
        assertEquals(expectedMax, clampedMax, 0.05f)
    }

    @Test
    fun volumeConversionsComputeCorrectly() {
        assertEquals(15f, CulinaryScaling.convertVolume(1f, "TBSP", "ML") ?: 0f, 0.01f)
        assertEquals(5f, CulinaryScaling.convertVolume(1f, "TSP", "ML") ?: 0f, 0.01f)
        assertEquals(240f, CulinaryScaling.convertVolume(1f, "CUP", "ML") ?: 0f, 0.01f)
        assertEquals(16f, CulinaryScaling.convertVolume(1f, "CUP", "TBSP") ?: 0f, 0.01f)
        assertEquals(48f, CulinaryScaling.convertVolume(1f, "CUP", "TSP") ?: 0f, 0.01f)
        assertEquals(1000f, CulinaryScaling.convertVolume(1f, "L", "ML") ?: 0f, 0.01f)
        assertNull(CulinaryScaling.convertVolume(1f, "GRAMS", "ML"))
    }

    @Test
    fun volumeConversionsCaseInsensitiveAndBidirectional() {
        assertEquals(0.5f, CulinaryScaling.convertVolume(500f, "ml", "l") ?: 0f, 0.01f)
        assertEquals(1f, CulinaryScaling.convertVolume(240f, "ml", "cup") ?: 0f, 0.01f)
        assertEquals(2f, CulinaryScaling.convertVolume(30f, "ml", "tbsp") ?: 0f, 0.01f)
        assertEquals(3f, CulinaryScaling.convertVolume(15f, "ml", "tsp") ?: 0f, 0.01f)
        assertEquals(3f, CulinaryScaling.convertVolume(1f, "tbsp", "tsp") ?: 0f, 0.01f)
        assertNull(CulinaryScaling.convertVolume(10f, "UNKNOWN", "ML"))
        assertNull(CulinaryScaling.convertVolume(10f, "ML", "UNKNOWN"))
    }

    @Test
    fun scalingDownPortionsPreservesSeasoningCharacter() {
        val baseQuantity = 10f
        // Scaling down from 4 portions to 2 portions (ratio = 0.5)
        val scaledSeasoning = CulinaryScaling.scaleQuantity(
            baseQuantity = baseQuantity,
            basePortions = 4,
            targetPortions = 2,
            isAromaticOrSeasoning = true,
        )
        val scaledBulk = CulinaryScaling.scaleQuantity(
            baseQuantity = baseQuantity,
            basePortions = 4,
            targetPortions = 2,
            isAromaticOrSeasoning = false,
        )
        // Bulk scales to 5.0 (50%)
        assertEquals(5.0f, scaledBulk, 0.01f)
        // Seasoning sub-linear power curve: 10 * 0.5^0.75 ≈ 5.95 (greater than linear 5.0)
        assertTrue(scaledSeasoning > scaledBulk)
        assertEquals(5.95f, scaledSeasoning, 0.05f)
    }
}
