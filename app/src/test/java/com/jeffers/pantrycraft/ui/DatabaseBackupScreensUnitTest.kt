package com.jeffers.pantrycraft.ui

import android.content.ContextWrapper
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseBackupScreensUnitTest {

    private class DummyContext : ContextWrapper(null)

    @Test
    fun launchRestoreSuccessNotifiesNullResult() = runTest {
        var callbackResult: String? = "initial"
        val latch = CountDownLatch(1)
        val context = DummyContext()
        launchRestore(
            context = context,
            scope = CoroutineScope(Dispatchers.Default),
            restore = { /* success */ },
            onResult = {
                callbackResult = it
                latch.countDown()
            },
        )
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertNull(callbackResult)
    }

    @Test
    fun launchRestoreFailureNotifiesErrorMessage() = runTest {
        var callbackResult: String? = null
        val latch = CountDownLatch(1)
        val context = DummyContext()
        launchRestore(
            context = context,
            scope = CoroutineScope(Dispatchers.Default),
            restore = { throw IOException("Restore corrupted") },
            onResult = {
                callbackResult = it
                latch.countDown()
            },
        )
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals("Restore corrupted", callbackResult)
    }

    @Test
    fun recoveryContentStateDataClassContract() {
        val onSelect: (String) -> Unit = {}
        val onPin: (String) -> Unit = {}
        val onOpen: () -> Unit = {}
        val onRest: () -> Unit = {}
        val onRes: () -> Unit = {}

        val state1 = RecoveryContentState(
            backups = listOf("backup1.db"),
            selectedBackup = "backup1.db",
            pin = "1234",
            error = null,
            onSelectBackup = onSelect,
            onPinChange = onPin,
            onOpenBackup = onOpen,
            onRestore = onRest,
            onReset = onRes,
        )

        val state2 = state1.copy(pin = "5678")

        assertEquals(listOf("backup1.db"), state1.component1())
        assertEquals("backup1.db", state1.component2())
        assertEquals("1234", state1.component3())
        assertNull(state1.component4())
        assertEquals(onSelect, state1.component5())
        assertEquals(onPin, state1.component6())
        assertEquals(onOpen, state1.component7())
        assertEquals(onRest, state1.component8())
        assertEquals(onRes, state1.component9())

        assertEquals(state1, state1)
        assertNotEquals(state1, state2)
        assertNotNull(state1.toString())
        assertTrue(state1.hashCode() != 0)
    }
}
