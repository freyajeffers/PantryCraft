package com.jeffers.pantrycraft.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class ExpirationAlertWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        if (!isEnabled(applicationContext)) return Result.success()

        val now = System.currentTimeMillis()
        val alertWindow = now + ALERT_WINDOW_MILLIS
        val expiringItems =
            AppDatabase
                .getDatabase(applicationContext)
                .pantryDao()
                .observeAllPantryItems()
                .first()
                .filter { item ->
                    item.expirationTimestamp != null &&
                        item.expirationTimestamp <= alertWindow &&
                        item.quantity > 0f &&
                        !item.isDeleted
                }.sortedBy { it.expirationTimestamp }

        if (expiringItems.isNotEmpty()) {
            ensureNotificationChannel(applicationContext)
            val summary =
                expiringItems
                    .take(MAX_ITEMS_IN_NOTIFICATION)
                    .joinToString(", ") { it.name }
            val suffix =
                if (expiringItems.size > MAX_ITEMS_IN_NOTIFICATION) {
                    " (+${expiringItems.size - MAX_ITEMS_IN_NOTIFICATION} more)"
                } else {
                    ""
                }
            val notification =
                NotificationCompat
                    .Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Pantry items need attention")
                    .setContentText("$summary$suffix")
                    .setStyle(NotificationCompat.BigTextStyle().bigText("$summary$suffix"))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .build()
            applicationContext
                .getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, notification)
        }
        return Result.success()
    }

    companion object {
        private const val PREFS_NAME = "expiration_alert_preferences"
        private const val ENABLED_KEY = "enabled"
        private const val WORK_NAME = "pantrycraft_expiration_alerts"
        private const val CHANNEL_ID = "pantrycraft_expiration_alerts"
        private const val NOTIFICATION_ID = 4101
        private const val ALERT_WINDOW_MILLIS = 3L * 24L * 60L * 60L * 1000L
        private const val MAX_ITEMS_IN_NOTIFICATION = 4

        fun isEnabled(context: Context): Boolean =
            context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(ENABLED_KEY, false)

        fun setEnabled(
            context: Context,
            enabled: Boolean,
        ) {
            context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(ENABLED_KEY, enabled)
                .apply()
            if (enabled) {
                schedule(context)
            } else {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            }
        }

        fun scheduleIfEnabled(context: Context) {
            if (isEnabled(context)) schedule(context)
        }

        private fun schedule(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<ExpirationAlertWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        private fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel =
                    NotificationChannel(
                        CHANNEL_ID,
                        "Expiration alerts",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    )
                context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            }
        }
    }
}

object ExpirationAlertScheduler {
    fun scheduleIfEnabled(context: Context) {
        ExpirationAlertWorker.scheduleIfEnabled(context)
    }
}
