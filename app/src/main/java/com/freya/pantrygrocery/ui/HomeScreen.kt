package com.freya.pantrygrocery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    onPantryClick: () -> Unit,
    onGroceryClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Pantry & Grocery")
        Text("Track what you have and what you need to buy.")
        Button(onClick = onPantryClick, modifier = Modifier.fillMaxWidth()) {
            Text("Open pantry")
        }
        Button(onClick = onGroceryClick, modifier = Modifier.fillMaxWidth()) {
            Text("Open grocery list")
        }
    }
}

