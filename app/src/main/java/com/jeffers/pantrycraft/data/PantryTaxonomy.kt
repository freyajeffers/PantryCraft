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
        listOf("COUNT", "EACH", "G", "KG", "ML", "L", "OZ", "LB", "PACKAGE", "CAN", "BOTTLE", "CUSTOM")

    val integerQuantityUnits = setOf("COUNT", "EACH", "PACKAGE", "CAN", "BOTTLE")

    val storageLocations = listOf("PANTRY", "FRIDGE", "FREEZER", "COUNTER")

    val shelfLifeOptionsDays = listOf(NO_SHELF_LIFE, THREE_DAYS, ONE_WEEK, TWO_WEEKS)

    val shelfLifePresetDays =
        listOf(
            NO_SHELF_LIFE,
            THREE_DAYS,
            ONE_WEEK,
            TWO_WEEKS,
            ONE_MONTH,
            TWO_MONTHS,
            THREE_MONTHS,
            SIX_MONTHS,
            ONE_YEAR,
        )

    fun isAromaticOrSeasoningCategory(category: String): Boolean =
        category.equals("SPICES", ignoreCase = true) || category.equals("CONDIMENTS", ignoreCase = true)

    fun openedShelfLifeDays(category: String): Int =
        when (category.uppercase()) {
            "MEAT", "SEAFOOD" -> THREE_DAYS
            "DAIRY", "PRODUCE", "BAKERY", "BEVERAGES", "CANNED" -> ONE_WEEK
            "CONDIMENTS", "SNACKS" -> TWO_WEEKS
            "FROZEN" -> ONE_MONTH
            "SPICES", "GRAINS", "PANTRY_STAPLE" -> THREE_MONTHS
            else -> TWO_WEEKS
        }

    private const val NO_SHELF_LIFE = 0
    private const val THREE_DAYS = 3
    private const val ONE_WEEK = 7
    private const val TWO_WEEKS = 14
    private const val ONE_MONTH = 30
    private const val TWO_MONTHS = 60
    private const val THREE_MONTHS = 90
    private const val SIX_MONTHS = 180
    private const val ONE_YEAR = 365
}
