package com.smartbus.app.ui.screens.passenger

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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.smartbus.app.data.model.*
import com.smartbus.app.ui.components.BusLiveMap
import com.smartbus.app.ui.components.BusSeatSelector
import kotlin.math.*

object UdupiLocalTimetable {
    private val coords = mapOf(
        "Udupi" to ("Udupi Bus Stand" to (13.3409 to 74.7421)),
        "Brahamagiri" to ("Brahamagiri" to (13.3375 to 74.7460)),
        "Santhekatte" to ("Santhekatte" to (13.3688 to 74.7294)),
        "Kolalgiri" to ("Kolalgiri" to (13.4142 to 74.7695)),
        "Perdoor" to ("Perdoor" to (13.3667 to 74.8805)),
        "Katapadi" to ("Katapadi" to (13.2848 to 74.7505)),
        "Shubhas Nagara" to ("Shubhas Nagara" to (13.2712 to 74.7689)),
        "Shankarpura" to ("Shankarpura" to (13.2603 to 74.7712)),
        "Mancha Kallu" to ("Mancha Kallu" to (13.2415 to 74.8012)),
        "Hiriyadka" to ("Hiriyadka" to (13.3474 to 74.8535)),
        "Bailur" to ("Bailur" to (13.2974 to 74.9382)),
        "Joduraste" to ("Joduraste" to (13.2305 to 74.9808)),
        "Karkala" to ("Karkala" to (13.2156 to 74.9990)),
        "Daina" to ("Daina Circle" to (13.3389 to 74.7475)),
        "Alevoor" to ("Alevoor" to (13.3135 to 74.7772)),
        "Kukkikatte" to ("Kukkikatte" to (13.3292 to 74.7634)),
        "Marne" to ("Marne" to (13.2830 to 74.8325)),
        "Manipal" to ("Manipal" to (13.3525 to 74.7868)),
        "Manipala" to ("Manipal" to (13.3525 to 74.7868)),
        "Hebri" to ("Hebri" to (13.4577 to 74.9942)),
        "Nellikatte" to ("Nellikatte" to (13.4385 to 74.9720)),
        "Agumbe" to ("Agumbe" to (13.5087 to 75.0956)),
        "Shivamogga" to ("Shivamogga" to (13.9299 to 75.5681)),
        "Malpe" to ("Malpe" to (13.3526 to 74.7037)),
        "Kalmady" to ("Kalmady" to (13.3490 to 74.7145)),
        "Hampankatte" to ("Hampankatte (Malpe)" to (13.3540 to 74.7095)),
        "Brahmavara" to ("Brahmavara" to (13.4357 to 74.7458)),
        "Salikere" to ("Salikere" to (13.4480 to 74.7380)),
        "Honnalla" to ("Honnalla" to (13.4620 to 74.7295)),
        "Indrali" to ("Indrali" to (13.3458 to 74.7695)),
        "Nejar" to ("Nejar" to (13.3792 to 74.7258)),
        "Kemmanu" to ("Kemmanu" to (13.4018 to 74.7128)),
        "Hoode" to ("Hoode" to (13.4090 to 74.6960)),
        "Moodubelle" to ("Moodubelle" to (13.2798 to 74.8058)),
        "Karavali Bipass" to ("Karavali Bypass" to (13.3408 to 74.7330)),
        "Padukere" to ("Padukere" to (13.3438 to 74.6992)),
        "Parkala" to ("Parkala" to (13.3578 to 74.8095)),
        "Herga" to ("Herga" to (13.3620 to 74.8190)),
        "Kanajar" to ("Kanajar" to (13.2495 to 74.9182)),
        "Kanjar" to ("Kanajar" to (13.2495 to 74.9182)),
        "Garidimajal" to ("Garidimajal" to (13.3895 to 74.7460)),
        "Malpe Beach" to ("Malpe Beach" to (13.3536 to 74.7005)),
        "Thotam" to ("Thotam" to (13.3775 to 74.6975)),
        "Padubidri" to ("Padubidri" to (13.1398 to 74.7721)),
        "DC Office" to ("DC Office (NH-66 South)" to (12.8639 to 74.8364)),
        "Adiudupi" to ("Adiudupi" to (13.3442 to 74.7250)),
        "Kodavoor" to ("Kodavoor" to (13.3590 to 74.7168)),
        "Thenkanidiyoor" to ("Thenkanidiyoor" to (13.3735 to 74.7130)),
        "Barkur" to ("Barkur" to (13.4675 to 74.7568)),
        "Mandarthi" to ("Mandarthi" to (13.4945 to 74.8285)),
        "Kokkarene" to ("Kokkarene" to (13.5050 to 74.8550)),
        "Kota" to ("Kota" to (13.5162 to 74.7132)),
        "Kundapura" to ("Kundapura" to (13.6213 to 74.6897)),
        "Shringeri" to ("Shringeri" to (13.4198 to 75.2567)),
        "Haladi" to ("Haladi" to (13.5752 to 74.8832)),
        "Siddapura" to ("Siddapura" to (13.6625 to 74.8635)),
        "Kollur" to ("Kollur" to (13.8635 to 74.8126)),
        "Biruru" to ("Biruru" to (13.5975 to 75.9735)),
        "Baaliga" to ("Baaliga" to (13.4280 to 74.7650)),
        "Upooru" to ("Upooru" to (13.4025 to 74.7495)),
        "Amasebailu" to ("Amasebailu" to (13.5515 to 74.9895))
    )

    private fun buildItem(slNo: Int, to: String, via: String, dep: String): UdupiScheduleItem {
        val rawTokens = mutableListOf("Udupi")
        via.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { rawTokens.add(it) }
        to.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { rawTokens.add(it) }
        val seen = mutableSetOf<String>()
        val stops = mutableListOf<UdupiStopCoord>()
        for (tk in rawTokens) {
            val pair = coords[tk] ?: (tk to (13.3409 to 74.7421))
            if (seen.add(pair.first)) {
                stops.add(UdupiStopCoord(stops.size + 1, pair.first, pair.second.first, pair.second.second))
            }
        }
        return UdupiScheduleItem(
            slNo = slNo,
            busNumber = "KA-20-UDP-${slNo.toString().padStart(3, '0')}",
            from = "Udupi",
            to = to,
            serviceClass = "Ordinary",
            via = via,
            departureTime = dep,
            stops = stops
        )
    }

    val all139Schedules: List<UdupiScheduleItem> by lazy {
        listOf(
            buildItem(1, "Perdoor", "Brahamagiri, Santhekatte, Kolalgiri", "06.30"),
            buildItem(2, "Mancha Kallu", "Katapadi, Shubhas Nagara, Shankarpura", "06.50"),
            buildItem(3, "Karkala", "Hiriyadka, Bailur, Joduraste", "06.55"),
            buildItem(4, "Marne", "Daina, Alevoor, Kukkikatte", "07.00"),
            buildItem(5, "Hebri,Nellikatte", "Manipal, Hiriyadka, Perdoor", "07.00"),
            buildItem(6, "Shivamogga", "Perdoor, Hebri, Agumbe", "07.00"),
            buildItem(7, "Hampankatte", "Malpe, Kalmady", "07.05"),
            buildItem(8, "Honnalla", "Santhekatte, Brahmavara, Salikere", "07.05"),
            buildItem(9, "Manipal", "Indrali", "07.10"),
            buildItem(10, "Kemmanu,Hoode", "Santhekatte, Nejar, Hoode", "07.10"),
            buildItem(11, "Moodubelle", "Daina, Moodubelle, Karkala", "07.15"),
            buildItem(12, "Shivamogga", "Perdoor, Hebri, Agumbe", "07.15"),
            buildItem(13, "Padukere", "Karavali Bipass, Kalmady, Malpe", "07.17"),
            buildItem(14, "Karkala", "Hiriyadka, Bailur, Joduraste", "07.22"),
            buildItem(15, "Herga", "Manipala, Indrali, Parkala", "07.26"),
            buildItem(16, "Karkala", "Kanajar", "07.32"),
            buildItem(17, "Garidimajal", "Santhekatte", "07.40"),
            buildItem(18, "Padukere", "Karavali Bipass, Kalmady, Malpe", "07.40"),
            buildItem(19, "Karkala", "Hiriyadka, Bailur, Joduraste", "07.42"),
            buildItem(20, "Thotam,Hoode", "Karavali Bipass, Kalmady, Malpe Beach", "07.45"),
            buildItem(21, "DC Office", "Padubidri", "08.06"),
            buildItem(22, "Shivamogga", "Perdoor, Hebri, Agumbe", "08.10"),
            buildItem(23, "Perdoor", "Brahamagiri, Santhekatte, Kolalgiri", "08.15"),
            buildItem(24, "Karkala", "Hiriyadka, Bailur, Joduraste", "08.24"),
            buildItem(25, "Hampankatte", "Malpe, Kalmady", "08.28"),
            buildItem(26, "Thenkanidiyoor", "Adiudupi, Kodavoor", "08.36"),
            buildItem(27, "Kemmanu,Hoode", "Santhekatte, Nejar, Hoode", "08.42"),
            buildItem(28, "Kokkarene", "Brahmavara, Barkur, Mandarthi", "08.45"),
            buildItem(29, "Mancha Kallu", "Katapadi, Shubhas Nagara, Shankarpura", "08.50"),
            buildItem(30, "Karkala", "Hiriyadka, Bailur, Joduraste", "08.57"),
            buildItem(31, "Hebri,Nellikatte", "Manipal, Hiriyadka, Perdoor", "09.00"),
            buildItem(32, "Kokkarene", "Brahmavara, Barkur, Mandarthi", "09.10"),
            buildItem(33, "Shivamogga", "Perdoor, Hebri, Agumbe", "09.10"),
            buildItem(34, "Garidimajal", "Santhekatte", "09.15"),
            buildItem(35, "Honnalla", "Santhekatte, Brahmavara, Salikere", "09.15"),
            buildItem(36, "Thotam,Hoode", "Karavali Bipass, Kalmady, Malpe Beach", "09.23"),
            buildItem(37, "Karkala", "Hiriyadka, Bailur, Joduraste", "09.23"),
            buildItem(38, "DC Office", "Padubidri", "09.30"),
            buildItem(39, "Kundapura", "Brahmavara, Kota", "09.30"),
            buildItem(40, "Shringeri", "Hebri, Agumbe, Perdoor", "09.40"),
            buildItem(41, "Hampankatte", "Malpe, Kalmady", "09.48"),
            buildItem(42, "Padukere", "Karavali Bipass, Kalmady, Malpe", "09.50"),
            buildItem(43, "Haladi,Siddapura", "Brahmavara, Barkur", "09.55"),
            buildItem(44, "Haladi", "Mandarthi", "10.00"),
            buildItem(45, "Karkala", "Hiriyadka, Bailur, Joduraste", "10.02"),
            buildItem(46, "Perdoor", "Brahamagiri, Santhekatte, Kolalgiri", "10.10"),
            buildItem(47, "Kemmanu,Hoode", "Santhekatte, Nejar, Hoode", "10.12"),
            buildItem(48, "Kundapura", "Brahmavara, Kota", "10.15"),
            buildItem(49, "Moodubelle", "Daina, Moodubelle", "10.20"),
            buildItem(50, "Hebri,Nellikatte", "Manipal, Hiriyadka, Perdoor", "10.30"),
            buildItem(51, "Perdoor", "Brahamagiri, Santhekatte, Kolalgiri", "10.40"),
            buildItem(52, "Marne", "Daina, Alevoor, Kukkikatte", "10.45"),
            buildItem(53, "Herga", "Manipala, Indrali, Parkala", "10.46"),
            buildItem(54, "Karkala", "Kanajar, Hiriyadka", "10.46"),
            buildItem(55, "Garidimajal", "Santhekatte", "10.47"),
            buildItem(56, "Mancha Kallu", "Katapadi, Shubhas Nagara, Shankarpura", "10.50"),
            buildItem(57, "Karkala", "Hiriyadka, Bailur, Joduraste", "10.50"),
            buildItem(58, "Karkala", "Hiriyadka, Bailur, Joduraste", "11.01"),
            buildItem(59, "Hampankatte", "Malpe, Kalmady", "11.18"),
            buildItem(60, "Malpe", "Karavali Bipass, Adiudupi", "11.20"),
            buildItem(61, "Thotam,Hoode", "Karavali Bipass, Kalmady, Malpe Beach", "11.26"),
            buildItem(62, "Haladi,Siddapura", "Brahmavara, Barkur", "11.30"),
            buildItem(63, "Karkala", "Hiriyadka, Bailur, Joduraste", "11.32"),
            buildItem(64, "DC Office", "Padubidri", "11.35"),
            buildItem(65, "Karkala", "Hiriyadka, Bailur, Joduraste", "11.43"),
            buildItem(66, "Kemmanu,Hoode", "Santhekatte, Nejar, Hoode", "11.45"),
            buildItem(67, "Hebri,Nellikatte", "Manipal, Hiriyadka, Perdoor", "12.00"),
            buildItem(68, "Kundapura", "Brahmavara, Kota", "12.00"),
            buildItem(69, "Herga", "Manipala, Indrali, Parkala", "12.01"),
            buildItem(70, "Kokkarene", "Brahmavara, Barkur, Mandarthi", "12.10"),
            buildItem(71, "Padukere", "Karavali Bipass, Kalmady, Malpe", "12.10"),
            buildItem(72, "Karkala", "Hiriyadka, Bailur, Joduraste", "12.17"),
            buildItem(73, "Kokkarene", "Brahmavara, Barkur, Mandarthi", "12.30"),
            buildItem(74, "Marne", "Daina, Alevoor, Kukkikatte", "12.35"),
            buildItem(75, "Karkala", "Hiriyadka, Bailur, Joduraste", "12.37"),
            buildItem(76, "Garidimajal", "Santhekatte", "12.40"),
            buildItem(77, "Honnalla", "Santhekatte, Brahmavara, Salikere", "12.40"),
            buildItem(78, "Shringeri", "Hebri, Agumbe, Perdoor", "12.45"),
            buildItem(79, "Siddapura,Kollur", "Brahmavara, Siddapura", "12.45"),
            buildItem(80, "Perdoor", "Brahamagiri, Santhekatte, Kolalgiri", "13.05"),
            buildItem(81, "Hampankatte", "Malpe, Kalmady", "13.07"),
            buildItem(82, "Karkala", "Daina, Moodubelle", "13.25"),
            buildItem(83, "Kemmanu,Hoode", "Santhekatte, Nejar, Hoode", "13.30"),
            buildItem(84, "Hebri,Nellikatte", "Manipal, Hiriyadka, Perdoor", "13.30"),
            buildItem(85, "Thotam,Hoode", "Karavali Bipass, Kalmady, Malpe Beach", "13.42"),
            buildItem(86, "Perdoor", "Brahamagiri, Santhekatte, Kolalgiri", "13.45"),
            buildItem(87, "Karkala", "Hiriyadka, Kanjar, Bailur", "13.46"),
            buildItem(88, "Karkala", "Hiriyadka, Bailur, Joduraste", "13.47"),
            buildItem(89, "Karkala", "Hiriyadka, Bailur, Joduraste", "13.56"),
            buildItem(90, "Biruru", "Hebri, Agumbe, Perdoor", "14.10"),
            buildItem(91, "Herga", "Manipala, Indrali, Parkala", "14.13"),
            buildItem(92, "Haladi,Siddapura", "Brahmavara, Barkur", "14.15"),
            buildItem(93, "Marne", "Daina, Alevoor, Kukkikatte", "14.20"),
            buildItem(94, "Padukere", "Karavali Bipass, Kalmady, Malpe", "14.20"),
            buildItem(95, "Karkala", "Hiriyadka, Bailur, Joduraste", "14.24"),
            buildItem(96, "Garidimajal", "Santhekatte", "14.37"),
            buildItem(97, "Amasebailu", "Baaliga, Upooru, Brahmavara", "14.40"),
            buildItem(98, "Karkala", "Hiriyadka, Bailur, Joduraste", "14.50"),
            buildItem(99, "Hampankatte", "Malpe, Kalmady", "14.57"),
            buildItem(100, "Kundapura", "Brahmavara, Kota", "15.00"),
            buildItem(101, "Mancha Kallu", "Katapadi, Shubhas Nagara, Shankarpura", "15.10"),
            buildItem(102, "Shivamogga", "Perdoor, Hebri, Agumbe", "15.10"),
            buildItem(103, "Mandarthi,Haladi", "Brahmavara, Barkur", "15.15"),
            buildItem(104, "Kundapura", "Brahmavara, Kota", "15.15"),
            buildItem(105, "Hebri,Nellikatte", "Manipal, Hiriyadka, Perdoor", "15.25"),
            buildItem(106, "Kokkarene", "Brahmavara, Barkur, Mandarthi", "15.30"),
            buildItem(107, "Thenkanidiyoor", "Adiudupi, Kodavoor", "15.45"),
            buildItem(108, "Shivamogga", "Perdoor, Hebri, Agumbe", "15.55"),
            buildItem(109, "Kokkarene", "Brahmavara, Barkur, Mandarthi", "16.00"),
            buildItem(110, "Perdoor", "Brahamagiri, Santhekatte, Kolalgiri", "16.00"),
            buildItem(111, "Karkala", "Hiriyadka, Bailur, Joduraste", "16.02"),
            buildItem(112, "Thotam,Hoode", "Karavali Bipass, Kalmady, Malpe Beach", "16.05"),
            buildItem(113, "Padukere", "Karavali Bipass, Kalmady, Malpe", "16.05"),
            buildItem(114, "Garidimajal", "Santhekatte", "16.15"),
            buildItem(115, "Haladi,Siddapura", "Brahmavara, Barkur", "16.15"),
            buildItem(116, "Karkala", "Hiriyadka, Bailur, Joduraste", "16.23"),
            buildItem(117, "Karkala", "Daina, Moodubelle", "16.25"),
            buildItem(118, "Marne", "Daina, Alevoor, Kukkikatte", "16.30"),
            buildItem(119, "Hampankatte", "Malpe, Kalmady", "16.30"),
            buildItem(120, "Shivamogga", "Perdoor, Hebri, Agumbe", "16.35"),
            buildItem(121, "Amasebailu", "Baaliga, Upooru, Brahmavara", "16.40"),
            buildItem(122, "Perdoor", "Brahamagiri, Santhekatte, Kolalgiri", "16.45"),
            buildItem(123, "Shringeri", "Hebri, Agumbe, Perdoor", "16.45"),
            buildItem(124, "Karkala", "Hiriyadka, Kanjar, Bailur", "16.46"),
            buildItem(125, "Hebri,Nellikatte", "Manipal, Hiriyadka, Perdoor", "16.50"),
            buildItem(126, "Karkala", "Hiriyadka, Bailur, Joduraste", "16.51"),
            buildItem(127, "Honnalla", "Santhekatte, Brahmavara, Salikere", "17.00"),
            buildItem(128, "Shivamogga", "Perdoor, Hebri, Agumbe", "17.00"),
            buildItem(129, "DC Office", "Padubidri", "17.05"),
            buildItem(130, "Kemmanu,Hoode", "Santhekatte, Nejar, Hoode", "17.05"),
            buildItem(131, "Mancha Kallu", "Katapadi, Shubhas Nagara, Shankarpura", "17.20"),
            buildItem(132, "Karkala", "Hiriyadka, Bailur, Joduraste", "17.22"),
            buildItem(133, "Marne", "Daina, Alevoor, Kukkikatte", "17.35"),
            buildItem(134, "Thotam,Hoode", "Karavali Bipass, Kalmady, Malpe Beach", "17.38"),
            buildItem(135, "Garidimajal", "Santhekatte", "17.45"),
            buildItem(136, "Karkala", "Hiriyadka, Bailur, Joduraste", "18.02"),
            buildItem(137, "Padukere", "Karavali Bipass, Kalmady, Malpe", "18.10"),
            buildItem(138, "Kokkarene", "Brahmavara, Barkur, Mandarthi", "18.35"),
            buildItem(139, "Perdoor", "Brahamagiri, Santhekatte, Kolalgiri", "18.50")
        )
    }
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 6371.0 * 2 * asin(sqrt(a))
    }

    fun findNearestPlace(lat: Double, lng: Double): Pair<String, Double> {
        var bestName = "Udupi Bus Stand"
        var bestDist = Double.MAX_VALUE
        for ((_, info) in coords) {
            val d = distanceKm(lat, lng, info.second.first, info.second.second)
            if (d < bestDist) {
                bestDist = d
                bestName = info.first
            }
        }
        return bestName to bestDist
    }

    fun estimateFareForBus(bus: UdupiScheduleItem): Double {
        if (bus.stops.size < 2) return 20.0
        var totalKm = 0.0
        for (i in 1 until bus.stops.size) {
            totalKm += distanceKm(bus.stops[i - 1].latitude, bus.stops[i - 1].longitude, bus.stops[i].latitude, bus.stops[i].longitude)
        }
        return maxOf(15.0, ((totalKm * 1.8) / 5.0).roundToInt() * 5.0)
    }
}

@Composable
fun UdupiTimetableView(
    schedules: List<UdupiScheduleItem>,
    activeTrips: List<Trip> = emptyList(),
    userLat: Double? = null,
    userLng: Double? = null,
    onRequestMyLocation: () -> Unit = {},
    onBookScheduleSlot: ((UdupiScheduleItem, String) -> Unit)? = null,
    onHoldScheduleSlot: ((UdupiScheduleItem) -> Unit)? = null,
    onSetWakeUpAlarmForStop: (UdupiStopCoord, String?) -> Unit,
    onClose: (() -> Unit)? = null
) {
    val activeList = schedules.ifEmpty { UdupiLocalTimetable.all139Schedules }
    var searchQuery by remember { mutableStateOf("") }
    var selectedDestFilter by remember { mutableStateOf("All") }
    var selectedBusForMap by remember { mutableStateOf<UdupiScheduleItem?>(null) }

    val destFilters = listOf(
        "All", "Karkala", "Perdoor", "Hebri", "Shivamogga",
        "Mancha Kallu", "Hoode", "Kundapura", "Hampankatte", "Padukere", "Marne"
    )

    val filteredBuses = remember(activeList, searchQuery, selectedDestFilter) {
        activeList.filter { item ->
            val matchesFilter = selectedDestFilter == "All" ||
                item.to.contains(selectedDestFilter, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                item.to.contains(searchQuery, ignoreCase = true) ||
                item.via.contains(searchQuery, ignoreCase = true) ||
                item.departureTime.contains(searchQuery, ignoreCase = true) ||
                item.slNo.toString() == searchQuery.trim()
            matchesFilter && matchesSearch
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8FAFC))) {
        // Header
        Surface(color = Color(0xFF0F172A), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "UDUPI BUS STAND TIMETABLE",
                            color = Color(0xFFFDE047),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                        Text(
                            "${filteredBuses.size} of ${activeList.size} Departures • Tap any bus for Map, Booking & Alert",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                    if (onClose != null) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search To, Via stop, Time (e.g. Karkala, 07.00)...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Quick Destination Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(destFilters) { filter ->
                FilterChip(
                    selected = selectedDestFilter == filter,
                    onClick = { selectedDestFilter = filter },
                    label = { Text(filter, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                )
            }
        }

        // 139 Buses List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredBuses, key = { it.slNo }) { bus ->
                val isOnline = activeTrips.any { it.id == bus.tripId && it.lastLocation != null }
                Card(
                    onClick = { selectedBusForMap = bus },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, if (isOnline) Color(0xFF10B981) else Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Sl.No + Departure Time Pill
                        Surface(
                            color = if (isOnline) Color(0xFF059669) else Color(0xFF0284C7),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.width(68.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("#${bus.slNo}", color = Color(0xFFBAE6FD), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(bus.departureTime, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${bus.from} → ${bus.to}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = Color(0xFF0F172A)
                            )
                            Text("Via: ${bus.via}", fontSize = 11.sp, color = Color(0xFF475569))
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(color = Color(0xFFF1F5F9), shape = RoundedCornerShape(6.dp)) {
                                    Text(bus.serviceClass, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                                if (isOnline) {
                                    Surface(color = Color(0xFFDCFCE7), shape = RoundedCornerShape(6.dp)) {
                                        Text("🟢 LIVE GPS", color = Color(0xFF15803D), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                } else {
                                    Surface(color = Color(0xFFE0F2FE), shape = RoundedCornerShape(6.dp)) {
                                        Text("${bus.stops.size} stops • Map & Book", color = Color(0xFF0369A1), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        }

                        Icon(Icons.Default.Map, contentDescription = "View Route Map", tint = Color(0xFF0284C7))
                    }
                }
            }
        }
    }

    // Reusable Selected Bus Map, Live Navigation, Booking & Alert Modal
    if (selectedBusForMap != null) {
        SelectedScheduleBusMapDialog(
            bus = selectedBusForMap!!,
            activeTrips = activeTrips,
            userLat = userLat,
            userLng = userLng,
            onRequestMyLocation = onRequestMyLocation,
            onBookSlot = onBookScheduleSlot,
            onHoldSlot = onHoldScheduleSlot,
            onSetWakeUpAlarmForStop = { stop, tripId ->
                selectedBusForMap = null
                onSetWakeUpAlarmForStop(stop, tripId)
            },
            onDismiss = { selectedBusForMap = null }
        )
    }
}

@Composable
fun SelectedScheduleBusMapDialog(
    bus: UdupiScheduleItem,
    activeTrips: List<Trip>,
    userLat: Double?,
    userLng: Double?,
    onRequestMyLocation: () -> Unit,
    onBookSlot: ((UdupiScheduleItem, String) -> Unit)? = null,
    onHoldSlot: ((UdupiScheduleItem) -> Unit)? = null,
    onSetWakeUpAlarmForStop: (UdupiStopCoord, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var showSeatPicker by remember { mutableStateOf(false) }

    val matchingActiveTrip = remember(bus.tripId, bus.busNumber, activeTrips) {
        activeTrips.find { it.id == bus.tripId }
            ?: activeTrips.find { it.route?.name?.contains(bus.to, ignoreCase = true) == true }
    }

    val routeStopsList = bus.stops.map { st ->
        RouteStop(
            id = "rs-${bus.slNo}-${st.order}",
            stopId = "stop-${bus.slNo}-${st.order}",
            stopOrder = st.order,
            stop = Stop(id = "stop-${bus.slNo}-${st.order}", name = st.name, latitude = st.latitude, longitude = st.longitude)
        )
    }
    val mapTrip = Trip(
        id = matchingActiveTrip?.id ?: bus.tripId ?: "schedule-${bus.slNo}",
        busId = "bus-${bus.slNo}",
        routeId = "route-${bus.slNo}",
        status = matchingActiveTrip?.status ?: bus.tripStatus,
        scheduledStart = bus.departureTime,
        bus = Bus(id = "bus-${bus.slNo}", busNumber = "#${bus.slNo} (${bus.departureTime})", registrationNo = bus.busNumber, capacity = 48),
        route = Route(
            id = "route-${bus.slNo}",
            name = "${bus.from} → ${bus.to} (Via ${bus.via})",
            code = "SL-${bus.slNo}",
            stops = routeStopsList
        ),
        lastLocation = matchingActiveTrip?.lastLocation
    )

    if (showSeatPicker) {
        BusSeatSelector(
            busNumber = "#${bus.slNo} (${bus.departureTime})",
            routeName = "${bus.from} → ${bus.to} (Via ${bus.via})",
            baseFare = UdupiLocalTimetable.estimateFareForBus(bus),
            availableSeatsCount = 32,
            onConfirmBooking = { _, _, paymentMode ->
                showSeatPicker = false
                onBookSlot?.invoke(bus, paymentMode)
                onDismiss()
            },
            onHoldBooking = { _, _ ->
                showSeatPicker = false
                onHoldSlot?.invoke(bus)
                onDismiss()
            },
            onDismiss = { showSeatPicker = false }
        )
        return
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                Surface(color = Color(0xFF0F172A)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sl.No #${bus.slNo}: ${bus.from} → ${bus.to}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Dep ${bus.departureTime} • ${bus.serviceClass} • Via ${bus.via}", color = Color(0xFF38BDF8), fontSize = 11.sp)
                        }
                    }
                }

                // Interactive OSRM Road Map + Live Navigation (if online) + Passenger Current Location Blue Pin
                Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                    BusLiveMap(
                        activeTrips = listOf(mapTrip),
                        selectedTrip = mapTrip,
                        onSelectTrip = {},
                        userLat = userLat,
                        userLng = userLng,
                        onRequestMyLocation = onRequestMyLocation
                    )
                }

                // Book Slot & Wake Alert Quick Bar
                if (onBookSlot != null || onHoldSlot != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onHoldSlot != null) {
                            OutlinedButton(
                                onClick = {
                                    onHoldSlot(bus)
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.HourglassTop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Hold Slot", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (onBookSlot != null) {
                            Button(
                                onClick = { showSeatPicker = true },
                                modifier = Modifier.weight(1.3f).height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                            ) {
                                Icon(Icons.Default.AirlineSeatReclineNormal, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Book Slot • ₹${UdupiLocalTimetable.estimateFareForBus(bus).toInt()}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Text(
                    "Route Stops (${bus.stops.size} Stops • Tap Wake Alarm before sleeping)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(bus.stops) { stop ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF0284C7)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("${stop.order}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stop.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        if (stop.order == 1) "Departure ${bus.departureTime} • Udupi Bus Stand"
                                        else if (stop.order == bus.stops.size) "Final Destination"
                                        else "Via Stop #${stop.order}",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                                if (stop.order > 1) {
                                    FilledTonalButton(
                                        onClick = { onSetWakeUpAlarmForStop(stop, mapTrip.id) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.AlarmAdd, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Wake Alarm", fontSize = 11.sp)
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
