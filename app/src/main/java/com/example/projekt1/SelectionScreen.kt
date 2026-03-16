package com.example.projekt1

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun SelectionScreen(
    viewModel: GameViewModel,
    onNavigate: (String) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Wybierz frakcję", modifier = Modifier.padding(bottom = 16.dp))

        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FactionContent(viewModel, onNavigate, Modifier.weight(1f), true)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FactionContent(viewModel, onNavigate, Modifier.fillMaxWidth(), false)
            }
        }
    }
}

@Composable
fun FactionContent(
    viewModel: GameViewModel,
    onNavigate: (String) -> Unit,
    modifier: Modifier,
    isLandscape: Boolean
) {
    Image(
        painter = painterResource(R.drawable.polandia),
        contentDescription = "Polandia",
        modifier = modifier.clickable { viewModel.onFrakcjaSelected(Frakcja.POLANDIA, onNavigate) }
    )

    Spacer(if (isLandscape) Modifier.width(16.dp) else Modifier.height(16.dp))

    Image(
        painter = painterResource(R.drawable.afrykania),
        contentDescription = "Afrykania",
        modifier = modifier.clickable { viewModel.onFrakcjaSelected(Frakcja.AFRYKANIA, onNavigate) }
    )
}