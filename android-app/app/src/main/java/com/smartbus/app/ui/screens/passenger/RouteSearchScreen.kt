package com.smartbus.app.ui.screens.passenger

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartbus.app.data.model.BusSearchResult
import com.smartbus.app.data.model.Stop
import com.smartbus.app.ui.components.BusSeatSelector
import com.smartbus.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteSearchScreen(
    stops: List<Stop>,
    busResults: List<BusSearchResult>,
    isSearching: Boolean,
    errorMsg: String?,
    selectedBoardingStopId: String?,
    selectedDestStopId: String?,
    onSelectBoardingStop: (String) -> Unit,
    onSelectDestStop: (String) -> Unit,
    onSearch: () -> Unit,
    onBookNow: (BusSearchResult) -> Unit,
    onHoldTicket: (BusSearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    var showBoardingDropdown by remember { mutableStateOf(false) }
    var showDestDropdown by remember { mutableStateOf(false) }
    var selectedDateTab by remember { mutableStateOf("Today") }
    var selectedFilterChip by remember { mutableStateOf("All") }

    // Seat selector modal
    var selectedBusForSeatSelection by remember { mutableStateOf<BusSearchResult?>(null) }

    val boardingStopName = stops.find { it.id == selectedBoardingStopId }?.name ?: ""
    val destStopName = stops.find { it.id == selectedDestStopId }?.name ?: ""

    var boardingQuery by remember(boardingStopName) { mutableStateOf(boardingStopName) }
    var destQuery by remember(destStopName) { mutableStateOf(destStopName) }

    val filteredBoardingStops = remember(stops, boardingQuery) {
        if (boardingQuery.isBlank()) stops
        else stops.filter { it.name.contains(boardingQuery, ignoreCase = true) }
    }

    val filteredDestStops = remember(stops, destQuery) {
        if (destQuery.isBlank()) stops
        else stops.filter { it.name.contains(destQuery, ignoreCase = true) }
    }

    // Filter results
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
            onConfirmBooking = { _, _ ->
                onBookNow(bus)
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
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                            text = "Find & Book Buses",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = TransitNavy
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Origin & Destination with Swap Button
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // From Stop (Type or Select)
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
                                    label = { Text("From (Type or Select Stop)") },
                                    placeholder = { Text("e.g. Udupi, Katpadi...") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.TripOrigin,
                                            contentDescription = null,
                                            tint = BusGreen,
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
                                    expanded = showBoardingDropdown && filteredBoardingStops.isNotEmpty(),
                                    onDismissRequest = { showBoardingDropdown = false }
                                ) {
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
                                    label = { Text("To (Type or Select Destination)") },
                                    placeholder = { Text("e.g. Shirva, Shankarapura...") },
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

                        // Swap Button in between
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

                    // Date Selector Chips
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

                    Spacer(modifier = Modifier.height(16.dp))

                    // CTA Search Button
                    Button(
                        onClick = onSearch,
                        enabled = !isSearching && selectedBoardingStopId != null && selectedDestStopId != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TransitPrimary)
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SEARCH BUSES", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }

        // Filter Bar Chips
        if (busResults.isNotEmpty()) {
            item {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filterChips = listOf("All", "Available > 10", "Express", "Low Fare")
                    items(filterChips) { chip ->
                        FilterChip(
                            selected = selectedFilterChip == chip,
                            onClick = { selectedFilterChip = chip },
                            label = { Text(chip, fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TransitNavy,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        // Error message if any
        if (errorMsg != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = BusRed)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(errorMsg, color = BusRed, fontSize = 13.sp)
                    }
                }
            }
        }

        // Results Section Header
        if (busResults.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${filteredResults.size} Buses Available",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TransitNavy
                    )
                    Text(
                        "Live Fleet Pricing",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }

        // Bus Results List
        items(filteredResults) { bus ->
            BusResultCard(
                bus = bus,
                onSelectSeats = { selectedBusForSeatSelection = bus },
                onQuickHold = { onHoldTicket(bus) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun BusResultCard(
    bus: BusSearchResult,
    onSelectSeats: () -> Unit,
    onQuickHold: () -> Unit
) {
    val isFillingFast = bus.seatsAvailable in 1..5

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Operator Name, Bus No & Rating
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SmartBus City Express",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = TransitNavy
                    )
                    Text(
                        text = "${bus.busNumber} • AC Electric 2+2",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BusGreen
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("4.7", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Journey Timeline (Departure -> Duration -> Arrival)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "08:30 AM",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TransitNavy
                    )
                    Text(
                        bus.boardingStop.name,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        maxLines = 1
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("~45 mins", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                    Divider(color = Color(0xFFCBD5E1), thickness = 1.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                    Text("Direct Route", fontSize = 10.sp, color = BusGreen, fontWeight = FontWeight.Bold)
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        "09:15 AM",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TransitNavy
                    )
                    Text(
                        bus.destinationStop.name,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Divider(color = Color(0xFFF1F5F9), thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Amenities & Seat Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Amenities Row
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("❄️ AC", fontSize = 11.sp)
                    Text("🔌 USB", fontSize = 11.sp)
                    Text("📍 Live GPS", fontSize = 11.sp)
                }

                // Fare with Discount
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "₹${(bus.fare * 1.2).toInt()}",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "₹${bus.fare.toInt()}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TransitPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Seat Availability Badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isFillingFast) Color(0xFFFEF3C7) else Color(0xFFECFDF5),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isFillingFast) Icons.Default.Warning else Icons.Default.EventSeat,
                        contentDescription = null,
                        tint = if (isFillingFast) BusAmber else BusGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isFillingFast) "Only ${bus.seatsAvailable} seats left - Filling fast!" else "${bus.seatsAvailable} seats available",
                        fontSize = 12.sp,
                        color = if (isFillingFast) Color(0xFF92400E) else Color(0xFF065F46),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Actions Row: [Hold & Board] & [Select Seats]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onQuickHold,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BusAmber),
                    border = BorderStroke(1.5.dp, BusAmber)
                ) {
                    Icon(Icons.Default.HourglassTop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Hold & Board", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onSelectSeats,
                    modifier = Modifier
                        .weight(1.3f)
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TransitPrimary)
                ) {
                    Icon(Icons.Default.AirlineSeatReclineNormal, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Select Seats", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
