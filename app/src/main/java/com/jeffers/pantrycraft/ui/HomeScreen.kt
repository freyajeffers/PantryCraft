package com.jeffers.pantrycraft.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jeffers.pantrycraft.data.Repository

private const val MILLIS_PER_DAY = 86_400_000L
private const val EXPIRING_DAYS = 5L

@Composable
fun HomeScreen(
    repository: Repository? = null,
    onNavigate: (String) -> Unit = {},
) {
    val pantryItems by repository?.observePantryItems()?.collectAsState(initial = emptyList())
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(emptyList()) }
    val lowStockItems by repository?.observeLowStock()?.collectAsState(initial = emptyList())
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(emptyList()) }
    val now = System.currentTimeMillis()
    val expiringCount = pantryItems.count { item ->
        item.expirationTimestamp != null &&
            (item.expirationTimestamp - now) in 0..(EXPIRING_DAYS * MILLIS_PER_DAY)
    }
    val expiredCount = pantryItems.count { item ->
        item.expirationTimestamp != null && item.expirationTimestamp < now
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HomeHeader()
        InventoryMetrics(
            pantryCount = pantryItems.size,
            expiringCount = expiringCount,
            expiredCount = expiredCount,
            lowStockCount = lowStockItems.size,
            onNavigate = onNavigate,
        )
        QuickIngestionCard(onNavigate)
        GroceryChecklistCard(lowStockItems.size, onNavigate)
        MealSynthesisCard(pantryItems.size)
    }
}

@Composable
private fun HomeHeader() {
    Column {
        Text("PantryCraft", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Minimalist, Local-First AI Kitchen Assistant",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InventoryMetrics(
    pantryCount: Int,
    expiringCount: Int,
    expiredCount: Int,
    lowStockCount: Int,
    onNavigate: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetricCard(
            title = "In Stock",
            value = pantryCount.toString(),
            subtitle = "Total items",
            modifier = Modifier.weight(1f).clickable { onNavigate("pantry") },
        )
        MetricCard(
            title = "Expiring",
            value = expiringCount.toString(),
            subtitle = if (expiredCount > 0) "+$expiredCount expired" else "Next 5 days",
            isWarning = expiringCount > 0 || expiredCount > 0,
            modifier = Modifier.weight(1f).clickable { onNavigate("pantry") },
        )
        MetricCard(
            title = "Low Stock",
            value = lowStockCount.toString(),
            subtitle = "Needs restock",
            modifier = Modifier.weight(1f).clickable { onNavigate("grocery") },
        )
    }
}

@Composable
private fun QuickIngestionCard(onNavigate: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Quick Ingestion", style = MaterialTheme.typography.titleMedium)
            Text(
                "Scan barcodes or capture shelf snapshots to update your encrypted local inventory.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onNavigate("camera") },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Filled.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text("Scan Shelf")
                }
                OutlinedButton(
                    onClick = { onNavigate("pantry") },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Filled.Inventory2,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text("Add Item")
                }
            }
        }
    }
}

@Composable
private fun GroceryChecklistCard(lowStockCount: Int, onNavigate: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Grocery Checklist", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (lowStockCount > 0) {
                        "$lowStockCount items are at or below threshold"
                    } else {
                        "Pantry stock is healthy"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = { onNavigate("grocery") }) {
                Icon(
                    Icons.Filled.ShoppingCart,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp),
                )
                Text("View List")
            }
        }
    }
}

@Composable
private fun MealSynthesisCard(pantryCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text("AI Meal Synthesis (Phase 3)", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Portion-aware culinary reasoning engine. Generates recipes across Strict " +
                    "(100% on-hand), Mostly (pantry staples), and Emergency Salvage modes " +
                    "using on-device LiteRT / AICore or BYOKey cloud models.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                "Ready to scaffold with $pantryCount ingredients currently in stock.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    isWarning: Boolean = false,
) {
    Card(
        modifier = modifier,
        colors = if (isWarning) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
            )
        } else {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                color = if (isWarning) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
