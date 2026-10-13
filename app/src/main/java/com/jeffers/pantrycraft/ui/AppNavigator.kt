package com.jeffers.pantrycraft.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jeffers.pantrycraft.data.AppDatabase
import com.jeffers.pantrycraft.data.DatabaseBackupManager
import com.jeffers.pantrycraft.data.DatabaseKeyInvalidatedException
import com.jeffers.pantrycraft.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppNavigator() {
    val context =
        androidx.compose.ui.platform.LocalContext.current
    val repositoryResult =
        produceState<Result<Repository>?>(initialValue = null, context) {
            value =
                withContext(Dispatchers.IO) {
                    runCatching { Repository.getInstance(context) }
                }
        }.value
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
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onPantryClick = { navController.navigate("pantry") },
                onGroceryClick = { navController.navigate("grocery") },
                onBackupClick = { navController.navigate("backup") },
            )
        }
        composable("pantry") {
            val vm: PantryViewModel =
                viewModel(factory = PantryViewModelFactory(repository))
            PantryScreen(vm)
        }
        composable("grocery") {
            val vm: GroceryViewModel =
                viewModel(factory = GroceryViewModelFactory(repository))
            GroceryScreen(vm)
        }
        composable("backup") {
            DatabaseBackupScreen(context)
        }
    }
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
