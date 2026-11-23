package com.smartbus.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.smartbus.app.ui.theme.*

data class BusSeat(
    val id: String,
    val row: Int,
    val col: String,
    val isBooked: Boolean = false,
    val isWomenOnly: Boolean = false,
    val price: Double = 45.0
)

@Composable
fun BusSeatSelector(
    busNumber: String,
    routeName: String,
    baseFare: Double,
    availableSeatsCount: Int,
    onConfirmBooking: (List<String>, Double) -> Unit,
    onHoldBooking: (List<String>, Double) -> Unit,
    onDismiss: () -> Unit
) {
    val totalRows = 10
    val bookedSeatIds = remember {
        val count = (40 - availableSeatsCount).coerceIn(0, 35)
        val set = mutableSetOf<String>()
        for (i in 1..count) {
            val r = (i % totalRows) + 1
            val c = listOf("A", "B", "C", "D")[i % 4]
            set.add("${r}$c")
        }
        set
    }

    var selectedSeats by remember { mutableStateOf<Set<String>>(emptySet()) }
    val totalFare = selectedSeats.size * baseFare
    var showPaymentModal by remember { mutableStateOf(false) }
    var selectedPaymentMethod by remember { mutableStateOf("WALLET") } // WALLET, RAZORPAY, CASH

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Surface(
                color = TransitPrimary,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Select Seats",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "$busNumber • $routeName",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // Seat Legend
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SeatLegendItem(color = Color.White, borderColor = Color.Gray, label = "Available")
                    SeatLegendItem(color = SeatSelected, borderColor = SeatSelected, label = "Selected")
                    SeatLegendItem(color = SeatBooked, borderColor = SeatBooked, label = "Occupied")
                    SeatLegendItem(color = SeatWomen, borderColor = SeatWomen, label = "Women")
                }
            }

            // Bus Deck
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(2.dp, Color(0xFFE2E8F0)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Driver Cabin Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Entry Door 🚪",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Driver", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFFF1F5F9),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.SportsScore,
                                        contentDescription = "Driver Cabin",
                                        tint = TransitNavy,
                                        modifier = Modifier.padding(6.dp)
                                    )
                                }
                            }
                        }

                        Divider(color = Color(0xFFE2E8F0), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Seats Grid
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(totalRows) { rowIndex ->
                                val rowNum = rowIndex + 1
                                val seatA = "${rowNum}A"
                                val seatB = "${rowNum}B"
                                val seatC = "${rowNum}C"
                                val seatD = "${rowNum}D"

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SeatBox(
                                        seatId = seatA,
                                        isBooked = bookedSeatIds.contains(seatA),
                                        isWomen = rowNum in 1..2,
                                        isSelected = selectedSeats.contains(seatA),
                                        onToggle = { toggleSeat(seatA, selectedSeats) { selectedSeats = it } }
                                    )
                                    SeatBox(
                                        seatId = seatB,
                                        isBooked = bookedSeatIds.contains(seatB),
                                        isWomen = false,
                                        isSelected = selectedSeats.contains(seatB),
                                        onToggle = { toggleSeat(seatB, selectedSeats) { selectedSeats = it } }
                                    )

                                    // Aisle
                                    Box(
                                        modifier = Modifier.width(28.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "$rowNum",
                                            fontSize = 11.sp,
                                            color = Color.LightGray,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    SeatBox(
                                        seatId = seatC,
                                        isBooked = bookedSeatIds.contains(seatC),
                                        isWomen = false,
                                        isSelected = selectedSeats.contains(seatC),
                                        onToggle = { toggleSeat(seatC, selectedSeats) { selectedSeats = it } }
                                    )
                                    SeatBox(
                                        seatId = seatD,
                                        isBooked = bookedSeatIds.contains(seatD),
                                        isWomen = false,
                                        isSelected = selectedSeats.contains(seatD),
                                        onToggle = { toggleSeat(seatD, selectedSeats) { selectedSeats = it } }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Booking Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (selectedSeats.isEmpty()) "Select at least 1 seat" else "Seats: ${selectedSeats.sorted().joinToString(", ")}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedSeats.isEmpty()) Color.Gray else TransitNavy
                            )
                            Text(
                                text = "Fare: ₹${baseFare.toInt()} per seat",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }

                        Text(
                            text = "₹${totalFare.toInt()}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = TransitPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onHoldBooking(selectedSeats.toList(), totalFare) },
                            enabled = selectedSeats.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BusAmber),
                            border = BorderStroke(1.5.dp, if (selectedSeats.isNotEmpty()) BusAmber else Color.LightGray)
                        ) {
                            Icon(Icons.Default.HourglassTop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Hold & Board", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { showPaymentModal = true },
                            enabled = selectedSeats.isNotEmpty(),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TransitPrimary)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Book Now (₹${totalFare.toInt()})", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Instant Payment Modal
        if (showPaymentModal) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { showPaymentModal = false }) {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Confirm Ticket & Pay", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = TransitNavy)
                        Text("$routeName • $busNumber", fontSize = 12.sp, color = Color.Gray)

                        Spacer(modifier = Modifier.height(14.dp))

                        // Fare breakdown box
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Selected Seats:", fontSize = 12.sp, color = Color.Gray)
                                    Text(selectedSeats.sorted().joinToString(", "), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Fare per Seat:", fontSize = 12.sp, color = Color.Gray)
                                    Text("₹${baseFare.toInt()}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                }
                                Divider(color = Color(0xFFE2E8F0), thickness = 1.dp)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Total Payable:", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TransitNavy)
                                    Text("₹${totalFare.toInt()}", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = TransitPrimary)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Select Payment Method", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF334155), modifier = Modifier.align(Alignment.Start))
                        Spacer(modifier = Modifier.height(8.dp))

                        // Option 1: 1-Tap Transit Wallet (Instant Tap & Pay)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, if (selectedPaymentMethod == "WALLET") TransitPrimary else Color(0xFFE2E8F0)),
                            color = if (selectedPaymentMethod == "WALLET") Color(0xFFF0F9FF) else Color.White,
                            modifier = Modifier.fillMaxWidth().clickable { selectedPaymentMethod = "WALLET" }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selectedPaymentMethod == "WALLET", onClick = { selectedPaymentMethod = "WALLET" })
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text("⚡ Instant 1-Tap Transit Wallet", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TransitNavy)
                                    Text("Fastest checkout • Direct wallet deduction", fontSize = 10.sp, color = Color.Gray)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Option 2: Razorpay UPI / Cards
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, if (selectedPaymentMethod == "RAZORPAY") TransitPrimary else Color(0xFFE2E8F0)),
                            color = if (selectedPaymentMethod == "RAZORPAY") Color(0xFFF0F9FF) else Color.White,
                            modifier = Modifier.fillMaxWidth().clickable { selectedPaymentMethod = "RAZORPAY" }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selectedPaymentMethod == "RAZORPAY", onClick = { selectedPaymentMethod = "RAZORPAY" })
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text("💳 Razorpay (UPI / GPay / PhonePe / Card)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TransitNavy)
                                    Text("Instant online UPI or debit/credit payment", fontSize = 10.sp, color = Color.Gray)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Option 3: Hold & Pay to Conductor
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, if (selectedPaymentMethod == "CASH") TransitPrimary else Color(0xFFE2E8F0)),
                            color = if (selectedPaymentMethod == "CASH") Color(0xFFF0F9FF) else Color.White,
                            modifier = Modifier.fillMaxWidth().clickable { selectedPaymentMethod = "CASH" }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selectedPaymentMethod == "CASH", onClick = { selectedPaymentMethod = "CASH" })
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text("🎟️ Pay Conductor on Boarding", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TransitNavy)
                                    Text("Hold seat now • Pay cash/UPI on the bus", fontSize = 10.sp, color = Color.Gray)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showPaymentModal = false },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Cancel")
                            }

                            Button(
                                onClick = {
                                    showPaymentModal = false
                                    if (selectedPaymentMethod == "CASH") {
                                        onHoldBooking(selectedSeats.toList(), totalFare)
                                    } else {
                                        onConfirmBooking(selectedSeats.toList(), totalFare)
                                    }
                                },
                                modifier = Modifier.weight(1.5f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BusGreen)
                            ) {
                                Text("Pay ₹${totalFare.toInt()}", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun toggleSeat(seatId: String, current: Set<String>, update: (Set<String>) -> Unit) {
    if (current.contains(seatId)) {
        update(current - seatId)
    } else {
        if (current.size < 6) {
            update(current + seatId)
        }
    }
}

@Composable
private fun SeatLegendItem(color: Color, borderColor: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
                .border(1.dp, borderColor, RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SeatBox(
    seatId: String,
    isBooked: Boolean,
    isWomen: Boolean,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val backgroundColor = when {
        isSelected -> SeatSelected
        isBooked -> SeatBooked
        isWomen -> Color(0xFFFDF2F8)
        else -> Color.White
    }

    val borderColor = when {
        isSelected -> SeatSelected
        isBooked -> Color(0xFF94A3B8)
        isWomen -> SeatWomen
        else -> Color(0xFFCBD5E1)
    }

    val textColor = when {
        isSelected -> Color.White
        isBooked -> Color.White
        isWomen -> SeatWomen
        else -> Color(0xFF475569)
    }

    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(enabled = !isBooked) { onToggle() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            } else if (isWomen && !isBooked) {
                Icon(
                    Icons.Default.Female,
                    contentDescription = null,
                    tint = SeatWomen,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = seatId,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}
