package com.smartbus.app.ui.screens.passenger

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.print.PrintHelper
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.smartbus.app.SmartBusApplication
import com.smartbus.app.data.model.*
import com.smartbus.app.service.DestinationAlertService
import com.smartbus.app.ui.components.BoardingPassCard
import com.smartbus.app.ui.components.BusLiveMap
import com.smartbus.app.ui.components.RollingQRCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengerDashboardScreen(
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as SmartBusApplication
    val scope = rememberCoroutineScope()

    var selectedNavIndex by remember { mutableStateOf(0) } // 0: Explore/Routes, 1: Live Tracking, 2: My Tickets, 3: Profile

    // Data states
    var routes by remember { mutableStateOf<List<Route>>(emptyList()) }
    var activeTrips by remember { mutableStateOf<List<Trip>>(emptyList()) }
    var myTickets by remember { mutableStateOf<List<Ticket>>(emptyList()) }
    var frequentCommutes by remember { mutableStateOf<List<FrequentRouteItem>>(emptyList()) }
    var allStops by remember { mutableStateOf<List<Stop>>(emptyList()) }
    var busSearchResults by remember { mutableStateOf<List<BusSearchResult>>(emptyList()) }
    var isSearchingBuses by remember { mutableStateOf(false) }
    var searchErrorMsg by remember { mutableStateOf<String?>(null) }
    var selectedBoardingStopId by remember { mutableStateOf<String?>(null) }
    var selectedDestStopId by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Search filters
    var originQuery by remember { mutableStateOf("") }
    var destinationQuery by remember { mutableStateOf("") }

    // Dialog states
    var selectedTripForBooking by remember { mutableStateOf<Trip?>(null) }
    var qrTicketToShow by remember { mutableStateOf<Ticket?>(null) }
    var showPermanentPassModal by remember { mutableStateOf(false) }
    var showPrintableIdCard by remember { mutableStateOf(false) }
    var permanentPassData by remember { mutableStateOf<PermanentPassData?>(null) }
    var permanentPassCode by remember { mutableStateOf("KA-PASS-8842") }
    var passengerDisplayName by remember { mutableStateOf("Passenger") }
    var walletBalance by remember { mutableStateOf(500.0) }
    var activeShareLink by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Destination Wake-Up Alarm (Night / Sleep Protection) states
    val destAlertState by DestinationAlertService.alertState.collectAsState()
    var showDestAlertModal by remember { mutableStateOf(false) }
    var preselectedAlertStopId by remember { mutableStateOf<String?>(null) }
    var preselectedAlertTripId by remember { mutableStateOf<String?>(null) }

    var pendingStartAlertParams by remember { mutableStateOf<(() -> Unit)?>(null) }
    val alertPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        pendingStartAlertParams?.invoke()
        pendingStartAlertParams = null
    }

    fun launchWithAlertPermissions(onReady: () -> Unit) {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) {
            pendingStartAlertParams = onReady
            alertPermissionLauncher.launch(needed.toTypedArray())
        } else {
            onReady()
        }
    }

    fun executeBusSearch() {
        val bId = selectedBoardingStopId ?: return
        val dId = selectedDestStopId ?: return
        scope.launch {
            isSearchingBuses = true
            searchErrorMsg = null
            app.transitRepository.searchBuses(bId, dId)
                .onSuccess {
                    busSearchResults = it
                    if (it.isEmpty()) searchErrorMsg = "No scheduled buses found between selected stops."
                }
                .onFailure {
                    searchErrorMsg = "Search error: ${it.message}"
                }
            isSearchingBuses = false
        }
    }

    fun refreshData() {
        scope.launch {
            isLoading = true
            app.transitRepository.getRoutes(
                origin = originQuery.ifEmpty { null },
                destination = destinationQuery.ifEmpty { null }
            ).onSuccess { routes = it }

            app.transitRepository.getTrips(status = "ACTIVE").onSuccess { activeTrips = it }
            app.transitRepository.getMyTickets().onSuccess { myTickets = it }
            app.transitRepository.getStops().onSuccess { allStops = it }
            app.transitRepository.getFrequentCommutes().onSuccess { frequentCommutes = it }
            app.transitRepository.getPermanentPass().onSuccess { pass ->
                permanentPassData = pass
                permanentPassCode = pass.passCode
                passengerDisplayName = pass.fullName
                walletBalance = pass.walletBalance
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    // Auto-poll live GPS positions every 6 seconds while viewing Live Map
    LaunchedEffect(selectedNavIndex) {
        while (selectedNavIndex == 1) {
            delay(6000L)
            app.transitRepository.getTrips(status = "ACTIVE").onSuccess { activeTrips = it }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (selectedNavIndex) {
                            0 -> "Search Routes & Buses"
                            1 -> "Live Bus Tracking"
                            2 -> "My Tickets"
                            else -> "Passenger Profile"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { showDestAlertModal = true }) {
                        Icon(
                            if (destAlertState.isActive) Icons.Default.AlarmOn else Icons.Default.AlarmAdd,
                            contentDescription = "Destination Wake-Up Alarm",
                            tint = if (destAlertState.isActive) Color(0xFFFDE047) else Color.White
                        )
                    }
                    IconButton(onClick = { showPermanentPassModal = true }) {
                        Icon(Icons.Default.Badge, contentDescription = "Permanent Pass", tint = Color.White)
                    }
                    IconButton(onClick = { refreshData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedNavIndex == 0,
                    onClick = { selectedNavIndex = 0 },
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text("Explore") }
                )
                NavigationBarItem(
                    selected = selectedNavIndex == 1,
                    onClick = { selectedNavIndex = 1 },
                    icon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                    label = { Text("Live Map") }
                )
                NavigationBarItem(
                    selected = selectedNavIndex == 2,
                    onClick = { selectedNavIndex = 2 },
                    icon = { Icon(Icons.Default.ConfirmationNumber, contentDescription = null) },
                    label = { Text("Tickets") }
                )
                NavigationBarItem(
                    selected = selectedNavIndex == 3,
                    onClick = { selectedNavIndex = 3 },
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text("Profile") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (selectedNavIndex) {
                0 -> Column(modifier = Modifier.fillMaxSize()) {
                    // Status / Payment confirmation banner
                    if (statusMessage != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7)),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(statusMessage!!, color = Color(0xFF15803D), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                IconButton(onClick = { statusMessage = null }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color(0xFF15803D), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    // Quick Wallet & Permanent Pass Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).clickable { selectedNavIndex = 3 }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("TAP & PAY WALLET", color = Color(0xFF94A3B8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text("₹${walletBalance.toInt()}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                                }
                                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).clickable { showPermanentPassModal = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("PERMANENT ID QR", color = Color(0xFFBAE6FD), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text(permanentPassCode.take(12), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                Icon(Icons.Default.QrCode2, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                    }

                    // Night Sleep Wake-Up Destination Alarm Quick Card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (destAlertState.isActive) Color(0xFF4C1D95) else Color(0xFFF5F3FF)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                            .clickable { showDestAlertModal = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    if (destAlertState.isActive) Icons.Default.AlarmOn else Icons.Default.Bedtime,
                                    contentDescription = null,
                                    tint = if (destAlertState.isActive) Color(0xFFFDE047) else Color(0xFF6D28D9),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        if (destAlertState.isActive)
                                            "Wake-Up Alarm ON: ${destAlertState.stopName}"
                                        else
                                            "Night Sleep Destination Wake-Up Alarm",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (destAlertState.isActive) Color.White else Color(0xFF4C1D95)
                                    )
                                    val distLabel = destAlertState.distanceMeters?.let { m ->
                                        if (m >= 1000) String.format("%.1f km away", m / 1000.0)
                                        else "${m.roundToInt()} m away"
                                    } ?: "Alerts ${if (destAlertState.radiusMeters >= 1000) "${destAlertState.radiusMeters / 1000} km" else "${destAlertState.radiusMeters} m"} before stop"
                                    Text(
                                        if (destAlertState.isActive) "$distLabel (${destAlertState.sourceLabel})"
                                        else "Sleeping on the bus? Get a loud alarm before your stop",
                                        fontSize = 10.sp,
                                        color = if (destAlertState.isActive) Color(0xFFDDD6FE) else Color(0xFF6D28D9)
                                    )
                                }
                            }
                            Text(
                                if (destAlertState.isActive) "Manage" else "Set",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (destAlertState.isActive) Color(0xFFFDE047) else Color(0xFF6D28D9)
                            )
                        }
                    }

                    // Quick Popular Karnataka Route Chips (always visible for 1-tap search)
                    if (allStops.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val quickPairs = listOf(
                                Triple("Udupi → Katpadi (₹20)", "Udupi", "Katpadi"),
                                Triple("Udupi → Shirva (₹45)", "Udupi", "Shirva"),
                                Triple("Katpadi → Shirva (₹25)", "Katpadi", "Shirva")
                            )
                            items(quickPairs) { (label, fromKey, toKey) ->
                                AssistChip(
                                    onClick = {
                                        val s1 = allStops.find { it.name.contains(fromKey, ignoreCase = true) }
                                        val s2 = allStops.find { it.name.contains(toKey, ignoreCase = true) }
                                        if (s1 != null && s2 != null) {
                                            selectedBoardingStopId = s1.id
                                            selectedDestStopId = s2.id
                                            executeBusSearch()
                                        }
                                    },
                                    label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                    leadingIcon = { Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF0284C7)) }
                                )
                            }
                        }
                    }

                    FrequentCommuteSection(
                        commutes = frequentCommutes,
                        onSelectCommute = { bId, dId ->
                            selectedBoardingStopId = bId
                            selectedDestStopId = dId
                            executeBusSearch()
                        }
                    )
                    RouteSearchScreen(
                        stops = allStops,
                        busResults = busSearchResults,
                        isSearching = isSearchingBuses,
                        errorMsg = searchErrorMsg,
                        selectedBoardingStopId = selectedBoardingStopId,
                        selectedDestStopId = selectedDestStopId,
                        onSelectBoardingStop = { selectedBoardingStopId = it },
                        onSelectDestStop = { selectedDestStopId = it },
                        onSearch = { executeBusSearch() },
                        onBookNow = { bus, paymentMode ->
                            val bId = selectedBoardingStopId ?: return@RouteSearchScreen
                            val dId = selectedDestStopId ?: return@RouteSearchScreen
                            scope.launch {
                                isLoading = true
                                app.transitRepository.payInstantTicket(bus.tripId, bId, dId, paymentMode)
                                    .onSuccess { payRes ->
                                        walletBalance = payRes.walletBalance
                                        preselectedAlertStopId = dId
                                        preselectedAlertTripId = bus.tripId
                                        statusMessage = "✅ Paid ₹${payRes.ticket.fare.toInt()} via $paymentMode! Tap 'Night Wake-Up Alarm' on your ticket if traveling at night."
                                        refreshData()
                                        selectedNavIndex = 2
                                    }
                                    .onFailure { err ->
                                        statusMessage = "⚠️ Payment failed: ${err.message}"
                                    }
                                isLoading = false
                            }
                        },
                        onHoldTicket = { bus ->
                            val bId = selectedBoardingStopId ?: return@RouteSearchScreen
                            val dId = selectedDestStopId ?: return@RouteSearchScreen
                            scope.launch {
                                isLoading = true
                                app.transitRepository.holdTicket(bus.tripId, bId, dId)
                                    .onSuccess {
                                        preselectedAlertStopId = dId
                                        preselectedAlertTripId = bus.tripId
                                        statusMessage = "🎟️ Seat Held! Pay ₹${it.fare.toInt()} to the conductor on boarding."
                                        refreshData()
                                        selectedNavIndex = 2
                                    }
                                    .onFailure { err ->
                                        statusMessage = "⚠️ Could not hold seat: ${err.message}"
                                    }
                                isLoading = false
                            }
                        }
                    )
                }
                1 -> LiveTrackingTab(
                    activeTrips = activeTrips,
                    onBookTrip = { selectedTripForBooking = it },
                    onShareJourney = { tripId ->
                        scope.launch {
                            app.transitRepository.shareJourney(tripId).onSuccess {
                                activeShareLink = "https://smart-bus-system-tan.vercel.app/shared-journey/${it.shareToken}"
                                val sendIntent: Intent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "Track my SmartBus journey live: $activeShareLink")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share with Family"))
                            }
                        }
                    }
                )
                2 -> MyTicketsTab(
                    tickets = myTickets,
                    onShowQr = { qrTicketToShow = it },
                    onTrackLive = { tripId ->
                        selectedNavIndex = 1
                    },
                    onShareJourney = { tripId ->
                        scope.launch {
                            app.transitRepository.shareJourney(tripId).onSuccess {
                                activeShareLink = "https://smart-bus-system-tan.vercel.app/shared-journey/${it.shareToken}"
                                val sendIntent: Intent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "Track my SmartBus journey live: $activeShareLink")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share with Family"))
                            }
                        }
                    },
                    onSetWakeUpAlert = { ticket ->
                        preselectedAlertStopId = ticket.destinationStopId
                        preselectedAlertTripId = ticket.tripId
                        showDestAlertModal = true
                    }
                )
                3 -> ProfileTab(
                    passengerName = passengerDisplayName,
                    walletBalance = walletBalance,
                    onTopUpWallet = { amount ->
                        scope.launch {
                            app.transitRepository.topUpWallet(amount).onSuccess { newBal ->
                                walletBalance = newBal
                                statusMessage = "✅ ₹${amount.toInt()} added to Tap & Pay Wallet!"
                            }
                        }
                    },
                    onOpenPermanentPass = { showPermanentPassModal = true },
                    onLogout = {
                        scope.launch {
                            app.authRepository.logout()
                            onLogout()
                        }
                    }
                )
            }

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    // Booking Dialog
    if (selectedTripForBooking != null) {
        val trip = selectedTripForBooking!!
        val stops = trip.route?.stops ?: emptyList()
        var selectedBoardingId by remember { mutableStateOf(stops.firstOrNull()?.stopId ?: "") }
        var selectedDestId by remember { mutableStateOf(stops.lastOrNull()?.stopId ?: "") }

        AlertDialog(
            onDismissRequest = { selectedTripForBooking = null },
            title = { Text("Book Digital Bus Ticket") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Route: ${trip.route?.name} (${trip.route?.code})", fontWeight = FontWeight.Bold)
                    Text("Bus Number: ${trip.bus?.busNumber}", style = MaterialTheme.typography.bodySmall)

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Select Boarding Stop:", style = MaterialTheme.typography.labelMedium)
                    stops.forEach { rs ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedBoardingId == rs.stopId,
                                onClick = { selectedBoardingId = rs.stopId }
                            )
                            Text(rs.stop.name, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Select Destination Stop:", style = MaterialTheme.typography.labelMedium)
                    stops.forEach { rs ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedDestId == rs.stopId,
                                onClick = { selectedDestId = rs.stopId }
                            )
                            Text(rs.stop.name, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            app.transitRepository.bookTicket(trip.id, selectedBoardingId, selectedDestId)
                                .onSuccess {
                                    selectedTripForBooking = null
                                    refreshData()
                                    selectedNavIndex = 2 // Go to Tickets tab
                                }
                        }
                    }
                ) {
                    Text("Confirm & Pay")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedTripForBooking = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // QR Code Display Dialog
    if (qrTicketToShow != null) {
        val ticket = qrTicketToShow!!

        Dialog(onDismissRequest = { qrTicketToShow = null }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Digital Boarding Pass", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Present to Conductor for Verification", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                    Spacer(modifier = Modifier.height(14.dp))

                    RollingQRCode(
                        ticketId = ticket.id,
                        ticketCode = ticket.ticketCode
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Ticket Code: ${ticket.ticketCode.take(8).uppercase()}", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    Text("Fare: ₹${ticket.fare.toInt()} • Status: ${ticket.status}", fontWeight = FontWeight.SemiBold)

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { qrTicketToShow = null }, modifier = Modifier.fillMaxWidth()) {
                        Text("Close")
                    }
                }
            }
        }
    }

    // Permanent Smart Transit Pass Modal
    if (showPermanentPassModal) {
        val qrPayload = "SMARTBUS-PASS:$permanentPassCode:$passengerDisplayName"
        val qrBitmap = remember(qrPayload) { generateQrCode(qrPayload) }
        var isRegenerating by remember { mutableStateOf(false) }

        Dialog(onDismissRequest = { showPermanentPassModal = false }) {
            Card(
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Smart Card Top Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("KARNATAKA TRANSIT PASS", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Color(0xFF0F172A))
                            Text("SmartBus Permanent Digital ID", fontSize = 11.sp, color = Color(0xFF0284C7), fontWeight = FontWeight.SemiBold)
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFDCFCE7)
                        ) {
                            Text("ACTIVE", color = Color(0xFF15803D), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // QR Display Container
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(16.dp))
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "Permanent Transit Pass QR",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Pass ID: $permanentPassCode", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
                    Text("Holder: $passengerDisplayName", fontSize = 13.sp, color = Color.Gray)
                    Text("Corridor: Udupi – Katpadi – Shirva", fontSize = 11.sp, color = Color(0xFF0284C7))

                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "ℹ️ Permanent QR: Valid indefinitely until you change it. Present this to the conductor on boarding, or print it out to stick on your card.",
                            fontSize = 11.sp,
                            color = Color(0xFF475569),
                            modifier = Modifier.padding(10.dp),
                            lineHeight = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        enabled = !isRegenerating,
                        onClick = {
                            isRegenerating = true
                            scope.launch {
                                app.transitRepository.regeneratePermanentPass().onSuccess { updated ->
                                    permanentPassCode = updated.passCode
                                    statusMessage = "🔄 Permanent Pass QR updated to ${updated.passCode}!"
                                }
                                isRegenerating = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isRegenerating) "Updating QR..." else "Change / Regenerate Pass QR", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                showPrintableIdCard = true
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Print ID Card", fontSize = 12.sp)
                        }

                        Button(
                            onClick = { showPermanentPassModal = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Done", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // Printable Physical ID Badge Dialog
    if (showPrintableIdCard) {
        val qrPayload = "SMARTBUS-PASS:$permanentPassCode:$passengerDisplayName"
        val qrBitmap = remember(qrPayload) { generateQrCode(qrPayload) }

        Dialog(onDismissRequest = { showPrintableIdCard = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Printable Smart Transit Card Badge", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Stick-on / Wallet Size (CR80 Standard)", fontSize = 11.sp, color = Color.Gray)

                    Spacer(modifier = Modifier.height(12.dp))

                    // Physical Card Preview Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), RoundedCornerShape(14.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("KARNATAKA SMARTBUS", color = Color(0xFFF59E0B), fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 1.sp)
                                Text("TRANSIT PASS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(passengerDisplayName.uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("Pass: $permanentPassCode", color = Color(0xFF94A3B8), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                    Text("Route: Udupi - Shirva", color = Color(0xFF38BDF8), fontSize = 11.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("VALID PERMANENTLY", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                }

                                if (qrBitmap != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(75.dp)
                                            .background(Color.White, RoundedCornerShape(8.dp))
                                            .padding(4.dp)
                                    ) {
                                        Image(
                                            bitmap = qrBitmap.asImageBitmap(),
                                            contentDescription = "Card QR",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "✂️ You can screenshot this badge, print it on paper/PVC, and stick it on your physical ID or wallet. The conductor's app will scan it just like a physical bus pass.",
                        fontSize = 11.sp,
                        color = Color(0xFF475569),
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showPrintableIdCard = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Close")
                        }
                        Button(
                            onClick = {
                                if (qrBitmap != null) {
                                    val printableBmp = createPrintableIdCardBitmap(passengerDisplayName, permanentPassCode, qrBitmap)
                                    val printHelper = PrintHelper(context).apply {
                                        scaleMode = PrintHelper.SCALE_MODE_FIT
                                    }
                                    printHelper.printBitmap("SmartBus_Pass_$permanentPassCode", printableBmp)
                                }
                            },
                            modifier = Modifier.weight(1.4f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Print / Save PDF")
                        }
                    }
                }
            }
        }
    }

    // Night Sleep Destination Wake-Up Alarm Setup Dialog
    if (showDestAlertModal) {
        val fallbackStops = remember {
            listOf(
                Stop("stop-1", "Udupi Service Bus Stand", 13.3409, 74.7421),
                Stop("stop-2", "Kinnimulki", 13.3235, 74.7388),
                Stop("stop-3", "Katpadi Junction", 13.2848, 74.7505),
                Stop("stop-4", "Shankarapura", 13.2603, 74.7712),
                Stop("stop-5", "Shirva Bus Stand", 13.2188, 74.8223)
            )
        }
        val availableStops = if (allStops.isNotEmpty()) allStops else fallbackStops
        var pickedStop by remember(preselectedAlertStopId, availableStops) {
            mutableStateOf(
                availableStops.find { it.id == preselectedAlertStopId }
                    ?: availableStops.find { it.name.contains("Katpadi", ignoreCase = true) }
                    ?: availableStops.last()
            )
        }
        var selectedRadius by remember { mutableStateOf(1000) }
        var isLoudNightMode by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showDestAlertModal = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bedtime, contentDescription = null, tint = Color(0xFF6D28D9))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Destination Wake-Up Alarm", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Designed for night & sleepy travel: monitors both your Phone GPS and live Bus GPS in background, then triggers a loud alarm & vibration before you miss your stop.",
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )

                    if (destAlertState.isActive) {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEDE9FE))) {
                            Column(modifier = Modifier.padding(10.dp).fillMaxWidth()) {
                                Text("Currently Monitoring: ${destAlertState.stopName}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF4C1D95))
                                val distTxt = destAlertState.distanceMeters?.let { m ->
                                    if (m >= 1000) String.format("%.1f km away", m / 1000.0) else "${m.roundToInt()} m away"
                                } ?: "Acquiring GPS..."
                                Text("$distTxt • via ${destAlertState.sourceLabel}", fontSize = 11.sp, color = Color(0xFF6D28D9))
                            }
                        }
                    }

                    Text("Select Alighting Stop:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    availableStops.forEach { stop ->
                        Surface(
                            onClick = { pickedStop = stop },
                            shape = RoundedCornerShape(10.dp),
                            color = if (pickedStop.id == stop.id) Color(0xFFEDE9FE) else Color(0xFFF8FAFC),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (pickedStop.id == stop.id) Color(0xFF7C3AED) else Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stop.name, fontSize = 13.sp, fontWeight = if (pickedStop.id == stop.id) FontWeight.Bold else FontWeight.Normal)
                                if (pickedStop.id == stop.id) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF6D28D9), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    Text("Wake Me Up Before Reaching Stop:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(500 to "500 m", 1000 to "1 km", 2000 to "2 km").forEach { (meters, label) ->
                            FilterChip(
                                selected = selectedRadius == meters,
                                onClick = { selectedRadius = meters },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Loud Night Alarm + Vibrate", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Rings continuous alarm sound until you dismiss", fontSize = 10.sp, color = Color.Gray)
                        }
                        Switch(checked = isLoudNightMode, onCheckedChange = { isLoudNightMode = it })
                    }

                    OutlinedButton(
                        onClick = {
                            launchWithAlertPermissions {
                                DestinationAlertService.triggerTestAlarm(context, pickedStop.name)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Night Alarm Sound & Vibrate", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val tripId = preselectedAlertTripId ?: activeTrips.firstOrNull()?.id
                        launchWithAlertPermissions {
                            DestinationAlertService.startAlert(
                                context = context,
                                stopName = pickedStop.name,
                                lat = pickedStop.latitude,
                                lng = pickedStop.longitude,
                                radiusMeters = selectedRadius,
                                isNightLoudMode = isLoudNightMode,
                                tripId = tripId
                            )
                            statusMessage = "🛌 Wake-Up Alarm armed for ${pickedStop.name} (${if (selectedRadius >= 1000) "${selectedRadius / 1000} km" else "$selectedRadius m"} before stop)!"
                            showDestAlertModal = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6D28D9))
                ) {
                    Text("Start Wake-Up Alarm")
                }
            },
            dismissButton = {
                Row {
                    if (destAlertState.isActive) {
                        TextButton(onClick = {
                            DestinationAlertService.stopAlert(context)
                            statusMessage = "Alarm turned off."
                            showDestAlertModal = false
                        }) {
                            Text("Turn Off", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { showDestAlertModal = false }) { Text("Cancel") }
                }
            }
        )
    }

    // Active Ringing Wake-Up Alarm Modal (Full Alert)
    if (destAlertState.isRinging) {
        Dialog(onDismissRequest = {}) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D)),
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Alarm,
                        contentDescription = null,
                        tint = Color(0xFFFDE047),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "WAKE UP! YOUR STOP IS NEXT",
                        color = Color(0xFFFDE047),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        destAlertState.stopName,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val distStr = destAlertState.distanceMeters?.let { m ->
                        if (m >= 1000) String.format("%.1f km away", m / 1000.0) else "${m.roundToInt()} meters away"
                    } ?: "Approaching stop"
                    Text(
                        "$distStr • Prepare to alight!",
                        color = Color(0xFFFECACA),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(22.dp))
                    Button(
                        onClick = { DestinationAlertService.dismissAlarm(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(54.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("I'M AWAKE • DISMISS ALARM", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ExploreRoutesTab(
    routes: List<Route>,
    activeTrips: List<Trip>,
    originQuery: String,
    destinationQuery: String,
    onOriginChange: (String) -> Unit,
    onDestChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBookTrip: (Trip) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Origin-Destination Search Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = originQuery,
                    onValueChange = onOriginChange,
                    label = { Text("From (Origin Bus Stop)") },
                    leadingIcon = { Icon(Icons.Default.TripOrigin, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = destinationQuery,
                    onValueChange = onDestChange,
                    label = { Text("To (Destination Stop)") },
                    leadingIcon = { Icon(Icons.Default.Place, contentDescription = null, tint = Color.Red) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onSearch,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Find Buses & Schedules")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Available Routes (${routes.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(routes) { route ->
                val trip = activeTrips.firstOrNull { it.routeId == route.id }
                Card(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(route.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    route.code,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (!route.description.isNullOrEmpty()) {
                            Text(route.description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "${route.stops.size} stops on this line",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (trip != null) {
                                Text("Bus ${trip.bus?.busNumber} is RUNNING", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Button(onClick = { onBookTrip(trip) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                                    Text("Book Ticket", fontSize = 12.sp)
                                }
                            } else {
                                Text("No active bus right now", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiveTrackingTab(
    activeTrips: List<Trip>,
    onBookTrip: (Trip) -> Unit,
    onShareJourney: (String) -> Unit
) {
    var isMapView by remember { mutableStateOf(true) }
    var selectedTrip by remember { mutableStateOf<Trip?>(activeTrips.firstOrNull()) }

    LaunchedEffect(activeTrips) {
        if (selectedTrip == null || activeTrips.none { it.id == selectedTrip?.id }) {
            selectedTrip = activeTrips.firstOrNull()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Toggle bar between Map View and List View
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Fleet Live Tracking",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "${activeTrips.size} active buses on route",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            // View Mode Segmented Buttons
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(modifier = Modifier.padding(3.dp)) {
                    Surface(
                        onClick = { isMapView = true },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isMapView) MaterialTheme.colorScheme.primary else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Map,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isMapView) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Map",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isMapView) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        onClick = { isMapView = false },
                        shape = RoundedCornerShape(16.dp),
                        color = if (!isMapView) MaterialTheme.colorScheme.primary else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.List,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (!isMapView) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "List",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (!isMapView) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (isMapView) {
            BusLiveMap(
                activeTrips = activeTrips,
                selectedTrip = selectedTrip,
                onSelectTrip = { selectedTrip = it },
                onBookTicket = onBookTrip,
                onShareJourney = onShareJourney,
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (activeTrips.isEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                            Box(modifier = Modifier.padding(32.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("No active buses on the road right now.", color = Color.Gray)
                            }
                        }
                    }
                }

                items(activeTrips) { trip ->
                    val loc = trip.lastLocation
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Bus ${trip.bus?.busNumber}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Text(trip.route?.name ?: "Active Route", style = MaterialTheme.typography.bodySmall)
                                }

                                if (loc?.isStale == true) {
                                    Surface(color = Color(0xFFFEF3C7), shape = RoundedCornerShape(6.dp)) {
                                        Text("Stale GPS", color = Color(0xFFB45309), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                } else if (loc != null) {
                                    Surface(color = Color(0xFFD1FAE5), shape = RoundedCornerShape(6.dp)) {
                                        Text("LIVE", color = Color(0xFF047857), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (loc != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("Coordinates: ${loc.latitude.toString().take(8)}, ${loc.longitude.toString().take(8)}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                        Text("Vehicle Speed: ${loc.speed?.toInt() ?: 0} km/h • Heading: ${loc.heading?.toInt() ?: 0}°", fontSize = 12.sp)
                                        Text("Updated: ${loc.ageSeconds}s ago", fontSize = 11.sp, color = Color.Gray)
                                    }
                                }
                            } else {
                                Text("Acquiring GPS fix from driver device...", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onBookTrip(trip) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.ConfirmationNumber, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Book Ticket", fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = { onShareJourney(trip.id) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Share", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MyTicketsTab(
    tickets: List<Ticket>,
    onShowQr: (Ticket) -> Unit,
    onTrackLive: (String) -> Unit,
    onShareJourney: (String) -> Unit,
    onSetWakeUpAlert: (Ticket) -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text("Your Digital Tickets & Passes", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                Text("Tap 'Show QR Pass' for dynamic rotating conductor verification", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }

        if (tickets.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Box(modifier = Modifier.padding(32.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No tickets booked yet. Search buses to book or hold a seat!", color = Color.Gray)
                    }
                }
            }
        }

        items(tickets) { ticket ->
            BoardingPassCard(
                ticket = ticket,
                onShowRollingQr = { onShowQr(ticket) },
                onTrackLive = { onTrackLive(ticket.tripId) },
                onShareWithFamily = { onShareJourney(ticket.tripId) },
                onSetWakeUpAlert = { onSetWakeUpAlert(ticket) }
            )
        }
    }
}

@Composable
fun ProfileTab(
    passengerName: String,
    walletBalance: Double,
    onTopUpWallet: (Double) -> Unit,
    onOpenPermanentPass: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(passengerName, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        Text("SmartBus Transit Member • Verified", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

        Spacer(modifier = Modifier.height(16.dp))

        // Permanent Smart Transit ID Pass Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFF59E0B),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Default.Badge, contentDescription = null, tint = Color.Black, modifier = Modifier.padding(10.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Permanent Transit Pass", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        Text("Stick-on / Printable ID • Valid 24/7", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }
                }
                Button(
                    onClick = onOpenPermanentPass,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("View Card", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // SmartBus Transit Wallet Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("SMARTBUS WALLET", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text("₹${walletBalance.toInt()}", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Button(
                        onClick = { onTopUpWallet(100.0) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+₹100", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Use for instant contactless 1-tap bus boarding", color = Color.Gray, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Escrow & Auto-Refund Protection Card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF3B82F6),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.padding(8.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Escrow & Auto-Refund Protection", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E3A8A))
                    Text(
                        "If you miss your bus or it gets cancelled, unboarded fares are automatically refunded to your account upon trip completion.",
                        fontSize = 11.sp,
                        color = Color(0xFF1E40AF),
                        lineHeight = 15.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Features list
        Card(
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("SmartBus Transit Features:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("• Dynamic 15-Sec Anti-Screenshot QR Code", fontSize = 12.sp, color = Color.DarkGray)
                Text("• Hold & Board with On-Bus Settlement", fontSize = 12.sp, color = Color.DarkGray)
                Text("• Live GPS Telemetry & Crowd Occupancy", fontSize = 12.sp, color = Color.DarkGray)
                Text("• Real-Time Driver Delay Broadcasts", fontSize = 12.sp, color = Color.DarkGray)
                Text("• Family Journey Live Tracking Links", fontSize = 12.sp, color = Color.DarkGray)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onLogout,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Logout, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sign Out", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// QR Code generator helper function
fun generateQrCode(text: String): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 512, 512)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bmp
    } catch (e: Exception) {
        null
    }
}

// Render high-res CR80 printable ID card bitmap with QR code for PrintHelper
fun createPrintableIdCardBitmap(name: String, passCode: String, qrBitmap: Bitmap): Bitmap {
    val width = 1000
    val height = 620
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    // White print margin background
    canvas.drawColor(android.graphics.Color.WHITE)

    // Card Dark Navy Background
    val bgPaint = Paint().apply { color = android.graphics.Color.parseColor("#0F172A") }
    canvas.drawRoundRect(40f, 40f, 960f, 580f, 36f, 36f, bgPaint)

    val goldPaint = Paint().apply {
        color = android.graphics.Color.parseColor("#F59E0B")
        textSize = 34f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    canvas.drawText("KARNATAKA SMARTBUS • TRANSIT PASS", 80f, 115f, goldPaint)

    val namePaint = Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 46f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    canvas.drawText(name.uppercase(), 80f, 220f, namePaint)

    val subPaint = Paint().apply {
        color = android.graphics.Color.parseColor("#94A3B8")
        textSize = 30f
        typeface = Typeface.MONOSPACE
    }
    canvas.drawText("Pass ID: $passCode", 80f, 285f, subPaint)

    val routePaint = Paint().apply {
        color = android.graphics.Color.parseColor("#38BDF8")
        textSize = 28f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    canvas.drawText("Route: Udupi – Katpadi – Shirva", 80f, 355f, routePaint)

    val validPaint = Paint().apply {
        color = android.graphics.Color.parseColor("#10B981")
        textSize = 26f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    canvas.drawText("PERMANENT ID • VALID UNTIL CHANGED", 80f, 515f, validPaint)

    // White QR container
    val whitePaint = Paint().apply { color = android.graphics.Color.WHITE }
    canvas.drawRoundRect(640f, 175f, 920f, 455f, 20f, 20f, whitePaint)
    val scaledQr = Bitmap.createScaledBitmap(qrBitmap, 250, 250, false)
    canvas.drawBitmap(scaledQr, 655f, 190f, null)

    return bmp
}
