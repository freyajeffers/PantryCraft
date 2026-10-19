package com.jeffers.pantrycraft.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomBarcodeDaoIntegrationTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var customBarcodeDao: CustomBarcodeDao
    private val barcode = "custom-test-barcode-${System.nanoTime()}"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        customBarcodeDao = database.customBarcodeDao()
    }

    @After
    fun tearDown() {
        database.close()
    }



    @Test
    fun findByBarcodeReturnsNullWhenNotCached() = runBlocking {
        val result = customBarcodeDao.findByBarcode("non-existent-$barcode")
        assertNull(result)
    }

    @Test
    fun upsertAndRetrieveCustomBarcodeItem() = runBlocking {
        val item = CustomBarcodeItem(
            barcode = barcode,
            name = "Specialty Olive Oil",
            category = "CONDIMENTS",
            defaultUnit = "BOTTLE",
            estimatedShelfLifeDays = 180,
            lastScanned = 1_000_000L,
        )

        customBarcodeDao.upsert(item)

        val retrieved = customBarcodeDao.findByBarcode(barcode)
        assertNotNull(retrieved)
        assertEquals(barcode, retrieved!!.barcode)
        assertEquals("Specialty Olive Oil", retrieved.name)
        assertEquals("CONDIMENTS", retrieved.category)
        assertEquals("BOTTLE", retrieved.defaultUnit)
        assertEquals(180, retrieved.estimatedShelfLifeDays)
        assertEquals(1_000_000L, retrieved.lastScanned)
    }

    @Test
    fun upsertReplacesExistingBarcodeWithUpdatedValues() = runBlocking {
        val initial = CustomBarcodeItem(
            barcode = barcode,
            name = "Initial Item",
            category = "OTHER",
        )
        customBarcodeDao.upsert(initial)

        val updated = CustomBarcodeItem(
            barcode = barcode,
            name = "Updated Item Name",
            category = "SPICES",
            estimatedShelfLifeDays = 90,
        )
        customBarcodeDao.upsert(updated)

        val result = customBarcodeDao.findByBarcode(barcode)
        assertNotNull(result)
        assertEquals("Updated Item Name", result!!.name)
        assertEquals("SPICES", result.category)
        assertEquals(90, result.estimatedShelfLifeDays)
    }
}
