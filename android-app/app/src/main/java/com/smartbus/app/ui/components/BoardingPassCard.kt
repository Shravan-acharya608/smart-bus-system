package com.smartbus.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartbus.app.data.model.Ticket
import com.smartbus.app.ui.theme.*

@Composable
fun BoardingPassCard(
    ticket: Ticket,
    onShowRollingQr: () -> Unit,
    onTrackLive: () -> Unit,
    onShareWithFamily: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isHeld = ticket.status.uppercase() == "HELD"
    val isUsed = ticket.status.uppercase() == "USED"
    val isRefunded = ticket.status.uppercase() == "REFUNDED"

    val statusText = when {
        isRefunded -> "REFUNDED"
        isHeld -> "SEAT HELD (PAY ON BUS)"
        isUsed -> "COMPLETED"
        else -> "CONFIRMED & PAID"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Bar
            Surface(
                color = if (isHeld) BusAmber else TransitPrimary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.DirectionsBus,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = ticket.trip?.bus?.busNumber ?: "SmartBus Express",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Surface(
                        color = Color.White.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = statusText,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Ticket Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Route Stops
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "BOARDING",
                            fontSize = 10.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            ticket.trip?.route?.stops?.firstOrNull()?.stop?.name ?: "Origin Stop",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TransitNavy
                        )
                    }

                    Icon(
                        Icons.Default.East,
                        contentDescription = "to",
                        tint = TransitPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(
                            "DROPPING",
                            fontSize = 10.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            ticket.trip?.route?.stops?.lastOrNull()?.stop?.name ?: "Destination Stop",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TransitNavy
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Divider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                Spacer(modifier = Modifier.height(14.dp))

                // Metadata Row: PNR, Seat, Amount
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("TICKET PNR", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                        Text(
                            ticket.ticketCode.take(8).uppercase(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TransitNavy
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("SEAT", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Confirmed",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = BusGreen
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text("FARE", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                        Text(
                            "₹${ticket.fare.toInt()}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TransitPrimary
                        )
                    }
                }

                // If Held ticket, show Hold & Board notice
                if (isHeld) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = BusAmber, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Pay ₹${ticket.fare.toInt()} in Cash or UPI directly to the Conductor when you board the bus.",
                                fontSize = 12.sp,
                                color = Color(0xFF92400E),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // QR Pass Button
                    Button(
                        onClick = onShowRollingQr,
                        modifier = Modifier.weight(1.3f).height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TransitNavy)
                    ) {
                        Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Show QR Pass", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    // Live Track Button
                    OutlinedButton(
                        onClick = onTrackLive,
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TransitPrimary),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TransitPrimary)
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Track", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    // Share Button
                    IconButton(
                        onClick = onShareWithFamily,
                        modifier = Modifier
                            .size(46.dp)
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share",
                            tint = TransitNavy,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
