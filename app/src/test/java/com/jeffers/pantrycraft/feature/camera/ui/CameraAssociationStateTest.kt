package com.jeffers.pantrycraft.feature.camera.ui

import android.view.View
import com.jeffers.pantrycraft.data.CustomBarcodeDao
import com.jeffers.pantrycraft.data.CustomBarcodeItem
import com.jeffers.pantrycraft.data.GroceryItem
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.PantryRepository
import com.jeffers.pantrycraft.data.Repository
import com.jeffers.pantrycraft.feature.camera.barcode.OfflineBarcodeResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CameraAssociationStateTest {

    private val testDispatcher = StandardTestDispatcher()
    private val scope = CoroutineScope(testDispatcher)

    @Test
    fun saveAssociatedBarcodeWithPositiveShelfLifeComputesExpirationAndSaves() = runTest(testDispatcher) {
        val fakeRepo = FakeAssociationPantryRepo()
        val repo = createStubRepo(fakeRepo)
        val dummyView = createStubView()
        val resolver = OfflineBarcodeResolver(FakeAssociationBarcodeDao())

        var statusMessage: String? = null
        var dismissed = false

        val state = CameraAssociationState(
            barcode = "123456789012",
            resolver = resolver,
            repository = repo,
            scope = scope,
            view = dummyView,
            onStatus = { statusMessage = it },
            onDismiss = { dismissed = true },
        )

        val args = AssociatedBarcodeArgs(
            name = "Curry Powder",
            category = "SPICES",
            unit = "JAR",
            storageLocation = "PANTRY",
            shelfLifeDays = 30,
            currentTimeMillis = 1_000_000_000L,
        )

        saveAssociatedBarcode(state, args)

        assertEquals(1, fakeRepo.insertedItems.size)
        val item = fakeRepo.insertedItems.first()
        assertEquals("Curry Powder", item.name)
        assertEquals("123456789012", item.barcode)
        assertEquals("JAR", item.unit)
        assertEquals("SPICES", item.category)
        assertTrue(item.isAromaticOrSeasoning)
        assertEquals(1_000_000_000L + 30 * 86_400_000L, item.expirationTimestamp)
        assertEquals("Saved and remembered Curry Powder", statusMessage)
        assertTrue(dismissed)
    }

    @Test
    fun saveAssociatedBarcodeWithZeroShelfLifeLeavesExpirationNull() = runTest(testDispatcher) {
        val fakeRepo = FakeAssociationPantryRepo()
        val repo = createStubRepo(fakeRepo)
        val dummyView = createStubView()
        val resolver = OfflineBarcodeResolver(FakeAssociationBarcodeDao())

        var statusMessage: String? = null
        var dismissed = false

        val state = CameraAssociationState(
            barcode = "987654321098",
            resolver = resolver,
            repository = repo,
            scope = scope,
            view = dummyView,
            onStatus = { statusMessage = it },
            onDismiss = { dismissed = true },
        )

        val args = AssociatedBarcodeArgs(
            name = "Fresh Milk",
            category = "DAIRY",
            unit = "BOTTLE",
            storageLocation = "FRIDGE",
            shelfLifeDays = 0,
            currentTimeMillis = 2_000_000_000L,
        )

        saveAssociatedBarcode(state, args)

        assertEquals(1, fakeRepo.insertedItems.size)
        val item = fakeRepo.insertedItems.first()
        assertNull(item.expirationTimestamp)
        assertFalse(item.isAromaticOrSeasoning)
        assertEquals("Saved and remembered Fresh Milk", statusMessage)
        assertTrue(dismissed)
    }

    @Test
    fun cameraAssociationStateDataClassContract() {
        val repo = createStubRepo(FakeAssociationPantryRepo())
        val dummyView = createStubView()
        val resolver = OfflineBarcodeResolver(FakeAssociationBarcodeDao())

        val state1 = CameraAssociationState(
            barcode = "123456789012",
            resolver = resolver,
            repository = repo,
            scope = scope,
            view = dummyView,
            onStatus = {},
            onDismiss = {},
        )

        assertEquals("123456789012", state1.component1())
        assertEquals(resolver, state1.component2())
        assertEquals(repo, state1.component3())
        assertEquals(scope, state1.component4())
        assertEquals(dummyView, state1.component5())
        assertNotNull(state1.component6())
        assertNotNull(state1.component7())

        assertEquals("123456789012", state1.barcode)
        assertEquals(resolver, state1.resolver)
        assertEquals(repo, state1.repository)
        assertEquals(scope, state1.scope)
        assertEquals(dummyView, state1.view)
        assertNotNull(state1.onStatus)
        assertNotNull(state1.onDismiss)

        val state2 = state1.copy(barcode = "999999999999")
        assertNotEquals(state1, state2)
        assertNotNull(state1.toString())
    }

    @Test
    fun associatedBarcodeArgsDataClassContract() {
        val args1 = AssociatedBarcodeArgs(
            name = "Salt",
            category = "SPICES",
            unit = "BOX",
            storageLocation = "PANTRY",
            shelfLifeDays = 365,
        )

        assertEquals("Salt", args1.component1())
        assertEquals("SPICES", args1.component2())
        assertEquals("BOX", args1.component3())
        assertEquals("PANTRY", args1.component4())
        assertEquals(365, args1.component5())
        assertTrue(args1.component6() > 0L)

        val args2 = args1.copy(name = "Pepper")
        assertNotEquals(args1, args2)
        assertNotNull(args1.toString())
    }

    private fun createStubRepo(pantryRepo: PantryRepository): Repository {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe").apply {
            isAccessible = true
        }
        val unsafe = theUnsafeField.get(null)
        val allocateMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
        val repo = allocateMethod.invoke(unsafe, Repository::class.java) as Repository

        for (field in Repository::class.java.declaredFields) {
            if (!java.lang.reflect.Modifier.isStatic(field.modifiers) &&
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
        val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe").apply {
            isAccessible = true
        }
        val unsafe = theUnsafeField.get(null)
        val allocateMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
        return allocateMethod.invoke(unsafe, View::class.java) as View
    }

    private class FakeAssociationBarcodeDao : CustomBarcodeDao {
        private val items = mutableMapOf<String, CustomBarcodeItem>()
        override suspend fun findByBarcode(barcode: String) = items[barcode]
        override suspend fun upsert(item: CustomBarcodeItem) { items[item.barcode] = item }
        override suspend fun delete(item: CustomBarcodeItem) { items.remove(item.barcode) }
        override suspend fun deleteByBarcodePrefix(prefix: String) = 0
    }

    private class FakeAssociationPantryRepo : PantryRepository {
        val insertedItems = mutableListOf<PantryItem>()
        private val pantryFlow = MutableStateFlow<List<PantryItem>>(emptyList())
        private val groceryFlow = MutableStateFlow<List<GroceryItem>>(emptyList())

        override suspend fun insertPantry(item: PantryItem): Long {
            insertedItems.add(item)
            return insertedItems.size.toLong()
        }

        override fun observePantryItems(): Flow<List<PantryItem>> = pantryFlow
        override fun searchPantryItems(query: String): Flow<List<PantryItem>> = pantryFlow
        override suspend fun updatePantry(item: PantryItem) { /* No-op */ }
        override suspend fun decrementQuantity(item: PantryItem, delta: Float) { /* No-op */ }
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
