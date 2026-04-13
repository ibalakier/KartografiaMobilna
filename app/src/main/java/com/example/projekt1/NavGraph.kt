package com.example.projekt1

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.projekt1.domain.model.RoomState

@Composable
fun NavGraph(viewModel: GameViewModel) {
    val initialGameData by viewModel.initialGameData.collectAsState()

    if (initialGameData?.roomState == RoomState.FULL) {
        Toast.makeText(LocalContext.current, "Poczekaj na wolny pokój.", Toast.LENGTH_LONG).show()
        return
    }

    val navController = rememberNavController()

    val startDestination = if (initialGameData?.roomState == RoomState.PLAYER1_ONLY) {
        viewModel.selectFaction(initialGameData?.faction)
        "game_screen/${initialGameData?.faction?.name}"
    } else {
        "selection"
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
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
            GameScreen(factionName, viewModel)
        }
    }
}