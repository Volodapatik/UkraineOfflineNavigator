package ua.volodapatik.navigator.ui

import android.Manifest
import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Marker
import ua.volodapatik.navigator.location.LocationService
import ua.volodapatik.navigator.model.NavigationState
import ua.volodapatik.navigator.model.TransportMode
import ua.volodapatik.navigator.utils.GeoUtils

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapScreen() {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val permissionsState = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var transportMode by remember { mutableStateOf(TransportMode.CAR) }
    var navigationState by remember { mutableStateOf(NavigationState()) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var mapLibreMap by remember { mutableStateOf<org.maplibre.android.maps.MapLibreMap?>(null) }
    var destinationLatLng by remember { mutableStateOf<LatLng?>(null) }
    var destinationMarker by remember { mutableStateOf<Marker?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isMapReady by remember { mutableStateOf(false) }

    val locationService = remember { LocationService(context) }

    // Location updates
    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) {
            locationService.getLocationUpdates().collect { location ->
                currentLocation = location

                destinationLatLng?.let { dest ->
                    val distance = GeoUtils.distanceMeters(
                        location.latitude, location.longitude,
                        dest.latitude, dest.longitude
                    )
                    val timeSec = GeoUtils.estimateTimeSeconds(distance, transportMode.averageSpeedKmh)
                    navigationState = navigationState.copy(
                        isNavigating = true,
                        remainingDistanceMeters = distance,
                        remainingTimeSeconds = timeSec,
                        transportMode = transportMode
                    )
                }
            }
        }
    }

    fun setDestination(point: LatLng, name: String = "Точка на карті") {
        destinationLatLng = point

        // Remove old marker
        destinationMarker?.let { mapLibreMap?.removeMarker(it) }

        // Add new destination marker
        mapLibreMap?.let { map ->
            destinationMarker = map.addMarker(
                MarkerOptions()
                    .position(point)
                    .title("Призначення")
                    .snippet(name)
            )
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 14.0))
        }

        currentLocation?.let { loc ->
            val distance = GeoUtils.distanceMeters(
                loc.latitude, loc.longitude,
                point.latitude, point.longitude
            )
            val timeSec = GeoUtils.estimateTimeSeconds(distance, transportMode.averageSpeedKmh)
            navigationState = NavigationState(
                isNavigating = true,
                remainingDistanceMeters = distance,
                remainingTimeSeconds = timeSec,
                destinationName = name,
                transportMode = transportMode
            )
        } ?: run {
            navigationState = NavigationState(
                isNavigating = true,
                destinationName = name,
                transportMode = transportMode
            )
        }
    }

    fun clearDestination() {
        destinationMarker?.let { mapLibreMap?.removeMarker(it) }
        destinationMarker = null
        destinationLatLng = null
        navigationState = NavigationState()
        searchQuery = ""
    }

    fun centerOnMe() {
        currentLocation?.let { loc ->
            mapLibreMap?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(loc.latitude, loc.longitude),
                    15.0
                )
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        if (!permissionsState.allPermissionsGranted) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Потрібен доступ до геолокації",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Для роботи навігатора потрібен доступ до GPS")
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
                    Text("Надати доступ")
                }
            }
        } else {
            // ===== MAP =====
            AndroidView(
                factory = { ctx ->
                    MapLibre.getInstance(ctx)
                    MapView(ctx).apply {
                        onCreate(null)
                        getMapAsync { map ->
                            mapLibreMap = map

                            map.setStyle(
                                Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")
                            ) { style ->
                                // Center on Ukraine first
                                map.cameraPosition = CameraPosition.Builder()
                                    .target(LatLng(48.3794, 31.1656))
                                    .zoom(6.5)
                                    .build()

                                // Location component (blue arrow)
                                try {
                                    val locationComponent = map.locationComponent
                                    locationComponent.activateLocationComponent(
                                        LocationComponentActivationOptions.builder(ctx, style)
                                            .useDefaultLocationEngine(true)
                                            .build()
                                    )
                                    locationComponent.isLocationComponentEnabled = true
                                    locationComponent.cameraMode = CameraMode.TRACKING_GPS_NORTH
                                    locationComponent.renderMode = RenderMode.GPS
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }

                                isMapReady = true

                                // Long click → set destination
                                map.addOnMapLongClickListener { point ->
                                    setDestination(point, "Точка на карті")
                                    true
                                }

                                // Single click just for feedback (optional)
                                map.addOnMapClickListener {
                                    // can show toast later
                                    false
                                }
                            }
                        }
                        mapViewRef = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // ===== TOP: Search + Transport =====
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp, start = 12.dp, end = 12.dp)
                    .fillMaxWidth()
            ) {
                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                    placeholder = { Text("Куди їдемо? (місто / вулиця)") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Очистити")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focusManager.clearFocus()
                            // MVP: просто показуємо що ввели як назву
                            // Пізніше підключимо офлайн пошук
                            if (searchQuery.isNotBlank()) {
                                // Поки що ставимо фіктивну точку біля центру України
                                // (реальний geocoding додамо наступним кроком)
                                val fakePoint = LatLng(50.4501, 30.5234) // Київ
                                setDestination(fakePoint, searchQuery.trim())
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Transport modes
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TransportMode.entries.forEach { mode ->
                        val selected = transportMode == mode
                        FilterChip(
                            selected = selected,
                            onClick = {
                                transportMode = mode
                                destinationLatLng?.let { dest ->
                                    currentLocation?.let { loc ->
                                        val distance = GeoUtils.distanceMeters(
                                            loc.latitude, loc.longitude,
                                            dest.latitude, dest.longitude
                                        )
                                        val timeSec = GeoUtils.estimateTimeSeconds(
                                            distance, mode.averageSpeedKmh
                                        )
                                        navigationState = navigationState.copy(
                                            remainingTimeSeconds = timeSec,
                                            transportMode = mode
                                        )
                                    }
                                }
                            },
                            label = {
                                Text(
                                    text = "${mode.iconEmoji} ${mode.titleUk}",
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }

            // ===== FAB: Center on me =====
            FloatingActionButton(
                onClick = { centerOnMe() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = if (navigationState.isNavigating) 220.dp else 80.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Моя позиція")
            }

            // ===== Bottom navigation panel =====
            if (navigationState.isNavigating) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = "Навігація",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = navigationState.destinationName.ifEmpty { "Точка призначення" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Відстань", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                Text(
                                    text = navigationState.remainingDistanceText,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Час", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                Text(
                                    text = navigationState.remainingTimeText,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Режим: ${navigationState.transportMode.iconEmoji} ${navigationState.transportMode.titleUk}",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { clearDestination() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Скасувати")
                        }
                    }
                }
            } else {
                Text(
                    text = "Довге натискання на карті = точка призначення",
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 28.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mapViewRef?.onDestroy()
        }
    }
}
