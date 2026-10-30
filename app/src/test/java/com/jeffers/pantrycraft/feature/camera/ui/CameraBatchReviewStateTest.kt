package com.jeffers.pantrycraft.feature.camera.ui

import android.view.View
import com.jeffers.pantrycraft.data.GroceryItem
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.PantryRepository
import com.jeffers.pantrycraft.data.Repository
import com.jeffers.pantrycraft.feature.camera.vision.NormalizedBox
import com.jeffers.pantrycraft.feature.camera.vision.ParsedVisionItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CameraBatchReviewStateTest {
    private val testDispatcher = StandardTestDispatcher()
    private val scope = CoroutineScope(testDispatcher)

    @Test
    fun confirmBatchReviewItemsSuccessUpdatesStateAndFeedback() =
        runTest(testDispatcher) {
            val fakeRepo = FakeReviewPantryRepo()
            val repo = createStubRepo(fakeRepo)
            val dummyView = createStubView()

            var dismissed = false
            var statusMessage: String? = null

            val state =
                CameraBatchReviewState(
                    items = listOf(sampleParsedItem("Apples", 3f)),
                    repository = repo,
                    scope = scope,
                    view = dummyView,
                    onDismiss = { dismissed = true },
                    onStatus = { statusMessage = it },
                )

            val confirmed =
                listOf(
                    sampleParsedItem("Apples", 3f),
                    sampleParsedItem("Oranges", 2f),
                )

            confirmBatchReviewItems(state, confirmed)

            assertEquals(2, fakeRepo.insertedItems.size)
            assertEquals("Apples", fakeRepo.insertedItems[0].name)
            assertEquals("Oranges", fakeRepo.insertedItems[1].name)
            assertTrue(dismissed)
            assertEquals("Added 2 items to your pantry", statusMessage)
        }

    @Test
    fun confirmBatchReviewItemsFailureNotifiesStatus() =
        runTest(testDispatcher) {
            val fakeRepo = FakeReviewPantryRepo().apply { shouldThrow = true }
            val repo = createStubRepo(fakeRepo)
            val dummyView = createStubView()

            var dismissed = false
            var statusMessage: String? = null

            val state =
                CameraBatchReviewState(
                    items = emptyList(),
                    repository = repo,
                    scope = scope,
                    view = dummyView,
                    onDismiss = { dismissed = true },
                    onStatus = { statusMessage = it },
                )

            val confirmed = listOf(sampleParsedItem("Milk", 1f, "L"))
            confirmBatchReviewItems(state, confirmed)

            assertTrue(!dismissed)
            assertEquals("Could not add items: Database insertion failure", statusMessage)
        }

    @Test
    fun cameraBatchReviewStateDataClassContract() {
        val repo = createStubRepo(FakeReviewPantryRepo())
        val dummyView = createStubView()
        val items = listOf(sampleParsedItem("Eggs", 12f))

        val state1 =
            CameraBatchReviewState(
                items = items,
                repository = repo,
                scope = scope,
                view = dummyView,
                onDismiss = {},
                onStatus = {},
            )

        assertEquals(items, state1.items)
        assertEquals(scope, state1.scope)
        assertEquals(items, state1.component1())
        assertEquals(repo, state1.component2())
        assertEquals(scope, state1.component3())
        assertEquals(dummyView, state1.component4())
        assertNotNull(state1.component5())
        assertNotNull(state1.component6())

        val state2 = state1.copy(items = emptyList())
        assertNotEquals(state1, state2)
        assertNotNull(state1.toString())
    }

    private fun sampleParsedItem(
        name: String,
        quantity: Float,
        unit: String = "pcs",
    ) = ParsedVisionItem(
        name = name,
        category = "PRODUCE",
        storageLocation = "PANTRY",
        quantity = quantity,
        unit = unit,
        confidence = 0.95f,
        estimatedShelfLifeDays = 7,
        isAromaticOrSeasoning = false,
        boundingBox = NormalizedBox(0.1f, 0.1f, 0.9f, 0.9f),
        conditionNotes = null,
    )

    private fun createStubRepo(pantryRepo: PantryRepository): Repository {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafeField =
            unsafeClass.getDeclaredField("theUnsafe").apply {
                isAccessible = true
            }
        val unsafe = theUnsafeField.get(null)
        val allocateMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
        val repo = allocateMethod.invoke(unsafe, Repository::class.java) as Repository

        for (field in Repository::class.java.declaredFields) {
            if (!java.lang.reflect.Modifier
                    .isStatic(field.modifiers) &&
                PantryRepository::class.java.isAssignableFrom(field.type)
            ) {
                field.isAccessible = true
                field.set(repo, pantryRepo)
            }
        }
        return repo
    }

    private fun createStubView(): View {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafeField =
            unsafeClass.getDeclaredField("theUnsafe").apply {
                isAccessible = true
            }
        val unsafe = theUnsafeField.get(null)
        val allocateMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
        return allocateMethod.invoke(unsafe, View::class.java) as View
    }

    private class FakeReviewPantryRepo : PantryRepository {
        var shouldThrow: Boolean = false
        val insertedItems = mutableListOf<PantryItem>()
        private val pantryFlow = MutableStateFlow<List<PantryItem>>(emptyList())
        private val groceryFlow = MutableStateFlow<List<GroceryItem>>(emptyList())

        override suspend fun insertPantry(item: PantryItem): Long {
            if (shouldThrow) error("Database insertion failure")
            insertedItems.add(item)
            return insertedItems.size.toLong()
        }

        override fun observePantryItems(): Flow<List<PantryItem>> = pantryFlow

        override fun searchPantryItems(query: String): Flow<List<PantryItem>> = pantryFlow

        override suspend fun updatePantry(item: PantryItem) { /* No-op */ }

        override suspend fun decrementQuantity(
            item: PantryItem,
            delta: Float,
        ) { /* No-op */ }

        override suspend fun deletePantry(item: PantryItem) { /* No-op */ }

        override suspend fun restorePantry(item: PantryItem) { /* No-op */ }

        override fun observeGroceryItems(): Flow<List<GroceryItem>> = groceryFlow

        override suspend fun insertGrocery(item: GroceryItem): Long = 1L

        override suspend fun updateGrocery(item: GroceryItem) { /* No-op */ }

        override suspend fun deleteGrocery(item: GroceryItem) { /* No-op */ }

        override suspend fun clearCheckedGrocery() { /* No-op */ }

        override suspend fun restockCheckedGroceryToPantry(): Int = 0

        override fun reclaimTemporaryMedia(): Int = 0
    }
}
