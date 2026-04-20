package com.example.projekt1

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SelectionScreen(
    viewModel: GameViewModel,
    onNavigate: (String) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF2B2B2B), // Ciemno-szary na górze
                        Color(0xFF0A0A0A)  // Prawie czarny na dole
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "WYBIERZ STRONĘ KONFLIKTU",
                color = Color.White,
                fontSize = if (isLandscape) 22.sp else 24.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            Text(
                text = "Przyszłość leży w Twoich rękach",
                color = Color.White,
                fontSize = 15.sp,
                modifier = Modifier.padding(bottom = if (isLandscape) 2.dp else 4.dp)
            )

            // --- IKONY FRAKCJI ---
            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FactionCard(
                        faction = Faction.ROlandia,
                        imageRes = R.drawable.rolandia,
                        themeColor = Color(0xFFD32F2F),
                        viewModel = viewModel,
                        onNavigate = onNavigate
                    )

                    Spacer(modifier = Modifier.width(48.dp))

                    Text(text = "VS", color = Color.DarkGray, fontSize = 24.sp, fontWeight = FontWeight.Black)

                    Spacer(modifier = Modifier.width(48.dp))

                    FactionCard(
                        faction = Faction.ROgród,
                        imageRes = R.drawable.rogrod,
                        themeColor = Color(0xFFFFC107), // Kolor podpisu
                        viewModel = viewModel,
                        onNavigate = onNavigate
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    FactionCard(
                        faction = Faction.ROlandia,
                        imageRes = R.drawable.rolandia,
                        themeColor = Color(0xFFD32F2F),
                        viewModel = viewModel,
                        onNavigate = onNavigate
                    )

                    Text(text = "VS", color = Color.DarkGray, fontSize = 20.sp, fontWeight = FontWeight.Black)

                    FactionCard(
                        faction = Faction.ROgród,
                        imageRes = R.drawable.rogrod,
                        themeColor = Color(0xFFFFC107),
                        viewModel = viewModel,
                        onNavigate = onNavigate
                    )
                }
            }
        }
    }
}


@Composable
fun FactionCard(
    faction: Faction,
    imageRes: Int,
    themeColor: Color,
    viewModel: GameViewModel,
    onNavigate: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .width(260.dp) // Szerokość klikalnego obszaru i obrazka
            .clickable {
                viewModel.selectFaction(faction)
                onNavigate("game_screen/${faction.name}")
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Sama grafika bez ramek i teł
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = faction.name,
            contentScale = ContentScale.Fit, // Dopasowuje obrazek tak, by był widoczny w całości
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f) // Utrzymuje kwadratowe proporcje obszaru
        )

        Spacer(modifier = Modifier.height(1.dp))

        // Podpis koloru frakcji
        Text(
            text = faction.name.uppercase(),
            color = themeColor,
            fontWeight = FontWeight.Black,
            letterSpacing = 3.sp,
            fontSize = 24.sp,
            textAlign = TextAlign.Center
        )
    }
}