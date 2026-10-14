package com.jeffers.pantrycraft.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Pantry & Grocery")
        Text("Track what you have and what you need to buy.")
        Text(
            "Use the navigation bar to switch between your dashboard, pantry, grocery list, and backups.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
