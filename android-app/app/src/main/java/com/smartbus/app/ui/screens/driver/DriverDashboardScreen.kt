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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.smartbus.app.SmartBusApplication
import com.smartbus.app.data.model.StaffDetailsResponse
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

    var selectedTab by remember { mutableStateOf(0) } // 0: Trips & GPS, 1: My Details, 2: Salary & Bhatta
    var assignedTrips by remember { mutableStateOf<List<Trip>>(emptyList()) }
    var activeTrip by remember { mutableStateOf<Trip?>(null) }
    var staffDetails by remember { mutableStateOf<StaffDetailsResponse?>(null) }

    // Explicit Driver GPS Sharing Consent
    var driverGpsConsent by remember { mutableStateOf(true) }
    var showGpsConsentDialog by remember { mutableStateOf(false) }
    var pendingTripToStart by remember { mutableStateOf<Trip?>(null) }

    var isTrackingActive by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    var showIncidentDialog by remember { mutableStateOf(false) }
    var incidentSeverity by remember { mutableStateOf("MEDIUM") }
    var incidentDelayMinutes by remember { mutableStateOf("15") }
    var incidentDescription by remember { mutableStateOf("") }

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

    fun loadData() {
        scope.launch {
            isLoading = true
            app.transitRepository.getTrips().onSuccess { trips ->
                assignedTrips = trips
                activeTrip = trips.firstOrNull { it.status == "ACTIVE" }
                isTrackingActive = activeTrip != null && driverGpsConsent
            }
            app.transitRepository.getStaffDetails().onSuccess {
                staffDetails = it
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (selectedTab) {
                            0 -> "Driver Console & GPS"
                            1 -> "Driver Profile & Bus"
                            else -> "Salary & Daily Bhatta"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { loadData() }) {
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
                    containerColor = Color(0xFF581C87),
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.DirectionsBus, contentDescription = null) },
                    label = { Text("Trips & GPS") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Badge, contentDescription = null) },
                    label = { Text("My Details") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                    label = { Text("Salary") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                0 -> DriverTripsAndGpsTab(
                    assignedTrips = assignedTrips,
                    activeTrip = activeTrip,
                    driverGpsConsent = driverGpsConsent,
                    isTrackingActive = isTrackingActive,
                    hasLocationPermission = hasLocationPermission,
                    statusMessage = statusMessage,
                    onToggleGpsConsent = { enabled ->
                        driverGpsConsent = enabled
                        if (enabled && activeTrip != null) {
                            if (hasLocationPermission) {
                                LocationTrackingService.startTracking(context, activeTrip!!.id)
                                isTrackingActive = true
                                statusMessage = "✅ Live GPS sharing enabled for passengers & owner."
                            } else {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        } else {
                            LocationTrackingService.stopTracking(context)
                            isTrackingActive = false
                            statusMessage = "📍 GPS sharing declined. Passengers see General Route schedule only."
                        }
                    },
                    onRequestPermission = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    onStartTrip = { trip ->
                        pendingTripToStart = trip
                        showGpsConsentDialog = true
                    },
                    onEndTrip = { trip ->
                        scope.launch {
                            app.transitRepository.endTrip(trip.id).onSuccess {
                                LocationTrackingService.stopTracking(context)
                                isTrackingActive = false
                                statusMessage = "Trip completed successfully."
                                loadData()
                            }
                        }
                    },
                    onOpenIncidentDialog = { showIncidentDialog = true }
                )
                1 -> StaffDetailsTab(staffDetails = staffDetails, roleLabel = "Driver")
                2 -> StaffSalaryTab(staffDetails = staffDetails)
            }
        }
    }

    // Explicit GPS Consent Dialog before starting trip
    if (showGpsConsentDialog && pendingTripToStart != null) {
        val trip = pendingTripToStart!!
        AlertDialog(
            onDismissRequest = { showGpsConsentDialog = false },
            title = { Text("Share Phone Live GPS?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Do you agree to share your phone's live GPS location for Bus ${trip.bus?.busNumber ?: "111"} so passengers & the bus owner can track live arrival?\n\nIf you decline, the app will show the General Scheduled Route without live navigation."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showGpsConsentDialog = false
                    driverGpsConsent = true
                    scope.launch {
                        app.transitRepository.startTrip(trip.id).onSuccess {
                            if (hasLocationPermission) {
                                LocationTrackingService.startTracking(context, trip.id)
                                isTrackingActive = true
                            }
                            statusMessage = "✅ Trip started with Live Phone GPS broadcasting!"
                            loadData()
                        }
                    }
                }) {
                    Text("Agree & Share GPS")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showGpsConsentDialog = false
                    driverGpsConsent = false
                    scope.launch {
                        app.transitRepository.startTrip(trip.id).onSuccess {
                            LocationTrackingService.stopTracking(context)
                            isTrackingActive = false
                            statusMessage = "📍 Trip started in General Route Mode (Live GPS Off)."
                            loadData()
                        }
                    }
                }) {
                    Text("Decline (General Route)")
                }
            }
        )
    }

    // Report Incident Dialog
    if (showIncidentDialog && activeTrip != null) {
        AlertDialog(
            onDismissRequest = { showIncidentDialog = false },
            title = { Text("Report Delay / Breakdown") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("LOW", "MEDIUM", "HIGH").forEach { sev ->
                            FilterChip(
                                selected = incidentSeverity == sev,
                                onClick = { incidentSeverity = sev },
                                label = { Text(sev, fontSize = 11.sp) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = incidentDelayMinutes,
                        onValueChange = { incidentDelayMinutes = it },
                        label = { Text("Estimated Delay (Minutes)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = incidentDescription,
                        onValueChange = { incidentDescription = it },
                        label = { Text("Reason (Traffic, Tyre, Weather...)") },
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        app.transitRepository.reportIncident(
                            tripId = activeTrip!!.id,
                            severity = incidentSeverity,
                            description = incidentDescription.ifBlank { "Traffic delay on Udupi-Katpadi route" },
                            delayMinutes = incidentDelayMinutes.toIntOrNull() ?: 15
                        ).onSuccess {
                            statusMessage = "⚠️ Incident reported to Bus Owner & Passengers!"
                            showIncidentDialog = false
                            incidentDescription = ""
                        }
                    }
                }) {
                    Text("Send Alert")
                }
            },
            dismissButton = {
                TextButton(onClick = { showIncidentDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DriverTripsAndGpsTab(
    assignedTrips: List<Trip>,
    activeTrip: Trip?,
    driverGpsConsent: Boolean,
    isTrackingActive: Boolean,
    hasLocationPermission: Boolean,
    statusMessage: String?,
    onToggleGpsConsent: (Boolean) -> Unit,
    onRequestPermission: () -> Unit,
    onStartTrip: (Trip) -> Unit,
    onEndTrip: (Trip) -> Unit,
    onOpenIncidentDialog: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // GPS Consent & Status Banner
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isTrackingActive) Color(0xFFECFDF5) else Color(0xFFFEF3C7)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (isTrackingActive) "LIVE GPS BROADCASTING" else "GENERAL ROUTE FALLBACK MODE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isTrackingActive) Color(0xFF047857) else Color(0xFF92400E)
                        )
                        Text(
                            if (isTrackingActive)
                                "Sharing phone GPS to Passenger & Owner maps"
                            else
                                "GPS sharing paused/declined. Showing scheduled corridor only.",
                            fontSize = 11.sp,
                            color = Color.DarkGray
                        )
                    }
                    Switch(
                        checked = driverGpsConsent,
                        onCheckedChange = onToggleGpsConsent
                    )
                }
            }
        }

        if (!hasLocationPermission) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Location permission needed for GPS", fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Button(onClick = onRequestPermission) { Text("Grant", fontSize = 11.sp) }
                }
            }
        }

        if (statusMessage != null) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFDBEAFE))) {
                Text(statusMessage, fontSize = 12.sp, color = Color(0xFF1E3A8A), modifier = Modifier.padding(12.dp))
            }
        }

        // Active Trip Control
        if (activeTrip != null) {
            Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(4.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ACTIVE TRIP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0284C7))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        activeTrip.route?.name ?: "Udupi – Katpadi – Shirva",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text("Bus ${activeTrip.bus?.busNumber ?: "KA-20-111"}", fontSize = 13.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Live Map Preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        BusLiveMap(
                            activeTrips = listOf(activeTrip),
                            selectedTrip = activeTrip,
                            onSelectTrip = {}
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onOpenIncidentDialog,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Report Delay")
                        }
                        Button(
                            onClick = { onEndTrip(activeTrip) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("End Trip")
                        }
                    }
                }
            }
        } else {
            Text("Scheduled / Assigned Trips", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            if (assignedTrips.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text("No scheduled trips found. Pull refresh or contact owner.", modifier = Modifier.padding(16.dp), color = Color.Gray)
                }
            } else {
                assignedTrips.forEach { trip ->
                    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(trip.route?.name ?: "Udupi – Katpadi – Shirva", fontWeight = FontWeight.Bold)
                                Text("Bus ${trip.bus?.busNumber ?: "KA-20-111"} • ${trip.status}", fontSize = 12.sp, color = Color.Gray)
                            }
                            Button(onClick = { onStartTrip(trip) }) {
                                Text("Start")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StaffDetailsTab(staffDetails: StaffDetailsResponse?, roleLabel: String) {
    val profile = staffDetails?.profile
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color(0xFFDBEAFE),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.padding(12.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(profile?.fullName ?: "Staff Member", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("$roleLabel • Badge ${profile?.badgeNo ?: "BDG-KA20-104"}", fontSize = 12.sp, color = Color.Gray)
                        Text("⭐ ${profile?.rating ?: 4.8} Rating • Active Duty", fontSize = 12.sp, color = Color(0xFF047857), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Operational & License Details", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                HorizontalDivider()
                DetailRow("License / Permit No.", profile?.licenseNo ?: "KA-20-2018-0049102")
                DetailRow("Assigned Bus", profile?.assignedBus ?: "KA-20-111 (KA-20-F-1234)")
                DetailRow("Assigned Route", profile?.assignedRoute ?: "Udupi – Katpadi – Shirva")
                DetailRow("Shift Timing", profile?.shiftTiming ?: "06:30 AM – 06:30 PM")
                DetailRow("Mobile", profile?.phone ?: "+91 98450 11223")
                DetailRow("Email", profile?.email ?: "-")
                DetailRow("Blood Group", profile?.bloodGroup ?: "O+")
                DetailRow("Emergency Contact", profile?.emergencyContact ?: "+91 94481 77210")
            }
        }
    }
}

@Composable
fun StaffSalaryTab(staffDetails: StaffDetailsResponse?) {
    val salary = staffDetails?.salary
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF065F46)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("NET PAYABLE (${salary?.month ?: "November 2026"})", color = Color(0xFFA7F3D0), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("₹${salary?.netPayable?.toInt() ?: 26900}", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.height(6.dp))
                Surface(color = Color(0xFF10B981), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        "${salary?.status ?: "PAID"} • ${salary?.paymentMethod ?: "UPI / Bank"}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Salary & Daily Bhatta Breakdown", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                HorizontalDivider()
                DetailRow("Monthly Base Salary", "₹${salary?.baseSalary?.toInt() ?: 18500}")
                DetailRow("Daily Bhatta (₹${salary?.dailyBhatta?.toInt() ?: 250} × ${salary?.dutyDays ?: 26} days)", "₹${salary?.totalBhatta?.toInt() ?: 6500}")
                DetailRow("Trip / Punctuality Incentive", "+ ₹${salary?.tripIncentive?.toInt() ?: 2400}")
                DetailRow("PF / Advance Deductions", "- ₹${salary?.deductions?.toInt() ?: 500}")
                HorizontalDivider()
                DetailRow("Total Net Salary", "₹${salary?.netPayable?.toInt() ?: 26900}")
            }
        }

        Text("Recent Payslips", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        (salary?.history ?: emptyList()).forEach { item ->
            Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(item.month, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Paid on ${item.paidOn}", fontSize = 11.sp, color = Color.Gray)
                    }
                    Text("₹${item.netAmount.toInt()} • ${item.status}", fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = Color.Gray)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
