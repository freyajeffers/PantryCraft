package com.jeffers.pantrycraft.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jeffers.pantrycraft.data.AppDatabase
import com.jeffers.pantrycraft.data.DatabaseBackupManager
import com.jeffers.pantrycraft.data.DatabaseKeyInvalidatedException
import com.jeffers.pantrycraft.data.Repository
import com.jeffers.pantrycraft.feature.camera.ui.CameraScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppNavigator() {
    val context =
        androidx.compose.ui.platform.LocalContext.current
    // Load Repository lazily off the UI thread so composition and first-draw are fast.
    val repositoryResultState = remember { mutableStateOf<Result<Repository>?>(null) }
    LaunchedEffect(Unit) {
        repositoryResultState.value =
            withContext(Dispatchers.IO) {
                runCatching { Repository.getInstance(context.applicationContext) }
            }
    }
    val repositoryResult = repositoryResultState.value
    if (repositoryResult == null) {
        DatabaseLoadingScreen()
        return
    }
    val databaseError = repositoryResult.exceptionOrNull()
    if (databaseError is DatabaseKeyInvalidatedException) {
        DatabaseRecoveryScreen(
            context = context,
            onRestore = { backupName, pin ->
                DatabaseBackupManager.restoreEncryptedBackup(context, backupName, pin)
                (context as? android.app.Activity)?.recreate()
            },
            onReset = {
                AppDatabase.resetEncryptedDatabase(context)
                (context as? android.app.Activity)?.recreate()
            },
        )
        return
    }
    val repository = repositoryResult.getOrThrow()
    var destination by rememberSaveable { mutableStateOf("pantry") }
    if (destination != "home") {
        BackHandler { destination = "home" }
    }
    Scaffold(
        bottomBar = {
            NavigationBar {
                BottomDestination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item.route,
                        onClick = { destination = item.route },
                        icon = {
                            Box(
                                modifier =
                                    Modifier
                                        .size(40.dp)
                                        .semantics { contentDescription = item.label },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    item.symbol,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        },
                        label = null,
                    )
                }
            }
        },
    ) { contentPadding ->
        Box(modifier = Modifier.padding(contentPadding)) {
            when (destination) {
                "home" -> HomeScreen()

                "pantry" -> {
                    val vm: PantryViewModel =
                        viewModel(factory = PantryViewModelFactory(repository))
                    PantryScreen(vm)
                }

                "grocery" -> {
                    val vm: GroceryViewModel =
                        viewModel(factory = GroceryViewModelFactory(repository))
                    GroceryScreen(vm)
                }

                "camera" -> CameraScreen()

                "settings" -> SettingsScreen(context, repository)
            }
        }
    }
}

private enum class BottomDestination(
    val route: String,
    val label: String,
    val symbol: String,
) {
    HOME("home", "Dashboard", "⌂"),
    PANTRY("pantry", "Pantry", "▣"),
    GROCERY("grocery", "Grocery", "✓"),
    CAMERA("camera", "Scan", "◎"),
    SETTINGS("settings", "Settings", "⚙"),
}

@Composable
private fun DatabaseLoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Opening encrypted pantry", style = MaterialTheme.typography.headlineSmall)
    }
}
