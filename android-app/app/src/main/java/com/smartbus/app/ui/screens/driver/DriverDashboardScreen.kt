package com.smartbus.app.ui.screens.driver

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.core.content.ContextCompat
import com.smartbus.app.SmartBusApplication
import com.smartbus.app.data.model.Trip
import com.smartbus.app.service.LocationTrackingService
import com.smartbus.app.ui.components.BusLiveMap
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverDashboardScreen(
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as SmartBusApplication
    val scope = rememberCoroutineScope()

    var assignedTrips by remember { mutableStateOf<List<Trip>>(emptyList()) }
    var activeTrip by remember { mutableStateOf<Trip?>(null) }
    var isTrackingActive by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showIncidentDialog by remember { mutableStateOf(false) }
    var incidentSeverity by remember { mutableStateOf("MEDIUM") }
    var incidentDelayMinutes by remember { mutableStateOf("15") }
    var incidentDescription by remember { mutableStateOf("") }

    // Location permissions check
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    fun loadTrips() {
        scope.launch {
            isLoading = true
            app.transitRepository.getTrips().onSuccess { trips ->
                assignedTrips = trips
                activeTrip = trips.firstOrNull { it.status == "ACTIVE" }
                isTrackingActive = activeTrip != null
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadTrips()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Driver Console", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { loadTrips() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }
                    IconButton(onClick = {
                        LocationTrackingService.stopTracking(context)
                        scope.launch {
                            app.authRepository.logout()
                            onLogout()
                        }
                    }) {
                        Icon(Icons.Default.Logout, contentDescription = "Logout", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF581C87), // Deep purple for Driver
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Live Status Banner
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isTrackingActive) Color(0xFFECFDF5) else Color(0xFFF1F5F9)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(
                                if (isTrackingActive) Color(0xFF10B981) else Color.Gray,
                                RoundedCornerShape(8.dp)
                            )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            if (isTrackingActive) "GPS BROADCASTING ACTIVE" else "GPS STANDBY (OFFLINE)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (isTrackingActive) Color(0xFF047857) else Color.DarkGray
                        )
                        Text(
                            if (isTrackingActive)
                                "Background telemetry active for Bus ${activeTrip?.bus?.busNumber ?: ""}"
                            else
                                "GPS tracking only runs while on an authorized active trip",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Permission Warning if missing
            if (!hasLocationPermission) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Location Permission Required", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Text(
                            "SmartBus requires location permission to transmit real-time telemetry to waiting passengers.",
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        ) {
                            Text("Grant Permission")
                        }
                    }
                }
            }

            // Message / Error banner
            if (statusMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Text(
                        statusMessage!!,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Active Trip Controls
            if (activeTrip != null) {
                val trip = activeTrip!!
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Current Active Run", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("Bus ${trip.bus?.busNumber}", fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        Text("Route: ${trip.route?.name} (${trip.route?.code})", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Scheduled: ${trip.scheduledStart.take(16).replace("T", " ")}", fontSize = 12.sp, color = Color.Gray)

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Live Route & GPS Map",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            BusLiveMap(
                                activeTrips = listOf(trip),
                                selectedTrip = trip,
                                onSelectTrip = {},
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Report Delay / Emergency
                        OutlinedButton(
                            onClick = { showIncidentDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("REPORT DELAY / EMERGENCY", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Big prominent END TRIP button to minimize driver distraction
                        Button(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    app.transitRepository.endTrip(trip.id).onSuccess {
                                        LocationTrackingService.stopTracking(context)
                                        isTrackingActive = false
                                        activeTrip = null
                                        statusMessage = "Trip completed successfully. GPS telemetry stopped."
                                        loadTrips()
                                    }.onFailure { err ->
                                        statusMessage = "Error ending trip: ${err.message}"
                                    }
                                    isLoading = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("COMPLETE & END TRIP", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            } else {
                // Scheduled Trips List ready to start
                Text(
                    "Upcoming Assigned Runs",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))

                val scheduled = assignedTrips.filter { it.status == "SCHEDULED" }
                if (scheduled.isEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Box(modifier = Modifier.padding(32.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("No pending assigned trips.", color = Color.Gray)
                        }
                    }
                } else {
                    scheduled.forEach { trip ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Bus ${trip.bus?.busNumber} • Route ${trip.route?.code}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(trip.route?.name ?: "", fontSize = 13.sp, color = Color.DarkGray)
                                Text("Start time: ${trip.scheduledStart.take(16).replace("T", " ")}", fontSize = 12.sp, color = Color.Gray)

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = {
                                        if (!hasLocationPermission) {
                                            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                                            return@Button
                                        }
                                        scope.launch {
                                            isLoading = true
                                            app.transitRepository.startTrip(trip.id).onSuccess { startedTrip ->
                                                activeTrip = startedTrip
                                                isTrackingActive = true
                                                LocationTrackingService.startTracking(context, startedTrip.id)
                                                statusMessage = "Trip started! Foreground location service active."
                                                loadTrips()
                                            }.onFailure { err ->
                                                statusMessage = "Failed to start trip: ${err.message}"
                                            }
                                            isLoading = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("START TRIP & TRANSMIT GPS", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showIncidentDialog && activeTrip != null) {
        val trip = activeTrip!!
        AlertDialog(
            onDismissRequest = { showIncidentDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Report Delay / Emergency", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Select Alert Severity:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("LOW", "MEDIUM", "HIGH", "CRITICAL").forEach { sev ->
                            FilterChip(
                                selected = incidentSeverity == sev,
                                onClick = { incidentSeverity = sev },
                                label = { Text(sev, fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = incidentDelayMinutes,
                        onValueChange = { incidentDelayMinutes = it.filter { c -> c.isDigit() } },
                        label = { Text("Estimated Delay (Minutes)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = incidentDescription,
                        onValueChange = { incidentDescription = it },
                        label = { Text("Incident Description / Cause") },
                        placeholder = { Text("e.g. Traffic jam near highway, breakdown") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val mins = incidentDelayMinutes.toIntOrNull() ?: 0
                        val desc = incidentDescription.ifBlank { "Traffic delay or route incident" }
                        scope.launch {
                            isLoading = true
                            app.transitRepository.reportIncident(trip.id, incidentSeverity, desc, mins)
                                .onSuccess {
                                    statusMessage = "Incident broadcast sent to passengers & admin."
                                    showIncidentDialog = false
                                    incidentDescription = ""
                                }.onFailure { err ->
                                    statusMessage = "Failed to report incident: ${err.message}"
                                }
                            isLoading = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Broadcast Alert")
                }
            },
            dismissButton = {
                TextButton(onClick = { showIncidentDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
