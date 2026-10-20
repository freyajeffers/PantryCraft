package com.jeffers.pantrycraft.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedRoomConcurrencyTest {
    private lateinit var context: Context
    private lateinit var repository: Repository
    private val marker = "concurrency-wal-${System.nanoTime()}"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = Repository.getInstance(context)
        runBlocking {
            AppDatabase.getDatabase(context).pantryDao().deleteByNamePrefix("concurrency-wal-")
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            AppDatabase.getDatabase(context).pantryDao().deleteByNamePrefix("concurrency-wal-")
        }
    }




    @Test
    fun concurrentWritesAndReadsUnderWalModeExecuteWithoutDatabaseLocks() = runBlocking(Dispatchers.IO) {
        val concurrentOperations = 10

        // Launch concurrent insertions and reads simultaneously on Dispatchers.IO
        val tasks = (0 until concurrentOperations).map { index ->
            async {
                val itemName = "$marker-item-$index"
                repository.insertPantry(
                    PantryItem(
                        name = itemName,
                        quantity = (index + 1).toFloat(),
                        storageLocation = "PANTRY",
                    )
                )

                // Interleaved search while other coroutines are actively inserting
                repository.searchPantryItems(marker).first()
            }
        }

        tasks.awaitAll()

        val savedItems = repository.observePantryItems().first().filter { it.name.startsWith(marker) }
        assertEquals(concurrentOperations, savedItems.size)
    }

    @Test
    fun concurrentDecrementOperationsSafelyUpdateSingleItem() = runBlocking(Dispatchers.IO) {
        val itemName = "$marker-decrement-target"
        val itemId = repository.insertPantry(
            PantryItem(
                name = itemName,
                quantity = 100f,
                storageLocation = "PANTRY",
            ),
        )
        val inserted = repository.observePantryItems().first().single { it.id == itemId }

        val decrementCount = 10
        val decrementDelta = 5f
        val tasks = (1..decrementCount).map {
            async {
                repository.decrementQuantity(inserted, decrementDelta)
            }
        }
        tasks.awaitAll()

        val finalItem = repository.observePantryItems().first().single { it.id == itemId }
        assertEquals(50f, finalItem.quantity)
    }
}
