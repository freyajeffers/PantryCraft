package com.jeffers.pantrycraft.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class PantryMutationDaoUnitTest {
    private class TestPantryMutationDao : PantryMutationDao {
        var softDeleteId: Long? = null
        var softDeleteTime: Long? = null
        var restoreId: Long? = null
        var restoreTime: Long? = null
        var decrementId: Long? = null
        var decrementDelta: Float? = null
        var decrementTime: Long? = null

        override suspend fun softDelete(
            id: Long,
            updatedAt: Long,
        ) {
            softDeleteId = id
            softDeleteTime = updatedAt
        }

        override suspend fun restore(
            id: Long,
            updatedAt: Long,
        ) {
            restoreId = id
            restoreTime = updatedAt
        }

        override suspend fun insert(item: PantryItem): Long = 1L

        override suspend fun insertBatch(items: List<PantryItem>): List<Long> = items.map { 1L }

        override suspend fun update(item: PantryItem) { /* no-op */ }

        override suspend fun decrementQuantity(
            id: Long,
            delta: Float,
            updatedAt: Long,
        ) {
            decrementId = id
            decrementDelta = delta
            decrementTime = updatedAt
        }

        override suspend fun delete(item: PantryItem) { /* no-op */ }

        override suspend fun deleteByNamePrefix(prefix: String): Int = 0

        override suspend fun deleteByBarcodePrefix(prefix: String): Int = 0
    }

    private val noOpContinuation =
        object : Continuation<Unit> {
            override val context: CoroutineContext = EmptyCoroutineContext

            override fun resumeWith(result: Result<Unit>) { /* no-op */ }
        }

    @Test
    fun defaultParametersInvokeCurrentTimeMillis() =
        runBlocking {
            val dao: PantryMutationDao = TestPantryMutationDao()
            val before = System.currentTimeMillis()

            dao.softDelete(42L)
            val impl = dao as TestPantryMutationDao
            assertEquals(42L, impl.softDeleteId)
            assertTrue(impl.softDeleteTime!! >= before)

            dao.restore(84L)
            assertEquals(84L, impl.restoreId)
            assertTrue(impl.restoreTime!! >= before)

            dao.decrementQuantity(100L, 2.5f)
            assertEquals(100L, impl.decrementId)
            assertEquals(2.5f, impl.decrementDelta)
            assertTrue(impl.decrementTime!! >= before)
        }

    @Test
    fun explicitParametersPassThroughDirectly() =
        runBlocking {
            val dao: PantryMutationDao = TestPantryMutationDao()
            dao.softDelete(1L, 9999L)
            val impl = dao as TestPantryMutationDao
            assertEquals(1L, impl.softDeleteId)
            assertEquals(9999L, impl.softDeleteTime)

            dao.restore(2L, 8888L)
            assertEquals(2L, impl.restoreId)
            assertEquals(8888L, impl.restoreTime)

            dao.decrementQuantity(3L, 4.0f, 7777L)
            assertEquals(3L, impl.decrementId)
            assertEquals(4.0f, impl.decrementDelta)
            assertEquals(7777L, impl.decrementTime)
        }

    @Test
    fun defaultImplsBridgeDelegatesToInterfaceDefaults() {
        val dao = TestPantryMutationDao()
        val defaultImpls = Class.forName("com.jeffers.pantrycraft.data.PantryMutationDao\$DefaultImpls")
        val softDelete =
            defaultImpls.getMethod(
                "softDelete\$default",
                PantryMutationDao::class.java,
                Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                Continuation::class.java,
                Int::class.javaPrimitiveType,
                Any::class.java,
            )
        softDelete.invoke(null, dao, 55L, 0L, noOpContinuation, 2, null)
        assertEquals(55L, dao.softDeleteId)

        val restore =
            defaultImpls.getMethod(
                "restore\$default",
                PantryMutationDao::class.java,
                Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                Continuation::class.java,
                Int::class.javaPrimitiveType,
                Any::class.java,
            )
        restore.invoke(null, dao, 77L, 0L, noOpContinuation, 2, null)
        assertEquals(77L, dao.restoreId)

        val decrement =
            defaultImpls.getMethod(
                "decrementQuantity\$default",
                PantryMutationDao::class.java,
                Long::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                Continuation::class.java,
                Int::class.javaPrimitiveType,
                Any::class.java,
            )
        decrement.invoke(null, dao, 88L, 3.5f, 0L, noOpContinuation, 4, null)
        assertEquals(88L, dao.decrementId)
    }
}
