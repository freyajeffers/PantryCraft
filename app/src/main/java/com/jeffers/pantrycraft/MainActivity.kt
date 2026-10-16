package com.jeffers.pantrycraft

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import com.jeffers.pantrycraft.data.AutomaticBackupScheduler
import com.jeffers.pantrycraft.data.ExpirationAlertScheduler
import com.jeffers.pantrycraft.data.ExpirationAlertWorker
import com.jeffers.pantrycraft.ui.PantryCraftApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AutomaticBackupScheduler.scheduleIfEnabled(this)
        ExpirationAlertScheduler.scheduleIfEnabled(this)
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ExpirationAlertWorker.isEnabled(this) &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST_CODE)
        }
        setContent {
            PantryCraftApp()
        }
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 4102
    }
}
