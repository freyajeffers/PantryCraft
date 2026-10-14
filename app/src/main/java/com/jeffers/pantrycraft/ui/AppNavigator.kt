package com.jeffers.pantrycraft.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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
    var destination by rememberSaveable { mutableStateOf("pantry") }
    if (destination != "home") {
        BackHandler { destination = "home" }
    }
    when (destination) {
        "home" -> {
            HomeScreen(
                onPantryClick = { destination = "pantry" },
                onGroceryClick = { destination = "grocery" },
                onBackupClick = { destination = "backup" },
            )
        }

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

        "backup" -> DatabaseBackupScreen(context)
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
