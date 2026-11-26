package com.smartbus.app.ui.components

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.viewinterop.AndroidView
import com.google.gson.Gson
import com.smartbus.app.data.model.Trip

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BusLiveMap(
    activeTrips: List<Trip>,
    selectedTrip: Trip?,
    onSelectTrip: (Trip) -> Unit,
    onBookTicket: ((Trip) -> Unit)? = null,
    onShareJourney: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isMapLoaded by remember { mutableStateOf(false) }

    // Default coordinate: Udupi Service Bus Stand (13.3409, 74.7421)
    val defaultLat = 13.3409
    val defaultLng = 74.7421

    // Build compact JSON payload of trips & stops for Leaflet JS
    val mapPayloadJson = remember(activeTrips, selectedTrip) {
        val tripsData = activeTrips.map { trip ->
            val sortedStops = (trip.route?.stops ?: emptyList()).sortedBy { it.stopOrder }
            val stopsList = sortedStops.map { rs ->
                mapOf(
                    "order" to rs.stopOrder,
                    "name" to rs.stop.name,
                    "lat" to rs.stop.latitude,
                    "lng" to rs.stop.longitude
                )
            }
            val loc = trip.lastLocation
            val fallbackStop = sortedStops.firstOrNull()?.stop
            mapOf(
                "id" to trip.id,
                "busNumber" to (trip.bus?.busNumber ?: "KA-20-111"),
                "routeName" to (trip.route?.name ?: "Udupi – Katpadi – Shirva"),
                "hasLiveGps" to (loc != null),
                "lat" to (loc?.latitude ?: fallbackStop?.latitude ?: defaultLat),
                "lng" to (loc?.longitude ?: fallbackStop?.longitude ?: defaultLng),
                "speed" to (loc?.speed ?: 0.0),
                "isSelected" to (selectedTrip?.id == trip.id),
                "stops" to stopsList
            )
        }
        Gson().toJson(tripsData)
    }

    LaunchedEffect(mapPayloadJson, isMapLoaded) {
        if (isMapLoaded) {
            webViewRef?.evaluateJavascript("window.updateBusMap($mapPayloadJson);", null)
        }
    }

    val leafletHtml = remember {
        """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
          <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
          <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
          <style>
            html, body, #map { height: 100%; width: 100%; margin: 0; padding: 0; background: #e2e8f0; }
            .bus-pin {
              background: #0284C7; color: #fff; border: 2px solid #fff; border-radius: 16px;
              padding: 4px 8px; font-family: sans-serif; font-weight: 700; font-size: 11px;
              box-shadow: 0 3px 10px rgba(0,0,0,0.35); white-space: nowrap;
            }
            .bus-pin.fallback { background: #D97706; }
            .stop-pin {
              background: #0F172A; color: #fff; border: 2px solid #fff; border-radius: 50%;
              width: 22px; height: 22px; line-height: 18px; text-align: center;
              font-family: sans-serif; font-size: 11px; font-weight: bold;
              box-shadow: 0 2px 6px rgba(0,0,0,0.3);
            }
          </style>
        </head>
        <body>
          <div id="map"></div>
          <script>
            const map = L.map('map', { zoomControl: false }).setView([$defaultLat, $defaultLng], 12);
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
              maxZoom: 19,
              attribution: '© OpenStreetMap'
            }).addTo(map);

            const layerGroup = L.layerGroup().addTo(map);
            let hasFitted = false;

            // Always draw Udupi - Katpadi - Shirva corridor as baseline
            const defaultStops = [
              { order: 1, name: 'Udupi Service Bus Stand', lat: 13.3409, lng: 74.7421 },
              { order: 2, name: 'Kinnimulki', lat: 13.3235, lng: 74.7388 },
              { order: 3, name: 'Katpadi Junction', lat: 13.2848, lng: 74.7505 },
              { order: 4, name: 'Shankarapura', lat: 13.2603, lng: 74.7712 },
              { order: 5, name: 'Shirva Bus Stand', lat: 13.2188, lng: 74.8223 }
            ];

            window.updateBusMap = function(trips) {
              layerGroup.clearLayers();
              const bounds = [];

              if (!trips || trips.length === 0) {
                const pts = defaultStops.map(s => [s.lat, s.lng]);
                L.polyline(pts, { color: '#0284C7', weight: 5, opacity: 0.75, dashArray: '8, 6' }).addTo(layerGroup);
                defaultStops.forEach(s => {
                  L.marker([s.lat, s.lng], {
                    icon: L.divIcon({ className: '', html: '<div class="stop-pin">' + s.order + '</div>', iconSize: [22, 22] })
                  }).bindPopup('<b>' + s.name + '</b><br/>Scheduled Stop #' + s.order).addTo(layerGroup);
                  bounds.push([s.lat, s.lng]);
                });
              } else {
                trips.forEach(t => {
                  const stops = (t.stops && t.stops.length > 0) ? t.stops : defaultStops;
                  const pts = stops.map(s => [s.lat, s.lng]);
                  L.polyline(pts, {
                    color: t.hasLiveGps ? '#0284C7' : '#D97706',
                    weight: t.isSelected ? 6 : 4,
                    opacity: 0.85,
                    dashArray: t.hasLiveGps ? null : '8, 6'
                  }).addTo(layerGroup);

                  stops.forEach(s => {
                    L.marker([s.lat, s.lng], {
                      icon: L.divIcon({ className: '', html: '<div class="stop-pin">' + s.order + '</div>', iconSize: [22, 22] })
                    }).bindPopup('<b>' + s.name + '</b><br/>Route: ' + t.routeName).addTo(layerGroup);
                    bounds.push([s.lat, s.lng]);
                  });

                  const badgeClass = t.hasLiveGps ? 'bus-pin' : 'bus-pin fallback';
                  const label = t.hasLiveGps
                    ? '🚌 ' + t.busNumber + ' • ' + Math.round(t.speed) + ' km/h'
                    : '📍 ' + t.busNumber + ' (Scheduled Route)';

                  L.marker([t.lat, t.lng], {
                    icon: L.divIcon({ className: '', html: '<div class="' + badgeClass + '">' + label + '</div>', iconSize: [140, 28] })
                  }).bindPopup('<b>Bus ' + t.busNumber + '</b><br/>' + t.routeName + '<br/>' + (t.hasLiveGps ? 'Live Driver Phone GPS' : 'General Route Fallback (GPS Offline)')).addTo(layerGroup);
                  bounds.push([t.lat, t.lng]);
                });
              }

              if (!hasFitted && bounds.length > 0) {
                map.fitBounds(bounds, { padding: [35, 35] });
                hasFitted = true;
              }
            };

            window.recenterMap = function() {
              map.setView([$defaultLat, $defaultLng], 12);
            };
          </script>
        </body>
        </html>
        """.trimIndent()
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isMapLoaded = true
                            view?.evaluateJavascript("window.updateBusMap($mapPayloadJson);", null)
                        }
                    }
                    loadDataWithBaseURL("https://openstreetmap.org", leafletHtml, "text/html", "UTF-8", null)
                    webViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Floating Status Header
        val hasAnyLiveGps = activeTrips.any { it.lastLocation != null }
        Surface(
            color = Color.White.copy(alpha = 0.94f),
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (hasAnyLiveGps) Color(0xFF10B981) else Color(0xFFD97706))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (hasAnyLiveGps)
                        "LIVE GPS ACTIVE • Udupi – Katpadi – Shirva"
                    else
                        "GENERAL ROUTE MODE • Driver GPS Offline",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (hasAnyLiveGps) Color(0xFF065F46) else Color(0xFF92400E)
                )
            }
        }

        // Recenter Button
        FloatingActionButton(
            onClick = { webViewRef?.evaluateJavascript("window.recenterMap();", null) },
            containerColor = Color.White,
            contentColor = Color(0xFF0284C7),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 60.dp, end = 14.dp)
                .size(42.dp)
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = "Recenter", modifier = Modifier.size(20.dp))
        }

        // Bottom Sheet Card for Selected Bus
        AnimatedVisibility(
            visible = selectedTrip != null || activeTrips.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(14.dp)
        ) {
            val trip = selectedTrip ?: activeTrips.firstOrNull()
            if (trip != null) {
                val hasGps = trip.lastLocation != null
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = if (hasGps) Color(0xFFDBEAFE) else Color(0xFFFEF3C7),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        Icons.Default.DirectionsBus,
                                        contentDescription = null,
                                        tint = if (hasGps) Color(0xFF0284C7) else Color(0xFFD97706),
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        "Bus ${trip.bus?.busNumber ?: "KA-20-111"}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        trip.route?.name ?: "Udupi – Katpadi – Shirva",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                            Surface(
                                color = if (hasGps) Color(0xFFECFDF5) else Color(0xFFFEF3C7),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    if (hasGps) "⚡ ${trip.lastLocation?.speed?.toInt() ?: 0} km/h" else "📍 Scheduled",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (hasGps) Color(0xFF047857) else Color(0xFF92400E),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (!hasGps) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Driver phone GPS is paused/declined. Showing scheduled corridor stops & timetable.",
                                fontSize = 11.sp,
                                color = Color(0xFF92400E)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (onBookTicket != null) {
                                Button(
                                    onClick = { onBookTicket(trip) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.ConfirmationNumber, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Book Ticket", fontSize = 12.sp)
                                }
                            }
                            if (onShareJourney != null) {
                                OutlinedButton(
                                    onClick = { onShareJourney(trip.id) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Share Live", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
