package com.jeffers.pantrycraft.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jeffers.pantrycraft.data.AppDatabase
import com.jeffers.pantrycraft.data.DatabaseBackupManager
import com.jeffers.pantrycraft.data.DatabaseKeyInvalidatedException
import com.jeffers.pantrycraft.data.Repository
import com.jeffers.pantrycraft.feature.camera.ui.CameraScreen
import com.jeffers.pantrycraft.feature.meals.engine.MealSynthesisEngine
import com.jeffers.pantrycraft.feature.meals.ui.MealSynthesisScreen
import com.jeffers.pantrycraft.feature.meals.ui.MealSynthesisViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppNavigator() {
    val context = LocalContext.current
    // Load Repository lazily off the UI thread so composition and first-draw are fast.
    val repositoryResultState = remember { mutableStateOf<Result<Repository>?>(null) }
    LaunchedEffect(Unit) {
        repositoryResultState.value =
            withContext(Dispatchers.IO) {
                runCatching { Repository.getInstance(context.applicationContext) }
            }
    }
    val repositoryResult = repositoryResultState.value ?: return DatabaseLoadingScreen()
    val databaseError = repositoryResult.exceptionOrNull()
    if (databaseError is DatabaseKeyInvalidatedException) {
        DatabaseRecoveryScreenHost(context)
        return
    }
    val repository = repositoryResult.getOrThrow()
    var destination by rememberSaveable { mutableStateOf("pantry") }
    if (destination != "home") {
        BackHandler { destination = "home" }
    }
    Scaffold(
        bottomBar = { AppBottomBar(destination) { destination = it } },
    ) { contentPadding ->
        DestinationContent(destination, repository, context, contentPadding) { destination = it }
    }
}

@Composable
internal fun DatabaseRecoveryScreenHost(context: android.content.Context) {
    DatabaseRecoveryScreen(
        context = context,
        onRestore = { backupName, pin ->
            if (backupName.startsWith("content://")) {
                DatabaseBackupManager.restoreEncryptedBackupFromUri(
                    context,
                    android.net.Uri.parse(backupName),
                    pin,
                )
            } else {
                DatabaseBackupManager.restoreEncryptedBackup(context, backupName, pin)
            }
            (context as? android.app.Activity)?.recreate()
        },
        onReset = {
            AppDatabase.resetEncryptedDatabase(context)
            (context as? android.app.Activity)?.recreate()
        },
    )
}

@Composable
internal fun AppBottomBar(
    destination: String,
    onDestinationSelected: (String) -> Unit,
) {
    NavigationBar {
        Row(modifier = Modifier.fillMaxWidth()) {
            BottomDestination.entries.forEach { item ->
                val selected = destination == item.route
                BoxWithConstraints(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clickable { onDestinationSelected(item.route) },
                    contentAlignment = Alignment.Center,
                ) {
                    val bubbleWidth = minOf(54.dp, maxWidth - 8.dp)
                    Box(
                        modifier =
                            Modifier
                                .width(bubbleWidth)
                                .height(32.dp)
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    RoundedCornerShape(16.dp),
                                ).semantics { contentDescription = item.label },
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = if (selected) item.selectedIcon else item.icon,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationContent(
    destination: String,
    repository: Repository,
    context: android.content.Context,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    onNavigate: (String) -> Unit,
) {
    Box(modifier = Modifier.padding(contentPadding)) {
        when (destination) {
            "home" -> HomeScreen(repository, onNavigate)
            "pantry" -> PantryScreen(viewModel(factory = PantryViewModelFactory(repository)))
            "grocery" -> GroceryScreen(viewModel(factory = GroceryViewModelFactory(repository)))
            "camera" -> CameraScreen()
            "settings" -> SettingsScreen(context, repository)
            "meals" -> MealSynthesisScreen(
                viewModel(
                    factory = MealSynthesisViewModelFactory(
                        repository = repository,
                        engine = MealSynthesisEngine(context),
                    ),
                ),
            )
        }
    }
}

internal enum class BottomDestination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    HOME(
        "home",
        "Dashboard",
        Icons.Filled.Home,
        Icons.Filled.Home,
    ),
    PANTRY(
        "pantry",
        "Pantry",
        Icons.Filled.Inventory2,
        Icons.Filled.Inventory2,
    ),
    GROCERY(
        "grocery",
        "Grocery",
        Icons.Filled.ShoppingCart,
        Icons.Filled.ShoppingCart,
    ),
    CAMERA(
        "camera",
        "Scan",
        Icons.Filled.QrCodeScanner,
        Icons.Filled.QrCodeScanner,
    ),
    SETTINGS(
        "settings",
        "Settings",
        Icons.Filled.Settings,
        Icons.Filled.Settings,
    ),
}

@Composable
internal fun DatabaseLoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Opening encrypted pantry", style = MaterialTheme.typography.headlineSmall)
    }
}
