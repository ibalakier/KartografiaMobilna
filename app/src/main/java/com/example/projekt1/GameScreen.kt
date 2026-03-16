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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView

private const val MAP_STYLE = "https://demotiles.maplibre.org/style.json"
private const val DEFAULT_ZOOM_LEVEL = 4.5

@Composable
fun rememberMapView(): MapView {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply { onCreate(null) }
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val lifecycle = lifecycleOwner.lifecycle

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { mapView.onStart() }
            override fun onResume(owner: LifecycleOwner) { mapView.onResume() }
            override fun onPause(owner: LifecycleOwner) { mapView.onPause() }
            override fun onStop(owner: LifecycleOwner) { mapView.onStop() }
            override fun onDestroy(owner: LifecycleOwner) { mapView.onDestroy() }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return mapView
}

@Composable
fun GameMap(
    modifier: Modifier = Modifier,
    initialLocation: LatLng
) {
    val context = LocalContext.current
    remember { MapLibre.getInstance(context) }

    val mapView = rememberMapView()

    AndroidView(
        factory = {
            mapView.setUp(initialLocation)
        },
        modifier = modifier
    )
}

fun MapView.setUp(location: LatLng): MapView {
    this.getMapAsync { map ->
        map.setStyle(MAP_STYLE)
        map.cameraPosition = CameraPosition.Builder()
            .target(location)
            .zoom(DEFAULT_ZOOM_LEVEL)
            .build()
    }
    return this
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