package com.example.projekt1

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView

private const val MAP_STYLE = "https://demotiles.maplibre.org/style.json"
private const val DEFAULT_ZOOM_LEVEL = 4.5
//
//@Composable
//fun GameScreen(factionName: String) {
//    val startLocation = if (factionName == "POLANDIA") {
//        LatLng(52.2297, 21.0122)
//    } else {
//        LatLng(0.0236, 37.9062)
//    }
//
//    Box(modifier = Modifier.fillMaxSize()) {
//        GameMap(
//            modifier = Modifier.fillMaxSize(),
//            initialLocation = startLocation
//        )
//
//        Text(
//            text = "Wybrana frakcja: $factionName",
//            modifier = Modifier
//                .align(Alignment.TopCenter)
//                .statusBarsPadding()
//                .padding(top = 8.dp),
//            style = MaterialTheme.typography.headlineSmall,
//            fontWeight = FontWeight.Bold,
//            color = Color.Black
//        )
//    }
//}

@Composable
fun GameMap(
    modifier: Modifier = Modifier,
    initialLocation: LatLng
) {
    val context = LocalContext.current

    remember { MapLibre.getInstance(context) }

    val mapView = remember { MapView(context) }

    AndroidView(
        factory = {
            mapView.apply {
                onCreate(null)
                getMapAsync { map ->
                    map.setStyle(MAP_STYLE)
                    map.cameraPosition = CameraPosition.Builder()
                        .target(initialLocation)
                        .zoom(DEFAULT_ZOOM_LEVEL)
                        .build()
                }
            }
        },
        modifier = modifier
    )

    DisposableEffect(Unit) {
        onDispose {
            mapView.onDestroy()
        }
    }
}

@Composable
fun GameScreen(factionName: String, viewModel: GameViewModel) {
    val startLocation = if (factionName == "POLANDIA") {
        LatLng(52.2297, 21.0122)
    } else {
        LatLng(0.0236, 37.9062)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GameMap(
            modifier = Modifier.fillMaxSize(),
            initialLocation = startLocation
        )
        Text(
            text = "Wybrana frakcja: $factionName",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        // PANEL STATYSTYK - Material Card
        Card(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 16.dp,top = 48.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Runda: ${viewModel.roundNumber}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                HorizontalDivider(modifier = Modifier.width(100.dp))
                Text(text = "💰 Złoto: ${viewModel.gold}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "🍞 Jedzenie: ${viewModel.food}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}