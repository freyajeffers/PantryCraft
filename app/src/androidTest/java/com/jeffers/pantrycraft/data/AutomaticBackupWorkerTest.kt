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
class AutomaticBackupWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var originalEnabled: Boolean = false
    private var originalInterval: Long = 1440L
    private var originalPin: CharArray? = null

    @Before
    fun setUp() {
        originalEnabled = AutomaticBackupPreferences.isEnabled(context)
        originalInterval = AutomaticBackupPreferences.intervalMinutes(context)
        originalPin = AutomaticBackupPreferences.loadPin(context)
        AutomaticBackupScheduler.cancel(context)
    }

    @After
    fun tearDown() {
        AutomaticBackupPreferences.setSchedule(context, originalEnabled, originalInterval)
        if (originalPin != null) {
            AutomaticBackupPreferences.savePin(context, originalPin!!)
        } else {
            context
                .getSharedPreferences("backup_preferences", Context.MODE_PRIVATE)
                .edit()
                .remove("automatic_backup_pin_ciphertext")
                .remove("automatic_backup_pin_iv")
                .apply()
        }
        if (originalEnabled) {
            AutomaticBackupScheduler.schedule(context, originalInterval)
        } else {
            AutomaticBackupScheduler.cancel(context)
        }
    }


    @Test
    fun doWorkReturnsSuccessWhenDisabled() = runBlocking {
        AutomaticBackupPreferences.setSchedule(context, enabled = false, intervalMinutes = 1440L)
        val worker = TestListenableWorkerBuilder<AutomaticBackupWorker>(context).build()

        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun doWorkReturnsFailureWhenEnabledWithoutPin() = runBlocking {
        AutomaticBackupPreferences.setSchedule(context, enabled = true, intervalMinutes = 1440L)
        val worker = TestListenableWorkerBuilder<AutomaticBackupWorker>(context).build()

        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Failure)
    }

    @Test
    fun doWorkSucceedsWhenEnabledWithPinAndEmptyInventory() = runBlocking {
        AutomaticBackupPreferences.setSchedule(context, enabled = true, intervalMinutes = 1440L)
        val pin = "654321".toCharArray()
        AutomaticBackupPreferences.savePin(context, pin)

        val worker = TestListenableWorkerBuilder<AutomaticBackupWorker>(context).build()
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun schedulerEnqueuesAndCancelsPeriodicWork() {
        AutomaticBackupScheduler.schedule(context, intervalMinutes = 60L)

        val enqueuedWork =
            WorkManager
                .getInstance(context)
                .getWorkInfosForUniqueWork("pantrycraft_automatic_backup")
                .get()

        assertFalse(enqueuedWork.isEmpty())
        assertEquals(WorkInfo.State.ENQUEUED, enqueuedWork.first().state)

        AutomaticBackupScheduler.cancel(context)

        val cancelledWork =
            WorkManager
                .getInstance(context)
                .getWorkInfosForUniqueWork("pantrycraft_automatic_backup")
                .get()

        assertTrue(
            cancelledWork.first().state == WorkInfo.State.CANCELLED ||
                cancelledWork.first().state == WorkInfo.State.FAILED,
        )
    }

    @Test
    fun schedulerScheduleIfEnabledEnqueuesWorkOnlyWhenEnabled() {
        AutomaticBackupPreferences.setSchedule(context, enabled = false, intervalMinutes = 60L)
        AutomaticBackupScheduler.scheduleIfEnabled(context)
        val initialWork = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("pantrycraft_automatic_backup").get()
        assertTrue(initialWork.isEmpty() || initialWork.all { it.state == WorkInfo.State.CANCELLED })

        AutomaticBackupPreferences.setSchedule(context, enabled = true, intervalMinutes = 60L)
        AutomaticBackupScheduler.scheduleIfEnabled(context)
        val enqueuedWork = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("pantrycraft_automatic_backup").get()
        assertFalse(enqueuedWork.isEmpty())
        assertEquals(WorkInfo.State.ENQUEUED, enqueuedWork.first().state)
    }
}
