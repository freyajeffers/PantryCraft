package com.jeffers.pantrycraft.data

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class AutomaticBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result =
        if (!AutomaticBackupPreferences.isEnabled(applicationContext)) {
            Result.success()
        } else {
            backupWithPin()
        }

    private suspend fun backupWithPin(): Result {
        val pin = AutomaticBackupPreferences.loadPin(applicationContext) ?: return Result.failure()
        return try {
            val database = AppDatabase.getDatabase(applicationContext)
            val hasPantryData = database.pantryDao().getAvailableIngredients().isNotEmpty()
            val hasGroceryData =
                database
                    .groceryDao()
                    .observeAllGroceryItems()
                    .first()
                    .isNotEmpty()
            if (hasPantryData || hasGroceryData) {
                backupIfChanged(pin)
            }
            Result.success()
        } catch (_: IllegalStateException) {
            Result.retry()
        } catch (_: java.io.IOException) {
            Result.retry()
        } finally {
            pin.fill('\u0000')
        }
    }

    private suspend fun backupIfChanged(pin: CharArray) {
        AppDatabase.checkpointForBackup(applicationContext)
        val checksum = DatabaseBackupManager.dbChecksum(applicationContext)
        if (checksum == null || checksum != DatabaseBackupManager.lastDbChecksum(applicationContext)) {
            val treeUri = DatabaseBackupManager.persistedAutoBackupTreeUri(applicationContext)
            val backupName = DatabaseBackupManager.createEncryptedBackup(applicationContext, pin)
            exportToTree(treeUri, backupName)
            DatabaseBackupManager.saveLastDbChecksum(applicationContext, checksum)
        }
    }

    private fun exportToTree(
        treeUri: android.net.Uri?,
        backupName: String,
    ) {
        if (treeUri == null) return
        val tree =
            DocumentFile.fromTreeUri(applicationContext, treeUri)
                ?: error("Automatic backup location is unavailable")
        val destination =
            tree.createFile("application/octet-stream", backupName)
                ?: error("Unable to create automatic backup file")
        DatabaseBackupManager.exportBackup(applicationContext, backupName, destination.uri)
    }
}

object AutomaticBackupScheduler {
    private const val WORK_NAME = "pantrycraft_automatic_backup"
    private const val MIN_INTERVAL_MINUTES = 15L

    fun scheduleIfEnabled(context: Context) {
        if (AutomaticBackupPreferences.isEnabled(context)) {
            schedule(
                context,
                AutomaticBackupPreferences.intervalMinutes(context),
            )
        }
    }

    fun schedule(
        context: Context,
        intervalMinutes: Long,
    ) {
        val interval = intervalMinutes.coerceAtLeast(MIN_INTERVAL_MINUTES)
        val request =
            PeriodicWorkRequestBuilder<AutomaticBackupWorker>(interval, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
