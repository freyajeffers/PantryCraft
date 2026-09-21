package com.freya.pantrygrocery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Welcome to Pantry & Grocery")
        Spacer(modifier = Modifier.height(8.dp))
        Text("Use the FABs to add items to your pantry or grocery list.")
    }
}
