package com.smartbus.app.data.model

data class LocationUpdate(
    val latitude: Double,
    val longitude: Double,
    val speed: Double? = 0.0,
    val heading: Double? = 0.0,
    val accuracy: Double? = null,
    val timestamp: String,
    val isStale: Boolean = false,
    val ageSeconds: Long = 0
)

data class PassengerCount(
    val tripId: String,
    val currentOnboard: Int,
    val capacity: Int,
    val availableSeats: Int,
    val totalBoardings: Int,
    val totalExits: Int,
    val prepaidBoardings: Int = 0,
    val conductorBoardings: Int = 0,
    val hasInconsistency: Boolean = false,
    val warning: String? = null
)

data class TripCrew(
    val id: String,
    val firstName: String,
    val lastName: String,
    val phone: String? = null
)

data class TripAssignment(
    val id: String,
    val tripId: String,
    val driver: TripCrew? = null,
    val conductor: TripCrew? = null
)

data class Trip(
    val id: String,
    val busId: String,
    val routeId: String,
    val status: String,
    val scheduledStart: String,
    val scheduledEnd: String? = null,
    val actualStart: String? = null,
    val actualEnd: String? = null,
    val notes: String? = null,
    val bus: Bus? = null,
    val route: Route? = null,
    val assignments: List<TripAssignment> = emptyList(),
    val lastLocation: LocationUpdate? = null,
    val passengerCount: PassengerCount? = null
)

data class LocationPayload(
    val latitude: Double,
    val longitude: Double,
    val speed: Double = 0.0,
    val heading: Double = 0.0,
    val accuracy: Double = 0.0,
    val timestamp: String? = null
)
