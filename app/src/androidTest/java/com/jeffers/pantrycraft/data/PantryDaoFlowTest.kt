package com.jeffers.pantrycraft.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PantryDaoFlowTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun pantryFlowEmitsAfterInsertAndUpdate() =
        runBlocking {
            val item = PantryItem(name = "Milk", quantity = 1f)
            val initial = database.pantryDao().observeAllPantryItems().first()
            assertEquals(emptyList<PantryItem>(), initial)

            val id = database.pantryDao().insert(item)
            val inserted = database.pantryDao().observeAllPantryItems().first()
            assertEquals("Milk", inserted.single().name)
            assertEquals(id, inserted.single().id)

            database.pantryDao().update(inserted.single().copy(quantity = 2f))
            val updated = database.pantryDao().observeAllPantryItems().first()
            assertEquals(2f, updated.single().quantity)
        }

    @Test
    fun observeLowStockEmitsOnlyItemsAtOrBelowThresholdAndExcludesDeleted() = runBlocking {
        val dao = database.pantryDao()
        dao.insert(PantryItem(name = "Flour", quantity = 1f, threshold = 2f))
        dao.insert(PantryItem(name = "Sugar", quantity = 2f, threshold = 2f))
        dao.insert(PantryItem(name = "Rice", quantity = 5f, threshold = 2f))
        val deletedId = dao.insert(PantryItem(name = "Oats", quantity = 0.5f, threshold = 2f))
        dao.softDelete(deletedId)

        val lowStock = dao.observeLowStock().first()
        assertEquals(2, lowStock.size)
        assertEquals("Flour", lowStock[0].name)
        assertEquals("Sugar", lowStock[1].name)
    }

    @Test
    fun getAvailableIngredientsOrdersByExpirationAndExcludesZeroQuantityAndDeleted() = runBlocking {
        val dao = database.pantryDao()
        dao.insert(PantryItem(name = "Apple", quantity = 2f, expirationTimestamp = 2000L))
        dao.insert(PantryItem(name = "Banana", quantity = 1f, expirationTimestamp = 1000L))
        dao.insert(PantryItem(name = "Carrot", quantity = 3f, expirationTimestamp = null))
        dao.insert(PantryItem(name = "Beans", quantity = 2f, expirationTimestamp = null))
        dao.insert(PantryItem(name = "Donut", quantity = 0f, expirationTimestamp = 500L))
        val deletedId = dao.insert(PantryItem(name = "Egg", quantity = 1f, expirationTimestamp = 800L))
        dao.softDelete(deletedId)

        val available = dao.getAvailableIngredients()
        assertEquals(4, available.size)
        assertEquals("Banana", available[0].name)
        assertEquals(1000L, available[0].expirationTimestamp)
        assertEquals("Apple", available[1].name)
        assertEquals(2000L, available[1].expirationTimestamp)
        assertEquals("Beans", available[2].name)
        assertNull(available[2].expirationTimestamp)
        assertEquals("Carrot", available[3].name)
        assertNull(available[3].expirationTimestamp)
    }

    @Test
    fun insertBatchInsertsMultipleItemsAtomically() = runBlocking {
        val dao = database.pantryDao()
        val items = listOf(
            PantryItem(name = "Item 1", quantity = 1f),
            PantryItem(name = "Item 2", quantity = 2f),
            PantryItem(name = "Item 3", quantity = 3f),
        )
        val ids = dao.insertBatch(items)
        assertEquals(3, ids.size)
        assertEquals(3, dao.observeAllPantryItems().first().size)
    }

    @Test
    fun decrementQuantityClampsToZero() = runBlocking {
        val dao = database.pantryDao()
        val id = dao.insert(PantryItem(name = "Olive Oil", quantity = 2.5f))

        dao.decrementQuantity(id, delta = 1.0f)
        val afterFirstDec = dao.observeAllPantryItems().first().single { it.id == id }
        assertEquals(1.5f, afterFirstDec.quantity)

        dao.decrementQuantity(id, delta = 5.0f)
        val afterSecondDec = dao.observeAllPantryItems().first().single { it.id == id }
        assertEquals(0f, afterSecondDec.quantity)
    }
}
