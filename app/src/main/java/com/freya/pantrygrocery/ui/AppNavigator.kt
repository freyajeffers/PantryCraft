package com.freya.pantrygrocery.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun AppNavigator() {
    val navController = rememberNavController()
    val context = LocalContext.current

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onPantryClick = { navController.navigate("pantry") },
                onGroceryClick = { navController.navigate("grocery") }
            )
        }
        composable("pantry") {
            val factory = PantryViewModelFactory(context)
            val vm: PantryViewModel = viewModel(factory = factory)
            PantryScreen(vm)
        }
        composable("grocery") {
            val factory = GroceryViewModelFactory(context)
            val vm: GroceryViewModel = viewModel(factory = factory)
            GroceryScreen(vm)
        }
    }
}
