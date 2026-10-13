package com.jeffers.pantrycraft.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
}
