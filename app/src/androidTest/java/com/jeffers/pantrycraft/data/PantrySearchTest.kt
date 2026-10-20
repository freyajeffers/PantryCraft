package com.jeffers.pantrycraft.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
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
        database =
            Room
                .databaseBuilder(
                    context,
                    AppDatabase::class.java,
                    "pantry-search-test.db",
                ).openHelperFactory(
                    SupportOpenHelperFactory(DatabaseKeyProvider.getKey(context)),
                ).build()
        AppDatabase.configureSearchIndex(database.openHelper.writableDatabase)
    }

    @After
    fun tearDown() {
        database.close()
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase("pantry-search-test.db")
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

            database.pantryDao().insert(
                PantryItem(
                    name = "Deleted Milk",
                    category = "DAIRY",
                    isDeleted = true,
                ),
            )

            assertEquals("Whole Milk", findSingle("milk").name)
            assertEquals("Whole Milk", findSingle("cereal").name)
            assertEquals("Rice", findSingle("staple").name)
        }
    }

    @Test
    fun searchIgnoresSoftDeletedItemsEvenWhenMatched() = runBlocking {
        database.pantryDao().insert(
            PantryItem(
                name = "Active Almonds",
                category = "SNACKS",
                isDeleted = false,
            ),
        )
        database.pantryDao().insert(
            PantryItem(
                name = "Deleted Almonds",
                category = "SNACKS",
                isDeleted = true,
            ),
        )

        val results = findFlow("almonds").first()
        assertEquals(1, results.size)
        assertEquals("Active Almonds", results.single().name)
    }

    @Test
    fun searchPrefixWithWildcardMatchesItemPrefix() = runBlocking {
        database.pantryDao().insert(
            PantryItem(
                name = "Cardamom Pods",
                category = "SPICES",
            ),
        )

        val results = findFlow("card*").first()
        assertEquals(1, results.size)
        assertEquals("Cardamom Pods", results.single().name)
    }

    private suspend fun findSingle(term: String) = findFlow(term).first().single()

    private fun findFlow(term: String) = database.pantryDao().search(searchQuery(term))

    private fun searchQuery(term: String) =
        SimpleSQLiteQuery(
            "SELECT pantry_items.* FROM pantry_items " +
                "JOIN pantry_items_fts ON pantry_items.rowid = pantry_items_fts.rowid " +
                "WHERE pantry_items.isDeleted = 0 AND pantry_items_fts MATCH ? " +
                "ORDER BY pantry_items.name COLLATE NOCASE",
            arrayOf(term),
        )
}
