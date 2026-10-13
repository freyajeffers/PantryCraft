package com.jeffers.pantrycraft.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    @Test
    fun repositoryPantryAndGroceryContracts() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val repository = Repository.getInstance(context)
            val marker = "repository-${System.nanoTime()}"
            val item =
                PantryItem(
                    name = "  $marker  ",
                    quantity = 2f,
                    threshold = 2f,
                    barcode = "barcode-$marker",
                    notes = "searchable note",
                )

            val id = repository.insertPantry(item)
            val observed = repository.observePantryItems().first().single { it.id == id }
            assertEquals(marker, observed.normalizedName)
            assertEquals("$marker", observed.name.trim())
            assertEquals(observed, repository.findPantryItemByBarcode(item.barcode!!))
            assertTrue(repository.observeLowStock().first().any { it.id == id })
            assertTrue(repository.getAvailableIngredientsForMeals().any { it.id == id })
            assertEquals(1, repository.searchPantryItems(marker).first().count { it.id == id })
            assertEquals(
                1,
                repository.searchPantryItems("$marker searchable").first().count { it.id == id },
            )
            assertEquals(1, repository.searchPantryItems("\"$marker\"").first().count { it.id == id })
            assertEquals(1, repository.searchPantryItems("searchable note").first().count { it.id == id })
            assertEquals(1, repository.searchPantryItems("  ").first().count { it.id == id })

            repository.decrementQuantity(observed, 5f)
            val depleted = repository.observePantryItems().first().single { it.id == id }
            assertEquals(0f, depleted.quantity)
            repository.updatePantry(observed.copy(quantity = 4f))
            val updated = repository.observePantryItems().first().single { it.id == id }
            assertEquals(4f, updated.quantity)
            repository.deletePantry(observed)
            assertFalse(repository.observePantryItems().first().any { it.id == id })
            assertEquals(null, repository.findPantryItemByBarcode(item.barcode!!))
            repository.restorePantry(observed)
            assertTrue(repository.observePantryItems().first().any { it.id == id })

            val grocery = GroceryItem(name = marker)
            val groceryId = repository.insertGrocery(grocery)
            assertTrue(repository.observeGroceryItems().first().any { it.id == groceryId })
            repository.updateGrocery(grocery.copy(id = groceryId, checked = true))
            val deletedGroceryId = repository.insertGrocery(GroceryItem(name = "$marker-delete"))
            repository.deleteGrocery(grocery.copy(id = deletedGroceryId))
            repository.clearCheckedGrocery()
            assertFalse(repository.observeGroceryItems().first().any { it.id == groceryId })

            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repository.insertPantry(item.copy(name = "   ")) }
            }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repository.insertPantry(item.copy(quantity = -1f)) }
            }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repository.insertPantry(item.copy(threshold = -1f)) }
            }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repository.insertPantry(item.copy(storageLocation = "INVALID")) }
            }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repository.decrementQuantity(observed, 0f) }
            }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repository.decrementQuantity(observed, Float.NaN) }
            }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repository.decrementQuantity(observed, Float.POSITIVE_INFINITY) }
            }

            val scratch = context.cacheDir.resolve("vision_scratch").apply { mkdirs() }
            scratch.resolve("temporary-frame").writeText("ephemeral")
            assertEquals(1, repository.reclaimTemporaryMedia())
            assertEquals(0, repository.reclaimTemporaryMedia())
        }
}
