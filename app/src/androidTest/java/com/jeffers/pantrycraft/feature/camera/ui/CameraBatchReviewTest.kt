package com.jeffers.pantrycraft.feature.camera.ui

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.pantrycraft.data.AppDatabase
import com.jeffers.pantrycraft.data.Repository
import com.jeffers.pantrycraft.feature.camera.vision.NormalizedBox
import com.jeffers.pantrycraft.feature.camera.vision.ParsedVisionItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraBatchReviewTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repository: Repository
    private val prefix = "batch-rev-${System.nanoTime()}-"

    @Before
    fun setUp() {
        repository = Repository.getInstance(context)
        runBlocking { cleanupTestData() }
    }

    @After
    fun tearDown() {
        runBlocking { cleanupTestData() }
    }

    private suspend fun cleanupTestData() {
        val db = AppDatabase.getDatabase(context)
        db.pantryDao().deleteByNamePrefix(prefix)
    }

    @Test
    fun confirmBatchReviewItemsInsertsPantryBatchAndUpdatesState() = runBlocking {
        var statusMessage: String? = null
        var dismissed = false

        val item1 = ParsedVisionItem(
            name = "${prefix}Paprika",
            category = "SPICES",
            storageLocation = "PANTRY",
            quantity = 2f,
            unit = "JAR",
            confidence = 0.95f,
            estimatedShelfLifeDays = 180,
            isAromaticOrSeasoning = true,
            boundingBox = NormalizedBox(0f, 0f, 1f, 1f),
            conditionNotes = null,
        )

        val item2 = ParsedVisionItem(
            name = "${prefix}Oat Milk",
            category = "BEVERAGES",
            storageLocation = "FRIDGE",
            quantity = 1f,
            unit = "CARTON",
            confidence = 0.90f,
            estimatedShelfLifeDays = 14,
            isAromaticOrSeasoning = false,
            boundingBox = NormalizedBox(0f, 0f, 1f, 1f),
            conditionNotes = null,
        )

        val state = CameraBatchReviewState(
            items = listOf(item1, item2),
            repository = repository,
            scope = CoroutineScope(Dispatchers.Main.immediate),
            view = View(context),
            onDismiss = { dismissed = true },
            onStatus = { statusMessage = it },
        )

        confirmBatchReviewItems(state, listOf(item1, item2))

        assertTrue(dismissed)
        assertEquals("Added 2 items to your pantry", statusMessage)

        val insertedItems = repository.observePantryItems().first().filter { it.name.startsWith(prefix) }
        assertEquals(2, insertedItems.size)

        val paprika = insertedItems.first { it.name == "${prefix}Paprika" }
        assertEquals(2f, paprika.quantity)
        assertEquals("JAR", paprika.unit)
        assertEquals("SPICES", paprika.category)
        assertTrue(paprika.isAromaticOrSeasoning)

        val oatMilk = insertedItems.first { it.name == "${prefix}Oat Milk" }
        assertEquals(1f, oatMilk.quantity)
        assertEquals("CARTON", oatMilk.unit)
        assertEquals("BEVERAGES", oatMilk.category)
        assertEquals(false, oatMilk.isAromaticOrSeasoning)
    }

    @Test
    fun confirmBatchReviewItemsWithEmptyList() = runBlocking {
        var statusMessage: String? = null
        var dismissed = false

        val state = CameraBatchReviewState(
            items = emptyList(),
            repository = repository,
            scope = CoroutineScope(Dispatchers.Main.immediate),
            view = View(context),
            onDismiss = { dismissed = true },
            onStatus = { statusMessage = it },
        )

        confirmBatchReviewItems(state, emptyList())

        assertTrue(dismissed)
        assertEquals("Added 0 items to your pantry", statusMessage)
    }
}
