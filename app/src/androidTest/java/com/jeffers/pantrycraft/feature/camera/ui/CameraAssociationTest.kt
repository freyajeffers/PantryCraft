package com.jeffers.pantrycraft.feature.camera.ui

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.pantrycraft.data.AppDatabase
import com.jeffers.pantrycraft.data.Repository
import com.jeffers.pantrycraft.feature.camera.barcode.OfflineBarcodeResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraAssociationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repository: Repository
    private lateinit var resolver: OfflineBarcodeResolver
    private val prefix = "assoc-test-${System.nanoTime()}-"
    private val testBarcode1 = "123456789012"
    private val testBarcode2 = "987654321098"

    @Before
    fun setUp() {
        repository = Repository.getInstance(context)
        val db = AppDatabase.getDatabase(context)
        resolver = OfflineBarcodeResolver(db.customBarcodeDao())
        runBlocking { cleanupTestData() }
    }

    @After
    fun tearDown() {
        runBlocking { cleanupTestData() }
    }

    private suspend fun cleanupTestData() {
        val db = AppDatabase.getDatabase(context)
        db.pantryDao().deleteByNamePrefix(prefix)
        db.customBarcodeDao().deleteByBarcodePrefix(testBarcode1)
        db.customBarcodeDao().deleteByBarcodePrefix(testBarcode2)
    }

    @Test
    fun saveAssociatedBarcodeWithAromaticCategoryAndPositiveShelfLife() = runBlocking {
        var statusMessage: String? = null
        var dismissed = false

        val state = CameraAssociationState(
            barcode = testBarcode1,
            resolver = resolver,
            repository = repository,
            scope = CoroutineScope(Dispatchers.Main.immediate),
            view = View(context),
            onStatus = { statusMessage = it },
            onDismiss = { dismissed = true },
        )

        val fixedTime = 1_700_000_000_000L
        val shelfLife = 90
        val args = AssociatedBarcodeArgs(
            name = "${prefix}Basil",
            category = "SPICES",
            unit = "BOTTLE",
            storageLocation = "PANTRY",
            shelfLifeDays = shelfLife,
            currentTimeMillis = fixedTime,
        )

        saveAssociatedBarcode(state, args)

        assertEquals("Saved and remembered ${prefix}Basil", statusMessage)
        assertTrue(dismissed)

        // Verify resolver remembers the barcode
        val resolved = resolver.resolveBarcode(testBarcode1)
        assertNotNull(resolved)
        assertEquals("${prefix}Basil", resolved?.name)
        assertEquals("SPICES", resolved?.category)
        assertEquals("BOTTLE", resolved?.unit)
        assertEquals(shelfLife, resolved?.estimatedShelfLifeDays)

        // Verify pantry item is inserted with aromatic seasoning tagged and expiration calculated
        val pantryItems = repository.observePantryItems().first().filter { it.name.startsWith(prefix) }
        assertEquals(1, pantryItems.size)
        val inserted = pantryItems.first()
        assertEquals("${prefix}Basil", inserted.name)
        assertEquals(testBarcode1, inserted.barcode)
        assertEquals(1f, inserted.quantity)
        assertEquals("BOTTLE", inserted.unit)
        assertEquals("SPICES", inserted.category)
        assertEquals("PANTRY", inserted.storageLocation)
        assertTrue("SPICES category must be tagged as aromatic/seasoning", inserted.isAromaticOrSeasoning)
        assertEquals(fixedTime + shelfLife * 86_400_000L, inserted.expirationTimestamp)
    }

    @Test
    fun saveAssociatedBarcodeWithZeroShelfLifeAndNonAromaticCategory() = runBlocking {
        var statusMessage: String? = null
        var dismissed = false

        val state = CameraAssociationState(
            barcode = testBarcode2,
            resolver = resolver,
            repository = repository,
            scope = CoroutineScope(Dispatchers.Main.immediate),
            view = View(context),
            onStatus = { statusMessage = it },
            onDismiss = { dismissed = true },
        )

        val args = AssociatedBarcodeArgs(
            name = "${prefix}Pasta",
            category = "GRAINS",
            unit = "BOX",
            storageLocation = "PANTRY",
            shelfLifeDays = 0,
            currentTimeMillis = 1_700_000_000_000L,
        )

        saveAssociatedBarcode(state, args)

        assertEquals("Saved and remembered ${prefix}Pasta", statusMessage)
        assertTrue(dismissed)

        val pantryItems = repository.observePantryItems().first().filter { it.name.startsWith(prefix) }
        assertEquals(1, pantryItems.size)
        val inserted = pantryItems.first()
        assertEquals("${prefix}Pasta", inserted.name)
        assertEquals(testBarcode2, inserted.barcode)
        assertEquals(1f, inserted.quantity)
        assertEquals(false, inserted.isAromaticOrSeasoning)
        assertNull("Shelf life <= 0 must result in null expirationTimestamp", inserted.expirationTimestamp)
    }
}
