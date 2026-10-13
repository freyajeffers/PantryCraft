package com.jeffers.pantrycraft.data

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureNanoTime

@RunWith(AndroidJUnit4::class)
class PantrySearchBenchmarkTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .databaseBuilder(context, AppDatabase::class.java, "pantry-search-benchmark.db")
                .openHelperFactory(SupportOpenHelperFactory(DatabaseKeyProvider.getKey(context)))
                .build()
        AppDatabase.configureSearchIndex(database.openHelper.writableDatabase)
        runBlocking {
            database.withTransaction {
                repeat(5_000) { index ->
                    database.pantryDao().insert(
                        PantryItem(
                            name = "Benchmark item $index unique$index",
                            notes = "synthetic benchmark record",
                            category = "BENCHMARK",
                        ),
                    )
                }
            }
        }
    }

    @After
    fun tearDown() {
        database.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("pantry-search-benchmark.db")
    }

    @Test
    fun prefixSearchAcross5000ItemsStaysUnderFiveMilliseconds() {
        val query =
            SimpleSQLiteQuery(
                "SELECT pantry_items.* FROM pantry_items " +
                    "JOIN pantry_items_fts ON pantry_items.rowid = pantry_items_fts.rowid " +
                    "WHERE pantry_items.isDeleted = 0 AND pantry_items_fts MATCH ? " +
                    "ORDER BY pantry_items.name COLLATE NOCASE",
                arrayOf("\"unique4999\"*"),
            )

        val writableDatabase = database.openHelper.writableDatabase

        fun executeSearch() {
            writableDatabase.query(query).use { cursor ->
                while (cursor.moveToNext()) {
                    cursor.getLong(0)
                }
            }
        }
        executeSearch()
        val elapsedMillis =
            (1..3).minOf {
                measureNanoTime { executeSearch() }
            } / 1_000_000.0
        Log.i("PantrySearchBenchmark", "5000-item prefix search: ${elapsedMillis}ms")
        assertTrue("Search took ${elapsedMillis}ms", elapsedMillis < 5.0)
    }
}
