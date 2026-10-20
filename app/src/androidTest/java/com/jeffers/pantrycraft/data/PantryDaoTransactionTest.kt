package com.jeffers.pantrycraft.data

import android.content.Context
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PantryDaoTransactionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var pantryDao: PantryDao
    private val prefix = "pantry-tx-test-${System.nanoTime()}-"

    @Before
    fun setUp() {
        db = AppDatabase.getDatabase(context)
        pantryDao = db.pantryDao()
        runBlocking {
            pantryDao.deleteByNamePrefix("pantry-tx-test-")
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            pantryDao.deleteByNamePrefix("pantry-tx-test-")
        }
    }

    @Test
    fun transactionAtomicRollbackLeavesNoOrphanedRows() = runBlocking {
        val itemName = "${prefix}rollback-item"

        try {
            db.withTransaction {
                pantryDao.insert(
                    PantryItem(
                        name = itemName,
                        quantity = 5f,
                        storageLocation = "PANTRY",
                    ),
                )
                // Intentionally fail transaction
                error("Simulated atomic failure")
            }
        } catch (_: IllegalStateException) {
            // Expected failure
        }

        val items = pantryDao.observeAllPantryItems().first().filter { it.name == itemName }
        assertTrue("Item inserted within aborted transaction must be rolled back", items.isEmpty())
    }

    @Test
    fun softDeleteAndRestoreMaintainsHistoryWithinTransaction() = runBlocking {
        val testBarcode = "999${System.nanoTime().toString().takeLast(8)}"
        val id = pantryDao.insert(
            PantryItem(
                name = "${prefix}soft-delete-item",
                quantity = 3f,
                barcode = testBarcode,
                storageLocation = "FRIDGE",
            ),
        )

        // Verify active
        val activeBefore = pantryDao.findByBarcode(testBarcode)
        assertNotNull(activeBefore)
        assertEquals(id, activeBefore?.id)
        assertFalse(activeBefore!!.isDeleted)

        // Soft delete
        pantryDao.softDelete(id)
        val activeAfterDelete = pantryDao.findByBarcode(testBarcode)
        assertNull(activeAfterDelete)

        // Verify row still exists in raw table marked deleted
        val anyAfterDelete = pantryDao.findAnyByBarcode(testBarcode)
        assertNotNull(anyAfterDelete)
        assertTrue(anyAfterDelete!!.isDeleted)

        // Restore
        pantryDao.restore(id)
        val activeAfterRestore = pantryDao.findByBarcode(testBarcode)
        assertNotNull(activeAfterRestore)
        assertFalse(activeAfterRestore!!.isDeleted)
    }
}
