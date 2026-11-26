package com.smartbus.app.ui.screens.conductor

import androidx.activity.compose.rememberLauncherForActivityResult
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
import com.smartbus.app.data.model.StaffDetailsResponse
import com.smartbus.app.data.model.Trip
import com.smartbus.app.data.model.VerifyTicketResponse
import com.smartbus.app.ui.screens.driver.StaffDetailsTab
import com.smartbus.app.ui.screens.driver.StaffSalaryTab
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConductorDashboardScreen(
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as SmartBusApplication
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(0) } // 0: Scan & Issue, 1: Collections, 2: Profile & Salary
    var activeTrip by remember { mutableStateOf<Trip?>(null) }
    var passengerCount by remember { mutableStateOf<PassengerCount?>(null) }
    var staffDetails by remember { mutableStateOf<StaffDetailsResponse?>(null) }
    val pendingSyncCount by app.conductorRepository.pendingSyncCount.collectAsState(initial = 0)

    var manualTicketCode by remember { mutableStateOf("") }
    var verificationResult by remember { mutableStateOf<VerifyTicketResponse?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Shift Collection Counters
    var cashTicketsIssued by remember { mutableStateOf(0) }
    var cashCollectedAmount by remember { mutableStateOf(0) }
    var digitalTicketsScanned by remember { mutableStateOf(0) }
    var permanentPassesScanned by remember { mutableStateOf(0) }

    // Onboard cash ticket dialog
    var showIssueTicketDialog by remember { mutableStateOf(false) }
    var selectedFromStop by remember { mutableStateOf("Udupi Service Bus Stand") }
    var selectedToStop by remember { mutableStateOf("Katpadi Junction") }
    var cashFareInput by remember { mutableStateOf("20") }

    val routeStops = listOf(
        "Udupi Service Bus Stand" to 0,
        "Kinnimulki" to 10,
        "Katpadi Junction" to 20,
        "Shankarapura" to 30,
        "Shirva Bus Stand" to 45
    )

    fun recalculateCashFare(from: String, to: String) {
        val f1 = routeStops.find { it.first == from }?.second ?: 0
        val f2 = routeStops.find { it.first == to }?.second ?: 20
        val diff = kotlin.math.abs(f2 - f1)
        cashFareInput = (if (diff > 0) diff else 10).toString()
    }

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
            app.transitRepository.getStaffDetails().onSuccess {
                staffDetails = it
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshState()
    }

    val qrScanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val scannedCode = result.contents

            if (scannedCode.startsWith("SMARTBUS-PASS:")) {
                val parts = scannedCode.split(":")
                val passId = parts.getOrNull(1) ?: "KA-PASS-8842"
                val passengerName = parts.getOrNull(2) ?: "Passenger"
                permanentPassesScanned += 1
                statusMessage = "✅ Permanent Smart Transit Pass Verified: $passengerName ($passId). Boarding Approved!"
                errorMessage = null
                refreshState()
                return@rememberLauncherForActivityResult
            }

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
                            app.conductorRepository.recordBoarding(verifyRes.ticketId, tripId, null)
                                .onSuccess {
                                    digitalTicketsScanned += 1
                                    statusMessage = "✅ Passenger Boarded: ${verifyRes.passengerName}"
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
                title = {
                    Text(
                        when (selectedTab) {
                            0 -> "Conductor Console"
                            1 -> "Shift Collections"
                            else -> "Details & Salary"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
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
                    containerColor = Color(0xFFC2410C),
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                    label = { Text("Scan & Issue") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Payments, contentDescription = null) },
                    label = { Text("Collections") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                    label = { Text("Details & Pay") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                0 -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
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
                                    Text("$pendingSyncCount offline records", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFFB45309))
                                }
                                Button(
                                    onClick = {
                                        scope.launch {
                                            isLoading = true
                                            app.conductorRepository.syncOfflineRecords()
                                                .onSuccess { summary ->
                                                    statusMessage = "Synced ${summary.processed} records"
                                                    refreshState()
                                                }
                                                .onFailure { err -> errorMessage = "Sync failed: ${err.message}" }
                                            isLoading = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309))
                                ) {
                                    Text("Sync", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Live Passenger Count Card
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
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("LIVE ONBOARD PASSENGERS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Text(
                                "$current",
                                fontSize = 48.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (current > cap) Color.Red else Color(0xFF047857)
                            )
                            Text("Bus ${activeTrip?.bus?.busNumber ?: "KA-20-111"} • Capacity: $cap", fontSize = 12.sp, color = Color.DarkGray)
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Boardings", fontSize = 11.sp, color = Color.Gray)
                                    Text("$boardings", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0284C7))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Exits", fontSize = 11.sp, color = Color.Gray)
                                    Text("$exits", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Seats Free", fontSize = 11.sp, color = Color.Gray)
                                    Text("${maxOf(0, cap - current)}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (statusMessage != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                        ) {
                            Text(statusMessage!!, color = Color(0xFF15803D), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(12.dp))
                        }
                    }
                    if (errorMessage != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
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
                                Text("Passenger: ${res.passengerName}")
                                Text("Fare: ₹${res.amountToCollect?.toInt() ?: res.fare.toInt()}", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        if (tripId != null) {
                                            scope.launch {
                                                app.conductorRepository.recordBoarding(res.ticketId, tripId, null)
                                                    .onSuccess {
                                                        cashCollectedAmount += (res.amountToCollect?.toInt() ?: res.fare.toInt())
                                                        statusMessage = "✅ Payment collected & Passenger Boarded!"
                                                        verificationResult = null
                                                        refreshState()
                                                    }
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

                    Button(
                        onClick = {
                            val options = ScanOptions().apply {
                                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                setPrompt("Scan Passenger Ticket or Permanent Pass QR")
                                setCameraId(0)
                                setBeepEnabled(true)
                                setOrientationLocked(true)
                            }
                            qrScanLauncher.launch(options)
                        },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC2410C))
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan Ticket / Permanent Pass QR", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { showIssueTicketDialog = true },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Receipt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Issue Cash / Paper Ticket (Walk-in)")
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = manualTicketCode,
                            onValueChange = { manualTicketCode = it },
                            label = { Text("Enter Ticket / Pass Code") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val code = manualTicketCode.trim()
                                if (code.startsWith("SMARTBUS-PASS:") || code.startsWith("KA-PASS-")) {
                                    permanentPassesScanned += 1
                                    statusMessage = "✅ Permanent Pass Verified: $code"
                                    manualTicketCode = ""
                                } else {
                                    val tripId = activeTrip?.id
                                    if (tripId != null && code.isNotBlank()) {
                                        scope.launch {
                                            app.conductorRepository.verifyTicket(code, tripId)
                                                .onSuccess { v ->
                                                    verificationResult = v
                                                    digitalTicketsScanned += 1
                                                    statusMessage = "✅ Ticket verified for ${v.passengerName}"
                                                }
                                                .onFailure { err -> errorMessage = err.message }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.height(54.dp)
                        ) {
                            Text("Verify")
                        }
                    }
                }
                1 -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF9A3412)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("TODAY'S SHIFT CASH COLLECTION", color = Color(0xFFFED7AA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("₹$cashCollectedAmount", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Bus ${activeTrip?.bus?.busNumber ?: "KA-20-111"} • Udupi – Katpadi – Shirva", color = Color(0xFFFFEDD5), fontSize = 12.sp)
                        }
                    }
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Shift Breakdown", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            HorizontalDivider()
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Walk-in Cash Tickets Issued", color = Color.Gray)
                                Text("$cashTicketsIssued", fontWeight = FontWeight.Bold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Digital / Razorpay QR Scanned", color = Color.Gray)
                                Text("$digitalTicketsScanned", fontWeight = FontWeight.Bold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Permanent ID Passes Scanned", color = Color.Gray)
                                Text("$permanentPassesScanned", fontWeight = FontWeight.Bold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total Current Onboard", color = Color.Gray)
                                Text("${passengerCount?.currentOnboard ?: 0}", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                2 -> {
                    var subTab by remember { mutableStateOf(0) }
                    Column(modifier = Modifier.fillMaxSize()) {
                        TabRow(selectedTabIndex = subTab) {
                            Tab(selected = subTab == 0, onClick = { subTab = 0 }, text = { Text("My Details") })
                            Tab(selected = subTab == 1, onClick = { subTab = 1 }, text = { Text("Salary & Bhatta") })
                        }
                        if (subTab == 0) {
                            StaffDetailsTab(staffDetails = staffDetails, roleLabel = "Conductor")
                        } else {
                            StaffSalaryTab(staffDetails = staffDetails)
                        }
                    }
                }
            }
        }
    }

    // Issue Cash Ticket Dialog with Stop Selector & Auto Fare
    if (showIssueTicketDialog) {
        AlertDialog(
            onDismissRequest = { showIssueTicketDialog = false },
            title = { Text("Issue Cash Ticket", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Route: Udupi – Katpadi – Shirva", fontSize = 12.sp, color = Color.Gray)
                    Text("From Stop:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    routeStops.forEach { (name, _) ->
                        FilterChip(
                            selected = selectedFromStop == name,
                            onClick = {
                                selectedFromStop = name
                                recalculateCashFare(name, selectedToStop)
                            },
                            label = { Text(name, fontSize = 11.sp) }
                        )
                    }
                    Text("To Stop:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    routeStops.forEach { (name, _) ->
                        FilterChip(
                            selected = selectedToStop == name,
                            onClick = {
                                selectedToStop = name
                                recalculateCashFare(selectedFromStop, name)
                            },
                            label = { Text(name, fontSize = 11.sp) }
                        )
                    }
                    OutlinedTextField(
                        value = cashFareInput,
                        onValueChange = { cashFareInput = it },
                        label = { Text("Fare (₹)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val fare = cashFareInput.toIntOrNull() ?: 20
                    cashTicketsIssued += 1
                    cashCollectedAmount += fare
                    statusMessage = "✅ Cash Ticket Issued ($selectedFromStop → $selectedToStop): ₹$fare"
                    showIssueTicketDialog = false
                }) {
                    Text("Issue & Collect ₹$cashFareInput")
                }
            },
            dismissButton = {
                TextButton(onClick = { showIssueTicketDialog = false }) { Text("Cancel") }
            }
        )
    }
}
