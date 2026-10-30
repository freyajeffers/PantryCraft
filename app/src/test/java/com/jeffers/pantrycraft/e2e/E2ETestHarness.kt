package com.jeffers.pantrycraft.e2e

import com.jeffers.pantrycraft.data.CustomBarcodeDao
import com.jeffers.pantrycraft.data.CustomBarcodeItem
import com.jeffers.pantrycraft.data.GroceryItem
import com.jeffers.pantrycraft.data.PantryItem
import com.jeffers.pantrycraft.data.PantryRepository
import com.jeffers.pantrycraft.data.reclaimScratchMedia
import com.jeffers.pantrycraft.data.sanitizeSearchQuery
import com.jeffers.pantrycraft.data.validatePantryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.TreeMap
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory stateful repository test double modeling SQLCipher Room database semantics,
 * including auto-increment IDs, soft deletion, zero-floor decrements, and grocery restock.
 */
class InMemoryPantryRepository(
    initialPantry: List<PantryItem> = emptyList(),
    initialGrocery: List<GroceryItem> = emptyList(),
    private val scratchDir: File? = null,
) : PantryRepository {
    private val nextPantryId = AtomicLong(100L)
    private val nextGroceryId = AtomicLong(500L)

    private val pantryItems = mutableListOf<PantryItem>()
    private val groceryItems = mutableListOf<GroceryItem>()

    private val pantryFlowMutable = MutableStateFlow<List<PantryItem>>(emptyList())
    private val groceryFlowMutable = MutableStateFlow<List<GroceryItem>>(emptyList())

    /**
     * Prefix-sorted inverted index mapping lowercased token prefixes to item IDs.
     * Modeled after SQLite FTS5 prefix search: O(log n + k) lookup vs O(n) linear scan.
     * Keys are word-tokens extracted from name, normalizedName, and category fields.
     * Built eagerly on each [refreshFlows] call.
     */
    private val prefixIndex: TreeMap<String, MutableSet<Long>> = TreeMap()

    init {
        initialPantry.forEach { item ->
            val id = if (item.id > 0L) item.id else nextPantryId.getAndIncrement()
            pantryItems.add(item.copy(id = id))
        }
        initialGrocery.forEach { item ->
            val id = if (item.id > 0L) item.id else nextGroceryId.getAndIncrement()
            groceryItems.add(item.copy(id = id))
        }
        refreshFlows()
    }

    private fun tokenize(text: String): List<String> = text.lowercase().split(" ", "_", "-").filter { it.length >= MIN_TOKEN_LENGTH }

    private fun indexItem(item: PantryItem) {
        val tokens = tokenize(item.name) + tokenize(item.normalizedName) + tokenize(item.category)
        for (token in tokens) {
            for (prefixLen in MIN_TOKEN_LENGTH..token.length) {
                val prefix = token.substring(0, prefixLen)
                prefixIndex.getOrPut(prefix) { mutableSetOf() }.add(item.id)
            }
        }
    }

    private fun rebuildIndex() {
        prefixIndex.clear()
        pantryItems.filter { !it.isDeleted }.forEach { indexItem(it) }
    }

    private fun refreshFlows() {
        pantryFlowMutable.value = pantryItems.filter { !it.isDeleted }
        groceryFlowMutable.value = groceryItems.toList()
        rebuildIndex()
    }

    override fun observePantryItems(): Flow<List<PantryItem>> = pantryFlowMutable.asStateFlow()

    override fun searchPantryItems(query: String): Flow<List<PantryItem>> {
        val sanitized = sanitizeSearchQuery(query)
        if (sanitized.isBlank()) return observePantryItems()
        val terms = sanitized.lowercase().split(" ").filter { it.isNotBlank() }
        val matchedIds: Set<Long> =
            terms
                .fold(null as Set<Long>?) { acc, term ->
                    val termMatches =
                        prefixIndex
                            .subMap(term, "$term\uFFFF")
                            .values
                            .flatten()
                            .toHashSet()
                    if (acc == null) termMatches else acc.intersect(termMatches)
                }.orEmpty()
        val matched =
            if (matchedIds.isEmpty()) {
                emptyList()
            } else {
                pantryItems.filter { !it.isDeleted && it.id in matchedIds }
            }
        return MutableStateFlow(matched)
    }

    override suspend fun insertPantry(item: PantryItem): Long {
        val validated = validatePantryItem(item)
        val barcode = validated.barcode?.trim()?.takeIf { it.isNotEmpty() }
        if (barcode != null) {
            val existingIndex = pantryItems.indexOfFirst { it.barcode == barcode }
            if (existingIndex >= 0) {
                val existing = pantryItems[existingIndex]
                return if (existing.isDeleted) {
                    val restored =
                        validated.copy(
                            id = existing.id,
                            isDeleted = false,
                            quantity = if (validated.quantity > 0f) validated.quantity else existing.quantity,
                            updatedAt = System.currentTimeMillis(),
                        )
                    pantryItems[existingIndex] = restored
                    refreshFlows()
                    existing.id
                } else {
                    val combined = existing.quantity + validated.quantity
                    val updated =
                        existing.copy(
                            quantity = combined,
                            name = validated.name.ifBlank { existing.name },
                            category = if (validated.category != "OTHER") validated.category else existing.category,
                            storageLocation = validated.storageLocation,
                            expirationTimestamp = validated.expirationTimestamp ?: existing.expirationTimestamp,
                            updatedAt = System.currentTimeMillis(),
                        )
                    pantryItems[existingIndex] = updated
                    refreshFlows()
                    existing.id
                }
            }
        }
        val id = if (validated.id > 0L) validated.id else nextPantryId.getAndIncrement()
        pantryItems.add(validated.copy(id = id))
        refreshFlows()
        return id
    }

    override suspend fun updatePantry(item: PantryItem) {
        val validated = validatePantryItem(item)
        val index = pantryItems.indexOfFirst { it.id == validated.id }
        if (index >= 0) {
            pantryItems[index] = validated.copy(updatedAt = System.currentTimeMillis())
            refreshFlows()
        }
    }

    override suspend fun decrementQuantity(
        item: PantryItem,
        delta: Float,
    ) {
        require(delta > 0f && delta.isFinite()) {
            "Quantity decrement must be finite and positive"
        }
        val index = pantryItems.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            val existing = pantryItems[index]
            val newQty = (existing.quantity - delta).coerceAtLeast(0f)
            pantryItems[index] =
                existing.copy(
                    quantity = newQty,
                    updatedAt = System.currentTimeMillis(),
                )
            refreshFlows()
        }
    }

    override suspend fun deletePantry(item: PantryItem) {
        val index = pantryItems.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            pantryItems[index] =
                pantryItems[index].copy(
                    isDeleted = true,
                    updatedAt = System.currentTimeMillis(),
                )
            refreshFlows()
        }
    }

    override suspend fun restorePantry(item: PantryItem) {
        val index = pantryItems.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            pantryItems[index] =
                pantryItems[index].copy(
                    isDeleted = false,
                    updatedAt = System.currentTimeMillis(),
                )
            refreshFlows()
        }
    }

    override fun observeGroceryItems(): Flow<List<GroceryItem>> = groceryFlowMutable.asStateFlow()

    override suspend fun insertGrocery(item: GroceryItem): Long {
        val id = if (item.id > 0L) item.id else nextGroceryId.getAndIncrement()
        groceryItems.add(item.copy(id = id))
        refreshFlows()
        return id
    }

    override suspend fun updateGrocery(item: GroceryItem) {
        val index = groceryItems.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            groceryItems[index] = item
            refreshFlows()
        }
    }

    override suspend fun deleteGrocery(item: GroceryItem) {
        groceryItems.removeAll { it.id == item.id }
        refreshFlows()
    }

    override suspend fun clearCheckedGrocery() {
        groceryItems.removeAll { it.checked }
        refreshFlows()
    }

    override suspend fun restockCheckedGroceryToPantry(): Int {
        val checkedList = groceryItems.filter { it.checked }
        for (grocery in checkedList) {
            val existingIndex =
                grocery.fromPantryId?.let { pid ->
                    pantryItems.indexOfFirst { it.id == pid }
                } ?: -1

            if (existingIndex >= 0) {
                val existing = pantryItems[existingIndex]
                pantryItems[existingIndex] =
                    existing.copy(
                        quantity = existing.quantity + grocery.quantity,
                        updatedAt = System.currentTimeMillis(),
                    )
            } else {
                val id = nextPantryId.getAndIncrement()
                pantryItems.add(
                    PantryItem(
                        id = id,
                        name = grocery.name,
                        quantity = grocery.quantity,
                        unit = grocery.unit,
                        storageLocation = "PANTRY",
                    ),
                )
            }
        }
        groceryItems.removeAll { it.checked }
        refreshFlows()
        return checkedList.size
    }

    override fun reclaimTemporaryMedia(): Int = scratchDir?.let { reclaimScratchMedia(it) } ?: 0

    fun getAllRawPantry(): List<PantryItem> = pantryItems.toList()

    companion object {
        private const val MIN_TOKEN_LENGTH = 1
    }
}

/**
 * In-memory test double for CustomBarcodeDao.
 */
class InMemoryCustomBarcodeDao : CustomBarcodeDao {
    private val records = mutableMapOf<String, CustomBarcodeItem>()

    override suspend fun findByBarcode(barcode: String): CustomBarcodeItem? = records[barcode]

    override suspend fun upsert(item: CustomBarcodeItem) {
        records[item.barcode] = item
    }

    override suspend fun delete(item: CustomBarcodeItem) {
        records.remove(item.barcode)
    }

    override suspend fun deleteByBarcodePrefix(prefix: String): Int {
        val matched = records.keys.filter { it.startsWith(prefix) }
        matched.forEach { records.remove(it) }
        return matched.size
    }

    fun count(): Int = records.size
}
