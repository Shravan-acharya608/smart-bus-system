package com.smartbus.app.ui.screens.conductor

import androidx.activity.compose.rememberLauncherForActivityResult
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
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.smartbus.app.SmartBusApplication
import com.smartbus.app.data.model.PassengerCount
import com.smartbus.app.data.model.Trip
import com.smartbus.app.data.model.VerifyTicketResponse
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConductorDashboardScreen(
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as SmartBusApplication
    val scope = rememberCoroutineScope()

    var activeTrip by remember { mutableStateOf<Trip?>(null) }
    var passengerCount by remember { mutableStateOf<PassengerCount?>(null) }
    val pendingSyncCount by app.conductorRepository.pendingSyncCount.collectAsState(initial = 0)

    var manualTicketCode by remember { mutableStateOf("") }
    var verificationResult by remember { mutableStateOf<VerifyTicketResponse?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Onboard cash ticket dialog
    var showIssueTicketDialog by remember { mutableStateOf(false) }
    var cashFareInput by remember { mutableStateOf("25") }

    fun refreshState() {
        scope.launch {
            app.transitRepository.getTrips().onSuccess { trips ->
                val active = trips.firstOrNull { it.status == "ACTIVE" } ?: trips.firstOrNull()
                activeTrip = active
                if (active != null) {
                    app.conductorRepository.getPassengerCount(active.id).onSuccess {
                        passengerCount = it
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshState()
    }

    // Camera QR Scanner launcher using ZXing Embedded
    val qrScanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val scannedCode = result.contents
            val rawCode = scannedCode.split(":").first().trim()
            val tripId = activeTrip?.id
            if (tripId == null) {
                errorMessage = "No active trip assigned to verify tickets"
                return@rememberLauncherForActivityResult
            }

            scope.launch {
                isLoading = true
                errorMessage = null
                statusMessage = null
                app.conductorRepository.verifyTicket(rawCode, tripId)
                    .onSuccess { verifyRes ->
                        verificationResult = verifyRes
                        if (verifyRes.isHeldPaymentRequired == true) {
                            statusMessage = "Seat Held: Collect ₹${verifyRes.amountToCollect?.toInt()} from ${verifyRes.passengerName}"
                        } else {
                            // Record boarding
                            app.conductorRepository.recordBoarding(verifyRes.ticketId, tripId, null)
                                .onSuccess {
                                    statusMessage = "Passenger Boarded: ${verifyRes.passengerName}"
                                    refreshState()
                                }
                        }
                    }.onFailure { err ->
                        errorMessage = "Invalid Ticket: ${err.message}"
                    }
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Conductor Console", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { refreshState() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }
                    IconButton(onClick = {
                        scope.launch {
                            app.authRepository.logout()
                            onLogout()
                        }
                    }) {
                        Icon(Icons.Default.Logout, contentDescription = "Logout", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFC2410C), // Deep Orange for Conductor
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Offline Sync Banner
            if (pendingSyncCount > 0) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudQueue, contentDescription = null, tint = Color(0xFFB45309))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "$pendingSyncCount offline records pending",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = Color(0xFFB45309)
                            )
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    app.conductorRepository.syncOfflineRecords()
                                        .onSuccess { summary ->
                                            statusMessage = "Synced ${summary.processed} records with server"
                                            refreshState()
                                        }
                                        .onFailure { err ->
                                            errorMessage = "Sync failed: ${err.message}"
                                        }
                                    isLoading = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Sync Now", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Real-Time Passenger Count Card
            val current = passengerCount?.currentOnboard ?: 0
            val cap = passengerCount?.capacity ?: 45
            val boardings = passengerCount?.totalBoardings ?: 0
            val exits = passengerCount?.totalExits ?: 0

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("LIVE ONBOARD PASSENGERS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Text(
                        "$current",
                        fontSize = 54.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (current > cap) Color.Red else Color(0xFF047857)
                    )
                    Text(
                        "Bus Capacity: $cap passengers",
                        fontSize = 13.sp,
                        color = Color.DarkGray
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Total Boardings", fontSize = 11.sp, color = Color.Gray)
                            Text("$boardings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0284C7))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Total Exits", fontSize = 11.sp, color = Color.Gray)
                            Text("$exits", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Seats Free", fontSize = 11.sp, color = Color.Gray)
                            Text("${Math.max(0, cap - current)}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Status message
            if (statusMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Text(statusMessage!!, color = Color(0xFF15803D), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(12.dp))
                }
            }

            if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(12.dp))
                }
            }

            if (verificationResult?.isHeldPaymentRequired == true) {
                val res = verificationResult!!
                val tripId = activeTrip?.id
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("⚠️ Collect Fare on Bus", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Passenger: ${res.passengerName}", style = MaterialTheme.typography.bodyMedium)
                        Text("Fare to Collect: ₹${res.amountToCollect?.toInt() ?: res.fare.toInt()}", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                if (tripId != null) {
                                    scope.launch {
                                        isLoading = true
                                        app.conductorRepository.recordBoarding(res.ticketId, tripId, null)
                                            .onSuccess {
                                                statusMessage = "Payment collected & Passenger Boarded!"
                                                verificationResult = null
                                                refreshState()
                                            }
                                        isLoading = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Confirm Cash/UPI Received & Board")
                        }
                    }
                }
            }

            // QR Scanner Action Button
            Button(
                onClick = {
                    val options = ScanOptions().apply {
                        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        setPrompt("Scan Passenger Ticket QR")
                        setCameraId(0)
                        setBeepEnabled(true)
                        setOrientationLocked(true)
                    }
                    qrScanLauncher.launch(options)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC2410C))
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("SCAN PASSENGER QR CODE", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Manual Code Verification
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = manualTicketCode,
                    onValueChange = { manualTicketCode = it },
                    label = { Text("Or enter Ticket Code") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val tripId = activeTrip?.id
                        if (tripId == null || manualTicketCode.isBlank()) return@Button
                        scope.launch {
                            isLoading = true
                            errorMessage = null
                            app.conductorRepository.verifyTicket(manualTicketCode.trim(), tripId)
                                .onSuccess { verifyRes ->
                                    app.conductorRepository.recordBoarding(verifyRes.ticketId, tripId, null)
                                    statusMessage = "Verified & Boarded: ${verifyRes.passengerName}"
                                    manualTicketCode = ""
                                    refreshState()
                                }
                                .onFailure { err ->
                                    errorMessage = "Error: ${err.message}"
                                }
                            isLoading = false
                        }
                    },
                    modifier = Modifier.height(56.dp)
                ) {
                    Text("Verify")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Passenger Exit & Cash Ticketing Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Record Exit button (reduces onboard count by 1)
                OutlinedButton(
                    onClick = {
                        val tripId = activeTrip?.id ?: return@OutlinedButton
                        scope.launch {
                            app.conductorRepository.recordExit("generic_exit_${System.currentTimeMillis()}", tripId, null)
                            statusMessage = "Passenger exit recorded."
                            refreshState()
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Record Exit", fontSize = 13.sp)
                }

                // Issue Cash Ticket button
                Button(
                    onClick = { showIssueTicketDialog = true },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.PointOfSale, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Issue Cash Ticket", fontSize = 13.sp)
                }
            }
        }
    }

    // Issue Cash Ticket Dialog
    if (showIssueTicketDialog) {
        val stops = activeTrip?.route?.stops ?: emptyList()
        var selectedBoardingId by remember { mutableStateOf(stops.firstOrNull()?.stopId ?: "") }
        var selectedDestId by remember { mutableStateOf(stops.lastOrNull()?.stopId ?: "") }

        AlertDialog(
            onDismissRequest = { showIssueTicketDialog = false },
            title = { Text("Issue Onboard Cash Ticket") },
            text = {
                Column {
                    Text("Fare Amount (INR):", style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        value = cashFareInput,
                        onValueChange = { cashFareInput = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Payment: CASH (offline supported)", fontSize = 12.sp, color = Color.Gray)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val tripId = activeTrip?.id ?: return@Button
                        val fare = cashFareInput.toDoubleOrNull() ?: 20.0
                        scope.launch {
                            app.conductorRepository.issueOnboardTicket(
                                tripId = tripId,
                                boardingStopId = selectedBoardingId.ifEmpty { "default_stop" },
                                destinationStopId = selectedDestId.ifEmpty { "default_dest" },
                                fare = fare,
                                paymentMethod = "CASH"
                            ).onSuccess {
                                statusMessage = "Cash ticket issued and passenger boarding recorded."
                                showIssueTicketDialog = false
                                refreshState()
                            }
                        }
                    }
                ) {
                    Text("Issue & Board")
                }
            },
            dismissButton = {
                TextButton(onClick = { showIssueTicketDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
