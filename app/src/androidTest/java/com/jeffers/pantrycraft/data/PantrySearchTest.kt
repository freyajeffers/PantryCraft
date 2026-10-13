package com.jeffers.pantrycraft.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
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
class PantrySearchTest {
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
    fun searchMatchesNameNotesAndCategory() {
        runBlocking {
            database.pantryDao().insert(
                PantryItem(
                    name = "Whole Milk",
                    category = "DAIRY",
                    notes = "For cereal",
                ),
            )
            database.pantryDao().insert(
                PantryItem(
                    name = "Rice",
                    category = "STAPLE",
                ),
            )

            assertEquals("Whole Milk", findSingle("milk").name)
            assertEquals("Whole Milk", findSingle("cereal").name)
            assertEquals("Rice", findSingle("staple").name)
        }
    }

    private suspend fun findSingle(term: String) = findFlow(term).first().single()

    private fun findFlow(term: String) = database.pantryDao().search(searchQuery(term))

    private fun searchQuery(term: String) =
        SimpleSQLiteQuery(
            "SELECT pantry_items.* FROM pantry_items " +
                "JOIN PantryItemFts ON pantry_items.rowid = PantryItemFts.rowid " +
                "WHERE PantryItemFts MATCH ? ORDER BY pantry_items.name COLLATE NOCASE",
            arrayOf(term),
        )
}
