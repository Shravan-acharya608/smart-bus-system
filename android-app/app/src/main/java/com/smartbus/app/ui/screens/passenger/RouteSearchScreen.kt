package com.smartbus.app.ui.screens.passenger

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartbus.app.data.model.BusSearchResult
import com.smartbus.app.data.model.Stop
import com.smartbus.app.data.model.Trip
import com.smartbus.app.data.model.UdupiScheduleItem
import com.smartbus.app.data.model.UdupiStopCoord
import com.smartbus.app.ui.components.BusSeatSelector
import com.smartbus.app.ui.theme.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteSearchScreen(
    stops: List<Stop>,
    allSchedules: List<UdupiScheduleItem> = UdupiLocalTimetable.all139Schedules,
    activeTrips: List<Trip> = emptyList(),
    busResults: List<BusSearchResult>,
    isSearching: Boolean,
    errorMsg: String?,
    selectedBoardingStopId: String?,
    selectedDestStopId: String?,
    userLat: Double? = null,
    userLng: Double? = null,
    currentLocationLabel: String? = null,
    isLocatingUser: Boolean = false,
    onUseCurrentLocation: () -> Unit = {},
    onSelectBoardingStop: (String) -> Unit,
    onSelectDestStop: (String) -> Unit,
    onSearch: () -> Unit,
    onBookNow: (BusSearchResult, String) -> Unit,
    onHoldTicket: (BusSearchResult) -> Unit,
    onBookScheduleBus: (UdupiScheduleItem, String) -> Unit = { _, _ -> },
    onHoldScheduleBus: (UdupiScheduleItem) -> Unit = {},
    onSetWakeUpAlertForStop: (UdupiStopCoord, String?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var showBoardingDropdown by remember { mutableStateOf(false) }
    var showDestDropdown by remember { mutableStateOf(false) }
    var selectedDateTab by remember { mutableStateOf("Today") }
    var selectedFilterChip by remember { mutableStateOf("All") }

    // Modals
    var selectedBusForSeatSelection by remember { mutableStateOf<BusSearchResult?>(null) }
    var selectedScheduleForMap by remember { mutableStateOf<UdupiScheduleItem?>(null) }
    var selectedScheduleForBooking by remember { mutableStateOf<UdupiScheduleItem?>(null) }

    val boardingStop = stops.find { it.id == selectedBoardingStopId }
    val destStop = stops.find { it.id == selectedDestStopId }
    val boardingStopName = currentLocationLabel ?: boardingStop?.name ?: ""
    val destStopName = destStop?.name ?: ""

    var boardingQuery by remember(boardingStopName) { mutableStateOf(boardingStopName) }
    var destQuery by remember(destStopName) { mutableStateOf(destStopName) }

    // Effective stop keyword for route-matching ("Near Katpadi" -> "Katpadi")
    val effectiveFromTerm = remember(boardingQuery, boardingStop, currentLocationLabel) {
        val raw = boardingStop?.name ?: currentLocationLabel ?: boardingQuery
        raw.replace("📍 Near ", "")
            .replace("Current Location (", "")
            .substringBefore("•")
            .replace(")", "")
            .replace(" Bus Stand", "")
            .trim()
    }
    val effectiveToTerm = remember(destQuery, destStop) {
        (destStop?.name ?: destQuery).replace(" Bus Stand", "").trim()
    }

    val filteredBoardingStops = remember(stops, boardingQuery) {
        val q = boardingQuery.replace("📍 Near ", "").trim()
        if (q.isBlank() || boardingQuery.startsWith("📍")) stops.take(25)
        else stops.filter { it.name.contains(q, ignoreCase = true) }.take(25)
    }

    val filteredDestStops = remember(stops, destQuery) {
        if (destQuery.isBlank()) stops.take(25)
        else stops.filter { it.name.contains(destQuery, ignoreCase = true) }.take(25)
    }

    // Match ALL buses from the 139 Udupi schedules whose route goes along effectiveFromTerm (-> effectiveToTerm)
    val matchingWaySchedules = remember(allSchedules, effectiveFromTerm, effectiveToTerm) {
        if (effectiveFromTerm.isBlank() && effectiveToTerm.isBlank()) {
            emptyList()
        } else {
            allSchedules.filter { bus ->
                val stopNames = bus.stops.map { it.name }
                val fromIdx = if (effectiveFromTerm.isBlank()) 0
                else stopNames.indexOfFirst { it.contains(effectiveFromTerm, ignoreCase = true) }

                if (fromIdx == -1) return@filter false

                if (effectiveToTerm.isBlank()) {
                    fromIdx < stopNames.size - 1 || bus.from.contains(effectiveFromTerm, ignoreCase = true)
                } else {
                    val toIdx = stopNames.indexOfFirst { it.contains(effectiveToTerm, ignoreCase = true) }
                    toIdx > fromIdx
                }
            }
        }
    }

    val filteredResults = remember(busResults, selectedFilterChip) {
        when (selectedFilterChip) {
            "Available > 10" -> busResults.filter { it.seatsAvailable >= 10 }
            "Low Fare" -> busResults.filter { it.fare <= 50.0 }
            "Express" -> busResults.filter { it.routeCode.contains("E", ignoreCase = true) || it.routeName.contains("Express", ignoreCase = true) }
            else -> busResults
        }
    }

    if (selectedBusForSeatSelection != null) {
        val bus = selectedBusForSeatSelection!!
        BusSeatSelector(
            busNumber = bus.busNumber,
            routeName = bus.routeName,
            baseFare = bus.fare,
            availableSeatsCount = bus.seatsAvailable,
            onConfirmBooking = { _, _, paymentMode ->
                onBookNow(bus, paymentMode)
                selectedBusForSeatSelection = null
            },
            onHoldBooking = { _, _ ->
                onHoldTicket(bus)
                selectedBusForSeatSelection = null
            },
            onDismiss = { selectedBusForSeatSelection = null }
        )
        return
    }

    if (selectedScheduleForBooking != null) {
        val sch = selectedScheduleForBooking!!
        BusSeatSelector(
            busNumber = "#${sch.slNo} (${sch.departureTime})",
            routeName = "${sch.from} → ${sch.to} (Via ${sch.via})",
            baseFare = UdupiLocalTimetable.estimateFareForBus(sch),
            availableSeatsCount = 32,
            onConfirmBooking = { _, _, paymentMode ->
                onBookScheduleBus(sch, paymentMode)
                selectedScheduleForBooking = null
            },
            onHoldBooking = { _, _ ->
                onHoldScheduleBus(sch)
                selectedScheduleForBooking = null
            },
            onDismiss = { selectedScheduleForBooking = null }
        )
        return
    }

    if (selectedScheduleForMap != null) {
        SelectedScheduleBusMapDialog(
            bus = selectedScheduleForMap!!,
            activeTrips = activeTrips,
            userLat = userLat,
            userLng = userLng,
            onRequestMyLocation = onUseCurrentLocation,
            onBookSlot = onBookScheduleBus,
            onHoldSlot = onHoldScheduleBus,
            onSetWakeUpAlarmForStop = { st, tripId ->
                selectedScheduleForMap = null
                onSetWakeUpAlertForStop(st, tripId)
            },
            onDismiss = { selectedScheduleForMap = null }
        )
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        // Hero Search Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = TransitSurfaceTint,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.DirectionsBus,
                                    contentDescription = null,
                                    tint = TransitPrimary,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Find Buses Your Way",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = TransitNavy
                            )
                        }

                        // Quick Current Location Chip
                        AssistChip(
                            onClick = onUseCurrentLocation,
                            label = {
                                Text(
                                    if (isLocatingUser) "Locating..." else "Current GPS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.MyLocation,
                                    contentDescription = "Use Current Location",
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // From Stop (Current Location OR Type OR Select)
                            ExposedDropdownMenuBox(
                                expanded = showBoardingDropdown,
                                onExpandedChange = { showBoardingDropdown = it }
                            ) {
                                OutlinedTextField(
                                    value = boardingQuery,
                                    onValueChange = {
                                        boardingQuery = it
                                        showBoardingDropdown = true
                                        val match = stops.find { s -> s.name.equals(it.trim(), ignoreCase = true) }
                                        if (match != null) onSelectBoardingStop(match.id)
                                    },
                                    readOnly = false,
                                    label = { Text("From (Current Location or Stop)") },
                                    placeholder = { Text("Tap 'Current GPS' or type Udupi, Katpadi...") },
                                    leadingIcon = {
                                        Icon(
                                            if (boardingQuery.startsWith("📍")) Icons.Default.MyLocation else Icons.Default.TripOrigin,
                                            contentDescription = null,
                                            tint = if (boardingQuery.startsWith("📍")) Color(0xFF2563EB) else BusGreen,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showBoardingDropdown) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(),
                                    shape = RoundedCornerShape(14.dp)
                                )

                                ExposedDropdownMenu(
                                    expanded = showBoardingDropdown,
                                    onDismissRequest = { showBoardingDropdown = false }
                                ) {
                                    // First Dropdown Item: Use My Current Location
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.MyLocation, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Use Current Location (Phone GPS)", fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8))
                                            }
                                        },
                                        onClick = {
                                            showBoardingDropdown = false
                                            onUseCurrentLocation()
                                        }
                                    )
                                    HorizontalDivider()
                                    filteredBoardingStops.forEach { stop ->
                                        DropdownMenuItem(
                                            text = { Text(stop.name) },
                                            onClick = {
                                                boardingQuery = stop.name
                                                onSelectBoardingStop(stop.id)
                                                showBoardingDropdown = false
                                            }
                                        )
                                    }
                                }
                            }

                            // To Stop (Type or Select)
                            ExposedDropdownMenuBox(
                                expanded = showDestDropdown,
                                onExpandedChange = { showDestDropdown = it }
                            ) {
                                OutlinedTextField(
                                    value = destQuery,
                                    onValueChange = {
                                        destQuery = it
                                        showDestDropdown = true
                                        val match = stops.find { s -> s.name.equals(it.trim(), ignoreCase = true) }
                                        if (match != null) onSelectDestStop(match.id)
                                    },
                                    readOnly = false,
                                    label = { Text("To (Optional — Destination)") },
                                    placeholder = { Text("e.g. Karkala, Perdoor, Hebri, Shirva...") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = TransitPrimary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showDestDropdown) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(),
                                    shape = RoundedCornerShape(14.dp)
                                )

                                ExposedDropdownMenu(
                                    expanded = showDestDropdown && filteredDestStops.isNotEmpty(),
                                    onDismissRequest = { showDestDropdown = false }
                                ) {
                                    filteredDestStops.forEach { stop ->
                                        DropdownMenuItem(
                                            text = { Text(stop.name) },
                                            onClick = {
                                                destQuery = stop.name
                                                onSelectDestStop(stop.id)
                                                showDestDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Swap Button
                        Surface(
                            shape = CircleShape,
                            color = TransitPrimary,
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 40.dp)
                                .size(36.dp)
                                .clickable {
                                    val tempBoarding = selectedBoardingStopId
                                    val tempDest = selectedDestStopId
                                    val tempQ = boardingQuery
                                    boardingQuery = destQuery
                                    destQuery = tempQ
                                    if (tempBoarding != null && tempDest != null) {
                                        onSelectBoardingStop(tempDest)
                                        onSelectDestStop(tempBoarding)
                                    }
                                }
                        ) {
                            Icon(
                                Icons.Default.SwapVert,
                                contentDescription = "Swap",
                                tint = Color.White,
                                modifier = Modifier.padding(6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Today", "Tomorrow").forEach { date ->
                            FilterChip(
                                selected = selectedDateTab == date,
                                onClick = { selectedDateTab = date },
                                label = { Text(date, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TransitSurfaceTint,
                                    selectedLabelColor = TransitPrimary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // CTA Search Button
                    Button(
                        onClick = onSearch,
                        enabled = !isSearching && selectedBoardingStopId != null && selectedDestStopId != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TransitPrimary)
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SEARCH LIVE BUSES", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        // Way-Matching Scheduled Buses Header (Appears immediately once From OR To is set!)
        if (matchingWaySchedules.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = if (effectiveToTerm.isNotBlank())
                            "${matchingWaySchedules.size} Buses Going From $effectiveFromTerm → $effectiveToTerm"
                        else
                            "${matchingWaySchedules.size} Buses Passing Through $effectiveFromTerm",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = TransitNavy
                    )
                    Text(
                        "Tap any bus for Route Map, Live Navigation, Slot Booking & Wake-Up Alert",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }

            items(matchingWaySchedules.take(40), key = { "way-${it.slNo}" }) { bus ->
                val isOnline = activeTrips.any { it.id == bus.tripId && it.lastLocation != null }
                val destStopCoord = bus.stops.lastOrNull()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, if (isOnline) Color(0xFF10B981) else Color(0xFFE2E8F0)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "#${bus.slNo} • ${bus.from} → ${bus.to}",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = TransitNavy
                                )
                                Text("Via: ${bus.via}", fontSize = 12.sp, color = Color(0xFF475569))
                            }
                            Surface(
                                color = if (isOnline) Color(0xFF059669) else Color(0xFF0284C7),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    if (isOnline) "🟢 LIVE • ${bus.departureTime}" else "Dep ${bus.departureTime}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = { selectedScheduleForMap = bus },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Route Map", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            if (destStopCoord != null) {
                                OutlinedButton(
                                    onClick = { onSetWakeUpAlertForStop(destStopCoord, bus.tripId) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.AlarmAdd, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF6D28D9))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Wake Alert", fontSize = 11.sp, color = Color(0xFF6D28D9), fontWeight = FontWeight.Bold)
                                }
                            }
                            Button(
                                onClick = { selectedScheduleForBooking = bus },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TransitPrimary),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Text("Book ₹${UdupiLocalTimetable.estimateFareForBus(bus).toInt()}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Error banner from DB search
        if (errorMsg != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2))
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = BusRed)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(errorMsg, color = BusRed, fontSize = 13.sp)
                    }
                }
            }
        }

        // Backend DB Search Results
        if (filteredResults.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${filteredResults.size} Direct Corridor Trips", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TransitNavy)
                    Text("Live DB Inventory", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }

            items(filteredResults) { bus ->
                BusResultCard(
                    bus = bus,
                    onSelectSeats = { selectedBusForSeatSelection = bus },
                    onQuickHold = { onHoldTicket(bus) },
                    onWakeAlert = {
                        val destCoord = UdupiLocalTimetable.all139Schedules
                            .flatMap { it.stops }
                            .find { it.name.contains(bus.destinationStop.name, ignoreCase = true) }
                            ?: UdupiStopCoord(2, bus.destinationStop.name, 13.2848, 74.7505)
                        onSetWakeUpAlertForStop(destCoord, bus.tripId)
                    }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun BusResultCard(
    bus: BusSearchResult,
    onSelectSeats: () -> Unit,
    onQuickHold: () -> Unit,
    onWakeAlert: () -> Unit = {}
) {
    val isFillingFast = bus.seatsAvailable in 1..5
    val isOnline = bus.lastLocation != null
    val depLabel = remember(bus.scheduledStart) {
        val t = bus.scheduledStart.substringAfter("T").take(5)
        if (t.length == 5 && t.contains(":")) t else "Scheduled"
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, if (isOnline) Color(0xFF10B981) else Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(bus.routeName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = TransitNavy)
                    Text("${bus.busNumber} • ${bus.routeCode}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isOnline) Color(0xFF059669) else Color(0xFF0284C7)
                ) {
                    Text(
                        if (isOnline) "🟢 LIVE GPS" else "Dep $depLabel",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(depLabel, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TransitNavy)
                    Text(bus.boardingStop.name, fontSize = 12.sp, color = Color.Gray, maxLines = 1)
                }
                Column(modifier = Modifier.weight(0.8f), horizontalAlignment = Alignment.CenterHorizontally) {
                    HorizontalDivider(color = Color(0xFFCBD5E1), thickness = 1.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                    Text("Direct", fontSize = 10.sp, color = BusGreen, fontWeight = FontWeight.Bold)
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("₹${bus.fare.toInt()}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TransitPrimary)
                    Text(bus.destinationStop.name, fontSize = 12.sp, color = Color.Gray, maxLines = 1)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onWakeAlert,
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(Icons.Default.AlarmAdd, contentDescription = "Wake Alert", modifier = Modifier.size(16.dp), tint = Color(0xFF6D28D9))
                }
                OutlinedButton(
                    onClick = onQuickHold,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BusAmber),
                    border = BorderStroke(1.5.dp, BusAmber)
                ) {
                    Text("Hold", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onSelectSeats,
                    modifier = Modifier.weight(1.3f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TransitPrimary)
                ) {
                    Text("Book Slot • ₹${bus.fare.toInt()}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
