package com.example.projekt1

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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
import android.content.res.Configuration
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

// --- 1. MODELE DANYCH I STAŁE ---
data class ArmyUnit(
    val id: String,
    val name: String,
    val iconRes: Int,
    val attackBonus: Double // Mnożnik przesunięcia frontu
)

val availableArmies = listOf(
    ArmyUnit("1", "Piechota", R.drawable.ic_tank, 1.0),
    ArmyUnit("2", "Czołg", R.drawable.ic_tank, 1.5),
    ArmyUnit("3", "Myśliwiec", R.drawable.ic_tank, 0.8),
    ArmyUnit("4", "Dron", R.drawable.ic_tank, 2.0)
)

// --- 2. NARZĘDZIA POMOCNICZE ---
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

// --- 3. MENEDŻER ZASOBÓW I WROGÓW ---
class MapEntityManager(val startLocation: LatLng, val targetLocation: LatLng) {
    private val entities = mutableListOf<Feature>()
    var currentFrontlineLat = startLocation.latitude
    var currentFrontlineLng = startLocation.longitude

    init {
        respawnResources()
        spawnEnemiesOnFrontline()
    }

    fun respawnResources() {
        val randomFactor = Math.random()
        val randomLatGold = startLocation.latitude + (currentFrontlineLat - startLocation.latitude) * randomFactor + (Math.random() * 1.0 - 0.5)
        val randomLngGold = startLocation.longitude + (currentFrontlineLng - startLocation.longitude) * randomFactor + (Math.random() * 1.0 - 0.5)
        addEntity(randomLatGold, randomLngGold, "gold")

        val randomLatFood = startLocation.latitude + (currentFrontlineLat - startLocation.latitude) * randomFactor + (Math.random() * 1.0 - 0.5)
        val randomLngFood = startLocation.longitude + (currentFrontlineLng - startLocation.longitude) * randomFactor + (Math.random() * 1.0 - 0.5)
        addEntity(randomLatFood, randomLngFood, "food")
    }

    private fun spawnEnemiesOnFrontline() {
        val latDirection = (targetLocation.latitude - currentFrontlineLat) * 0.15
        val lngDirection = (targetLocation.longitude - currentFrontlineLng) * 0.15
        addEntity(currentFrontlineLat + latDirection + (Math.random() * 1.0 - 0.5), currentFrontlineLng + lngDirection + (Math.random() * 1.0 - 0.5), "tank")
        addEntity(currentFrontlineLat + latDirection + (Math.random() * 1.0 - 0.5), currentFrontlineLng + lngDirection + (Math.random() * 1.0 - 0.5), "tank")
    }

    fun pushFrontline(multiplier: Double = 1.0) {
        // Front przesuwa się bazowo o 10%, pomnożone przez bonus jednostki
        currentFrontlineLat += (targetLocation.latitude - currentFrontlineLat) * 0.1 * multiplier
        currentFrontlineLng += (targetLocation.longitude - currentFrontlineLng) * 0.1 * multiplier
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

// --- 4. KOMPONENTY MAPY ---
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
    opponentLocation: LatLng,
    isAttackMode: () -> Boolean,
    currentRound: Int,
    selectedArmy: ArmyUnit?,
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
            mapView.setUp(initialLocation, isAttackMode, entityManager, selectedArmy, canTakeAction, onActionTaken, onResourceCollected)
        },
        modifier = modifier
    )
}

fun MapView.setUp(
    location: LatLng,
    isAttackMode: () -> Boolean,
    entityManager: MapEntityManager,
    selectedArmy: ArmyUnit?,
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

            style.addLayer(SymbolLayer("layer-gold", "entities-source").withProperties(PropertyFactory.iconImage("icon-gold"), PropertyFactory.iconSize(1.5f)).withFilter(Expression.eq(Expression.get("entity_type"), "gold")))
            style.addLayer(SymbolLayer("layer-food", "entities-source").withProperties(PropertyFactory.iconImage("icon-food"), PropertyFactory.iconSize(1.5f)).withFilter(Expression.eq(Expression.get("entity_type"), "food")))
            style.addLayer(SymbolLayer("layer-tank", "entities-source").withProperties(PropertyFactory.iconImage("icon-tank"), PropertyFactory.iconSize(1.5f)).withFilter(Expression.eq(Expression.get("entity_type"), "tank")))

            val targetSource = GeoJsonSource("target-source")
            style.addSource(targetSource)
            style.addLayerBelow(FillLayer("target-layer", "target-source").apply {
                setProperties(PropertyFactory.fillColor(AndroidColor.parseColor("#80FF0000")), PropertyFactory.fillOutlineColor(AndroidColor.parseColor("#FF0000")))
            }, "layer-tank")

            map.addOnMapClickListener { latLng ->
                val pointF = map.projection.toScreenLocation(latLng)
                val features = map.queryRenderedFeatures(RectF(pointF.x - 30f, pointF.y - 30f, pointF.x + 30f, pointF.y + 30f), "layer-gold", "layer-food", "layer-tank")

                if (features.isNotEmpty()) {
                    if (!canTakeAction()) {
                        Toast.makeText(context, "Wykorzystałeś już akcję!", Toast.LENGTH_SHORT).show()
                        return@addOnMapClickListener true
                    }

                    val clickedFeature = features.first()
                    val type = clickedFeature.getStringProperty("entity_type")
                    val id = clickedFeature.getStringProperty("id")

                    if (isAttackMode()) {
                        if (type == "tank") {
                            if (selectedArmy == null) {
                                Toast.makeText(context, "Wybierz armię z panelu po lewej!", Toast.LENGTH_SHORT).show()
                                return@addOnMapClickListener true
                            }
                            entityManager.removeEntity(id)
                            entityManager.pushFrontline(selectedArmy.attackBonus)
                            style.getSourceAs<GeoJsonSource>("entities-source")?.setGeoJson(entityManager.getFeatureCollection())
                            map.animateCamera(CameraUpdateFactory.newLatLng(LatLng(entityManager.currentFrontlineLat, entityManager.currentFrontlineLng)), 1500)
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

// --- 5. GŁÓWNY WIDOK EKRANU GRY ---
@Composable
fun GameScreen(factionName: String, viewModel: GameViewModel) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val startLocation = if (factionName == "POLANDIA") LatLng(52.2297, 21.0122) else LatLng(0.0236, 37.9062)
    val opponentLocation = if (factionName == "POLANDIA") LatLng(0.0236, 37.9062) else LatLng(52.2297, 21.0122)
    val opponentName = if (factionName == "POLANDIA") "AFRYKANIA" else "POLANDIA"

    var isAttackMode by remember { mutableStateOf(false) }
    var actionTakenThisRound by remember { mutableStateOf(false) }
    var selectedArmyId by remember { mutableStateOf<String?>(null) }

    val currentSelectedArmy = availableArmies.find { it.id == selectedArmyId }

    var showOpponentTurnDialog by remember { mutableStateOf(false) }
    var didOpponentAttack by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxSize()) {
        if (isLandscape) {
            Column(
                modifier = Modifier
                    .width(145.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                    .padding(8.dp)
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Armia", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                availableArmies.forEach { unit ->
                    ArmyItem(
                        unit = unit,
                        isSelected = selectedArmyId == unit.id,
                        onClick = { selectedArmyId = unit.id }
                    )
                }
            }
        }

        // KONTENER MAPY I UI
        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            GameMap(
                modifier = Modifier.fillMaxSize(),
                initialLocation = startLocation,
                opponentLocation = opponentLocation,
                isAttackMode = { isAttackMode },
                currentRound = viewModel.roundNumber,
                selectedArmy = currentSelectedArmy,
                canTakeAction = { !actionTakenThisRound },
                onActionTaken = { actionTakenThisRound = true },
                onResourceCollected = { type ->
                    if (type == "gold") viewModel.addGold(50)
                    if (type == "food") viewModel.addFood(50)
                }
            )

            // UI NAKŁADKA
            Text(
                text = "Wybrana frakcja: $factionName",
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Card(
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 16.dp, top = 48.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Runda: ${viewModel.roundNumber}", fontWeight = FontWeight.Bold)
                    HorizontalDivider(modifier = Modifier.width(100.dp))
                    Text(text = "💰 Złoto: ${viewModel.gold}")
                    Text(text = "🍞 Jedzenie: ${viewModel.food}")
                    if (actionTakenThisRound) Text(text = "Akcja zużyta!", color = Color.Red, style = MaterialTheme.typography.bodySmall)
                }
            }

            Button(
                onClick = { isAttackMode = !isAttackMode },
                colors = ButtonDefaults.buttonColors(containerColor = if (isAttackMode) Color.Red else Color.DarkGray),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = if(isLandscape) 8.dp else 16.dp)
            ) {
                Text(text = if (isAttackMode) "⚔️ ATAK" else "🖐 ZBIERZ", color = Color.White)
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
                Text("Koniec Tury ➔")
            }
        }
    }

    if (showOpponentTurnDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(if (didOpponentAttack) "⚠️ ATAK WROGA!" else "Raport wywiadu") },
            text = { Text(if (didOpponentAttack) "$opponentName zaatakowała cię! Bronisz?" else "$opponentName nie atakuje.") },
            confirmButton = {
                Button(onClick = {
                    if (didOpponentAttack) { viewModel.addGold(-20); viewModel.addFood(-10) }
                    showOpponentTurnDialog = false
                }) { Text(if (didOpponentAttack) "Broń (-20💰, -10🍞)" else "OK") }
            },
            dismissButton = {
                if (didOpponentAttack) {
                    Button(onClick = { showOpponentTurnDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Poddaj") }
                }
            }
        )
    }
}

@Composable
fun ArmyItem(unit: ArmyUnit, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF6C4600) else Color(0xFF8D6111)
        ),
        shape = CircleShape,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 8.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White, CircleShape)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = unit.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                maxLines = 1
            )
        }
    }
}