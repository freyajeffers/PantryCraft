package com.jeffers.pantrycraft.feature.camera.ui

import java.math.BigDecimal

internal fun formatQuantity(quantity: Float): String =
    BigDecimal(quantity.toString())
        .stripTrailingZeros()
        .toPlainString()
