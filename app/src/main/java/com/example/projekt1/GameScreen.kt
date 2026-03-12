package com.example.projekt1

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// TODO: Implement the MapLibre map here using AndroidView.


@Composable
fun GameScreen(factionName: String) {
    Box(modifier = Modifier.fillMaxSize()) {

        // TODO: Replace this placeholder with your own GameMap composable implementation.
        //       The GameMap should display a MapLibre map filling the entire screen.
        //       For now, this is only a placeholder Text.
        Text(
            text = "TODO: Implement GameMap() here",
            modifier = Modifier.align(Alignment.Center)
        )

        Text(
            text = "Wybrana frakcja: $factionName",
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}
