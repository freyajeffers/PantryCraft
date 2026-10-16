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
    override suspend fun doWork(): Result {
        if (!AutomaticBackupPreferences.isEnabled(applicationContext)) return Result.success()
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
            val hasUserData = hasPantryData || hasGroceryData
            if (hasUserData) {
                AppDatabase.checkpointForBackup(applicationContext)
                val checksum = DatabaseBackupManager.dbChecksum(applicationContext)
                val last = DatabaseBackupManager.lastDbChecksum(applicationContext)
                if (checksum != null && checksum == last) {
                    // no changes since last backup; skip
                } else {
                    val treeUri = DatabaseBackupManager.persistedAutoBackupTreeUri(applicationContext)
                    val backupName = DatabaseBackupManager.createEncryptedBackup(applicationContext, pin)
                    if (treeUri != null) {
                        val tree = DocumentFile.fromTreeUri(applicationContext, treeUri)
                        if (tree == null) {
                            throw IllegalStateException("Automatic backup location is unavailable")
                        }
                        val destination =
                            tree.createFile("application/octet-stream", backupName)
                                ?: error("Unable to create automatic backup file")
                        DatabaseBackupManager.exportBackup(applicationContext, backupName, destination.uri)
                    }
                    DatabaseBackupManager.saveLastDbChecksum(applicationContext, checksum)
                }
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        } finally {
            pin.fill('\u0000')
        }
    }
}

object AutomaticBackupScheduler {
    private const val WORK_NAME = "pantrycraft_automatic_backup"

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
        val interval = intervalMinutes.coerceAtLeast(15L)
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
