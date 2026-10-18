package com.jeffers.pantrycraft.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.jeffers.pantrycraft.data.ExpirationAlertWorker

@Composable
fun NotificationSettingsCard(
    context: Context,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    permissionLauncher: ManagedActivityResultLauncher<String, Boolean>,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Notifications & Alerts", style = MaterialTheme.typography.titleLarge)
            Text(
                "Receive a private, local daily notification on this device if any pantry items " +
                    "are expiring within 48 hours. No external servers or cloud services are contacted.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NotificationToggle(
                context = context,
                enabled = enabled,
                onEnabledChange = onEnabledChange,
                permissionLauncher = permissionLauncher,
            )
        }
    }
}

@Composable
private fun NotificationToggle(
    context: Context,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    permissionLauncher: ManagedActivityResultLauncher<String, Boolean>,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Daily expiration alerts")
            Text(
                "Checks once daily for expiring food",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = { requested ->
                if (requested && notificationsNeedPermission(context)) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    onEnabledChange(requested)
                    ExpirationAlertWorker.setEnabled(context, requested)
                }
            },
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

private fun notificationsNeedPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED
