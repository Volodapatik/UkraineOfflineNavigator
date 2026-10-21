package ua.volodapatik.navigator.ui

import android.Manifest
import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import ua.volodapatik.navigator.location.LocationService
import ua.volodapatik.navigator.model.NavigationState
import ua.volodapatik.navigator.model.TransportMode
import ua.volodapatik.navigator.utils.GeoUtils

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Permissions
    val permissionsState = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // State
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var transportMode by remember { mutableStateOf(TransportMode.CAR) }
    var navigationState by remember { mutableStateOf(NavigationState()) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var destinationLatLng by remember { mutableStateOf<LatLng?>(null) }

    // Location service
    val locationService = remember { LocationService(context) }

    // Start location updates when permission granted
    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) {
            locationService.getLocationUpdates().collect { location ->
                currentLocation = location

                // Update navigation remaining time/distance if we have a destination
                destinationLatLng?.let { dest ->
                    val distance = GeoUtils.distanceMeters(
                        location.latitude, location.longitude,
                        dest.latitude, dest.longitude
                    )
                    val timeSec = GeoUtils.estimateTimeSeconds(
                        distance,
                        transportMode.averageSpeedKmh
                    )
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

    Box(modifier = Modifier.fillMaxSize()) {

        if (!permissionsState.allPermissionsGranted) {
            // Permission request UI
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
                Text(
                    text = "Для роботи навігатора потрібен доступ до GPS",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
                    Text("Надати доступ")
                }
            }
        } else {
            // Map
            AndroidView(
                factory = { ctx ->
                    MapLibre.getInstance(ctx)
                    MapView(ctx).apply {
                        onCreate(null)
                        getMapAsync { map ->
                            // Free style (MapLibre demo / OpenFreeMap or similar)
                            // For MVP we use a public style. Later replace with offline tiles.
                            map.setStyle(
                                Style.Builder()
                                    .fromUri("https://demotiles.maplibre.org/style.json")
                            ) { style ->
                                // Enable location component
                                val locationComponent = map.locationComponent
                                locationComponent.activateLocationComponent(
                                    LocationComponentActivationOptions.builder(ctx, style)
                                        .useDefaultLocationEngine(true)
                                        .build()
                                )
                                locationComponent.isLocationComponentEnabled = true
                                locationComponent.cameraMode = CameraMode.TRACKING_GPS
                                locationComponent.renderMode = RenderMode.GPS

                                // Long click to set destination (test navigation)
                                map.addOnMapLongClickListener { point ->
                                    destinationLatLng = point
                                    currentLocation?.let { loc ->
                                        val distance = GeoUtils.distanceMeters(
                                            loc.latitude, loc.longitude,
                                            point.latitude, point.longitude
                                        )
                                        val timeSec = GeoUtils.estimateTimeSeconds(
                                            distance,
                                            transportMode.averageSpeedKmh
                                        )
                                        navigationState = NavigationState(
                                            isNavigating = true,
                                            remainingDistanceMeters = distance,
                                            remainingTimeSeconds = timeSec,
                                            destinationName = "Точка на карті",
                                            transportMode = transportMode
                                        )
                                    }
                                    true
                                }
                            }
                        }
                        mapView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    // keep alive
                }
            )

            // Top bar - Transport mode selector
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TransportMode.entries.forEach { mode ->
                    val selected = transportMode == mode
                    FilterChip(
                        selected = selected,
                        onClick = {
                            transportMode = mode
                            // Recalculate ETA if navigating
                            destinationLatLng?.let { dest ->
                                currentLocation?.let { loc ->
                                    val distance = GeoUtils.distanceMeters(
                                        loc.latitude, loc.longitude,
                                        dest.latitude, dest.longitude
                                    )
                                    val timeSec = GeoUtils.estimateTimeSeconds(
                                        distance,
                                        mode.averageSpeedKmh
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

            // Bottom navigation info panel (test navigator)
            if (navigationState.isNavigating) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = "Тестова навігація",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
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
                                Text(
                                    text = "Залишилось",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                                Text(
                                    text = navigationState.remainingDistanceText,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Час",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                                Text(
                                    text = navigationState.remainingTimeText,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Режим: ${navigationState.transportMode.iconEmoji} ${navigationState.transportMode.titleUk}",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                navigationState = NavigationState()
                                destinationLatLng = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Скасувати навігацію")
                        }
                    }
                }
            } else {
                // Hint when not navigating
                Text(
                    text = "Довге натискання на карті — поставити точку призначення",
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    // Lifecycle for MapView
    DisposableEffect(Unit) {
        onDispose {
            mapView?.onDestroy()
        }
    }
}
