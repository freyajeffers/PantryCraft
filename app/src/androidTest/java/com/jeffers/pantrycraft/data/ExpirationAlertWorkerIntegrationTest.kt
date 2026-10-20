package com.jeffers.pantrycraft.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExpirationAlertWorkerIntegrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var originalEnabled: Boolean = false
    private val testPrefix = "expiration-worker-test-"

    @Before
    fun setUp() {
        originalEnabled = ExpirationAlertWorker.isEnabled(context)
        ExpirationAlertWorker.setEnabled(context, false)
        runBlocking {
            AppDatabase.getDatabase(context).pantryDao().deleteByNamePrefix(testPrefix)
        }
    }

    @After
    fun tearDown() {
        ExpirationAlertWorker.setEnabled(context, originalEnabled)
        runBlocking {
            AppDatabase.getDatabase(context).pantryDao().deleteByNamePrefix(testPrefix)
        }
    }

    @Test
    fun doWorkReturnsSuccessWhenDisabled() = runBlocking {
        ExpirationAlertWorker.setEnabled(context, false)
        val worker = TestListenableWorkerBuilder<ExpirationAlertWorker>(context).build()
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun doWorkSucceedsWhenEnabledWithExpiringItem() = runBlocking {
        ExpirationAlertWorker.setEnabled(context, true)
        val repository = Repository.getInstance(context)
        val testItem = PantryItem(
            name = "${testPrefix}milk",
            quantity = 1f,
            storageLocation = "FRIDGE",
            category = "DAIRY",
            expirationTimestamp = System.currentTimeMillis() + 3600_000L, // 1 hour from now
        )
        repository.insertPantry(testItem)

        val worker = TestListenableWorkerBuilder<ExpirationAlertWorker>(context).build()
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun initialDelayIsWithinTwentyFourHours() {
        val delay = ExpirationAlertWorker.calculateInitialDelayToNineAmMillis()
        assertTrue(delay >= 0L)
        assertTrue(delay <= 24L * 60L * 60L * 1000L)
    }

    @Test
    fun setEnabledEnqueuesAndCancelsWork() {
        ExpirationAlertWorker.setEnabled(context, true)

        val enqueuedWork =
            WorkManager
                .getInstance(context)
                .getWorkInfosForUniqueWork("pantrycraft_expiration_alerts")
                .get()

        assertFalse(enqueuedWork.isEmpty())
        assertEquals(WorkInfo.State.ENQUEUED, enqueuedWork.first().state)

        ExpirationAlertWorker.setEnabled(context, false)

        val cancelledWork =
            WorkManager
                .getInstance(context)
                .getWorkInfosForUniqueWork("pantrycraft_expiration_alerts")
                .get()

        assertTrue(
            cancelledWork.first().state == WorkInfo.State.CANCELLED ||
                cancelledWork.first().state == WorkInfo.State.FAILED,
        )
    }
}
