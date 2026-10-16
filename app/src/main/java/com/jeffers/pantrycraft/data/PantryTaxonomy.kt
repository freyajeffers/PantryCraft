package com.jeffers.pantrycraft.data

object PantryTaxonomy {
    val categories =
        listOf(
            "OTHER",
            "PRODUCE",
            "DAIRY",
            "MEAT",
            "SEAFOOD",
            "BAKERY",
            "GRAINS",
            "CANNED",
            "PANTRY_STAPLE",
            "FROZEN",
            "SNACKS",
            "BEVERAGES",
            "CONDIMENTS",
            "SPICES",
        )

    val units =
        listOf("COUNT", "EACH", "G", "KG", "ML", "L", "OZ", "LB", "PACKAGE", "CAN", "BOTTLE")

    val integerQuantityUnits = setOf("COUNT", "EACH", "PACKAGE", "CAN", "BOTTLE")

    val storageLocations = listOf("PANTRY", "FRIDGE", "FREEZER", "COUNTER")

    val shelfLifeOptionsDays = listOf(0, 3, 7, 14)

    val shelfLifePresetDays = listOf(0, 3, 7, 14, 30, 60, 90, 180, 365)

    fun isAromaticOrSeasoningCategory(category: String): Boolean =
        category.equals("SPICES", ignoreCase = true) || category.equals("CONDIMENTS", ignoreCase = true)
}
