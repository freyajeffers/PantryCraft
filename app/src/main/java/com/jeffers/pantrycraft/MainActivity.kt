package com.jeffers.pantrycraft

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.jeffers.pantrycraft.data.AutomaticBackupScheduler
import com.jeffers.pantrycraft.data.ExpirationAlertScheduler
import com.jeffers.pantrycraft.data.ExpirationAlertWorker
import com.jeffers.pantrycraft.ui.PantryCraftApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        window.decorView.setFilterTouchesWhenObscured(true)
        super.onCreate(savedInstanceState)
        setContent {
            PantryCraftApp()
        }
        window.decorView.post {
            AutomaticBackupScheduler.scheduleIfEnabled(this)
            ExpirationAlertScheduler.scheduleIfEnabled(this)
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ExpirationAlertWorker.isEnabled(this) &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE,
                )
            }
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val obscuredFlags = MotionEvent.FLAG_WINDOW_IS_OBSCURED or MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED
        if ((event.flags and obscuredFlags) != 0) return false
        return super.dispatchTouchEvent(event)
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 4102
    }
}
