package com.example.projekt1

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.projekt1.domain.model.RoomState

@Composable
fun NavGraph(viewModel: GameViewModel) {
    val initialGameData by viewModel.initialGameData.collectAsState()

    if (initialGameData == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

//    if (initialGameData?.roomState == RoomState.FULL) {
//        val context = LocalContext.current
//        Toast.makeText(context, "Poczekaj na wolny pokój.", Toast.LENGTH_LONG).show()
//        (context as? Activity)?.finish()
//        return
//    }

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