package com.jeffers.pantrycraft.feature.meals.deduction

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
import org.junit.Test

class MealCookHandlerTest {

    @Test
    fun executeCookMealDeductsPantryAndInsertsGrocery() = runBlocking {
        val pantryItem = PantryItem(id = 100L, name = "Rice", quantity = 500f, unit = "g")
        val fakePantry = FakePantryRepository(listOf(pantryItem))
        val fakeGrocery = FakeGroceryRepository()

        val recipe = MealRecipe(
            recipeId = "rec_1",
            title = "Rice Bowl",
            itemsUsedFromStock = listOf(
                RecipePantryUsage(name = "Rice", pantryItemId = 100L, quantityUsed = 150f, preventsWaste = true),
            ),
            missingItemsForGrocery = listOf(
                RecipeMissingItem(name = "Sesame Seeds", quantityNeeded = 1f, unit = "JAR"),
            ),
        )

        val result = MealCookHandler.executeCookMeal(
            recipe = recipe,
            pantryRepository = fakePantry,
            groceryRepository = fakeGrocery,
            addMissingToGrocery = true,
        )

        assertEquals(1, result.itemsDeductedCount)
        assertEquals(1, result.foodWastePreventedCount)
        assertEquals(1, result.groceryItemsAddedCount)
        assertEquals(150f, fakePantry.lastDecrementedDelta)
        assertEquals("Sesame Seeds", fakeGrocery.insertedItems.first().name)
    }

    private class FakePantryRepository(initial: List<PantryItem>) : PantryRepository {
        val flow = MutableStateFlow(initial)
        var lastDecrementedDelta: Float? = null

        override fun observePantryItems(): Flow<List<PantryItem>> = flow
        override fun searchPantryItems(query: String): Flow<List<PantryItem>> = flow
        override suspend fun insertPantry(item: PantryItem): Long = 1L
        override suspend fun updatePantry(item: PantryItem) { /* no-op for test */ }
        override suspend fun decrementQuantity(item: PantryItem, delta: Float) {
            lastDecrementedDelta = delta
        }
        override suspend fun deletePantry(item: PantryItem) { /* no-op for test */ }
        override suspend fun restorePantry(item: PantryItem) { /* no-op for test */ }
        override fun observeGroceryItems(): Flow<List<GroceryItem>> = MutableStateFlow(emptyList())
        override suspend fun insertGrocery(item: GroceryItem): Long = 1L
        override suspend fun updateGrocery(item: GroceryItem) { /* no-op for test */ }
        override suspend fun deleteGrocery(item: GroceryItem) { /* no-op for test */ }
        override suspend fun clearCheckedGrocery() { /* no-op for test */ }
        override suspend fun restockCheckedGroceryToPantry(): Int = 0
        override fun reclaimTemporaryMedia(): Int = 0
    }

    private class FakeGroceryRepository : GroceryRepository {
        val insertedItems = mutableListOf<GroceryItem>()
        override fun observeGroceryItems(): Flow<List<GroceryItem>> = MutableStateFlow(emptyList())
        override suspend fun insertGrocery(item: GroceryItem): Long {
            insertedItems.add(item)
            return insertedItems.size.toLong()
        }
        override suspend fun updateGrocery(item: GroceryItem) { /* no-op for test */ }
        override suspend fun deleteGrocery(item: GroceryItem) { /* no-op for test */ }
        override suspend fun clearCheckedGrocery() { /* no-op for test */ }
        override suspend fun restockCheckedGroceryToPantry(): Int = 0
        override fun reclaimTemporaryMedia(): Int = 0
    }
}
