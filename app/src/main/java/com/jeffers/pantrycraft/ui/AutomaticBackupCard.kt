package com.jeffers.pantrycraft.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AutomaticBackupCard(props: AutomaticBackupProps) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            BackupDescription()
            BackupSchedule(props)
            BackupNowButton(props)
        }
    }
}

@Composable
private fun BackupDescription() {
    Text("Encrypted backup", style = MaterialTheme.typography.titleLarge)
    Text(
        "Save an encrypted copy of your pantry and grocery data on this device. " +
            "Choose a PIN that you can remember; you will need it to restore the backup. " +
            "Nothing is uploaded when you create a backup.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        "Backups are stored in the app's private storage.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun BackupSchedule(props: AutomaticBackupProps) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Automatic background backups")
            Text(
                "Backups run while the app is not open and stay on this device. " +
                    "The PIN is protected by Android Keystore.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = props.enabled,
            onCheckedChange = { if (it) props.onEnableRequested() else props.onDisable() },
        )
    }
    if (props.enabled) BackupIntervals(props)
}

@Composable
private fun BackupIntervals(props: AutomaticBackupProps) {
    Text("Backup interval", modifier = Modifier.padding(top = 12.dp))
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        backupIntervals.forEach { interval ->
            val selected = props.intervalMinutes == interval.first
            if (selected) {
                Button(onClick = { props.onIntervalSelected(interval.first) }) {
                    Text("✓ ${interval.second}")
                }
            } else {
                OutlinedButton(onClick = { props.onIntervalSelected(interval.first) }) {
                    Text(interval.second)
                }
            }
        }
    }
}

@Composable
private fun BackupNowButton(props: AutomaticBackupProps) {
    if (props.databaseHasUserData) {
        Button(
            onClick = props.onBackupNow,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save encrypted backup file") }
    }
}

private const val MINUTES_PER_HOUR = 60L
private const val THREE_HOURS = 3L
private const val SIX_HOURS = 6L
private const val TWELVE_HOURS = 12L
private const val HOURS_PER_DAY = 24L
private const val DAYS_PER_WEEK = 7L
private const val DAYS_PER_MONTH = 30L

private val backupIntervals =
    listOf(
        MINUTES_PER_HOUR to "1 hour",
        THREE_HOURS * MINUTES_PER_HOUR to "3 hours",
        SIX_HOURS * MINUTES_PER_HOUR to "6 hours",
        TWELVE_HOURS * MINUTES_PER_HOUR to "12 hours",
        HOURS_PER_DAY * MINUTES_PER_HOUR to "Daily",
        DAYS_PER_WEEK * HOURS_PER_DAY * MINUTES_PER_HOUR to "Weekly",
        DAYS_PER_MONTH * HOURS_PER_DAY * MINUTES_PER_HOUR to "Monthly",
    )
