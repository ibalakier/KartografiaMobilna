package com.example.projekt1

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun NavGraph(viewModel: GameViewModel) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "selection",
        modifier = Modifier.fillMaxSize()
    ) {
        composable("selection") {
            SelectionScreen(
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) }
            )
        }
        composable("game_screen/{factionName}") { backStackEntry ->
            val factionName = backStackEntry.arguments?.getString("factionName") ?: ""
            GameScreen(factionName)
        }
    }
}