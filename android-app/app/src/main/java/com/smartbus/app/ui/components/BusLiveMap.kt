package com.smartbus.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.smartbus.app.data.model.Trip
import kotlinx.coroutines.launch

@Composable
fun BusLiveMap(
    activeTrips: List<Trip>,
    selectedTrip: Trip?,
    onSelectTrip: (Trip) -> Unit,
    onBookTicket: ((Trip) -> Unit)? = null,
    onShareJourney: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    // Default coordinate: Central Station (40.7128, -74.006) or first active bus location
    val defaultLocation = remember(activeTrips) {
        val firstLoc = activeTrips.firstNotNullOfOrNull { it.lastLocation }
        if (firstLoc != null) {
            LatLng(firstLoc.latitude, firstLoc.longitude)
        } else {
            LatLng(40.7128, -74.006)
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, 13f)
    }

    var mapType by remember { mutableStateOf(MapType.NORMAL) }
    var uiSettings by remember {
        mutableStateOf(
            MapUiSettings(
                zoomControlsEnabled = false,
                compassEnabled = true,
                myLocationButtonEnabled = false,
                mapToolbarEnabled = true
            )
        )
    }
    val properties by remember(mapType) {
        mutableStateOf(
            MapProperties(
                isMyLocationEnabled = false,
                mapType = mapType
            )
        )
    }

    // Animate to selected trip if changed
    LaunchedEffect(selectedTrip) {
        selectedTrip?.lastLocation?.let { loc ->
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(loc.latitude, loc.longitude),
                    15f
                )
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = properties,
            uiSettings = uiSettings
        ) {
            // Draw Route Polylines and Stop Markers for each trip
            activeTrips.forEach { trip ->
                val stops = trip.route?.stops ?: emptyList()
                val sortedStops = stops.sortedBy { it.stopOrder }

                if (sortedStops.isNotEmpty()) {
                    val stopPoints = sortedStops.map { LatLng(it.stop.latitude, it.stop.longitude) }

                    // Route line
                    val isSelected = selectedTrip?.id == trip.id
                    Polyline(
                        points = stopPoints,
                        color = if (isSelected) Color(0xFF0284C7) else Color(0x8094A3B8),
                        width = if (isSelected) 10f else 6f
                    )

                    // Stop Markers
                    sortedStops.forEach { routeStop ->
                        Marker(
                            state = MarkerState(
                                position = LatLng(routeStop.stop.latitude, routeStop.stop.longitude)
                            ),
                            title = "${routeStop.stopOrder}. ${routeStop.stop.name}",
                            snippet = "Route: ${trip.route?.name ?: ""}",
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                        )
                    }
                }

                // Bus Marker
                trip.lastLocation?.let { loc ->
                    val isStale = loc.isStale
                    val markerHue = if (isStale) {
                        BitmapDescriptorFactory.HUE_ORANGE
                    } else {
                        BitmapDescriptorFactory.HUE_GREEN
                    }

                    Marker(
                        state = MarkerState(position = LatLng(loc.latitude, loc.longitude)),
                        title = "Bus ${trip.bus?.busNumber ?: ""}",
                        snippet = "Speed: ${loc.speed?.toInt() ?: 0} km/h • Route: ${trip.route?.code ?: ""}",
                        icon = BitmapDescriptorFactory.defaultMarker(markerHue),
                        onClick = {
                            onSelectTrip(trip)
                            false
                        }
                    )
                }
            }
        }

        // Top Controls: Map Type Toggle and Recenter Button
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Map Type Toggle Button (Normal / Satellite)
            SmallFloatingActionButton(
                onClick = {
                    mapType = if (mapType == MapType.NORMAL) MapType.HYBRID else MapType.NORMAL
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (mapType == MapType.NORMAL) Icons.Default.Layers else Icons.Default.Map,
                    contentDescription = "Toggle Satellite"
                )
            }

            // Recenter Button
            SmallFloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        val target = selectedTrip?.lastLocation?.let {
                            LatLng(it.latitude, it.longitude)
                        } ?: defaultLocation
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(target, 14f)
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Recenter Map")
            }
        }

        // Active Buses Pill Filter Row
        if (activeTrips.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 16.dp, end = 120.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                activeTrips.forEach { trip ->
                    val isSelected = selectedTrip?.id == trip.id
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectTrip(trip) },
                        label = {
                            Text(
                                "Bus ${trip.bus?.busNumber ?: ""}",
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (trip.lastLocation?.isStale == true) Color(0xFFF59E0B)
                                        else Color(0xFF10B981)
                                    )
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                        )
                    )
                }
            }
        }

        // Bottom Selected Bus Telemetry Card
        AnimatedVisibility(
            visible = selectedTrip != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            selectedTrip?.let { trip ->
                val loc = trip.lastLocation
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Bus ${trip.bus?.busNumber ?: ""}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = if (loc?.isStale == true) Color(0xFFFEF3C7) else Color(0xFFD1FAE5),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            if (loc?.isStale == true) "Stale GPS" else "LIVE",
                                            color = if (loc?.isStale == true) Color(0xFFB45309) else Color(0xFF047857),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    trip.route?.name ?: "Active Route",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Speed Gauge
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "${loc?.speed?.toInt() ?: 0}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        "km/h",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        if (loc != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "GPS: ${String.format("%.4f", loc.latitude)}, ${String.format("%.4f", loc.longitude)}",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    "Accuracy: ±${loc.accuracy?.toInt() ?: 5}m",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Action Buttons: Book Ticket & Share Journey
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (onBookTicket != null) {
                                Button(
                                    onClick = { onBookTicket(trip) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.ConfirmationNumber, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Book Ticket", fontSize = 13.sp)
                                }
                            }

                            if (onShareJourney != null) {
                                OutlinedButton(
                                    onClick = { onShareJourney(trip.id) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Share Live", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
