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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.*

data class RoadEtaInfo(
    val minutes: Int,
    val distanceKm: Double,
    val targetStopName: String
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BusLiveMap(
    activeTrips: List<Trip>,
    selectedTrip: Trip?,
    onSelectTrip: (Trip) -> Unit,
    userLat: Double? = null,
    userLng: Double? = null,
    onRequestMyLocation: (() -> Unit)? = null,
    onBookTicket: ((Trip) -> Unit)? = null,
    onShareJourney: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isMapLoaded by remember { mutableStateOf(false) }

    // Default coordinate: Udupi Bus Stand (13.3409, 74.7421)
    val defaultLat = 13.3409
    val defaultLng = 74.7421

    // Calculate OSRM live road ETA for active trip
    val focusTrip = selectedTrip ?: activeTrips.firstOrNull()
    var liveEtaInfo by remember { mutableStateOf<RoadEtaInfo?>(null) }

    LaunchedEffect(
        focusTrip?.id,
        focusTrip?.lastLocation?.latitude,
        focusTrip?.lastLocation?.longitude
    ) {
        val loc = focusTrip?.lastLocation
        if (loc == null) {
            liveEtaInfo = null
            return@LaunchedEffect
        }
        val sortedStops = (focusTrip.route?.stops ?: emptyList()).sortedBy { it.stopOrder }
        val terminalStop = sortedStops.lastOrNull()?.stop
        val destLat = terminalStop?.latitude ?: 13.2188
        val destLng = terminalStop?.longitude ?: 74.8223
        val destName = terminalStop?.name ?: "Destination Stop"

        liveEtaInfo = withContext(Dispatchers.IO) {
            fetchOsrmEta(loc.latitude, loc.longitude, destLat, destLng, destName)
        }
    }

    // Build compact JSON payload of trips, stops & passenger current location
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
                "routeName" to (trip.route?.name ?: "Udupi Bus Route"),
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

    val userLocJson = remember(userLat, userLng) {
        if (userLat != null && userLng != null) {
            """{"lat":$userLat,"lng":$userLng}"""
        } else {
            "null"
        }
    }

    LaunchedEffect(mapPayloadJson, userLocJson, isMapLoaded) {
        if (isMapLoaded) {
            webViewRef?.evaluateJavascript("window.updateBusMap($mapPayloadJson, $userLocJson);", null)
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
              background: #059669; color: #fff; border: 2px solid #fff; border-radius: 16px;
              padding: 4px 9px; font-family: sans-serif; font-weight: 700; font-size: 11px;
              box-shadow: 0 3px 10px rgba(0,0,0,0.35); white-space: nowrap;
            }
            .bus-pin.fallback { background: #D97706; }
            .stop-pin {
              background: #0F172A; color: #fff; border: 2px solid #fff; border-radius: 50%;
              width: 22px; height: 22px; line-height: 18px; text-align: center;
              font-family: sans-serif; font-size: 11px; font-weight: bold;
              box-shadow: 0 2px 6px rgba(0,0,0,0.3);
            }
            .you-dot {
              width: 18px; height: 18px; border-radius: 50%;
              background: #2563EB; border: 3px solid #fff;
              box-shadow: 0 0 0 6px rgba(37, 99, 235, 0.3), 0 2px 6px rgba(0,0,0,0.4);
            }
            .you-label {
              background: #1E3A8A; color: #fff; font-size: 9px; font-weight: 800;
              padding: 1px 5px; border-radius: 6px; margin-top: 2px;
              font-family: sans-serif; white-space: nowrap; text-align: center;
            }
          </style>
        </head>
        <body>
          <div id="map"></div>
          <script>
            const map = L.map('map', { zoomControl: false }).setView([$defaultLat, $defaultLng], 12);
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
              maxZoom: 19,
              attribution: '© OpenStreetMap | OSRM'
            }).addTo(map);

            const layerGroup = L.layerGroup().addTo(map);
            let lastRouteSignature = '';
            const osrmRouteCache = {};
            let lastUserLoc = null;

            const defaultStops = [
              { order: 1, name: 'Udupi Bus Stand', lat: 13.3409, lng: 74.7421 },
              { order: 2, name: 'Kinnimulki', lat: 13.3235, lng: 74.7388 },
              { order: 3, name: 'Katpadi', lat: 13.2848, lng: 74.7505 },
              { order: 4, name: 'Shankarpura', lat: 13.2603, lng: 74.7712 },
              { order: 5, name: 'Shirva', lat: 13.2188, lng: 74.8223 }
            ];

            function drawRoadPolyline(stops, styleOpts) {
              const fallbackPts = stops.map(s => [s.lat, s.lng]);
              const key = stops.map(s => s.lng.toFixed(5) + ',' + s.lat.toFixed(5)).join(';');
              const initialPts = osrmRouteCache[key] || fallbackPts;
              const poly = L.polyline(initialPts, styleOpts).addTo(layerGroup);

              if (!osrmRouteCache[key] && stops.length >= 2) {
                fetch('https://router.project-osrm.org/route/v1/driving/' + key + '?overview=full&geometries=geojson')
                  .then(r => r.json())
                  .then(data => {
                    if (data && data.routes && data.routes[0] && data.routes[0].geometry) {
                      const roadPts = data.routes[0].geometry.coordinates.map(c => [c[1], c[0]]);
                      osrmRouteCache[key] = roadPts;
                      poly.setLatLngs(roadPts);
                    }
                  })
                  .catch(() => {});
              }
            }

            window.updateBusMap = function(trips, userLoc) {
              layerGroup.clearLayers();
              const bounds = [];
              let sig = '';

              if (!trips || trips.length === 0) {
                sig = 'default';
                drawRoadPolyline(defaultStops, { color: '#0284C7', weight: 5, opacity: 0.8 });
                defaultStops.forEach(s => {
                  L.marker([s.lat, s.lng], {
                    icon: L.divIcon({ className: '', html: '<div class="stop-pin">' + s.order + '</div>', iconSize: [22, 22] })
                  }).bindPopup('<b>' + s.name + '</b><br/>Scheduled Stop #' + s.order).addTo(layerGroup);
                  bounds.push([s.lat, s.lng]);
                });
              } else {
                trips.forEach(t => {
                  sig += t.id + '|';
                  const stops = (t.stops && t.stops.length > 0) ? t.stops : defaultStops;
                  drawRoadPolyline(stops, {
                    color: t.hasLiveGps ? '#059669' : '#0284C7',
                    weight: t.isSelected ? 6 : 4,
                    opacity: 0.88,
                    dashArray: t.hasLiveGps ? null : '7, 5'
                  });

                  stops.forEach(s => {
                    L.marker([s.lat, s.lng], {
                      icon: L.divIcon({ className: '', html: '<div class="stop-pin">' + s.order + '</div>', iconSize: [22, 22] })
                    }).bindPopup('<b>' + s.name + '</b><br/>Stop #' + s.order).addTo(layerGroup);
                    bounds.push([s.lat, s.lng]);
                  });

                  const badgeClass = t.hasLiveGps ? 'bus-pin' : 'bus-pin fallback';
                  const label = t.hasLiveGps
                    ? '🚌 LIVE ' + t.busNumber + ' • ' + Math.round(t.speed) + ' km/h'
                    : '🚌 ' + t.busNumber + ' (Route View)';

                  L.marker([t.lat, t.lng], {
                    icon: L.divIcon({ className: '', html: '<div class="' + badgeClass + '">' + label + '</div>', iconSize: [145, 28] })
                  }).bindPopup('<b>Bus ' + t.busNumber + '</b><br/>' + t.routeName + '<br/>' + (t.hasLiveGps ? '🟢 Live Driver GPS Online' : '📍 Scheduled Route (Driver GPS Offline)')).addTo(layerGroup);
                  bounds.push([t.lat, t.lng]);
                });
              }

              // Render Passenger Current Location Blue Pin ("You")
              if (userLoc && userLoc.lat && userLoc.lng) {
                lastUserLoc = userLoc;
                L.marker([userLoc.lat, userLoc.lng], {
                  icon: L.divIcon({
                    className: '',
                    html: '<div style="display:flex;flex-direction:column;align-items:center;"><div class="you-dot"></div><div class="you-label">YOU</div></div>',
                    iconSize: [40, 36],
                    iconAnchor: [20, 10]
                  }),
                  zIndexOffset: 1000
                }).bindPopup('<b>📍 Your Current Location</b><br/>Lat: ' + userLoc.lat.toFixed(5) + ', Lng: ' + userLoc.lng.toFixed(5)).addTo(layerGroup);
                bounds.push([userLoc.lat, userLoc.lng]);
              }

              if (sig !== lastRouteSignature && bounds.length > 0) {
                map.fitBounds(bounds, { padding: [38, 38], maxZoom: 14 });
                lastRouteSignature = sig;
              }
            };

            window.recenterMap = function() {
              map.setView([$defaultLat, $defaultLng], 12);
            };

            window.focusUserLocation = function() {
              if (lastUserLoc && lastUserLoc.lat && lastUserLoc.lng) {
                map.setView([lastUserLoc.lat, lastUserLoc.lng], 15, { animate: true });
              }
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
                            view?.evaluateJavascript("window.updateBusMap($mapPayloadJson, $userLocJson);", null)
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
                        .background(if (hasAnyLiveGps) Color(0xFF10B981) else Color(0xFF0284C7))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (hasAnyLiveGps)
                        "LIVE BUS NAVIGATION • Driver GPS Online"
                    else
                        "BUS ROUTE MAP • OSRM Road Path" + if (userLat != null) " + Your GPS" else "",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (hasAnyLiveGps) Color(0xFF065F46) else Color(0xFF0C4A6E)
                )
            }
        }

        // My Current Location & Recenter Buttons
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 58.dp, end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    onRequestMyLocation?.invoke()
                    webViewRef?.evaluateJavascript("window.focusUserLocation();", null)
                },
                containerColor = if (userLat != null) Color(0xFF2563EB) else Color.White,
                contentColor = if (userLat != null) Color.White else Color(0xFF2563EB),
                modifier = Modifier.size(42.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "My Current Location", modifier = Modifier.size(20.dp))
            }

            FloatingActionButton(
                onClick = { webViewRef?.evaluateJavascript("window.recenterMap();", null) },
                containerColor = Color.White,
                contentColor = Color(0xFF0284C7),
                modifier = Modifier.size(42.dp)
            ) {
                Icon(Icons.Default.DirectionsBus, contentDescription = "View Route", modifier = Modifier.size(20.dp))
            }
        }

        // Bottom Sheet Card for Selected Bus
        AnimatedVisibility(
            visible = focusTrip != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(14.dp)
        ) {
            if (focusTrip != null) {
                val hasGps = focusTrip.lastLocation != null
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
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Surface(
                                    color = if (hasGps) Color(0xFFDCFCE7) else Color(0xFFDBEAFE),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        Icons.Default.DirectionsBus,
                                        contentDescription = null,
                                        tint = if (hasGps) Color(0xFF059669) else Color(0xFF0284C7),
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        "Bus ${focusTrip.bus?.busNumber ?: "KA-20-111"}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        focusTrip.route?.name ?: "Udupi Bus Route",
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        maxLines = 1
                                    )
                                }
                            }
                            Surface(
                                color = if (hasGps) Color(0xFFECFDF5) else Color(0xFFFEF3C7),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    if (hasGps) "⚡ ${focusTrip.lastLocation?.speed?.toInt() ?: 0} km/h" else "📍 Scheduled",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (hasGps) Color(0xFF047857) else Color(0xFF92400E),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (hasGps && liveEtaInfo != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                color = Color(0xFFF0FDF4),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Navigation, contentDescription = null, tint = Color(0xFF15803D), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Live Navigation ETA: ~${liveEtaInfo!!.minutes} min (${String.format("%.1f", liveEtaInfo!!.distanceKm)} km) to ${liveEtaInfo!!.targetStopName}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF15803D)
                                    )
                                }
                            }
                        } else if (!hasGps) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Bus GPS offline — showing route corridor" + if (userLat != null) " & your blue GPS pin." else ".",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }

                        if (onBookTicket != null || onShareJourney != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (onBookTicket != null) {
                                    Button(
                                        onClick = { onBookTicket(focusTrip) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.ConfirmationNumber, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Book Slot", fontSize = 12.sp)
                                    }
                                }
                                if (onShareJourney != null) {
                                    OutlinedButton(
                                        onClick = { onShareJourney(focusTrip.id) },
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
}

private fun fetchOsrmEta(
    fromLat: Double,
    fromLng: Double,
    toLat: Double,
    toLng: Double,
    targetStopName: String
): RoadEtaInfo {
    try {
        val url = URL("https://router.project-osrm.org/route/v1/driving/$fromLng,$fromLat;$toLng,$toLat?overview=false")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 4000
            readTimeout = 4000
            requestMethod = "GET"
        }
        if (conn.responseCode == 200) {
            val body = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(body)
            val routes = json.optJSONArray("routes")
            if (routes != null && routes.length() > 0) {
                val r0 = routes.getJSONObject(0)
                val sec = r0.optDouble("duration", 600.0)
                val meters = r0.optDouble("distance", 1000.0)
                return RoadEtaInfo(
                    minutes = maxOf(1, (sec / 60.0).roundToInt()),
                    distanceKm = meters / 1000.0,
                    targetStopName = targetStopName
                )
            }
        }
    } catch (_: Exception) {
    }
    val dLat = Math.toRadians(toLat - fromLat)
    val dLng = Math.toRadians(toLng - fromLng)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(fromLat)) * cos(Math.toRadians(toLat)) * sin(dLng / 2).pow(2)
    val km = 6371.0 * 2 * asin(sqrt(a)) * 1.25
    val mins = maxOf(1, ((km / 35.0) * 60.0).roundToInt())
    return RoadEtaInfo(minutes = mins, distanceKm = km, targetStopName = targetStopName)
}
