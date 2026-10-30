package com.jeffers.pantrycraft.feature.meals.deduction

import com.jeffers.pantrycraft.data.CulinaryScaling
import com.jeffers.pantrycraft.data.GroceryItem
import com.jeffers.pantrycraft.data.GroceryRepository
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.PantryRepository
import com.jeffers.pantrycraft.feature.meals.model.MealRecipe
import com.jeffers.pantrycraft.feature.meals.model.RecipeMissingItem
import com.jeffers.pantrycraft.feature.meals.model.RecipePantryUsage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class MealCookHandlerChallengerTest {
    @Test
    fun volumeConversionsAcrossAllStandardPairs() {
        val volumeUnits =
            listOf(
                "TSP" to 5f,
                "TBSP" to 15f,
                "FL_OZ" to 30f,
                "CUP" to 240f,
                "ML" to 1f,
                "L" to 1000f,
            )

        for ((fromUnit, fromFactor) in volumeUnits) {
            for ((toUnit, toFactor) in volumeUnits) {
                val inputQty = 2.5f
                val expected = (inputQty * fromFactor) / toFactor
                val actual = CulinaryScaling.convertVolume(inputQty, fromUnit, toUnit)
                org.junit.Assert.assertNotNull("Conversion from $fromUnit to $toUnit must not be null", actual)
                assertEquals("Failed converting $inputQty $fromUnit to $toUnit", expected, actual ?: 0f, 0.005f)
            }
        }
    }

    @Test
    fun volumeConversionsWithVariousAliasesAndFormatting() {
        val aliases =
            listOf(
                "teaspoon" to 5f,
                "teaspoons" to 5f,
                "tsps" to 5f,
                "t" to 5f,
                "t." to 5f,
                "tablespoon" to 15f,
                "tablespoons" to 15f,
                "tbsps" to 15f,
                "tb" to 15f,
                "tb." to 15f,
                "fl oz" to 30f,
                "fl_oz" to 30f,
                "floz" to 30f,
                "fluid ounce" to 30f,
                "fluid ounces" to 30f,
                "FL. OZ." to 30f,
                "  fl oz  " to 30f,
                "cup" to 240f,
                "cups" to 240f,
                "c" to 240f,
                "c." to 240f,
                "milliliter" to 1f,
                "milliliters" to 1f,
                "millilitre" to 1f,
                "millilitres" to 1f,
                "mls" to 1f,
                "liter" to 1000f,
                "liters" to 1000f,
                "litre" to 1000f,
                "litres" to 1000f,
            )

        for ((alias, factor) in aliases) {
            val converted = CulinaryScaling.convertVolume(1f, alias, "ML")
            org.junit.Assert.assertNotNull("Alias '$alias' should convert to ML", converted)
            assertEquals("Alias '$alias' converted to ML mismatch", factor, converted ?: 0f, 0.001f)
        }
    }

    @Test
    fun incompatibleUnitsReturnNullInConvertVolume() {
        val incompatiblePairs =
            listOf(
                "g" to "ml",
                "kg" to "l",
                "oz" to "fl_oz",
                "lb" to "cup",
                "count" to "tbsp",
                "pieces" to "cup",
                "clove" to "tsp",
                "can" to "ml",
                "handful" to "ml",
                "pinch" to "tsp",
                "dash" to "ml",
                "" to "cup",
                "   " to "l",
                "unknown" to "tsp",
            )

        for ((fromUnit, toUnit) in incompatiblePairs) {
            val result = CulinaryScaling.convertVolume(10f, fromUnit, toUnit)
            assertNull("Expected null conversion from '$fromUnit' to '$toUnit'", result)
        }
    }

    @Test
    fun incompatibleUnitsFallbackSafelyInCookDeduction() =
        runBlocking {
            val flour = PantryItem(id = 1L, name = "Flour", quantity = 500f, unit = "g")
            val eggs = PantryItem(id = 2L, name = "Eggs", quantity = 12f, unit = "count")
            val salt = PantryItem(id = 3L, name = "Salt", quantity = 100f, unit = null)
            val sugar = PantryItem(id = 4L, name = "Sugar", quantity = 100f, unit = "cup")
            val pantry = ChallengerFakePantryRepository(listOf(flour, eggs, salt, sugar))

            val recipe =
                MealRecipe(
                    recipeId = "rec_fallback",
                    title = "Fallback Recipe",
                    itemsUsedFromStock =
                        listOf(
                            RecipePantryUsage(name = "Flour", pantryItemId = 1L, quantityUsed = 200f, unit = "ml"),
                            RecipePantryUsage(name = "Eggs", pantryItemId = 2L, quantityUsed = 2f, unit = "tbsp"),
                            RecipePantryUsage(name = "Salt", pantryItemId = 3L, quantityUsed = 5f, unit = "tsp"),
                            RecipePantryUsage(name = "Sugar", pantryItemId = 4L, quantityUsed = 1f, unit = null),
                        ),
                )

            val result = MealCookHandler.executeCookMeal(recipe, pantry, ChallengerFakeGroceryRepository())

            assertEquals(4, result.itemsDeductedCount)
            assertEquals(300f, pantry.getItem("Flour")?.quantity ?: 0f, 0.01f)
            assertEquals(10f, pantry.getItem("Eggs")?.quantity ?: 0f, 0.01f)
            assertEquals(95f, pantry.getItem("Salt")?.quantity ?: 0f, 0.01f)
            assertEquals(99f, pantry.getItem("Sugar")?.quantity ?: 0f, 0.01f)
        }

    @Test
    fun zeroQuantityUsageDoesNotDeductAndDoesNotThrow() =
        runBlocking {
            val pepper = PantryItem(id = 1L, name = "Pepper", quantity = 50f, unit = "g")
            val pantry = ChallengerFakePantryRepository(listOf(pepper))

            val recipe =
                MealRecipe(
                    recipeId = "rec_zero",
                    title = "Zero Quantity Recipe",
                    itemsUsedFromStock =
                        listOf(
                            RecipePantryUsage(name = "Pepper", pantryItemId = 1L, quantityUsed = 0f, unit = "g"),
                        ),
                )

            val result = MealCookHandler.executeCookMeal(recipe, pantry, ChallengerFakeGroceryRepository())

            assertEquals(0, result.itemsDeductedCount)
            assertEquals(50f, pantry.getItem("Pepper")?.quantity ?: 0f, 0.01f)
        }

    @Test
    fun negativeQuantityUsageDoesNotDeductAndDoesNotThrow() =
        runBlocking {
            val pepper = PantryItem(id = 1L, name = "Pepper", quantity = 50f, unit = "g")
            val pantry = ChallengerFakePantryRepository(listOf(pepper))

            val recipe =
                MealRecipe(
                    recipeId = "rec_neg",
                    title = "Negative Quantity Recipe",
                    itemsUsedFromStock =
                        listOf(
                            RecipePantryUsage(name = "Pepper", pantryItemId = 1L, quantityUsed = -10f, unit = "g"),
                        ),
                )

            val result = MealCookHandler.executeCookMeal(recipe, pantry, ChallengerFakeGroceryRepository())

            assertEquals(0, result.itemsDeductedCount)
            assertEquals(50f, pantry.getItem("Pepper")?.quantity ?: 0f, 0.01f)
        }

    @Test
    fun nanAndInfiniteQuantityUsageDoesNotDeductAndDoesNotThrow() =
        runBlocking {
            val milk = PantryItem(id = 1L, name = "Milk", quantity = 1000f, unit = "ML")
            val pantry = ChallengerFakePantryRepository(listOf(milk))

            val recipe =
                MealRecipe(
                    recipeId = "rec_nan_inf",
                    title = "NaN and Infinity Recipe",
                    itemsUsedFromStock =
                        listOf(
                            RecipePantryUsage(
                                name = "Milk",
                                pantryItemId = 1L,
                                quantityUsed = Float.NaN,
                                unit = "ML",
                            ),
                            RecipePantryUsage(
                                name = "Milk",
                                pantryItemId = 1L,
                                quantityUsed = Float.POSITIVE_INFINITY,
                                unit = "ML",
                            ),
                            RecipePantryUsage(
                                name = "Milk",
                                pantryItemId = 1L,
                                quantityUsed = Float.NEGATIVE_INFINITY,
                                unit = "ML",
                            ),
                        ),
                )

            val result = MealCookHandler.executeCookMeal(recipe, pantry, ChallengerFakeGroceryRepository())

            assertEquals(0, result.itemsDeductedCount)
            assertEquals(1000f, pantry.getItem("Milk")?.quantity ?: 0f, 0.01f)
        }

    @Test
    fun culinaryScalingValidatesQuantitiesAndPortions() {
        assertEquals(0f, CulinaryScaling.scaleQuantity(0f, 2, 4, false), 0.001f)
        assertEquals(0f, CulinaryScaling.scaleQuantity(0f, 2, 4, true), 0.001f)

        assertThrows(IllegalArgumentException::class.java) {
            CulinaryScaling.scaleQuantity(-1f, 2, 4, false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CulinaryScaling.scaleQuantity(10f, 0, 4, false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CulinaryScaling.scaleQuantity(10f, 2, 0, false)
        }

        assertEquals(0f, CulinaryScaling.convertVolume(0f, "CUP", "ML") ?: -1f, 0.001f)
        assertNull(CulinaryScaling.convertVolume(-5f, "CUP", "ML"))
        assertNull(CulinaryScaling.convertVolume(Float.NaN, "CUP", "ML"))
        assertNull(CulinaryScaling.convertVolume(Float.POSITIVE_INFINITY, "CUP", "ML"))
        assertNull(CulinaryScaling.convertVolume(Float.NEGATIVE_INFINITY, "CUP", "ML"))
    }

    @Test
    fun blankOrMissingUsageNameHandling() =
        runBlocking {
            val cumin = PantryItem(id = 10L, name = "Cumin", quantity = 30f, unit = "g")
            val pantry = ChallengerFakePantryRepository(listOf(cumin))

            val recipe =
                MealRecipe(
                    recipeId = "rec_blank_names",
                    title = "Blank Names Recipe",
                    itemsUsedFromStock =
                        listOf(
                            RecipePantryUsage(name = "", pantryItemId = null, quantityUsed = 5f),
                            RecipePantryUsage(name = "   ", pantryItemId = null, quantityUsed = 5f),
                            RecipePantryUsage(name = "", pantryItemId = 10L, quantityUsed = 5f),
                            RecipePantryUsage(name = "  Cumin  ", pantryItemId = null, quantityUsed = 5f),
                        ),
                )

            val result = MealCookHandler.executeCookMeal(recipe, pantry, ChallengerFakeGroceryRepository())

            // Usage 1 and 2 skipped; usage 3 matched by ID; usage 4 matched by trimmed name
            assertEquals(2, result.itemsDeductedCount)
            assertEquals(20f, pantry.getItem("Cumin")?.quantity ?: 0f, 0.01f)
        }

    @Test
    fun blankOrMissingGroceryNameHandling() =
        runBlocking {
            val grocery = ChallengerFakeGroceryRepository()
            val recipe =
                MealRecipe(
                    recipeId = "rec_blank_grocery",
                    title = "Blank Grocery Recipe",
                    missingItemsForGrocery =
                        listOf(
                            RecipeMissingItem(name = "", quantityNeeded = 1f),
                            RecipeMissingItem(name = "   ", quantityNeeded = 2f),
                            RecipeMissingItem(name = "  Cilantro  ", quantityNeeded = 1f, unit = "BUNCH"),
                        ),
                )

            val result =
                MealCookHandler.executeCookMeal(
                    recipe = recipe,
                    pantryRepository = ChallengerFakePantryRepository(),
                    groceryRepository = grocery,
                    addMissingToGrocery = true,
                )

            assertEquals(1, result.groceryItemsAddedCount)
            assertEquals(1, grocery.items.size)
            assertEquals("Cilantro", grocery.items.first().name)
            org.junit.Assert.assertFalse(grocery.items.first().checked)
        }

    @Test
    fun softDeletedPantryItemsAreNeverDeducted() =
        runBlocking {
            val butter = PantryItem(id = 20L, name = "Butter", quantity = 200f, unit = "g", isDeleted = true)
            val pantry = ChallengerFakePantryRepository(listOf(butter))

            val recipeWithId =
                MealRecipe(
                    recipeId = "rec_deleted_id",
                    title = "Deleted Item By ID",
                    itemsUsedFromStock =
                        listOf(
                            RecipePantryUsage(name = "Butter", pantryItemId = 20L, quantityUsed = 50f),
                        ),
                )
            val result1 = MealCookHandler.executeCookMeal(recipeWithId, pantry, ChallengerFakeGroceryRepository())
            assertEquals(0, result1.itemsDeductedCount)
            assertEquals(200f, pantry.getItem("Butter")?.quantity ?: 0f, 0.01f)

            val recipeByName =
                MealRecipe(
                    recipeId = "rec_deleted_name",
                    title = "Deleted Item By Name",
                    itemsUsedFromStock =
                        listOf(
                            RecipePantryUsage(name = "Butter", pantryItemId = null, quantityUsed = 50f),
                        ),
                )
            val result2 = MealCookHandler.executeCookMeal(recipeByName, pantry, ChallengerFakeGroceryRepository())
            assertEquals(0, result2.itemsDeductedCount)
            assertEquals(200f, pantry.getItem("Butter")?.quantity ?: 0f, 0.01f)
        }

    @Test
    fun seasoningPowerCurveFormulaStrictlyEnforced() {
        val baseSaltGrams = 10f
        val scaled4 =
            CulinaryScaling.scaleQuantity(
                baseQuantity = baseSaltGrams,
                basePortions = 2,
                targetPortions = 4,
                isAromaticOrSeasoning = true,
            )
        val expected4 = (10.0 * 2.0.pow(0.75)).toFloat()
        assertEquals(expected4, scaled4, 0.05f)

        val bulk4 =
            CulinaryScaling.scaleQuantity(
                baseQuantity = baseSaltGrams,
                basePortions = 2,
                targetPortions = 4,
                isAromaticOrSeasoning = false,
            )
        assertEquals(20f, bulk4, 0.01f)
        assertTrue("Aromatic scaled quantity must be less than linear bulk scaling", scaled4 < bulk4)

        // Palate exponent clamping
        val clampedBelow =
            CulinaryScaling.scaleQuantity(
                baseQuantity = 10f,
                basePortions = 1,
                targetPortions = 4,
                isAromaticOrSeasoning = true,
                exponent = 0.50,
            )
        val expectedMin = (10.0 * 4.0.pow(0.65)).toFloat()
        assertEquals(expectedMin, clampedBelow, 0.05f)

        val clampedAbove =
            CulinaryScaling.scaleQuantity(
                baseQuantity = 10f,
                basePortions = 1,
                targetPortions = 4,
                isAromaticOrSeasoning = true,
                exponent = 1.20,
            )
        val expectedMax = (10.0 * 4.0.pow(0.85)).toFloat()
        assertEquals(expectedMax, clampedAbove, 0.05f)
    }
}

private class ChallengerFakePantryRepository(
    initial: List<PantryItem> = emptyList(),
) : PantryRepository {
    private val items = initial.map { it.copy() }.toMutableList()
    val flow = MutableStateFlow<List<PantryItem>>(items.toList())

    fun getItem(name: String): PantryItem? = items.firstOrNull { it.name.equals(name, ignoreCase = true) }

    override fun observePantryItems(): Flow<List<PantryItem>> = flow

    override fun searchPantryItems(query: String): Flow<List<PantryItem>> = flow

    override suspend fun insertPantry(item: PantryItem): Long = 1L

    override suspend fun updatePantry(item: PantryItem) { /* no-op */ }

    override suspend fun decrementQuantity(
        item: PantryItem,
        delta: Float,
    ) {
        require(delta > 0f && delta.isFinite()) {
            "Quantity decrement must be finite and positive"
        }
        val index = items.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            val existing = items[index]
            val clamped = (existing.quantity - delta).coerceAtLeast(0f)
            items[index] = existing.copy(quantity = clamped)
            flow.value = items.toList()
        }
    }

    override suspend fun deletePantry(item: PantryItem) { /* no-op */ }

    override suspend fun restorePantry(item: PantryItem) { /* no-op */ }

    override fun observeGroceryItems(): Flow<List<GroceryItem>> = MutableStateFlow(emptyList())

    override suspend fun insertGrocery(item: GroceryItem): Long = 1L

    override suspend fun updateGrocery(item: GroceryItem) { /* no-op */ }

    override suspend fun deleteGrocery(item: GroceryItem) { /* no-op */ }

    override suspend fun clearCheckedGrocery() { /* no-op */ }

    override suspend fun restockCheckedGroceryToPantry(): Int = 0

    override fun reclaimTemporaryMedia(): Int = 0
}

private class ChallengerFakeGroceryRepository : GroceryRepository {
    val items = mutableListOf<GroceryItem>()

    override fun observeGroceryItems(): Flow<List<GroceryItem>> = MutableStateFlow(items.toList())

    override suspend fun insertGrocery(item: GroceryItem): Long {
        items.add(item)
        return items.size.toLong()
    }

    override suspend fun updateGrocery(item: GroceryItem) { /* no-op */ }

    override suspend fun deleteGrocery(item: GroceryItem) { /* no-op */ }

    override suspend fun clearCheckedGrocery() { /* no-op */ }

    override suspend fun restockCheckedGroceryToPantry(): Int = 0

    override fun reclaimTemporaryMedia(): Int = 0
}
