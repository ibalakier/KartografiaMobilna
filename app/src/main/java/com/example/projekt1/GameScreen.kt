package com.example.projekt1

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView

private const val MAP_STYLE = "https://demotiles.maplibre.org/style.json"
private const val DEFAULT_ZOOM_LEVEL = 4.5
private val DEFAULT_LOCATION = LatLng(52.2297, 21.0122)


@Composable
fun GameScreen(factionName: String) {
    Box(modifier = Modifier.fillMaxSize()) {
        GameMap(modifier = Modifier.matchParentSize())

        Text(
            text = "Wybrana frakcja: $factionName",
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
fun GameMap(
    modifier: Modifier = Modifier,
    style: String = MAP_STYLE,
    target: LatLng = DEFAULT_LOCATION,
    zoomLevel: Double = DEFAULT_ZOOM_LEVEL
) {
    val mapView = rememberMapView()

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.apply {
                setUp(style, target, zoomLevel)
            }
        }
    )
}

@Composable
fun rememberMapView(): MapView {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply { onCreate(null) }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycle = lifecycleOwner.lifecycle

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = mapView.onStart()
            override fun onResume(owner: LifecycleOwner) = mapView.onResume()
            override fun onPause(owner: LifecycleOwner) = mapView.onPause()
            override fun onStop(owner: LifecycleOwner) = mapView.onStop()
            override fun onDestroy(owner: LifecycleOwner) = mapView.onDestroy()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    return mapView
}

fun MapView.setUp(
    style: String,
    target: LatLng,
    zoomLevel: Double
) {
    getMapAsync { map ->
        map.setStyle(style) {
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(target, zoomLevel)
            )
        }
    }
}
