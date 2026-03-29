package com.example.projekt1

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import android.graphics.Color as AndroidColor
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

private const val MAP_STYLE = "https://demotiles.maplibre.org/style.json"
private const val DEFAULT_ZOOM_LEVEL = 4.5

// --- 1. NARZĘDZIA POMOCNICZE ---
fun getBitmapFromVectorDrawable(context: Context, drawableId: Int): Bitmap? {
    val drawable = ContextCompat.getDrawable(context, drawableId) ?: return null
    val bitmap = Bitmap.createBitmap(
        drawable.intrinsicWidth,
        drawable.intrinsicHeight,
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

fun createCircleFeature(center: LatLng, radiusKm: Double, pointsCount: Int = 36): Feature {
    val latDegreesPerKm = 1.0 / 111.0
    val lngDegreesPerKm = 1.0 / (111.0 * cos(Math.toRadians(center.latitude)))
    val points = mutableListOf<Point>()

    for (i in 0 until pointsCount) {
        val angle = 2.0 * PI * i / pointsCount
        val dLng = radiusKm * cos(angle) * lngDegreesPerKm
        val dLat = radiusKm * sin(angle) * latDegreesPerKm
        points.add(Point.fromLngLat(center.longitude + dLng, center.latitude + dLat))
    }
    if (points.isNotEmpty()) points.add(points.first())
    val polygon = Polygon.fromLngLats(listOf(points))
    return Feature.fromGeometry(polygon)
}

// --- 2. MENEDŻER ZASOBÓW I WROGÓW (Z NOWĄ LOGIKĄ FRONTU) ---
class MapEntityManager(val startLocation: LatLng, val targetLocation: LatLng) {
    private val entities = mutableListOf<Feature>()

    // Zmienne przechowujące obecną pozycję "frontu" (na początku to nasza baza)
    var currentFrontlineLat = startLocation.latitude
    var currentFrontlineLng = startLocation.longitude

    init {
        // Początkowe zasoby w bazie
        respawnResources()
        // Początkowi wrogowie przed nami
        spawnEnemiesOnFrontline()
    }

    fun respawnResources() {
        // Surowce pojawiają się bezpiecznie między naszą bazą a frontem (lub tuż wokół bazy)
        // Math.random() określa, jak blisko bazy lub frontu się pojawią
        val randomFactor = Math.random()

        val randomLatGold = startLocation.latitude + (currentFrontlineLat - startLocation.latitude) * randomFactor + (Math.random() * 1.0 - 0.5)
        val randomLngGold = startLocation.longitude + (currentFrontlineLng - startLocation.longitude) * randomFactor + (Math.random() * 1.0 - 0.5)
        addEntity(randomLatGold, randomLngGold, "gold")

        val randomLatFood = startLocation.latitude + (currentFrontlineLat - startLocation.latitude) * randomFactor + (Math.random() * 1.0 - 0.5)
        val randomLngFood = startLocation.longitude + (currentFrontlineLng - startLocation.longitude) * randomFactor + (Math.random() * 1.0 - 0.5)
        addEntity(randomLatFood, randomLngFood, "food")
    }

    private fun spawnEnemiesOnFrontline() {
        // Wrogowie pojawiają się dalej w kierunku bazy wroga
        val latDirection = (targetLocation.latitude - currentFrontlineLat) * 0.15 // 15% dystansu do wroga
        val lngDirection = (targetLocation.longitude - currentFrontlineLng) * 0.15

        // Generujemy dwa czołgi lekko rozsunięte od siebie na nowej linii frontu
        addEntity(currentFrontlineLat + latDirection + (Math.random() * 1.0 - 0.5), currentFrontlineLng + lngDirection + (Math.random() * 1.0 - 0.5), "tank")
        addEntity(currentFrontlineLat + latDirection + (Math.random() * 1.0 - 0.5), currentFrontlineLng + lngDirection + (Math.random() * 1.0 - 0.5), "tank")
    }

    fun pushFrontline() {
        // Przesuwamy nasz front o 10% całkowitego dystansu w stronę bazy wroga!
        currentFrontlineLat += (targetLocation.latitude - currentFrontlineLat) * 0.1
        currentFrontlineLng += (targetLocation.longitude - currentFrontlineLng) * 0.1

        // Ponieważ poszliśmy naprzód, pojawiają się nowi wrogowie do pokonania
        spawnEnemiesOnFrontline()
    }

    private fun addEntity(lat: Double, lng: Double, type: String) {
        val feature = Feature.fromGeometry(Point.fromLngLat(lng, lat)).apply {
            addStringProperty("entity_type", type)
            addStringProperty("id", UUID.randomUUID().toString())
        }
        entities.add(feature)
    }

    fun removeEntity(id: String) {
        entities.removeAll { it.getStringProperty("id") == id }
    }

    fun getFeatureCollection(): FeatureCollection = FeatureCollection.fromFeatures(entities)
}

// --- 3. KOMPONENTY MAPY ---
@Composable
fun rememberMapView(): MapView {
    val context = LocalContext.current
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    val lifecycleOwner = LocalLifecycleOwner.current
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
    initialLocation: LatLng,
    opponentLocation: LatLng, // Nowe: przekazujemy pozycję wroga
    isAttackMode: () -> Boolean,
    currentRound: Int,
    canTakeAction: () -> Boolean,
    onActionTaken: () -> Unit,
    onResourceCollected: (String) -> Unit
) {
    val context = LocalContext.current
    remember { MapLibre.getInstance(context) }

    val mapView = rememberMapView()
    val entityManager = remember { MapEntityManager(initialLocation, opponentLocation) }

    LaunchedEffect(currentRound) {
        if (currentRound > 1 && currentRound % 3 == 0) {
            entityManager.respawnResources()
            mapView.getMapAsync { map ->
                map.style?.getSourceAs<GeoJsonSource>("entities-source")
                    ?.setGeoJson(entityManager.getFeatureCollection())
            }
        }
    }

    AndroidView(
        factory = {
            mapView.setUp(initialLocation, isAttackMode, entityManager, canTakeAction, onActionTaken, onResourceCollected)
        },
        modifier = modifier
    )
}

fun MapView.setUp(
    location: LatLng,
    isAttackMode: () -> Boolean,
    entityManager: MapEntityManager,
    canTakeAction: () -> Boolean,
    onActionTaken: () -> Unit,
    onResourceCollected: (String) -> Unit
): MapView {
    this.getMapAsync { map ->
        map.cameraPosition = CameraPosition.Builder().target(location).zoom(DEFAULT_ZOOM_LEVEL).build()

        map.setStyle(MAP_STYLE) { style ->
            val context = this.context

            getBitmapFromVectorDrawable(context, R.drawable.ic_gold)?.let { style.addImage("icon-gold", it) }
            getBitmapFromVectorDrawable(context, R.drawable.ic_food)?.let { style.addImage("icon-food", it) }
            getBitmapFromVectorDrawable(context, R.drawable.ic_tank)?.let { style.addImage("icon-tank", it) }

            val entitiesSource = GeoJsonSource("entities-source", entityManager.getFeatureCollection())
            style.addSource(entitiesSource)

            val goldLayer = SymbolLayer("layer-gold", "entities-source")
                .withProperties(PropertyFactory.iconImage("icon-gold"), PropertyFactory.iconSize(1.5f))
                .withFilter(Expression.eq(Expression.get("entity_type"), "gold"))
            style.addLayer(goldLayer)

            val foodLayer = SymbolLayer("layer-food", "entities-source")
                .withProperties(PropertyFactory.iconImage("icon-food"), PropertyFactory.iconSize(1.5f))
                .withFilter(Expression.eq(Expression.get("entity_type"), "food"))
            style.addLayer(foodLayer)

            val tankLayer = SymbolLayer("layer-tank", "entities-source")
                .withProperties(PropertyFactory.iconImage("icon-tank"), PropertyFactory.iconSize(1.5f))
                .withFilter(Expression.eq(Expression.get("entity_type"), "tank"))
            style.addLayer(tankLayer)

            val targetSource = GeoJsonSource("target-source")
            style.addSource(targetSource)

            val fillLayer = FillLayer("target-layer", "target-source")
            fillLayer.setProperties(
                PropertyFactory.fillColor(AndroidColor.parseColor("#80FF0000")),
                PropertyFactory.fillOutlineColor(AndroidColor.parseColor("#FF0000"))
            )
            style.addLayerBelow(fillLayer, "layer-tank")

            map.addOnMapClickListener { latLng ->
                val pointF = map.projection.toScreenLocation(latLng)
                val hitBox = RectF(pointF.x - 30f, pointF.y - 30f, pointF.x + 30f, pointF.y + 30f)
                val features = map.queryRenderedFeatures(hitBox, "layer-gold", "layer-food", "layer-tank")

                if (features.isNotEmpty()) {
                    if (!canTakeAction()) {
                        Toast.makeText(context, "Wykorzystałeś już akcję w tej rundzie! Kliknij 'Koniec Tury'.", Toast.LENGTH_SHORT).show()
                        return@addOnMapClickListener true
                    }

                    val clickedFeature = features.first()
                    val type = clickedFeature.getStringProperty("entity_type")
                    val id = clickedFeature.getStringProperty("id")

                    if (isAttackMode()) {
                        if (type == "tank") {
                            val tankPoint = clickedFeature.geometry() as Point
                            val circleFeature = createCircleFeature(LatLng(tankPoint.latitude(), tankPoint.longitude()), radiusKm = 25.0)
                            style.getSourceAs<GeoJsonSource>("target-source")?.setGeoJson(circleFeature)

                            // 1. Usuwamy zniszczony czołg
                            entityManager.removeEntity(id)
                            // 2. PRZESUWAMY FRONT W STRONĘ WROGA!
                            entityManager.pushFrontline()

                            style.getSourceAs<GeoJsonSource>("entities-source")?.setGeoJson(entityManager.getFeatureCollection())

                            // 3. Przesuwamy kamerę, żeby podążała za akcją
                            val newFrontlinePos = LatLng(entityManager.currentFrontlineLat, entityManager.currentFrontlineLng)
                            map.animateCamera(CameraUpdateFactory.newLatLng(newFrontlinePos), 1500)

                            onActionTaken()
                        }
                    } else {
                        if (type == "gold" || type == "food") {
                            entityManager.removeEntity(id)
                            style.getSourceAs<GeoJsonSource>("entities-source")?.setGeoJson(entityManager.getFeatureCollection())
                            onResourceCollected(type)
                            onActionTaken()
                        }
                    }
                }
                true
            }
        }
    }
    return this
}

// --- 4. GŁÓWNY WIDOK EKRANU GRY ---
@Composable
fun GameScreen(factionName: String, viewModel: GameViewModel) {
    val context = LocalContext.current

    // Ustalamy pozycje bazy naszej i bazy wroga
    val startLocation = if (factionName == "POLANDIA") LatLng(52.2297, 21.0122) else LatLng(0.0236, 37.9062)
    val opponentLocation = if (factionName == "POLANDIA") LatLng(0.0236, 37.9062) else LatLng(52.2297, 21.0122)
    val opponentName = if (factionName == "POLANDIA") "AFRYKANIA" else "POLANDIA"

    var isAttackMode by remember { mutableStateOf(false) }
    var actionTakenThisRound by remember { mutableStateOf(false) }

    var showOpponentTurnDialog by remember { mutableStateOf(false) }
    var didOpponentAttack by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        GameMap(
            modifier = Modifier.fillMaxSize(),
            initialLocation = startLocation,
            opponentLocation = opponentLocation, // Przekazujemy cel misji do mapy
            isAttackMode = { isAttackMode },
            currentRound = viewModel.roundNumber,
            canTakeAction = { !actionTakenThisRound },
            onActionTaken = { actionTakenThisRound = true },
            onResourceCollected = { type ->
                if (type == "gold") viewModel.addGold(50)
                if (type == "food") viewModel.addFood(50)
            }
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

        Card(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 16.dp, top = 48.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Runda: ${viewModel.roundNumber}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                HorizontalDivider(modifier = Modifier.width(100.dp))
                Text(text = "💰 Złoto: ${viewModel.gold}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "🍞 Jedzenie: ${viewModel.food}", style = MaterialTheme.typography.bodyMedium)
                if (actionTakenThisRound) {
                    Text(text = "Akcja zużyta!", color = Color.Red, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Button(
            onClick = { isAttackMode = !isAttackMode },
            colors = ButtonDefaults.buttonColors(containerColor = if (isAttackMode) Color.Red else Color.DarkGray),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp)
        ) {
            Text(text = if (isAttackMode) "⚔️ ATAK" else "🖐 ZBIERZ", color = Color.White, fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = {
                viewModel.nextRound()
                actionTakenThisRound = false
                didOpponentAttack = Math.random() < 0.4
                showOpponentTurnDialog = true
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).navigationBarsPadding()
        ) {
            Text("Koniec Tury ➔", color = Color.White, fontWeight = FontWeight.Bold)
        }

        if (showOpponentTurnDialog) {
            AlertDialog(
                onDismissRequest = { },
                title = {
                    Text(
                        text = if (didOpponentAttack) "⚠️ ATAK WROGA!" else "Raport wywiadu",
                        fontWeight = FontWeight.Bold,
                        color = if (didOpponentAttack) Color.Red else Color.Black
                    )
                },
                text = {
                    Text(
                        text = if (didOpponentAttack) {
                            "$opponentName zaatakowała cię! Bronisz ten obszar, czy go poddajesz?"
                        } else {
                            "$opponentName nie atakuje cię w tej rundzie. Sytuacja na froncie jest stabilna."
                        }
                    )
                },
                confirmButton = {
                    if (didOpponentAttack) {
                        Button(
                            onClick = {
                                viewModel.addGold(-20)
                                viewModel.addFood(-10)
                                Toast.makeText(context, "Obroniono obszar!", Toast.LENGTH_SHORT).show()
                                showOpponentTurnDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) {
                            Text("Broń (-20💰, -10🍞)")
                        }
                    } else {
                        Button(onClick = { showOpponentTurnDialog = false }) {
                            Text("OK")
                        }
                    }
                },
                dismissButton = {
                    if (didOpponentAttack) {
                        Button(
                            onClick = {
                                Toast.makeText(context, "Terytorium stracone...", Toast.LENGTH_SHORT).show()
                                showOpponentTurnDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text("Poddaj")
                        }
                    }
                }
            )
        }
    }
}