package com.example.projekt1

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
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

private const val MAP_STYLE = "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
private const val BASE_ZOOM_LEVEL = 3.5
private const val COMBAT_ZOOM_LEVEL = 2.0

// --- 1. MODELE DANYCH ---
data class ArmyUnit(
    val id: String,
    val name: String,
    val iconRes: Int,
    val attackBonus: Double,
    val goldCost: Int,
    val foodCost: Int
)

fun getAvailableArmies(isPoland: Boolean): List<ArmyUnit> {
    return listOf(
        ArmyUnit("1", "Piechota", if (isPoland) R.drawable.piechota_p else R.drawable.piechota_a, 1.0, 10, 5),
        ArmyUnit("2", "Czołgi", if (isPoland) R.drawable.czolgi_p else R.drawable.czolgi_a, 2.5, 30, 10),
        ArmyUnit("3", "Myśliwiec", if (isPoland) R.drawable.mysliwce_p else R.drawable.mysliwce_a, 4.0, 60, 20),
        ArmyUnit("4", "Dron", if (isPoland) R.drawable.drony_p else R.drawable.drony_a, 6.0, 100, 30)
    )
}

enum class CombatPhase { IDLE, TARGETING }

// --- 2. OPTYMALIZACJA GEOMETRII ---
fun getSafeScaledBitmap(context: Context, drawableId: Int, reqWidth: Int = 128, reqHeight: Int = 128): Bitmap? {
    val options = BitmapFactory.Options()
    options.inJustDecodeBounds = true
    BitmapFactory.decodeResource(context.resources, drawableId, options)

    var inSampleSize = 1
    if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
        val halfHeight: Int = options.outHeight / 2
        val halfWidth: Int = options.outWidth / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }

    options.inJustDecodeBounds = false
    options.inSampleSize = inSampleSize
    return BitmapFactory.decodeResource(context.resources, drawableId, options)
}

fun createCircleFeature(center: LatLng, radiusKm: Double, pointsCount: Int = 36): Feature {
    if (radiusKm <= 0.0) return Feature.fromGeometry(Point.fromLngLat(0.0, 0.0))
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
    return Feature.fromGeometry(Polygon.fromLngLats(listOf(points)))
}

// --- 3. MENEDŻER TERYTORIUM ---
class MapEntityManager(val baseLocation: LatLng, val combatArenaLocation: LatLng, initialRadius: Double = 0.0) {
    private val resources = mutableListOf<Feature>()
    var conqueredRadiusKm = initialRadius

    private var cachedConqueredFeature: Feature? = null
    private var lastCalculatedRadius: Double = -1.0

    private var cachedResourcesCollection: FeatureCollection? = null
    private var resourcesDirty = true

    init { respawnResources() }

    fun updateConqueredTerritory(multiplier: Double, isPolandAction: Boolean) {
        val change = 100.0 * multiplier
        if (isPolandAction) {
            conqueredRadiusKm += change
        } else {
            conqueredRadiusKm -= change
            if (conqueredRadiusKm < 0.0) conqueredRadiusKm = 0.0
        }
    }

    fun respawnResources() {
        val goldLat = baseLocation.latitude + (Math.random() * 5.0 - 2.5)
        val goldLng = baseLocation.longitude + (Math.random() * 6.0 - 3.0)
        addResource(goldLat, goldLng, "gold")

        val foodLat = baseLocation.latitude + (Math.random() * 5.0 - 2.5)
        val foodLng = baseLocation.longitude + (Math.random() * 6.0 - 3.0)
        addResource(foodLat, foodLng, "food")
    }

    private fun addResource(lat: Double, lng: Double, type: String) {
        val feature = Feature.fromGeometry(Point.fromLngLat(lng, lat)).apply {
            addStringProperty("entity_type", type)
            addStringProperty("id", UUID.randomUUID().toString())
        }
        resources.add(feature)
        resourcesDirty = true
    }

    fun removeResource(id: String) {
        resources.removeAll { it.getStringProperty("id") == id }
        resourcesDirty = true
    }

    fun getResourcesCollection(): FeatureCollection {
        if (resourcesDirty || cachedResourcesCollection == null) {
            cachedResourcesCollection = FeatureCollection.fromFeatures(resources)
            resourcesDirty = false
        }
        return cachedResourcesCollection!!
    }

    fun getConqueredAreaFeature(): Feature {
        if (conqueredRadiusKm == lastCalculatedRadius && cachedConqueredFeature != null) {
            return cachedConqueredFeature!!
        }
        lastCalculatedRadius = conqueredRadiusKm
        cachedConqueredFeature = createCircleFeature(combatArenaLocation, conqueredRadiusKm)
        return cachedConqueredFeature!!
    }
}

// --- 4. MAPA MAPLIBRE ---
@Composable
fun rememberMapView(): MapView {
    val context = LocalContext.current
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { mapView.onStart() }
            override fun onResume(owner: LifecycleOwner) { mapView.onResume() }
            override fun onPause(owner: LifecycleOwner) { mapView.onPause() }
            override fun onStop(owner: LifecycleOwner) { mapView.onStop() }
            override fun onDestroy(owner: LifecycleOwner) { mapView.onDestroy() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return mapView
}

@Composable
fun GameMap(
    modifier: Modifier = Modifier,
    initialLocation: LatLng,
    entityManager: MapEntityManager,
    isAttackMode: () -> Boolean,
    isPlayerTurn: () -> Boolean,
    onPlayerActionFinished: () -> Unit,
    onResourceCollected: (String) -> Unit,
    onMapReady: (MapLibreMap) -> Unit
) {
    val context = LocalContext.current
    remember { MapLibre.getInstance(context) }
    val mapView = rememberMapView()

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.getMapAsync { map ->
                onMapReady(map)
                map.cameraPosition = CameraPosition.Builder().target(initialLocation).zoom(BASE_ZOOM_LEVEL).build()

                map.setStyle(MAP_STYLE) { style ->
                    val goldBitmap = getSafeScaledBitmap(context, R.drawable.zloto)
                    val foodBitmap = getSafeScaledBitmap(context, R.drawable.jedzenie)

                    if (goldBitmap != null) style.addImage("icon-gold", goldBitmap)
                    if (foodBitmap != null) style.addImage("icon-food", foodBitmap)

                    style.addSource(GeoJsonSource("conquered-source", entityManager.getConqueredAreaFeature()))
                    style.addLayer(FillLayer("conquered-layer", "conquered-source").withProperties(
                        PropertyFactory.fillColor(AndroidColor.parseColor("#600000FF")),
                        PropertyFactory.fillOutlineColor(AndroidColor.parseColor("#4488FF"))
                    ))

                    style.addSource(GeoJsonSource("resources-source", entityManager.getResourcesCollection()))

                    style.addLayer(SymbolLayer("layer-gold", "resources-source").withProperties(
                        PropertyFactory.iconImage("icon-gold"),
                        PropertyFactory.iconSize(0.35f),
                        PropertyFactory.iconAllowOverlap(true)
                    ).withFilter(Expression.eq(Expression.get("entity_type"), "gold")))

                    style.addLayer(SymbolLayer("layer-food", "resources-source").withProperties(
                        PropertyFactory.iconImage("icon-food"),
                        PropertyFactory.iconSize(0.35f),
                        PropertyFactory.iconAllowOverlap(true)
                    ).withFilter(Expression.eq(Expression.get("entity_type"), "food")))

                    map.addOnMapClickListener { latLng ->
                        if (isAttackMode() || !isPlayerTurn()) return@addOnMapClickListener true

                        val pointF = map.projection.toScreenLocation(latLng)
                        val features = map.queryRenderedFeatures(
                            RectF(pointF.x - 50f, pointF.y - 50f, pointF.x + 50f, pointF.y + 50f),
                            "layer-gold", "layer-food"
                        )

                        if (features.isNotEmpty()) {
                            val clickedFeature = features.first()
                            val type = clickedFeature.getStringProperty("entity_type")
                            val id = clickedFeature.getStringProperty("id")

                            entityManager.removeResource(id)
                            style.getSourceAs<GeoJsonSource>("resources-source")
                                ?.setGeoJson(entityManager.getResourcesCollection())
                            onResourceCollected(type)

                            Toast.makeText(context, "Zebrano zasoby! Czas na ruch wroga.", Toast.LENGTH_SHORT).show()
                            onPlayerActionFinished()
                        }
                        true
                    }
                }
            }
            mapView
        }
    )
}

// --- 5. GŁÓWNY WIDOK EKRANU GRY ---
@Composable
fun GameScreen(factionName: String, viewModel: GameViewModel, onNavigateToMenu: () -> Unit = {}) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val coroutineScope = rememberCoroutineScope()

    val isPoland = factionName == "ROlandia"
    val baseLocation = if (isPoland) LatLng(52.2297, 21.0122) else LatLng(0.0236, 37.9062)
    val combatArenaLocation = LatLng(2.0, 18.0)
    val opponentName = if (isPoland) "ROgród" else "ROlandia"

    val factionArmies = remember { getAvailableArmies(isPoland) }

    // entityManager inicjalizujemy z wartością z ViewModelu (już załadowaną z bazy)
    val entityManager = remember {
        MapEntityManager(baseLocation, combatArenaLocation, viewModel.conqueredRadiusKm)
    }
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }

    // isPlayerTurn pochodzi z bazy przez ViewModel
    val isPlayerTurn by remember { derivedStateOf { viewModel.isMyTurn } }

    var combatPhase by remember { mutableStateOf(CombatPhase.IDLE) }

    // Inwazja została rozpoczęta jeśli ktokolwiek już coś podbił
    var hasInvasionStarted by remember { mutableStateOf(viewModel.conqueredRadiusKm > 0.0) }

    var selectedArmyId by remember { mutableStateOf<String?>(null) }
    var attackQuantity by remember { mutableIntStateOf(1) }
    var armyInventory by remember { mutableStateOf(factionArmies.associate { it.id to 2 }) }
    var currentEnemyDefense by remember { mutableDoubleStateOf((1..4).random().toDouble()) }

    var cameraTarget by remember { mutableStateOf(baseLocation) }
    var targetZoom by remember { mutableDoubleStateOf(BASE_ZOOM_LEVEL) }

    var showExplosion by remember { mutableStateOf(false) }
    var gameOverMessage by remember { mutableStateOf<String?>(null) }

    // Synchronizacja conqueredRadiusKm z bazy do entityManager i mapy
    LaunchedEffect(viewModel.conqueredRadiusKm) {
        val newRadius = viewModel.conqueredRadiusKm
        entityManager.conqueredRadiusKm = newRadius
        if (newRadius > 0.0) hasInvasionStarted = true
        mapInstance?.style?.getSourceAs<GeoJsonSource>("conquered-source")
            ?.setGeoJson(entityManager.getConqueredAreaFeature())
    }

    // Gdy tura wraca do nas — chowamy panel ataku i wracamy kamerą do bazy
    LaunchedEffect(isPlayerTurn) {
        if (isPlayerTurn) {
            combatPhase = CombatPhase.IDLE
            selectedArmyId = null
            attackQuantity = 1
            cameraTarget = baseLocation
            targetZoom = BASE_ZOOM_LEVEL

            // Co 3 rundy respawnujemy zasoby
            if (viewModel.roundNumber % 3 == 0) {
                entityManager.respawnResources()
                mapInstance?.style?.getSourceAs<GeoJsonSource>("resources-source")
                    ?.setGeoJson(entityManager.getResourcesCollection())
            }
        }
    }

    LaunchedEffect(cameraTarget, targetZoom) {
        mapInstance?.animateCamera(CameraUpdateFactory.newLatLngZoom(cameraTarget, targetZoom), 1500)
    }

    fun checkWinCondition(): Boolean {
        if (entityManager.conqueredRadiusKm >= 3000.0) {
            gameOverMessage = if (isPoland)
                "🏆 ZWYCIĘSTWO!\nROlandia zdominowała ROgród!"
            else
                "💀 PORAŻKA!\nROlandia zdominowała Twój kontynent."
            return true
        } else if (hasInvasionStarted && entityManager.conqueredRadiusKm <= 0.0) {
            gameOverMessage = if (isPoland)
                "💀 PORAŻKA!\nWyrzucono Was z ROgrodu."
            else
                "🏆 ZWYCIĘSTWO!\nOdeparłeś inwazję ROlandii!"
            return true
        }
        return false
    }

    // Kończy naszą turę — zapisuje do bazy, drugi gracz dostaje isMyTurn = true
    fun finishPlayerAction() {
        if (checkWinCondition()) return
        viewModel.endTurn()
    }

    fun executeAttack(unit: ArmyUnit, quantity: Int) {
        val currentStock = armyInventory[unit.id] ?: 0
        if (currentStock < quantity) {
            Toast.makeText(context, "Nie masz tylu jednostek!", Toast.LENGTH_SHORT).show()
            return
        }

        armyInventory = armyInventory.toMutableMap().apply { this[unit.id] = currentStock - quantity }

        coroutineScope.launch {
            val totalAttackPower = unit.attackBonus * quantity
            if (totalAttackPower >= currentEnemyDefense) {
                showExplosion = true
                hasInvasionStarted = true
                entityManager.updateConqueredTerritory(totalAttackPower, isPolandAction = isPoland)
                // Zapisz nowy radius do bazy
                viewModel.updateConqueredRadius(entityManager.conqueredRadiusKm)
                mapInstance?.style?.getSourceAs<GeoJsonSource>("conquered-source")
                    ?.setGeoJson(entityManager.getConqueredAreaFeature())
                Toast.makeText(context, "Sukces! Terytorium zdobyte.", Toast.LENGTH_SHORT).show()

                delay(1500)
                showExplosion = false
                currentEnemyDefense = (2..6).random().toDouble()
            } else {
                Toast.makeText(context, "Atak odparty! Za mała siła.", Toast.LENGTH_LONG).show()
                delay(1000)
            }

            cameraTarget = baseLocation
            targetZoom = BASE_ZOOM_LEVEL
            combatPhase = CombatPhase.IDLE
            selectedArmyId = null
            attackQuantity = 1

            finishPlayerAction()
        }
    }

    fun buyWeapon(unit: ArmyUnit) {
        if (viewModel.gold >= unit.goldCost && viewModel.food >= unit.foodCost) {
            viewModel.addGold(-unit.goldCost)
            viewModel.addFood(-unit.foodCost)
            armyInventory = armyInventory.toMutableMap().apply { this[unit.id] = (this[unit.id] ?: 0) + 1 }
            Toast.makeText(context, "Kupiono: ${unit.name}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Brak surowców! Wymagane: ${unit.goldCost} Złota, ${unit.foodCost} Jedzenia", Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {
        Row(modifier = Modifier.fillMaxSize()) {

            // LEWY PANEL (landscape)
            if (isLandscape) {
                Column(
                    modifier = Modifier
                        .width(190.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF1A1A1A).copy(alpha = 0.95f))
                        .padding(8.dp)
                        .statusBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Wojsko", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    factionArmies.forEach { unit ->
                        ArmyItem(
                            unit = unit,
                            quantity = armyInventory[unit.id] ?: 0,
                            isSelected = selectedArmyId == unit.id,
                            onClick = {
                                if (isPlayerTurn) {
                                    selectedArmyId = unit.id
                                    attackQuantity = 1
                                }
                            },
                            onBuy = { buyWeapon(unit) },
                            isPlayerTurn = isPlayerTurn
                        )
                    }
                }
            }

            // ŚRODEK — MAPA
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                GameMap(
                    modifier = Modifier.fillMaxSize(),
                    initialLocation = baseLocation,
                    entityManager = entityManager,
                    isAttackMode = { combatPhase != CombatPhase.IDLE },
                    isPlayerTurn = { isPlayerTurn },
                    onPlayerActionFinished = { finishPlayerAction() },
                    onResourceCollected = { type ->
                        if (type == "gold") viewModel.addGold(40)
                        if (type == "food") viewModel.addFood(30)
                    },
                    onMapReady = { mapInstance = it }
                )

                // PASEK FRONTU
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .statusBarsPadding()
                ) {
                    FrontlineBanner(conqueredRadius = entityManager.conqueredRadiusKm, maxRadius = 3000.0)
                }

                // PRZYCISK TRYBU (tylko nasza tura)
                if (isPlayerTurn) {
                    Button(
                        onClick = {
                            if (combatPhase == CombatPhase.IDLE) {
                                combatPhase = CombatPhase.TARGETING
                                cameraTarget = combatArenaLocation
                                targetZoom = COMBAT_ZOOM_LEVEL
                            } else {
                                combatPhase = CombatPhase.IDLE
                                cameraTarget = baseLocation
                                targetZoom = BASE_ZOOM_LEVEL
                            }
                            selectedArmyId = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (combatPhase == CombatPhase.TARGETING) Color(0xFFD32F2F) else Color(0xFF424242)
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 32.dp)
                            .navigationBarsPadding()
                    ) {
                        Text(
                            text = if (combatPhase == CombatPhase.TARGETING) "TRYB: ATAK" else "TRYB: ZBIERANIE",
                            color = Color.White, fontWeight = FontWeight.Bold
                        )
                    }
                }

                // PANEL ATAKU
                if (combatPhase == CombatPhase.TARGETING && isPlayerTurn) {
                    val currentSelectedArmy = factionArmies.find { it.id == selectedArmyId }

                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp)
                            .navigationBarsPadding(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF222222).copy(alpha = 0.95f)),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Zwiad: Siła wroga to $currentEnemyDefense",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF5252)
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            if (!isLandscape) {
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    factionArmies.forEach { unit ->
                                        ArmyItem(
                                            unit = unit,
                                            quantity = armyInventory[unit.id] ?: 0,
                                            isSelected = selectedArmyId == unit.id,
                                            onClick = { selectedArmyId = unit.id; attackQuantity = 1 },
                                            onBuy = { buyWeapon(unit) },
                                            isPlayerTurn = isPlayerTurn
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            if (currentSelectedArmy != null) {
                                val available = armyInventory[currentSelectedArmy.id] ?: 0
                                val totalPower = currentSelectedArmy.attackBonus * attackQuantity
                                val willSucceed = totalPower >= currentEnemyDefense

                                Text(
                                    text = "Twoja siła: $totalPower | Oczekiwane zwycięstwo: ${if (willSucceed) "TAK" else "NIE"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (willSucceed) Color(0xFF69F0AE) else Color(0xFFFF5252),
                                    fontWeight = FontWeight.Bold
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Button(
                                        onClick = { if (attackQuantity > 1) attackQuantity-- },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                                    ) { Text("-", color = Color.White) }
                                    Text("Wyślij: $attackQuantity", fontWeight = FontWeight.Bold, color = Color.White)
                                    Button(
                                        onClick = { if (attackQuantity < available) attackQuantity++ },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                                    ) { Text("+", color = Color.White) }
                                }

                                Button(
                                    onClick = { executeAttack(currentSelectedArmy, attackQuantity) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                                ) {
                                    Text(if (isPoland) "Uderz!" else "Odbij!", color = Color.White)
                                }
                            } else {
                                Text("Wybierz jednostkę do ataku.", color = Color.Gray)
                            }
                        }
                    }
                }

                // OVERLAY: TURA PRZECIWNIKA
                if (!isPlayerTurn) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.65f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFFFFC107), strokeWidth = 4.dp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Tura $opponentName...",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Poczekaj na ruch przeciwnika",
                                color = Color.LightGray,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // EKSPLOZJA
                if (showExplosion) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Red.copy(alpha = 0.4f))
                    ) {
                        Text(text = "💥", fontSize = 120.sp)
                    }
                }

                // EKRAN KOŃCA GRY
                if (gameOverMessage != null) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.9f))
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = gameOverMessage!!,
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.resetGame() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
                            ) {
                                Text("Zagraj ponownie", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // PRAWY PANEL — zasoby (landscape)
            if (isLandscape) {
                Column(
                    modifier = Modifier
                        .width(90.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF1A1A1A).copy(alpha = 0.95f))
                        .padding(8.dp)
                        .statusBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Text("Surowce", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    DynamicResourceBanner(imageRes = R.drawable.zloto, amount = viewModel.gold, maxAmount = 500, fillColor = Color(0xFFFFD54F))
                    DynamicResourceBanner(imageRes = R.drawable.jedzenie, amount = viewModel.food, maxAmount = 500, fillColor = Color(0xFFFFB74D))
                }
            }
        }

        // PASEK ZASOBÓW PORTRAIT (dół ekranu)
        if (!isLandscape) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 8.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DynamicResourceBanner(imageRes = R.drawable.zloto, amount = viewModel.gold, maxAmount = 500, fillColor = Color(0xFFFFD54F))
                DynamicResourceBanner(imageRes = R.drawable.jedzenie, amount = viewModel.food, maxAmount = 500, fillColor = Color(0xFFFFB74D))
            }
        }

        // RUNDA — zawsze widoczna (lewy górny róg)
        Text(
            text = "Runda ${viewModel.roundNumber}",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 8.dp, top = 8.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// --- KOMPONENT: PASEK FRONTU ---
@Composable
fun FrontlineBanner(conqueredRadius: Double, maxRadius: Double) {
    val progress = (conqueredRadius / maxRadius).toFloat().coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(targetValue = progress, animationSpec = tween(1500), label = "frontAnim")

    Box(contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.padding(top = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(220.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .border(1.dp, Color.Black, RoundedCornerShape(7.dp))
                    .background(Color(0xFFFFC107))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .background(Color(0xFFFF5252))
                )
            }

            Row(
                modifier = Modifier.width(220.dp).padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("ROlandia: ${(progress * 100).toInt()}%", color = Color(0xFFFF5252), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("ROgród: ${((1f - progress) * 100).toInt()}%", color = Color(0xFFFFC107), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Image(
            painter = painterResource(id = R.drawable.rozboj),
            contentDescription = "Frontline Logo",
            modifier = Modifier.height(50.dp),
            contentScale = ContentScale.Fit
        )
    }
}

// --- DYNAMICZNY PASEK ZASOBÓW ---
@Composable
fun DynamicResourceBanner(imageRes: Int, amount: Int, maxAmount: Int, fillColor: Color) {
    val fillPercentage = (amount.toFloat() / maxAmount.toFloat()).coerceIn(0f, 1f)
    val animatedFill by animateFloatAsState(targetValue = fillPercentage, animationSpec = tween(1000), label = "fillAnim")

    Box(
        modifier = Modifier.width(60.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .padding(top = 25.dp)
                .width(36.dp)
                .height(110.dp)
                .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                .background(Color(0xFFEEEEEE))
                .border(2.dp, Color(0xFF3E2723), RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(animatedFill)
                    .align(Alignment.BottomCenter)
                    .background(fillColor)
            )
            Text(
                text = amount.toString(),
                modifier = Modifier.align(Alignment.Center).padding(top = 10.dp),
                fontWeight = FontWeight.ExtraBold,
                color = Color.Black,
                fontSize = 14.sp
            )
        }

        Image(
            painter = painterResource(id = imageRes),
            contentDescription = null,
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.Black)
                .border(2.dp, Color(0xFF3E2723), CircleShape),
            contentScale = ContentScale.Crop
        )
    }
}

// --- KOMPONENT JEDNOSTKI ---
@Composable
fun ArmyItem(
    unit: ArmyUnit,
    quantity: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    onBuy: () -> Unit,
    isPlayerTurn: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = isPlayerTurn) { onClick() },
        colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFF6C4600) else Color(0xFF333333)),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 8.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Image(
                        painter = painterResource(id = unit.iconRes),
                        contentDescription = unit.name,
                        modifier = Modifier.size(50.dp).clip(CircleShape).background(Color.Black),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .background(Color.Red, CircleShape)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(text = "$quantity", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = unit.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(text = "⚔️${unit.attackBonus}", fontSize = 10.sp, color = Color.LightGray)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.zloto),
                            contentDescription = "Złoto",
                            modifier = Modifier.size(18.dp).clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(text = "${unit.goldCost}", fontSize = 12.sp, color = Color.LightGray)
                        Spacer(modifier = Modifier.width(6.dp))
                        Image(
                            painter = painterResource(id = R.drawable.jedzenie),
                            contentDescription = "Jedzenie",
                            modifier = Modifier.size(18.dp).clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(text = "${unit.foodCost}", fontSize = 12.sp, color = Color.LightGray)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isPlayerTurn) Color(0xFF424242) else Color.Gray)
                    .clickable(enabled = isPlayerTurn) { onBuy() },
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }

}